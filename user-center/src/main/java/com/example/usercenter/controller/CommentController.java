package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.Comment;
import com.example.usercenter.model.enums.CommentStatus;
import com.example.usercenter.service.CommentService;
import com.example.usercenter.utils.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论接口
 * @author zy
 */
@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController extends BaseController {

    @Resource
    private CommentService commentService;

    /**
     * 获取评论列表（免登录查看，未登录只能看已审核评论）
     */
    @GetMapping("/list")
    public BaseResponse<List<Comment>> getCommentList(
            @RequestParam(required = false) Long noteId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        // 分页参数校验
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        // 可选获取登录用户，未登录则非管理员（只能看已审核评论）
        boolean isAdmin = isAdmin();
        List<Comment> comments = commentService.getCommentList(noteId, keyword, status, page, pageSize, isAdmin);

        return ResultUtils.success(comments);
    }

    /**
     * 获取当前用户的评论（分页，"我的评论"专属页）
     */
    @GetMapping("/my")
    @LoginRequired
    public BaseResponse<PageResult<Comment>> getMyComments(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        Long userId = UserContext.get().getUserId();
        PageResult<Comment> result = commentService.getMyComments(userId, page, pageSize);
        return ResultUtils.success(result);
    }

    /**
     * 添加评论（核心修复：兼容字符串noteId + 精细化校验）
     */
    @PostMapping("/add")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "评论过于频繁，请稍后再试")
    public BaseResponse<Comment> addComment(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        // 参数校验
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        // 修复：兼容字符串/数字类型的noteId（核心修改）
        Long noteId = getLongValueEnhanced(requestBody, "noteId");
        String content = (String) requestBody.get("content");

        // 精细化参数校验
        if (noteId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论内容不能超过500字符");
        }

        Comment comment = commentService.addComment(noteId, content, request);
        // @提及通知
        List<Long> mentionedIds = extractMentionedIds(requestBody);
        if (!mentionedIds.isEmpty()) {
            commentService.notifyMentioned(mentionedIds, noteId, UserContext.get().getUserId(), content);
        }
        return ResultUtils.success(comment);
    }

    /**
     * 回复评论（修复参数校验 + 兼容字符串noteId）
     */
    @PostMapping("/reply/{commentId}")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "回复过于频繁，请稍后再试")
    public BaseResponse<Comment> replyComment(
            @PathVariable Long commentId,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        // 参数校验
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        if (commentId == null || commentId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回复的评论ID不合法");
        }

        // 兼容字符串noteId
        Long noteId = getLongValueEnhanced(requestBody, "noteId");
        String content = (String) requestBody.get("content");

        // 精细化校验
        if (noteId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回复内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回复内容不能超过500字符");
        }

        Comment comment = commentService.replyComment(commentId, noteId, content, request);
        // @提及通知
        List<Long> mentionedIds = extractMentionedIds(requestBody);
        if (!mentionedIds.isEmpty()) {
            commentService.notifyMentioned(mentionedIds, noteId, UserContext.get().getUserId(), content);
        }
        return ResultUtils.success(comment);
    }

    /**
     * 删除评论（优化提示）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestBody Map<String, Long> requestBody, HttpServletRequest request) {
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = requestBody.get("id");
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论ID不能为空且必须为正数");
        }

        boolean result = commentService.deleteComment(id, request);
        return ResultUtils.success(result);
    }

    /**
     * 审核评论（管理员）
     */
    @PostMapping("/approve")
    public BaseResponse<Boolean> approveComment(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        // 仅管理员可操作
        requireAdmin();

        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = getLongValueEnhanced(requestBody, "id");
        String status = (String) requestBody.get("status");

        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论ID不合法");
        }
        if (StringUtils.isBlank(status) || !CommentStatus.APPROVABLE.contains(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态只能是 approved 或 rejected");
        }

        boolean result = commentService.approveComment(id, status, request);
        return ResultUtils.success(result);
    }

    /**
     * 举报评论
     */
    @PostMapping("/report")
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> reportComment(@RequestBody Map<String, Object> requestBody, HttpServletRequest request) {
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = getLongValueEnhanced(requestBody, "id");
        String reason = (String) requestBody.get("reason");

        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论ID不合法");
        }

        boolean result = commentService.reportComment(id, reason, request);
        return ResultUtils.success(result);
    }

    /**
     * 点赞评论（优化返回值 + 异常提示）
     */
    @PostMapping("/like/{id}")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "点赞过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> likeComment(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "评论ID不能为空且必须为正数");
        }

        boolean result = commentService.likeComment(id, request);
        if (result) {
            Comment comment = commentService.getById(id);
            Map<String, Object> data = new HashMap<>();
            data.put("likeCount", comment.getLikeCount() == null ? 0 : comment.getLikeCount());
            return ResultUtils.success(data);
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "点赞失败，请重试");
        }
    }

    /**
     * 增强版：从Map中获取Long值（兼容字符串类型）
     * 核心修复：处理前端传递的字符串类型noteId（如"2"）
     */
    private Long getLongValueEnhanced(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        // 数字类型直接转换
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        // 字符串类型尝试转换
        if (value instanceof String) {
            String strValue = (String) value;
            if (StringUtils.isBlank(strValue)) {
                return null;
            }
            try {
                return Long.parseLong(strValue.trim());
            } catch (NumberFormatException e) {
                log.error("转换{}为Long失败，值：{}", key, strValue, e);
                throw new BusinessException(ErrorCode.PARAMS_ERROR, key + "必须为数字类型（如123）");
            }
        }
        // 其他类型返回null并抛异常
        throw new BusinessException(ErrorCode.PARAMS_ERROR, key + "类型不支持，仅支持数字/字符串数字");
    }

    // 保留原方法（兼容其他调用）
    private Long getLongValue(Map<String, Object> map, String key) {
        return getLongValueEnhanced(map, key);
    }

    /**
     * 从请求体提取被 @提及的用户ID列表（兼容数字/字符串）
     */
    private List<Long> extractMentionedIds(Map<String, Object> body) {
        Object v = body.get("mentionedUserIds");
        if (!(v instanceof List)) return java.util.Collections.emptyList();
        List<Long> ids = new ArrayList<>();
        for (Object o : (List<?>) v) {
            if (o instanceof Number) {
                ids.add(((Number) o).longValue());
            } else if (o instanceof String) {
                try { ids.add(Long.parseLong(((String) o).trim())); }
                catch (NumberFormatException e) { /* 跳过非法值 */ }
            }
        }
        return ids;
    }
}