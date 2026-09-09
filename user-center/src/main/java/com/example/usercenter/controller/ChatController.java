package com.example.usercenter.controller;

import com.example.usercenter.annotation.RateLimit;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.ChatMessage;
import com.example.usercenter.model.domain.ChatSession;
import com.example.usercenter.model.domain.request.ChatRequest;
import com.example.usercenter.model.domain.request.SessionRequest;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.ChatService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 聊天控制器
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "聊天接口", description = "AI聊天功能接口")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/message")
    @Operation(summary = "发送聊天消息")
    public BaseResponse<ChatMessage> sendMessage(
            @Valid @RequestBody ChatRequest chatRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        ChatMessage message = chatService.sendMessage(chatRequest, userId);
        return ResultUtils.success(message);
    }

    @PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送聊天消息")
    @RateLimit(key = "chat_stream", windowSeconds = 60, maxRequests = 20)
    public Flux<ServerSentEvent<String>> sendMessageStream(
            @Valid @RequestBody ChatRequest chatRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        return chatService.sendMessageStream(chatRequest, userId);
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表")
    public BaseResponse<List<ChatSession>> getSessionList(HttpServletRequest request) {
        Long userId = getCurrentUserId();
        List<ChatSession> sessions = chatService.getSessionList(userId);
        return ResultUtils.success(sessions);
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "获取会话详情")
    public BaseResponse<ChatSession> getSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        ChatSession session = chatService.getSession(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        return ResultUtils.success(session);
    }

    @PostMapping("/session")
    @Operation(summary = "创建会话")
    public BaseResponse<ChatSession> createSession(
            @Parameter(description = "会话名称") @RequestParam String sessionName,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        ChatSession session = chatService.createSession(sessionName, userId);
        return ResultUtils.success(session);
    }

    @PutMapping("/session")
    @Operation(summary = "更新会话")
    public BaseResponse<ChatSession> updateSession(
            @Valid @RequestBody SessionRequest sessionRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        ChatSession session = chatService.updateSession(
                sessionRequest.getSessionId(),
                sessionRequest.getSessionName(),
                userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        return ResultUtils.success(session);
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除会话")
    public BaseResponse<Boolean> deleteSession(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        boolean success = chatService.deleteSession(sessionId, userId);
        if (!success) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        return ResultUtils.success(success);
    }

    @GetMapping("/messages/{sessionId}")
    @Operation(summary = "获取会话消息列表")
    public BaseResponse<List<ChatMessage>> getMessageList(
            @Parameter(description = "会话ID") @PathVariable String sessionId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        List<ChatMessage> messages = chatService.getMessageList(sessionId, userId, page, size);
        return ResultUtils.success(messages);
    }

    @PutMapping("/message/{messageId}/read")
    @Operation(summary = "标记消息为已读")
    public BaseResponse<Boolean> markMessageAsRead(
            @Parameter(description = "消息ID") @PathVariable String messageId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId();
        boolean success = chatService.markMessageAsRead(messageId, userId);
        return ResultUtils.success(success);
    }

    /**
     * 从 UserContext 获取当前登录用户ID（JWT 认证）
     */
    private Long getCurrentUserId() {
        LoginUserDTO user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "用户未登录");
        }
        return user.getUserId();
    }
}
