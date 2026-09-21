package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.StarMapper;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Star;
import com.example.usercenter.model.domain.StarMember;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.NotificationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 星球接口
 */
@RestController
@RequestMapping("/star")
@Slf4j
public class StarController extends BaseController {

    @Resource
    private StarMapper starMapper;

    @Resource
    private StarMemberMapper starMemberMapper;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private com.example.usercenter.utils.SensitiveWordChecker sensitiveWordChecker;

    /**
     * 获取星球列表
     */
    @GetMapping("/list")
    public BaseResponse<List<Map<String, Object>>> getStarList(
            @RequestParam(required = false) String keyword) {
        QueryWrapper<Star> wrapper = new QueryWrapper<>();
        wrapper.eq("is_delete", 0).eq("status", "active");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("name", keyword).or().like("description", keyword));
        }
        wrapper.orderByDesc("id");
        List<Star> stars = starMapper.selectList(wrapper);

        // 获取当前用户（可能未登录）
        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        final Long currentUserId = loginUser != null ? loginUser.getUserId() : null;

        List<Map<String, Object>> result = stars.stream().map(star -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", star.getId());
            map.put("name", star.getName());
            map.put("description", star.getDescription());
            map.put("coverImage", star.getCoverImage());
            map.put("memberCount", star.getMemberCount() != null ? star.getMemberCount() : 0);
            map.put("contentCount", star.getContentCount() != null ? star.getContentCount() : 0);
            map.put("price", star.getPrice() != null ? star.getPrice() : 0);
            map.put("starType", "free");
            // 是否已加入
            boolean joined = false;
            if (currentUserId != null) {
                joined = isMember(star.getId(), currentUserId);
            }
            map.put("isJoined", joined);
            return map;
        }).collect(Collectors.toList());

        return ResultUtils.success(result);
    }

    /**
     * 获取热门星球（按成员数、内容数排序）
     */
    @GetMapping("/hot")
    public BaseResponse<List<Map<String, Object>>> getHotStars(
            @RequestParam(defaultValue = "10") Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));

        QueryWrapper<Star> wrapper = new QueryWrapper<>();
        wrapper.eq("is_delete", 0)
                .eq("status", "active")
                .orderByDesc("member_count")
                .orderByDesc("content_count")
                .last("LIMIT " + safeLimit);

        List<Star> stars = starMapper.selectList(wrapper);

        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        final Long currentUserId = loginUser != null ? loginUser.getUserId() : null;

        List<Map<String, Object>> result = stars.stream().map(star -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", star.getId());
            map.put("name", star.getName());
            map.put("description", star.getDescription());
            map.put("coverImage", star.getCoverImage());
            map.put("memberCount", star.getMemberCount() != null ? star.getMemberCount() : 0);
            map.put("contentCount", star.getContentCount() != null ? star.getContentCount() : 0);
            map.put("price", star.getPrice() != null ? star.getPrice() : 0);
            map.put("starType", "free");

            boolean joined = false;
            if (currentUserId != null) {
                joined = isMember(star.getId(), currentUserId);
            }
            map.put("isJoined", joined);
            return map;
        }).collect(Collectors.toList());

        return ResultUtils.success(result);
    }

    /**
     * 获取当前用户加入的星球
     */
    @GetMapping("/my")
    public BaseResponse<List<Map<String, Object>>> getMyStars() {
        LoginUserDTO loginUser = getLoginUser();

        QueryWrapper<StarMember> memberWrapper = new QueryWrapper<>();
        memberWrapper.eq("user_id", loginUser.getUserId()).eq("is_delete", 0).orderByDesc("join_time");
        List<StarMember> memberships = starMemberMapper.selectList(memberWrapper);

        if (memberships == null || memberships.isEmpty()) {
            return ResultUtils.success(List.of());
        }

        List<Long> starIds = memberships.stream().map(StarMember::getStarId).distinct().collect(Collectors.toList());
        QueryWrapper<Star> starWrapper = new QueryWrapper<>();
        starWrapper.in("id", starIds).eq("is_delete", 0).eq("status", "active");
        List<Star> stars = starMapper.selectList(starWrapper);

        Map<Long, Star> starMap = stars.stream().collect(Collectors.toMap(Star::getId, s -> s));
        List<Map<String, Object>> result = memberships.stream()
                .map(membership -> {
                    Star star = starMap.get(membership.getStarId());
                    if (star == null) {
                        return null;
                    }
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", star.getId());
                    map.put("name", star.getName());
                    map.put("description", star.getDescription());
                    map.put("coverImage", star.getCoverImage());
                    map.put("memberCount", star.getMemberCount() != null ? star.getMemberCount() : 0);
                    map.put("contentCount", star.getContentCount() != null ? star.getContentCount() : 0);
                    map.put("price", star.getPrice() != null ? star.getPrice() : 0);
                    map.put("starType", "free");
                    map.put("isJoined", true);
                    map.put("joinTime", membership.getJoinTime());
                    return map;
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());

        return ResultUtils.success(result);
    }

    /**
     * 获取星球详情（支持内容分页）
     */
    @GetMapping("/{id}")
    public BaseResponse<Map<String, Object>> getStarDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Star star = starMapper.selectById(id);
        if (star == null || star.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }

        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        final Long currentUserId = loginUser != null ? loginUser.getUserId() : null;

        boolean joined = false;
        if (currentUserId != null) {
            QueryWrapper<StarMember> mq = new QueryWrapper<>();
            mq.eq("star_id", id).eq("user_id", currentUserId).eq("is_delete", 0);
            joined = starMemberMapper.selectCount(mq) > 0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ownerId", star.getOwnerId());

        // 获取星球内容（分页）
        Page<Note> notePage = new Page<>(page, pageSize);
        QueryWrapper<Note> noteWrapper = new QueryWrapper<>();
        noteWrapper.eq("star_id", id).eq("status", "published").eq("is_delete", 0)
            .orderByDesc("is_top").orderByDesc("publish_time");
        IPage<Note> notePageResult = noteMapper.selectPage(notePage, noteWrapper);

        // 付费星球且当前用户无阅读权限时，内容正文脱敏（仅保留标题等基础信息）
        final boolean locked = !canReadStarContent(star, loginUser);
        if (locked) {
            notePageResult.getRecords().forEach(note -> note.setContent(null));
        }

        result.put("id", star.getId());
        result.put("name", star.getName());
        result.put("description", star.getDescription());
        result.put("announcement", star.getAnnouncement() != null ? star.getAnnouncement() : star.getDescription());
        result.put("coverImage", star.getCoverImage());
        result.put("memberCount", star.getMemberCount() != null ? star.getMemberCount() : 0);
        // 内容数以实时分页查询结果为准（避免 star.content_count 字段历史数据不准导致详情页显示 0）
        result.put("contentCount", notePageResult.getTotal());
        result.put("price", star.getPrice() != null ? star.getPrice() : 0);
        result.put("locked", locked);
        result.put("starType", "free");
        result.put("isJoined", joined);
        result.put("contentList", notePageResult.getRecords());
        result.put("contentTotal", notePageResult.getTotal());
        result.put("contentPage", page);
        result.put("contentPageSize", pageSize);

        return ResultUtils.success(result);
    }

    /**
     * 获取星球成员列表
     */
    @GetMapping("/{id}/members")
    public BaseResponse<List<Map<String, Object>>> getStarMembers(@PathVariable Long id) {
        Star star = starMapper.selectById(id);
        if (star == null || star.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }

        QueryWrapper<StarMember> memberWrapper = new QueryWrapper<>();
        memberWrapper.eq("star_id", id).eq("is_delete", 0).orderByAsc("join_time");
        List<StarMember> members = starMemberMapper.selectList(memberWrapper);

        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        final Long currentUserId = loginUser != null ? loginUser.getUserId() : null;
        final boolean canManage = loginUser != null && (Integer.valueOf(1).equals(loginUser.getUserRole()) || Objects.equals(star.getOwnerId(), loginUser.getUserId()));

        // 批量查询用户信息，消除 N+1 查询
        Map<Long, User> userMap = new HashMap<>();
        if (members != null && !members.isEmpty()) {
            List<Long> userIds = members.stream().map(StarMember::getUserId).distinct().collect(Collectors.toList());
            userMap = userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }
        final Map<Long, User> finalUserMap = userMap;

        List<Map<String, Object>> result = members.stream().map(member -> {
            User user = finalUserMap.get(member.getUserId());
            Map<String, Object> map = new HashMap<>();
            map.put("userId", member.getUserId());
            map.put("starId", member.getStarId());
            map.put("role", member.getRole());
            map.put("joinTime", member.getJoinTime());
            map.put("username", user != null ? user.getUsername() : "未知用户");
            map.put("avatarUrl", user != null ? user.getAvatarUrl() : null);
            map.put("isOwner", Objects.equals(star.getOwnerId(), member.getUserId()));
            map.put("isSelf", currentUserId != null && currentUserId.equals(member.getUserId()));
            map.put("canRemove", canManage && !Objects.equals(star.getOwnerId(), member.getUserId()));
            return map;
        }).collect(Collectors.toList());

        return ResultUtils.success(result);
    }

    /**
     * 移除星球成员
     */
    @PostMapping("/{starId}/members/{userId}/remove")
    public BaseResponse<Boolean> removeStarMember(@PathVariable Long starId, @PathVariable Long userId) {
        LoginUserDTO loginUser = getLoginUser();
        Star star = starMapper.selectById(starId);
        if (star == null || star.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        if (!Integer.valueOf(1).equals(loginUser.getUserRole()) && !Objects.equals(star.getOwnerId(), loginUser.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限移除成员");
        }
        if (Objects.equals(star.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能移除星球创建者");
        }

        QueryWrapper<StarMember> memberWrapper = new QueryWrapper<>();
        memberWrapper.eq("star_id", starId).eq("user_id", userId).eq("is_delete", 0);
        StarMember member = starMemberMapper.selectOne(memberWrapper);
        if (member == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "成员不存在");
        }

        // 物理删除成员关系，释放 (star_id, user_id) 唯一键，以便该用户可重新加入
        starMemberMapper.deletePhysical(starId, userId);

        Star update = new Star();
        update.setId(starId);
        update.setMemberCount(Math.max(0, (star.getMemberCount() != null ? star.getMemberCount() : 0) - 1));
        starMapper.updateById(update);

        return ResultUtils.success(true);
    }

    /**
     * 加入星球（付费星球需客户端传 pay=true 确认支付；支付流程为模拟实现）
     */
    @PostMapping("/join/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> joinStar(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        LoginUserDTO loginUser = getLoginUser();
        Star star = starMapper.selectById(id);
        if (star == null || star.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        if (star.getStatus() != null && !"active".equals(star.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该星球已停用，无法加入");
        }

        QueryWrapper<StarMember> mq = new QueryWrapper<>();
        mq.eq("star_id", id).eq("user_id", loginUser.getUserId()).eq("is_delete", 0);
        if (starMemberMapper.selectCount(mq) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已加入该星球");
        }

        // 付费星球必须显式确认支付
        int price = star.getPrice() != null ? star.getPrice() : 0;
        if (price > 0) {
            boolean confirmed = body != null && Boolean.TRUE.equals(body.get("pay"));
            if (!confirmed) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "该星球为付费星球（¥" + price + "），请确认支付后加入");
            }
        }

        StarMember member = new StarMember();
        member.setStarId(id);
        member.setUserId(loginUser.getUserId());
        member.setRole("member");
        member.setJoinTime(new Date());
        starMemberMapper.insert(member);

        // 多租户：用户首次加入星球时，自动把它设为「当前星球」
        User joinedUser = userMapper.selectById(loginUser.getUserId());
        if (joinedUser != null && joinedUser.getCurrentStarId() == null) {
            User userPatch = new User();
            userPatch.setId(joinedUser.getId());
            userPatch.setCurrentStarId(id);
            userMapper.updateById(userPatch);
        }

        // 更新成员数
        Star update = new Star();
        update.setId(id);
        update.setMemberCount((star.getMemberCount() != null ? star.getMemberCount() : 0) + 1);
        starMapper.updateById(update);

        // 通知星球创建者有新成员加入
        if (star.getOwnerId() != null) {
            User joiner = userMapper.selectById(loginUser.getUserId());
            if (joiner != null) {
                notificationService.send(
                        star.getOwnerId(),
                        loginUser.getUserId(),
                        joiner.getUsername(),
                        joiner.getAvatarUrl(),
                        "join_star",
                        star.getId(),
                        star.getName(),
                        joiner.getUsername() + " 加入了你的星球《" + star.getName() + "》"
                );
            }
        }

        return ResultUtils.success(true);
    }

    /**
     * 退出星球
     */
    @PostMapping("/exit/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> exitStar(@PathVariable Long id) {
        LoginUserDTO loginUser = getLoginUser();
        Star star = starMapper.selectById(id);
        if (star == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }

        QueryWrapper<StarMember> mq = new QueryWrapper<>();
        mq.eq("star_id", id).eq("user_id", loginUser.getUserId()).eq("is_delete", 0);
        if (starMemberMapper.selectCount(mq) == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "您未加入该星球");
        }
        // 物理删除成员关系，释放 (star_id, user_id) 唯一键，以便后续可重新加入
        starMemberMapper.deletePhysical(id, loginUser.getUserId());

        // 更新成员数
        int count = star.getMemberCount() != null ? star.getMemberCount() : 0;
        Star update = new Star();
        update.setId(id);
        update.setMemberCount(Math.max(0, count - 1));
        starMapper.updateById(update);

        // 多租户：若退出的正是「当前星球」，则改选一个已加入的星球（没有则置空）
        User exitingUser = userMapper.selectById(loginUser.getUserId());
        if (exitingUser != null && Objects.equals(exitingUser.getCurrentStarId(), id)) {
            Long nextStarId = starMemberMapper.selectPrimaryStarId(loginUser.getUserId());
            // updateById 默认忽略 null 字段，置空必须用 UpdateWrapper
            LambdaUpdateWrapper<User> userWrapper = new LambdaUpdateWrapper<>();
            userWrapper.eq(User::getId, exitingUser.getId())
                       .set(User::getCurrentStarId, nextStarId);
            userMapper.update(null, userWrapper);
        }

        return ResultUtils.success(true);
    }

    /**
     * 判断用户是否已加入星球
     */
    private boolean isMember(Long starId, Long userId) {
        if (userId == null) return false;
        QueryWrapper<StarMember> mq = new QueryWrapper<>();
        mq.eq("star_id", starId).eq("user_id", userId).eq("is_delete", 0);
        return starMemberMapper.selectCount(mq) > 0;
    }

    /**
     * 判断用户是否可阅读付费星球内容：
     * 成员 / 创建者 / 管理员均可
     */
    private boolean canReadStarContent(Star star, LoginUserDTO loginUser) {
        if (star == null || star.getPrice() == null || star.getPrice() <= 0) return true;
        if (loginUser == null) return false;
        if (Integer.valueOf(1).equals(loginUser.getUserRole())) return true;
        if (Objects.equals(star.getOwnerId(), loginUser.getUserId())) return true;
        return isMember(star.getId(), loginUser.getUserId());
    }

    /**
     * 普通用户创建星球（创建者自动成为 owner 并加入星球）
     */
    @PostMapping("/create")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "创建过于频繁，请稍后再试")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BaseResponse<Star> createStar(@RequestBody Star body) {
        LoginUserDTO loginUser = getLoginUser();
        if (body == null || body.getName() == null || body.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球名称不能为空");
        }
        String name = body.getName().trim();
        if (name.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球名称不能超过30个字符");
        }
        String description = body.getDescription() != null ? body.getDescription().trim() : null;
        if (description != null && description.length() > 200) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球简介不能超过200个字符");
        }
        // 加入价格：0-9999 元，空或负数视为免费
        int price = body.getPrice() != null && body.getPrice() > 0 ? body.getPrice() : 0;
        if (price > 9999) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "加入价格不能超过9999元");
        }
        // 同名星球不允许重复创建
        QueryWrapper<Star> dupWrapper = new QueryWrapper<>();
        dupWrapper.eq("name", name).eq("is_delete", 0);
        if (starMapper.selectCount(dupWrapper) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已存在同名星球");
        }
        // 敏感词校验
        sensitiveWordChecker.check("星球名称或简介", name, description);

        Star star = new Star();
        star.setName(name);
        star.setDescription(description);
        star.setCoverImage(body.getCoverImage());
        star.setPrice(price);
        star.setOwnerId(loginUser.getUserId());
        star.setMemberCount(1);
        star.setContentCount(0);
        star.setStatus("active");
        star.setCreateTime(new Date());
        star.setUpdateTime(new Date());
        star.setIsDelete(0);
        starMapper.insert(star);

        // 创建者以 owner 身份加入星球
        StarMember member = new StarMember();
        member.setStarId(star.getId());
        member.setUserId(loginUser.getUserId());
        member.setRole("owner");
        member.setJoinTime(new Date());
        member.setIsDelete(0);
        starMemberMapper.insert(member);

        return ResultUtils.success(star);
    }

    /**
     * 我加入的星球的最新帖子（首页社区动态）
     */
    @GetMapping("/feed")
    @LoginRequired
    public BaseResponse<List<Map<String, Object>>> getMyStarFeed(
            @RequestParam(defaultValue = "10") Integer limit) {
        LoginUserDTO loginUser = getLoginUser();
        int safeLimit = Math.max(1, Math.min(limit, 50));

        // 我加入的星球
        QueryWrapper<StarMember> memberWrapper = new QueryWrapper<>();
        memberWrapper.eq("user_id", loginUser.getUserId()).eq("is_delete", 0);
        List<StarMember> memberships = starMemberMapper.selectList(memberWrapper);
        if (memberships == null || memberships.isEmpty()) {
            return ResultUtils.success(List.of());
        }
        List<Long> starIds = memberships.stream().map(StarMember::getStarId).distinct().collect(Collectors.toList());

        // 这些星球的已发布笔记，按发布时间倒序取最新
        QueryWrapper<Note> noteWrapper = new QueryWrapper<>();
        noteWrapper.in("star_id", starIds)
                .eq("status", "published")
                .eq("is_delete", 0)
                .orderByDesc("publish_time")
                .last("LIMIT " + safeLimit);
        List<Note> notes = noteMapper.selectList(noteWrapper);
        if (notes.isEmpty()) {
            return ResultUtils.success(List.of());
        }

        // 星球名与作者信息批量填充，避免 N+1
        Map<Long, Star> starMap = starMapper.selectList(new QueryWrapper<Star>().in("id", starIds))
                .stream().collect(Collectors.toMap(Star::getId, s -> s));
        List<Long> authorIds = notes.stream().map(Note::getAuthorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> authorMap = authorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = notes.stream().map(note -> {
            Map<String, Object> map = new HashMap<>();
            map.put("noteId", note.getId());
            map.put("title", note.getTitle());
            map.put("summary", note.getSummary());
            map.put("likeCount", note.getLikeCount());
            map.put("viewCount", note.getViewCount());
            map.put("publishTime", note.getPublishTime());
            map.put("starId", note.getStarId());
            Star star = starMap.get(note.getStarId());
            map.put("starName", star != null ? star.getName() : "未知星球");
            User author = authorMap.get(note.getAuthorId());
            map.put("authorName", author != null ? author.getUsername() : "未知用户");
            map.put("authorAvatar", author != null ? author.getAvatarUrl() : null);
            return map;
        }).collect(Collectors.toList());

        return ResultUtils.success(result);
    }

    /**
     * 更新星球公告（管理员或创建者可操作）
     */
    @PostMapping("/{id}/announcement")
    public BaseResponse<Boolean> updateAnnouncement(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        LoginUserDTO loginUser = getLoginUser();
        Star star = starMapper.selectById(id);
        if (star == null || star.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        if (!Integer.valueOf(1).equals(loginUser.getUserRole()) && !Objects.equals(star.getOwnerId(), loginUser.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限编辑公告");
        }

        String announcement = body.get("announcement");
        // 敏感词校验
        sensitiveWordChecker.check("星球公告", announcement);
        Star update = new Star();
        update.setId(id);
        update.setAnnouncement(announcement);
        update.setUpdateTime(new Date());
        starMapper.updateById(update);

        return ResultUtils.success(true);
    }
}
