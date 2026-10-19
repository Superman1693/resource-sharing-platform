package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.CommentMapper;
import com.example.usercenter.mapper.LikeRecordMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Comment;
import com.example.usercenter.model.domain.LikeRecord;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.enums.CommentStatus;
import com.example.usercenter.service.CommentService;
import com.example.usercenter.service.PointsService;
import com.example.usercenter.service.ReportService;
import com.example.usercenter.service.NotificationService;
import com.example.usercenter.service.NoteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.example.usercenter.contant.UserConstant.ADMIN_ROLE;
import static com.example.usercenter.contant.UserConstant.USER_LOGIN_STATE;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.SensitiveWordChecker;
import com.example.usercenter.utils.UserContext;

/**
 * 评论服务实现类
 * @author zy
 */
@Service
@Slf4j
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NoteService noteService;

    @Resource
    private SensitiveWordChecker sensitiveWordChecker;

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private PointsService pointsService;

    @Resource
    private ReportService reportService;

    @Resource
    private CommentMapper commentMapper;

    /**
     * 获取评论列表（修复分页 + 填充更多字段）
     */
    @Override
    public List<Comment> getCommentList(Long noteId, String keyword, String status, Integer page, Integer pageSize, boolean isAdmin) {
        // 1. 构建分页对象（修复原分页不生效问题）
        Page<Comment> commentPage = new Page<>(page, pageSize);

        // 2. 构建查询条件（核心：按权限筛选状态）
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();

        // 笔记ID筛选
        if (noteId != null) {
            queryWrapper.eq("note_id", noteId);
        }

        // 关键词筛选
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like("content", keyword);
        }

        // 状态筛选：评论已无「待审核」概念。
        // 普通用户恒只看 approved（hidden 是被屏蔽内容，不能越权查看）；
        // 管理员可显式筛选任意状态，或不传 status 查看全部（便于在「评论管理」中处理）。
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq("status", isAdmin ? status : CommentStatus.APPROVED.getCode());
        } else if (!isAdmin) {
            queryWrapper.eq("status", CommentStatus.APPROVED.getCode());
        }

        // 只查询顶级评论（非回复）
        queryWrapper.isNull("parent_id");
        queryWrapper.orderByDesc("create_time");

        // 3. 分页查询主评论
        IPage<Comment> pageResult = this.page(commentPage, queryWrapper);
        List<Comment> comments = pageResult.getRecords();
        if (comments.isEmpty()) {
            return comments;
        }

        // 4. 批量查询所有回复（消除 N+1：原来每个主评论单独查一次回复）
        Set<Long> parentIds = comments.stream()
                .map(Comment::getId)
                .collect(Collectors.toSet());
        QueryWrapper<Comment> replyWrapper = new QueryWrapper<>();
        replyWrapper.in("parent_id", parentIds);
        if (!isAdmin) {
            replyWrapper.eq("status", "approved");
        }
        replyWrapper.orderByAsc("create_time");
        List<Comment> allReplies = this.list(replyWrapper);
        Map<Long, List<Comment>> replyMap = allReplies.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        // 5. 批量填充用户信息和笔记信息（消除 N+1：原来每条评论单独查 user 和 note）
        List<Comment> allComments = new ArrayList<>(comments);
        allComments.addAll(allReplies);
        fillCommentInfoBatch(allComments);

        // 6. 将回复挂到主评论上
        for (Comment comment : comments) {
            comment.setReplies(replyMap.getOrDefault(comment.getId(), new ArrayList<>()));
        }

        return comments;
    }

    /**
     * 添加评论（补充笔记存在性校验）
     */
    @Override
    public Comment addComment(Long noteId, String content, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        // 补充：校验笔记是否存在
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }

        if (noteId == null || StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能超过500字符");
        }
        // 敏感词校验
        sensitiveWordChecker.check("评论内容", content);

        Comment comment = new Comment();
        comment.setNoteId(noteId);
        // 多租户：评论的 star_id 由所属笔记派生（笔记为全平台公开内容时为空）
        comment.setStarId(note.getStarId());
        comment.setUserId(loginUser.getId());
        comment.setContent(content);
        // 【变更】评论不再人工审核：上面的 sensitiveWordChecker.check 已做敏感词拦截
        // （命中直接抛异常拒绝发布），通过校验的评论立即可见。
        comment.setStatus(CommentStatus.APPROVED.getCode());
        comment.setLikeCount(0);
        comment.setCreateTime(new Date());

        boolean saved = this.save(comment);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "添加评论失败");
        }

        // 异步更新笔记评论数
        noteService.asyncIncrementCommentCount(noteId);

        // 通知笔记作者（评论审核通过后才有意义，但先发通知让作者知道有人评论了）
        User sender = userMapper.selectById(loginUser.getId());
        if (sender != null && note.getAuthorId() != null) {
            notificationService.send(
                    note.getAuthorId(),
                    loginUser.getId(),
                    sender.getUsername(),
                    sender.getAvatarUrl(),
                    "comment_note",
                    noteId,
                    note.getTitle(),
                    sender.getUsername() + " 评论了你的笔记《" + note.getTitle() + "》"
            );
        }

        // 发表评论 +1 积分
        pointsService.addPoints(loginUser.getId(), 1, "comment", "comment", comment.getId(), "发表评论");
        return fillCommentInfo(comment);
    }

    /**
     * 回复评论（补充父评论存在性校验）
     */
    @Override
    public Comment replyComment(Long commentId, Long noteId, String content, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        // 补充：校验父评论是否存在
        Comment parentComment = this.getById(commentId);
        if (parentComment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "被回复的评论不存在");
        }
        // 补充：校验笔记是否存在
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }

        if (commentId == null || noteId == null || StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回复内容不能超过500字符");
        }
        // 敏感词校验
        sensitiveWordChecker.check("回复内容", content);

        Comment comment = new Comment();
        comment.setNoteId(noteId);
        // 多租户：评论的 star_id 由所属笔记派生（笔记为全平台公开内容时为空）
        comment.setStarId(note.getStarId());
        comment.setUserId(loginUser.getId());
        comment.setContent(content);
        comment.setParentId(commentId);
        // 【变更】同 addComment：回复也不再审核，敏感词校验已在上方完成
        comment.setStatus(CommentStatus.APPROVED.getCode());
        comment.setLikeCount(0);
        comment.setCreateTime(new Date());

        boolean saved = this.save(comment);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "回复评论失败");
        }

        // 【修复】回复同样属于评论，需计入笔记评论数。
        // 原实现只在 addComment 中调用，导致 note.comment_count 长期不含回复。
        noteService.asyncIncrementCommentCount(noteId);

        // 通知被回复的评论作者
        User sender = userMapper.selectById(loginUser.getId());
        if (sender != null && parentComment.getUserId() != null) {
            notificationService.send(
                    parentComment.getUserId(),
                    loginUser.getId(),
                    sender.getUsername(),
                    sender.getAvatarUrl(),
                    "reply_comment",
                    noteId,
                    note.getTitle(),
                    sender.getUsername() + " 回复了你在《" + note.getTitle() + "》下的评论"
            );
        }

        // 回复评论 +1 积分
        pointsService.addPoints(loginUser.getId(), 1, "comment", "comment", comment.getId(), "回复评论");
        return fillCommentInfo(comment);
    }

    /**
     * @提及通知：遍历被提及用户，发送 mention 通知
     * 排除发送者自己、笔记作者（作者已收 comment_note）
     */
    @Override
    public void notifyMentioned(List<Long> mentionedUserIds, Long noteId, Long senderId, String content) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty() || senderId == null) return;
        User sender = userMapper.selectById(senderId);
        if (sender == null) return;
        Note note = noteMapper.selectById(noteId);
        if (note == null) return;
        String snippet = (content != null && content.length() > 30) ? content.substring(0, 30) : (content == null ? "" : content);
        String notifContent = sender.getUsername() + " 在笔记《" + note.getTitle() + "》的评论中提及了你：" + snippet;
        for (Long uid : mentionedUserIds) {
            if (uid == null || uid.equals(senderId)) continue;
            if (note.getAuthorId() != null && uid.equals(note.getAuthorId())) continue;
            try {
                notificationService.send(uid, senderId, sender.getUsername(), sender.getAvatarUrl(),
                        "mention", noteId, note.getTitle(), notifContent);
            } catch (Exception e) {
                log.warn("mention 通知发送失败: userId={}", uid, e);
            }
        }
    }

    /**
     * 删除评论（级联删除回复）
     */
    @Override
    public boolean deleteComment(Long id, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        Comment comment = this.getById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }

        // 只能删除自己的评论或管理员可以删除
        if (!comment.getUserId().equals(loginUser.getId()) && !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限删除该评论");
        }

        // 补充：级联删除该评论的所有回复
        QueryWrapper<Comment> replyWrapper = new QueryWrapper<>();
        replyWrapper.eq("parent_id", id);
        long replyCount = this.count(replyWrapper);
        this.remove(replyWrapper);

        // 删除主评论
        boolean removed = this.removeById(id);
        // 【修复】删除评论需同步递减笔记评论数（含级联删除的回复）。
        // 原实现只删记录、不减计数，导致 note.comment_count 只增不减、永久虚高。
        if (removed) {
            noteMapper.decrementCommentCount(comment.getNoteId(), (int) (replyCount + 1));
        }
        return removed;
    }

    /**
     * 设置评论可见状态（管理员）。
     *
     * <p>评论不再经过人工审核（发布时只做敏感词过滤），因此本方法只承担
     * 「屏蔽违规评论 / 恢复被举报冻结的评论」这一内容治理职责。</p>
     */
    @Override
    public boolean setCommentStatus(Long id, String status, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "仅管理员可操作评论状态");
        }
        if (!CommentStatus.APPROVED.getCode().equals(status)
                && !CommentStatus.HIDDEN.getCode().equals(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态只能是 approved 或 hidden");
        }
        Comment comment = this.getById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }
        comment.setStatus(status);
        comment.setUpdateTime(new Date());
        return this.updateById(comment);
    }

    /**
     * 点赞评论（优化空值处理）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likeComment(Long id, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        Comment comment = this.getById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }

        // 插入 like_record，依赖唯一约束 uk_user_target 防止重复点赞
        LikeRecord likeRecord = new LikeRecord();
        likeRecord.setUserId(loginUser.getId());
        likeRecord.setTargetType("comment");
        likeRecord.setTargetId(id);
        likeRecord.setCreateTime(new Date());
        try {
            likeRecordMapper.insert(likeRecord);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已点赞，请勿重复操作");
        }

        // INSERT 成功后原子递增计数（避免并发丢失更新）
        commentMapper.incrementLikeCount(id);
        return true;
    }

    @Override
    public boolean reportComment(Long id, String reason, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        Comment comment = this.getById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        }

        // 【变更】举报即冻结：直接把评论置为 hidden，等待管理员在「评论管理」中处理。
        // 原实现用 resolveModerationStatus(内容) 推导状态（按内容是否含敏感词），
        // 与「举报」这个动作本身无关，逻辑并不成立。
        comment.setStatus(CommentStatus.HIDDEN.getCode());
        comment.setUpdateTime(new Date());
        boolean updated = this.updateById(comment);
        if (!updated) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "举报失败，请重试");
        }

        User reporter = userMapper.selectById(loginUser.getId());
        List<User> admins = userMapper.selectList(new QueryWrapper<User>().eq("user_role", ADMIN_ROLE).eq("is_delete", 0));
        for (User admin : admins) {
            notificationService.send(
                    admin.getId(),
                    loginUser.getId(),
                    reporter != null ? reporter.getUsername() : "匿名用户",
                    reporter != null ? reporter.getAvatarUrl() : null,
                    "report_comment",
                    comment.getId(),
                    comment.getNoteTitle(),
                    StringUtils.isNotBlank(reason) ? reason : "有人举报了一条评论"
            );
        }

        // 落库举报记录
        reportService.create(loginUser.getId(), "comment", id, reason);

        return true;
    }

    /**
     * 获取当前用户的评论（分页，含回复，用于"我的评论"专属页）
     */
    @Override
    public PageResult<Comment> getMyComments(Long userId, Integer page, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        int pageNum = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<Comment> pageParam = new Page<>(pageNum, size);
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        // 含回复（不限制 parent_id is null），按创建时间倒序；is_delete 由 @TableLogic 自动过滤
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        IPage<Comment> result = this.page(pageParam, wrapper);

        List<Comment> comments = result.getRecords();
        fillCommentInfoBatch(comments);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), comments);
    }

    /**
     * 填充单条评论信息（委托批量方法）
     */
    private Comment fillCommentInfo(Comment comment) {
        if (comment != null) {
            fillCommentInfoBatch(List.of(comment));
        }
        return comment;
    }

    /**
     * 批量填充评论信息（消除 N+1 查询）
     */
    private void fillCommentInfoBatch(List<Comment> comments) {
        if (comments == null || comments.isEmpty()) {
            return;
        }

        // 收集需要查询的 userId 和 noteId
        Set<Long> userIds = comments.stream()
                .filter(c -> c.getUserId() != null)
                .map(Comment::getUserId)
                .collect(Collectors.toSet());
        Set<Long> noteIds = comments.stream()
                .filter(c -> c.getNoteId() != null)
                .map(Comment::getNoteId)
                .collect(Collectors.toSet());

        // 批量查询用户
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查询笔记
        Map<Long, Note> noteMap = noteIds.isEmpty() ? Map.of() :
                noteMapper.selectByIds(noteIds).stream()
                        .collect(Collectors.toMap(Note::getId, n -> n));

        // 填充字段
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Comment comment : comments) {
            // 用户信息
            if (comment.getUserId() != null) {
                User user = userMap.get(comment.getUserId());
                if (user != null) {
                    comment.setUsername(user.getUsername());
                    comment.setAvatar(user.getAvatarUrl());
                }
            }
            // 笔记标题 + isAuthor
            if (comment.getNoteId() != null) {
                Note note = noteMap.get(comment.getNoteId());
                if (note != null) {
                    comment.setNoteTitle(note.getTitle());
                    comment.setIsAuthor(comment.getUserId() != null
                            && note.getAuthorId() != null
                            && note.getAuthorId().equals(comment.getUserId()));
                }
            }
            // 时间格式化
            if (comment.getCreateTime() != null) {
                comment.setCreateTimeStr(sdf.format(comment.getCreateTime()));
            }
            // 空值处理
            if (comment.getLikeCount() == null) {
                comment.setLikeCount(0);
            }
            if (comment.getIsAuthor() == null) {
                comment.setIsAuthor(false);
            }
        }
    }

    /**
     * 获取登录用户（优化空值处理）
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
}