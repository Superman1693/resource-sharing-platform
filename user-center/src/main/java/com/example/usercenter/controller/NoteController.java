package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Star;
import com.example.usercenter.model.domain.StarMember;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.NoteQueryRequest;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.mapper.StarMapper;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.service.HotRankService;
import com.example.usercenter.service.NotificationService;
import com.example.usercenter.service.NoteService;
import com.example.usercenter.service.PointsService;
import com.example.usercenter.service.ReportService;
import com.example.usercenter.service.UserService;
import com.example.usercenter.utils.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 笔记/内容接口
 * 
 * @author zy
 */
@RestController
@RequestMapping("/note")
@Slf4j
public class NoteController extends BaseController {

    @Resource
    private NoteService noteService;

    @Resource
    private HotRankService hotRankService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserService userService;

    @Resource
    private StarMapper starMapper;

    @Resource
    private StarMemberMapper starMemberMapper;

    @Resource
    private ReportService reportService;

    @Resource
    private PointsService pointsService;

    /**
     * 获取笔记列表（免登录浏览）
     */
    @GetMapping("/list")
    public BaseResponse<PageResult<Note>> getNoteList(
            NoteQueryRequest noteQueryRequest,
            HttpServletRequest request) {
        // 浏览笔记列表不需要登录

        if (noteQueryRequest == null) {
            noteQueryRequest = new NoteQueryRequest();
        }

        // 非管理员强制只能查询已发布的笔记
        // （selectPageWithCondition XML 中已固定 status = 'published'，此处保留逻辑注释）

        PageResult<Note> result = noteService.getNoteList(noteQueryRequest);
        return ResultUtils.success(result);
    }

    /**
     * 获取笔记详情（免登录浏览）
     * <p>published 内容走多级缓存；非 published（draft / pending / rejected）仅对**作者本人与管理员**放行，
     * 其他登录用户与匿名访客一律按「笔记不存在」处理。作者编辑回填另有 {@code GET /note/my/{id}}。
     */
    @GetMapping("/{id}")
    public BaseResponse<Note> getNoteDetail(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        // 解析当前登录用户（可空，匿名访客）
        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        Long userId = loginUser == null ? null : loginUser.getUserId();
        boolean isAdmin = loginUser != null && Integer.valueOf(1).equals(loginUser.getUserRole());

        // published 内容走多级缓存（全员共享）。
        // 命中不到时不再直接抛错：笔记被举报后会被 markNotePending 置为 pending，
        // 此时「举报管理 → 查看」必须仍能打开它，否则管理员无法查看被举报内容、无从判断是否违规；
        // 作者也应能查看自己被打回的内容。故回退到带身份校验的 getNoteDetail，
        // 由它决定放行（作者/管理员）还是照旧抛「笔记不存在」（其他登录用户 / 匿名访客）。
        Note note;
        try {
            note = noteService.getNoteDetailPublished(id);
        } catch (BusinessException e) {
            if (userId == null) {
                throw e;   // 匿名访客：维持原行为，不暴露 draft/pending/rejected 的存在
            }
            note = noteService.getNoteDetail(id, userId, isAdmin);
        }

        // 增加浏览量
        noteService.increaseViewCount(id);

        // 付费星球内容门禁：未加入该星球的用户只能看到标题等基础信息
        if (note.getStarId() != null) {
            Star star = starMapper.selectById(note.getStarId());
            if (star != null && star.getPrice() != null && star.getPrice() > 0) {
                boolean canRead = false;
                if (loginUser != null) {
                    if (isAdmin
                            || Objects.equals(star.getOwnerId(), loginUser.getUserId())
                            || Objects.equals(note.getAuthorId(), loginUser.getUserId())) {
                        canRead = true;
                    } else {
                        QueryWrapper<StarMember> mq = new QueryWrapper<>();
                        mq.eq("star_id", star.getId()).eq("user_id", loginUser.getUserId()).eq("is_delete", 0);
                        canRead = starMemberMapper.selectCount(mq) > 0;
                    }
                }
                if (!canRead) {
                    Note masked = new Note();
                    BeanUtils.copyProperties(note, masked);
                    masked.setContent(null);
                    masked.setLocked(true);
                    return ResultUtils.success(masked);
                }
            }
        }

        // 付费笔记门禁：未解锁的用户只能看到摘要，正文置空并标记 locked，
        // 前端据此显示「解锁需 N 积分」的遮罩（复用星球内容的遮罩样式）。
        if (note.getPrice() != null && note.getPrice() > 0) {
            boolean canRead = false;
            if (loginUser != null) {
                Long uid = loginUser.getUserId();
                canRead = isAdmin
                        || Objects.equals(note.getAuthorId(), uid)
                        || pointsService.hasUnlocked(uid, id);
            }
            if (!canRead) {
                Note masked = new Note();
                BeanUtils.copyProperties(note, masked);
                masked.setContent(null);
                masked.setLocked(true);
                return ResultUtils.success(masked);
            }
        }

        return ResultUtils.success(note);
    }

    /**
     * 我的笔记详情（作者编辑回填用，返回任意状态，无缓存，需登录）
     */
    @GetMapping("/my/{id}")
    @LoginRequired
    public BaseResponse<Note> getMyNoteDetail(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        LoginUserDTO loginUser = getLoginUser();
        Long userId = loginUser.getUserId();
        boolean isAdmin = Integer.valueOf(1).equals(loginUser.getUserRole());
        // 走含权限校验的查询（作者可看自己任意状态，无缓存）
        Note note = noteService.getNoteDetail(id, userId, isAdmin);
        return ResultUtils.success(note);
    }

    /**
     * 创建笔记（需要登录）
     */
    @PostMapping("/add")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "提交过于频繁，请稍后再试")
    public BaseResponse<Note> createNote(@RequestBody Note note, HttpServletRequest request) {
        // 参数校验
        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记信息不能为空");
        }

        Note createdNote = noteService.createNote(note, request);
        return ResultUtils.success(createdNote);
    }

    /**
     * 更新笔记（需要登录）
     */
    @PutMapping("/update/{id}")
    @LoginRequired
    public BaseResponse<Boolean> updateNote(@PathVariable Long id, @RequestBody Note note, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        if (note == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记信息不能为空");
        }

        boolean result = noteService.updateNote(id, note, request);
        return ResultUtils.success(result);
    }

    /**
     * 删除笔记（需要登录）
     */
    @PostMapping("/delete")
    @LoginRequired
    public BaseResponse<Boolean> deleteNote(@RequestBody Map<String, Long> requestBody, HttpServletRequest request) {
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = requestBody.get("id");
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        boolean result = noteService.deleteNote(id, request);
        return ResultUtils.success(result);
    }

    /**
     * 点赞笔记（需要登录）
     */
    @PostMapping("/like/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "点赞过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> likeNote(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        boolean result = noteService.likeNote(id, request);
        if (result) {
            Note note = noteService.getById(id);
            Map<String, Object> data = new HashMap<>();
            data.put("likeCount", note.getLikeCount());

            // 发送点赞通知给笔记作者
            LoginUserDTO loginUser = UserContext.get();
            if (loginUser != null && note.getAuthorId() != null) {
                User sender = userService.getById(loginUser.getUserId());
                if (sender != null) {
                    notificationService.send(
                            note.getAuthorId(),
                            loginUser.getUserId(),
                            sender.getUsername(),
                            sender.getAvatarUrl(),
                            "like_note",
                            note.getId(),
                            note.getTitle(),
                            sender.getUsername() + " 赞了你的笔记《" + note.getTitle() + "》");
                }
            }
            return ResultUtils.success(data);
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "点赞失败");
        }
    }

    /**
     * 增加浏览量
     */
    @PostMapping("/view/{id}")
    public BaseResponse<Boolean> increaseViewCount(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        boolean result = noteService.increaseViewCount(id);
        return ResultUtils.success(result);
    }

    /**
     * 置顶/取消置顶笔记（仅管理员）
     */
    @PostMapping("/top/{id}")
    @AdminRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, keyPrefix = "prevent_duplicate:topNote", message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> topNote(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer isTop) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        boolean result = noteService.topNote(id, isTop);
        return ResultUtils.success(result);
    }

    /**
     * 举报笔记（进入待审核状态，需要登录）
     */
    /**
     * 用积分自助置顶自己的笔记（消耗固定积分，置顶 24 小时后自动下架）
     */
    @PostMapping("/boost/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> boostNote(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        Long userId = getLoginUser().getUserId();
        return ResultUtils.success(noteService.boostNote(id, userId));
    }

    /**
     * 用积分解锁付费笔记（免费 / 作者本人 / 已购买均直接成功，幂等）
     */
    @PostMapping("/unlock/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> unlockNote(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        Long userId = getLoginUser().getUserId();
        return ResultUtils.success(pointsService.unlockNote(userId, id));
    }

    /**
     * 打赏笔记作者（可重复，单次 1 ~ 500 积分）
     */
    @PostMapping("/tip/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> tipNote(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        int amount = 0;
        if (body != null && body.get("amount") != null) {
            try {
                amount = Integer.parseInt(String.valueOf(body.get("amount")).trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "打赏金额不合法");
            }
        }
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "打赏金额不合法");
        }
        Long userId = getLoginUser().getUserId();
        return ResultUtils.success(pointsService.tipNote(userId, id, amount));
    }

    @PostMapping("/report/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> reportNote(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        Long reporterId = UserContext.get().getUserId();

        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        Note note = noteService.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }

        String reason = body == null ? null : body.get("reason");
        // 落库举报记录
        reportService.create(reporterId, "note", id, reason);
        // 标记 pending 并清 noteDetail/noteList 缓存（走 service 的 @CacheEvict）
        boolean result = noteService.markNotePending(id);
        return ResultUtils.success(result);
    }

    /**
     * 我的笔记（草稿箱/我的内容，按 status 筛选，需登录）
     */
    @GetMapping("/my")
    @LoginRequired
    public BaseResponse<PageResult<Note>> getMyNotes(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(noteService.getMyNotes(userId, status, page, pageSize));
    }

    /**
     * 获取热榜（免登录）
     */
    @GetMapping("/hot")
    public BaseResponse<List<Note>> getHotRank(
            @RequestParam(defaultValue = "day") String period) {
        List<Note> hotList = hotRankService.getHotRank(period);
        return ResultUtils.success(hotList);
    }
}
