package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Message;
import com.example.usercenter.model.domain.request.SendMessageRequest;
import com.example.usercenter.model.dto.ConversationVO;
import com.example.usercenter.service.MessageService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "私信接口")
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表")
    public BaseResponse<PageResult<ConversationVO>> getConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(messageService.getConversationList(userId, page, pageSize));
    }

    @GetMapping("/history")
    @Operation(summary = "获取聊天记录")
    public BaseResponse<PageResult<Message>> getHistory(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(messageService.getMessageHistory(conversationId, userId, page, pageSize));
    }

    @PostMapping("/send")
    @Operation(summary = "发送私信")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "发送过于频繁，请稍后再试")
    public BaseResponse<Message> send(@RequestBody SendMessageRequest request) {
        Long senderId = UserContext.get().getUserId();
        Message msg = messageService.sendMessage(senderId, request.getReceiverId(), request.getContent());
        return ResultUtils.success(msg);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "获取未读私信数")
    public BaseResponse<Long> unreadCount() {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(messageService.getUnreadCount(userId));
    }

    @PostMapping("/read/{conversationId}")
    @Operation(summary = "标记会话消息已读")
    public BaseResponse<Boolean> markRead(@PathVariable Long conversationId) {
        Long userId = UserContext.get().getUserId();
        messageService.markConversationRead(conversationId, userId);
        return ResultUtils.success(true);
    }

    @PostMapping("/conversation/{userId}")
    @Operation(summary = "获取或创建与指定用户的会话（发起私信入口）")
    public BaseResponse<ConversationVO> getOrCreateConversation(@PathVariable("userId") Long otherUserId) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(messageService.getOrCreateConversationVO(userId, otherUserId));
    }
}
