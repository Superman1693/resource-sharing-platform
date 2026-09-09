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
     */
    @GetMapping("/{id}")
    public BaseResponse<Note> getNoteDetail(@PathVariable Long id, HttpServletRequest request) {
        // 浏览笔记详情不需要登录

        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        Note note = noteService.getNoteDetail(id);

        // 增加浏览量
        noteService.increaseViewCount(id);

        // 付费星球内容门禁：未加入该星球的用户只能看到标题等基础信息
        // （getNoteDetail 结果有缓存，脱敏必须基于副本，避免污染缓存）
        if (note.getStarId() != null) {
            Star star = starMapper.selectById(note.getStarId());
            if (star != null && star.getPrice() != null && star.getPrice() > 0) {
                boolean canRead = false;
                LoginUserDTO loginUser = null;
                try {
                    loginUser = getLoginUser();
                } catch (Exception ignored) {
                }
                if (loginUser != null) {
                    if (Integer.valueOf(1).equals(loginUser.getUserRole())
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
    @PostMapping("/report/{id}")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> reportNote(@PathVariable Long id, HttpServletRequest request) {
        getLoginUser();

        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }

        Note note = noteService.getById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }

        Note update = new Note();
        update.setId(id);
        update.setStatus("pending");
        boolean result = noteService.updateById(update);
        return ResultUtils.success(result);
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
