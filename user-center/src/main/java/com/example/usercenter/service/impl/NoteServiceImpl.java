package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.LikeRecordMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.StarMapper;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.mapper.PointsLogMapper;
import com.example.usercenter.mapper.UserMapper;
import org.redisson.api.RBloomFilter;
import com.example.usercenter.model.domain.LikeRecord;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.NoteQueryRequest;
import com.example.usercenter.event.NoteHotScoreEvent;
import com.example.usercenter.service.NoteService;
import com.example.usercenter.service.EsSearchService;
import com.example.usercenter.service.KnowledgeService;
import com.example.usercenter.service.PointsService;
import com.example.usercenter.service.TagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.usercenter.contant.UserConstant.ADMIN_ROLE;
import static com.example.usercenter.contant.UserConstant.USER_LOGIN_STATE;
import com.example.usercenter.model.domain.StarMember;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.SensitiveWordChecker;
import com.example.usercenter.utils.UserContext;

/**
 * 笔记服务实现类
 * @author zy
 */
@Service
@Slf4j
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements NoteService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private SensitiveWordChecker sensitiveWordChecker;

    @Resource
    private StarMapper starMapper;

    @Resource
    private StarMemberMapper starMemberMapper;

    @Resource
    private PointsLogMapper pointsLogMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource(name = "esSearchServiceImpl")
    private EsSearchService esSearchService;

    @Lazy
    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private TagService tagService;

    @Resource
    private PointsService pointsService;

    @Resource
    private RBloomFilter<Long> noteBloomFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Cacheable(value = "noteList", key = "T(String).valueOf(#request.keyword ?: '') + ':' + T(String).valueOf(#request.category ?: '') + ':' + T(String).valueOf(#request.contentType ?: '') + ':' + T(String).valueOf(#request.sortType ?: '') + ':' + T(String).valueOf(#request.userId ?: '') + ':' + T(String).valueOf(#request.page ?: 1) + ':' + T(String).valueOf(#request.pageSize ?: 10)", unless = "#result == null || #result.records.isEmpty()")
    public PageResult<Note> getNoteList(NoteQueryRequest request) {
        int pageSize = request.getPageSize() == null ? 10 : request.getPageSize();
        if (pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过50");
        }
        int pageNum = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();

        IPage<Note> pageParam = new Page<>(pageNum, pageSize);
        IPage<Note> result = noteMapper.selectPageWithCondition(
                pageParam,
                request.getKeyword(),
                request.getCategory(),
                request.getContentType(),
                request.getSortType(),
                request.getUserId()
        );

        List<Note> records = result.getRecords();
        fillAuthorInfoBatch(records);
        records.forEach(note -> {
            processNoteTags(note);
            if (note.getPublishTime() != null) {
                note.setPublishTimestamp(note.getPublishTime().getTime());
            }
        });

        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public Note getNoteDetail(Long id, Long userId, boolean isAdmin) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        // 布隆过滤器拦截（仅访客走布隆；draft/scheduled 不在布隆里，作者/管理员跳过避免误伤）
        if (userId == null || !isAdmin) {
            if (!noteBloomFilter.contains(id)) {
                log.debug("布隆过滤器拦截: noteId={} 不存在", id);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
            }
        }

        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }

        // status 权限过滤：非作者非管理员访问非 published 笔记视为不存在
        boolean isOwner = userId != null && note.getAuthorId() != null && userId.equals(note.getAuthorId());
        if (!"published".equals(note.getStatus()) && !isOwner && !isAdmin) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }

        processNoteTags(note);
        fillAuthorInfo(note);
        if (note.getPublishTime() != null) {
            note.setPublishTimestamp(note.getPublishTime().getTime());
        }

        return note;
    }

    @Override
    @CacheEvict(value = "noteList", allEntries = true)
    public Note createNote(Note note, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        // 参数校验
        if (note == null || StringUtils.isBlank(note.getTitle()) || StringUtils.isBlank(note.getContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题和内容不能为空");
        }

        // 敏感词逐字段校验（命中时错误含字段名，方便前端定位高亮）
        checkSensitive("笔记标题", note.getTitle());
        checkSensitive("笔记摘要", note.getSummary());
        checkSensitive("笔记正文", note.getContent());

        if (note.getTitle().length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能超过100字符");
        }

        if (StringUtils.isBlank(note.getCategory())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类不能为空");
        }

        // 【安全修复】校验笔记归属的星球：starId 来自客户端请求体，
        // 不校验则任何登录用户都能把内容写入任意星球（含自己未加入的），击穿多租户写入隔离。
        if (note.getStarId() != null) {
            QueryWrapper<StarMember> memberWrapper = new QueryWrapper<>();
            memberWrapper.eq("star_id", note.getStarId()).eq("user_id", loginUser.getId());
            Long memberCount = starMemberMapper.selectCount(memberWrapper);
            if (memberCount == null || memberCount == 0) {
                throw new BusinessException(ErrorCode.NO_AUTH, "你不是该星球的成员，无法在该星球发布内容");
            }
        }

        // 设置作者信息
        note.setAuthorId(loginUser.getId());
        note.setAuthor(loginUser.getUsername());
        note.setViewCount(0);
        note.setCommentCount(0);
        note.setLikeCount(0);
        // 付费笔记售价（0 = 免费；上限按用户等级动态给出）
        note.setPrice(normalizeNotePrice(note.getPrice(), loginUser.getId()));
        // status：空默认 published；支持 draft/scheduled
        if (StringUtils.isBlank(note.getStatus())) {
            note.setStatus("published");
        }
        if ("scheduled".equals(note.getStatus())) {
            // 定时发布需指定未来的发布时间
            if (note.getPublishTime() == null || !note.getPublishTime().after(new Date())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "定时发布需指定未来的发布时间");
            }
        } else if ("draft".equals(note.getStatus())) {
            // 草稿不记录发布时间
            note.setPublishTime(null);
        } else {
            // published
            note.setPublishTime(new Date());
        }
        note.setCreateTime(new Date());
        note.setUpdateTime(new Date());

        // 处理标签
        if (note.getTagList() != null && !note.getTagList().isEmpty()) {
            try {
                note.setTags(objectMapper.writeValueAsString(note.getTagList()));
            } catch (Exception e) {
                log.error("标签序列化失败", e);
            }
        }

        boolean saved = this.save(note);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建笔记失败");
        }

        processNoteTags(note);
        // 仅 published 触发发布副作用（布隆/ES/content_count/知识地图/标签关联/积分）
        // draft/scheduled 不进发现流，等草稿发布或定时任务到点再触发
        if ("published".equals(note.getStatus())) {
            publishSideEffects(note, true);
        }
        return note;
    }

    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean updateNote(Long id, Note note, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        Note oldNote = this.getById(id);
        if (oldNote == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }

        // 只能修改自己的笔记或管理员可以修改
        if (!oldNote.getAuthorId().equals(loginUser.getId()) && !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        // 敏感词校验（仅校验本次提交的字段）
        sensitiveWordChecker.check("笔记标题或内容", note.getTitle(), note.getSummary(), note.getContent());

        // 更新字段
        if (StringUtils.isNotBlank(note.getTitle())) {
            oldNote.setTitle(note.getTitle());
        }
        if (StringUtils.isNotBlank(note.getContent())) {
            oldNote.setContent(note.getContent());
        }
        if (StringUtils.isNotBlank(note.getCategory())) {
            oldNote.setCategory(note.getCategory());
        }
        if (StringUtils.isNotBlank(note.getContentType())) {
            oldNote.setContentType(note.getContentType());
        }
        if (StringUtils.isNotBlank(note.getSummary())) {
            oldNote.setSummary(note.getSummary());
        }
        if (StringUtils.isNotBlank(note.getCoverImage())) {
            oldNote.setCoverImage(note.getCoverImage());
        }
        // 付费笔记售价（传 null 表示不修改；上限按用户等级动态给出）
        if (note.getPrice() != null) {
            oldNote.setPrice(normalizeNotePrice(note.getPrice(), loginUser.getId()));
        }
        String previousStatus = oldNote.getStatus();
        boolean becamePublished = false;
        if (StringUtils.isNotBlank(note.getStatus())) {
            String newStatus = note.getStatus();
            // 禁止 rejected → published/scheduled 绕过审核
            if ("rejected".equals(previousStatus)
                    && ("published".equals(newStatus) || "scheduled".equals(newStatus))) {
                throw new BusinessException(ErrorCode.NO_AUTH, "被拒绝的笔记需修改后重新提交，不能直接发布");
            }
            // 【修复】禁止把「待审（pending）」的笔记直接改回发布态。
            // 举报会通过 markNotePending 把笔记置为 pending，原实现只拦 rejected，
            // 作者可将被举报笔记一键改回 published，审核形同虚设。
            if ("pending".equals(previousStatus)
                    && ("published".equals(newStatus) || "scheduled".equals(newStatus))
                    && !isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH, "笔记正在审核中，不能直接发布，请等待审核结果");
            }
            oldNote.setStatus(newStatus);
            // 非发布态 → 发布态：刷新发布时间并标记触发发布副作用
            if ("published".equals(newStatus) && !"published".equals(previousStatus)) {
                oldNote.setPublishTime(new Date());
                // 【修复】仅「从未发放过发布积分的草稿/定时笔记」才触发完整发布副作用。
                // 原条件为 !"published".equals(previousStatus)，配合 published → draft → published
                // 可无限重复发放 +20 积分、重复累加星球 content_count。
                // 注意：becamePublished=false 时下方 "published" 分支仍会同步 ES/知识地图/标签。
                becamePublished = ("draft".equals(previousStatus) || "scheduled".equals(previousStatus))
                        && !hasAwardedPublishPoints(oldNote.getId());
            }
        }
        // 记录旧标签列表（用于 diff 关联表），在 setTags 覆盖前取
        List<String> oldTagList = parseTagsJsonToList(oldNote.getTags());
        if (note.getTagList() != null) {
            try {
                oldNote.setTags(objectMapper.writeValueAsString(note.getTagList()));
            } catch (Exception e) {
                log.error("标签序列化失败", e);
            }
        }

        oldNote.setUpdateTime(new Date());
        boolean result = this.updateById(oldNote);
        if (result) {
            processNoteTags(oldNote);
            if (becamePublished) {
                // 草稿/定时/待审 → 已发布：触发完整发布副作用（含积分，首次发布）
                publishSideEffects(oldNote, true);
            } else if ("published".equals(oldNote.getStatus())) {
                // 已发布笔记编辑后仍发布：同步 ES + 知识地图 + 标签 diff
                try { esSearchService.indexNote(oldNote); }
                catch (Exception e) { log.warn("ES 索引失败: noteId={}", id, e); }
                try { knowledgeService.syncNodeFromNote(oldNote); }
                catch (Exception e) { log.warn("知识地图同步失败: noteId={}", id, e); }
                if (note.getTagList() != null) {
                    try { tagService.syncNoteTags(id, oldTagList, note.getTagList()); }
                    catch (Exception e) { log.warn("标签关联同步失败: noteId={}", id, e); }
                }
            } else if ("published".equals(previousStatus)) {
                // 【修复】原本已发布、本次改成 draft/pending/rejected 等非公开状态：
                // 必须做逆向清理，否则已下架内容仍留在 ES 搜索结果中、星球计数虚高。
                demotePublishedSideEffects(oldNote);
            }
            // draft/scheduled 编辑：不索引 ES，不进发现流
        }
        return result;
    }

    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean deleteNote(Long id, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }

        // 只能删除自己的笔记或管理员可以删除
        if (!note.getAuthorId().equals(loginUser.getId()) && !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        boolean result = this.removeById(id);
        // 从 ES 删除，并维护星球内容计数
        if (result) {
            esSearchService.deleteNote(id);
            if (note.getStarId() != null && "published".equals(note.getStatus())) {
                starMapper.updateContentCount(note.getStarId(), -1);
            }
            // 从星球知识地图移除节点及相关连线
            knowledgeService.removeNodeFromNote(note.getStarId(), id);
        }
        return result;
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "noteDetail", key = "#id"),
        @CacheEvict(value = "noteList", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public boolean likeNote(Long id, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }

        // 直接尝试插入，依赖唯一约束 uk_user_target 保证幂等（消除 TOCTOU 窗口）
        LikeRecord likeRecord = new LikeRecord();
        likeRecord.setUserId(loginUser.getId());
        likeRecord.setTargetType("note");
        likeRecord.setTargetId(id);
        likeRecord.setCreateTime(new Date());
        try {
            likeRecordMapper.insert(likeRecord);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已点赞，请勿重复操作");
        }

        // INSERT 成功后再递增计数（保证一致性）
        noteMapper.incrementLikeCount(id);

        // 异步更新热度分（通过事件驱动，避免 @Async 自调用失效）
        eventPublisher.publishEvent(new NoteHotScoreEvent(this, id));

        // 给笔记作者 +2 积分
        // 【修复】自赞不计分：否则作者可以给自己的笔记反复点赞刷分（虽受唯一约束限制，
        // 但配合多篇笔记仍可稳定增加收益），这里直接排除作者本人。
        if (note.getAuthorId() != null && !note.getAuthorId().equals(loginUser.getId())) {
            pointsService.addPoints(note.getAuthorId(), 2, "like", "note", id, "笔记被点赞");
        }

        return true;
    }

    @Override
    public boolean increaseViewCount(Long id) {
        try {
            // Redis 可用路径：缓冲增量，原子 INCR
            redisTemplate.opsForValue().increment("note:view:" + id);
            // 异步更新热度分
            eventPublisher.publishEvent(new NoteHotScoreEvent(this, id));
            return true;
        } catch (RedisConnectionFailureException e) {
            // Redis 不可用降级路径
            log.warn("Redis不可用，降级为直接写库, noteId={}", id);
            boolean result = noteMapper.incrementViewCount(id) > 0;
            if (result) {
                eventPublisher.publishEvent(new NoteHotScoreEvent(this, id));
            }
            return result;
        } catch (Exception e) {
            // 其他异常也降级
            log.warn("Redis异常，降级为直接写库, noteId={}, error={}", id, e.getMessage());
            boolean result = noteMapper.incrementViewCount(id) > 0;
            if (result) {
                eventPublisher.publishEvent(new NoteHotScoreEvent(this, id));
            }
            return result;
        }
    }

    /**
     * 异步更新评论数（需求17：异步处理）
     */
    @Override
    @Async("asyncTaskExecutor")
    public void asyncIncrementCommentCount(Long noteId) {
        try {
            noteMapper.incrementCommentCount(noteId);
        } catch (Exception e) {
            log.error("异步更新评论数失败, noteId={}: {}", noteId, e.getMessage());
        }
    }

    @Override
    public List<Note> searchContent(String keyword, String contentType, String status, Long authorId) {
        QueryWrapper<Note> queryWrapper = new QueryWrapper<>();
        
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like("title", keyword).or().like("content", keyword));
        }
        if (StringUtils.isNotBlank(contentType)) {
            queryWrapper.eq("content_type", contentType);
        }
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq("status", status);
        }
        if (authorId != null) {
            queryWrapper.eq("author_id", authorId);
        }

        queryWrapper.orderByDesc("create_time");

        List<Note> notes = this.list(queryWrapper);
        fillAuthorInfoBatch(notes);
        notes.forEach(this::processNoteTags);
        return notes;
    }

    @Override
    public boolean topNote(Long id, Integer isTop) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }

        // 置顶时校验当前置顶数量
        if (Integer.valueOf(1).equals(isTop)) {
            QueryWrapper<Note> countWrapper = new QueryWrapper<>();
            countWrapper.eq("is_top", 1).eq("status", "published");
            long topCount = this.count(countWrapper);
            if (topCount >= 10) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "置顶笔记数量已达上限（最多10条）");
            }
        }

        Note updateNote = new Note();
        updateNote.setId(id);
        updateNote.setIsTop(isTop);
        return this.updateById(updateNote);
    }

    /**
     * 用积分自助置顶笔记。
     *
     * <p>消耗 {@code PointsService.BOOST_COST} 积分、置顶 {@code BOOST_HOURS} 小时；
     * 到期由 {@code ExpireTopNoteTask} 自动取消（把 is_top 置回 0 并清空 top_until）。
     * 与管理员置顶的区别：管理员置顶不带 top_until，不会自动过期。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean boostNote(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }
        if (!userId.equals(note.getAuthorId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只能置顶自己的笔记");
        }
        if (!"published".equals(note.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只有已发布的笔记可以置顶");
        }
        // 置顶数量上限（排除自己，便于对已置顶的笔记续期）
        QueryWrapper<Note> countWrapper = new QueryWrapper<>();
        countWrapper.eq("is_top", 1).eq("status", "published").ne("id", id);
        if (this.count(countWrapper) >= 10) {
            throw new BusinessException(ErrorCode.TOO_MANY_TOPS);
        }
        // 扣积分（余额不足抛异常；本方法有事务，不会出现「扣了分却没置顶」）
        pointsService.spend(userId, PointsService.BOOST_COST, "boost", "note", id,
                "自助置顶《" + note.getTitle() + "》" + PointsService.BOOST_HOURS + " 小时");

        Date until = new Date(System.currentTimeMillis() + PointsService.BOOST_HOURS * 3600_000L);
        Note update = new Note();
        update.setId(id);
        update.setIsTop(1);
        update.setTopUntil(until);
        boolean ok = this.updateById(update);
        log.info("自助置顶成功: noteId={}, userId={}, 到期时间={}", id, userId, until);
        return ok;
    }

    /**
     * 处理标签（JSON字符串和List互转）
     */
    private void processNoteTags(Note note) {
        if (StringUtils.isNotBlank(note.getTags())) {
            try {
                note.setTagList(objectMapper.readValue(note.getTags(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.error("标签反序列化失败", e);
                note.setTagList(java.util.Collections.emptyList());
            }
        } else {
            note.setTagList(java.util.Collections.emptyList());
        }
    }

    /**
     * 解析 tags JSON 字符串为列表（用于 diff 标签关联表）
     */
    private List<String> parseTagsJsonToList(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) return java.util.Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 填充作者信息（单条，委托批量方法）
     */
    private void fillAuthorInfo(Note note) {
        if (note != null) {
            fillAuthorInfoBatch(List.of(note));
        }
    }

    /**
     * 批量填充作者信息（消除 N+1 查询）
     */
    private void fillAuthorInfoBatch(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Set<Long> authorIds = new java.util.HashSet<>();
        notes.forEach(n -> {
            if (n.getAuthorId() != null) authorIds.add(n.getAuthorId());
        });
        if (authorIds.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = userMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        for (Note note : notes) {
            if (note.getAuthorId() == null) continue;
            User user = userMap.get(note.getAuthorId());
            if (user == null) continue;
            if (StringUtils.isBlank(note.getAuthor())) {
                note.setAuthor(user.getUsername());
            }
            // 填充作者头像，供详情页作者信息栏展示
            if (StringUtils.isBlank(note.getAuthorAvatar())) {
                note.setAuthorAvatar(user.getAvatarUrl());
            }
        }
    }

    /**
     * 获取登录用户
     */
    private User getLoginUser(HttpServletRequest request) {
        LoginUserDTO dto = UserContext.get();
        if (dto == null) return null;
        User user = new User();
        user.setId(dto.getUserId());
        user.setUserRole(dto.getUserRole());
        return user;
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return user != null && user.getUserRole() != null && user.getUserRole() == ADMIN_ROLE;
    }

    /**
     * 笔记发布副作用：布隆过滤器 + 星球内容计数 + ES 索引 + 知识地图 + 标签关联 + 积分
     * 由 createNote(published)、approveNote、ScheduledPublishTask 复用，保证三处一致
     * @param awardPoints 是否发放发布积分（首次发布 true；审核通过 false，因举报转 pending 前已发过）
     */
    /**
     * 校验并归一化笔记售价：null / 负数 → 0（免费），超过「当前等级售价上限」抛异常。
     *
     * <p>上限不再是一个写死的常量，而是按用户等级（累计获得积分）动态给出，
     * 见 {@code PointsLevel}。L1（新手）上限为 0，即新号不能给内容定价。</p>
     */
    private int normalizeNotePrice(Integer price, Long userId) {
        if (price == null || price < 0) {
            return 0;
        }
        int limit = pointsService.getPriceLimit(userId);
        if (price > limit) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, limit <= 0
                    ? "当前等级暂不能设置售价，可通过签到、发布、互动累积积分提升等级"
                    : "当前等级售价上限为 " + limit + " 积分，提升等级可获得更高上限");
        }
        return price;
    }

    /**
     * 该笔记是否已发放过「发布笔记」积分。
     *
     * <p>用于让发布副作用（+20 积分 / 星球内容计数 +1）幂等：同一篇笔记
     * 反复 {@code published → draft → published} 不应重复发放积分。</p>
     */
    private boolean hasAwardedPublishPoints(Long noteId) {
        if (noteId == null) return false;
        Long cnt = pointsLogMapper.selectCount(new QueryWrapper<PointsLog>()
                .eq("type", "publish").eq("ref_type", "note").eq("ref_id", noteId));
        return cnt != null && cnt > 0;
    }

    private void publishSideEffects(Note note, boolean awardPoints) {
        // 加入布隆过滤器
        try { noteBloomFilter.add(note.getId()); }
        catch (Exception e) { log.warn("布隆过滤器添加失败: noteId={}", note.getId(), e); }
        // 维护星球内容计数
        if (note.getStarId() != null) {
            try { starMapper.updateContentCount(note.getStarId(), 1); }
            catch (Exception e) { log.warn("星球内容计数+1失败: noteId={}", note.getId(), e); }
        }
        // 同步到 ES
        try { esSearchService.indexNote(note); }
        catch (Exception e) { log.warn("ES 索引失败: noteId={}", note.getId(), e); }
        // 同步到星球知识地图（自动生成节点+连线）
        try { knowledgeService.syncNodeFromNote(note); }
        catch (Exception e) { log.warn("知识地图同步失败: noteId={}", note.getId(), e); }
        // 双写标签关联表（仅已发布笔记进入标签广场）
        if (note.getTagList() != null) {
            try { tagService.syncNoteTags(note.getId(), java.util.Collections.emptyList(), note.getTagList()); }
            catch (Exception e) { log.warn("标签关联同步失败: noteId={}", note.getId(), e); }
        }
        // 发布笔记 +20 积分（仅首次发布）
        if (awardPoints && note.getAuthorId() != null) {
            try { pointsService.addPoints(note.getAuthorId(), 20, "publish", "note", note.getId(), "发布笔记"); }
            catch (Exception e) { log.warn("发布积分发放失败: noteId={}", note.getId(), e); }
        }
    }

    /**
     * 审核通过笔记（管理员）—— 统一走 NoteService，补发布副作用（不重发积分）
     */
    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean approveNote(Long id) {
        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }
        boolean wasPublished = "published".equals(note.getStatus());
        note.setStatus("published");
        if (!wasPublished) {
            note.setPublishTime(new Date());
        }
        boolean result = this.updateById(note);
        if (result && !wasPublished) {
            // 审核通过：补布隆/ES/content_count/知识地图/标签关联，但不重发积分（举报转 pending 前已发过）
            processNoteTags(note);
            publishSideEffects(note, false);
        }
        return result;
    }

    /**
     * 拒绝笔记（管理员）—— 若原 published 则从 ES 移除并 content_count-1
     */
    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean rejectNote(Long id) {
        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
        }
        boolean wasPublished = "published".equals(note.getStatus());
        note.setStatus("rejected");
        note.setUpdateTime(new Date());
        boolean result = this.updateById(note);
        if (result && wasPublished) {
            // 原发布态被拒：从 ES 移除并星球内容计数-1、知识地图移除节点
            try { esSearchService.deleteNote(id); }
            catch (Exception e) { log.warn("ES 删除失败: noteId={}", id, e); }
            if (note.getStarId() != null) {
                try { starMapper.updateContentCount(note.getStarId(), -1); }
                catch (Exception e) { log.warn("星球内容计数-1失败: noteId={}", id, e); }
            }
            try { knowledgeService.removeNodeFromNote(note.getStarId(), id); }
            catch (Exception e) { log.warn("知识地图节点移除失败: noteId={}", id, e); }
        }
        return result;
    }

    /**
     * 查询当前用户的笔记（草稿箱/我的内容，按 status 筛选）
     */
    @Override
    public PageResult<Note> getMyNotes(Long userId, String status, Integer page, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        int pageNum = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        QueryWrapper<Note> wrapper = new QueryWrapper<>();
        wrapper.eq("author_id", userId);
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("update_time");
        IPage<Note> result = this.page(new Page<>(pageNum, size), wrapper);

        List<Note> records = result.getRecords();
        fillAuthorInfoBatch(records);
        records.forEach(this::processNoteTags);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /**
     * 发布定时笔记（由 ScheduledPublishTask 到点调用）：转 published + 触发发布副作用（含积分，首次发布）
     */
    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean publishScheduledNote(Long noteId) {
        Note note = this.getById(noteId);
        if (note == null) {
            log.warn("定时发布：笔记不存在 noteId={}", noteId);
            return false;
        }
        if (!"scheduled".equals(note.getStatus())) {
            log.debug("定时发布：笔记非 scheduled 态，跳过 noteId={} status={}", noteId, note.getStatus());
            return false;
        }
        note.setStatus("published");
        note.setPublishTime(new Date());
        boolean result = this.updateById(note);
        if (result) {
            processNoteTags(note);
            publishSideEffects(note, true);
        }
        return result;
    }

    /**
     * 获取已发布笔记详情（多级缓存，全员共享 published 内容）
     * 非 published 抛 NOT_FOUND（不缓存）；布隆过滤器防穿透
     */
    @Override
    @Cacheable(value = "noteDetail", key = "#id", unless = "#result == null")
    public Note getNoteDetailPublished(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        // 布隆过滤器拦截防穿透（published 笔记才在布隆里）
        if (!noteBloomFilter.contains(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        Note note = this.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        // 只缓存 published，非 published 抛异常不缓存（draft/pending/rejected 不进缓存）
        if (!"published".equals(note.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        processNoteTags(note);
        fillAuthorInfo(note);
        if (note.getPublishTime() != null) {
            note.setPublishTimestamp(note.getPublishTime().getTime());
        }
        return note;
    }

    /**
     * 标记笔记为待审核（举报触发），清 noteDetail/noteList 缓存
     */
    @Override
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public boolean markNotePending(Long id) {
        Note note = this.getById(id);
        if (note == null) {
            // 与原实现保持一致的宽松语义：笔记不存在时静默失败（举报流程不应因此报错）
            return false;
        }
        boolean wasPublished = "published".equals(note.getStatus());
        note.setStatus("pending");
        note.setUpdateTime(new Date());
        boolean result = this.updateById(note);
        // 【修复】原实现只改 status、不做逆向清理 → 被举报笔记仍留在 ES 索引中
        // （搜索结果里还能搜到），且星球 content_count 虚高、知识地图节点残留。
        // 这里复用与 rejectNote 相同的降态清理；审核通过时由 approveNote 补回。
        if (result && wasPublished) {
            demotePublishedSideEffects(note);
        }
        return result;
    }

    /**
     * 已发布内容「降态」（转为 pending / rejected / draft 等非公开状态）时的逆向清理：
     * 从 ES 移除索引、星球内容计数 -1、知识地图移除节点。
     *
     * <p>原先只有 {@code rejectNote} 做了这件事；举报冻结（markNotePending）与作者把
     * 已发布笔记改回草稿都只改了 status，导致内容在搜索结果中继续可见、计数长期虚高。</p>
     */
    private void demotePublishedSideEffects(Note note) {
        if (note == null || note.getId() == null) return;
        try { esSearchService.deleteNote(note.getId()); }
        catch (Exception e) { log.warn("ES 删除失败: noteId={}", note.getId(), e); }
        if (note.getStarId() != null) {
            try { starMapper.updateContentCount(note.getStarId(), -1); }
            catch (Exception e) { log.warn("星球内容计数-1失败: noteId={}", note.getId(), e); }
        }
        try { knowledgeService.removeNodeFromNote(note.getStarId(), note.getId()); }
        catch (Exception e) { log.warn("知识地图节点移除失败: noteId={}", note.getId(), e); }
    }

    /**
     * 敏感词逐字段校验：命中时抛出含字段名 + 命中词的异常，方便前端定位高亮
     */
    private void checkSensitive(String field, String text) {
        String hit = sensitiveWordChecker.findFirst(text);
        if (hit != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    field + "包含敏感词「" + hit + "」，请修改后再发布");
        }
    }
}
