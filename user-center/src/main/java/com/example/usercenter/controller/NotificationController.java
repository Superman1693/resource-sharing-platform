package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Notification;
import com.example.usercenter.service.NotificationService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "通知接口")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    @Operation(summary = "获取通知列表")
    public BaseResponse<PageResult<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.get().getUserId();
        log.info("获取通知列表请求, userId={}, page={}, pageSize={}", userId, page, pageSize);
        return ResultUtils.success(notificationService.getList(userId, page, pageSize));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "获取未读通知数量")
    public BaseResponse<Long> unreadCount() {
        Long userId = UserContext.get().getUserId();
        log.info("获取未读通知数请求, userId={}", userId);
        return ResultUtils.success(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public BaseResponse<Void> markRead(@PathVariable Long id) {
        Long userId = UserContext.get().getUserId();
        notificationService.markRead(id, userId);
        return ResultUtils.success(null);
    }

    @PutMapping("/read/all")
    @Operation(summary = "标记全部已读")
    public BaseResponse<Void> markAllRead() {
        Long userId = UserContext.get().getUserId();
        notificationService.markAllRead(userId);
        return ResultUtils.success(null);
    }
}
