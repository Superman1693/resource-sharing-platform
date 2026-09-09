package com.example.usercenter.service.impl;

import com.example.usercenter.model.domain.ChatMessage;
import com.example.usercenter.model.domain.ChatSession;
import com.example.usercenter.model.domain.request.ChatRequest;
import com.example.usercenter.service.ChatService;
import com.example.usercenter.utils.RedisChatOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

/**
 * 聊天服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final RedisChatOperator redisChatOperator;

    @Override
    public ChatMessage sendMessage(ChatRequest chatRequest, Long userId) {
        final String sessionId = resolveSessionId(chatRequest, userId);

        // 保存用户消息
        ChatMessage userMessage = buildMessage(chatRequest.getContent(), sessionId, userId, "user", "sent");
        redisChatOperator.saveMessage(userMessage);

        // 调用 AI
        String aiResponse = chatClient.prompt()
                .user(chatRequest.getContent())
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .call()
                .content();

        // 修复点3：cleanAiResponse 不再压缩双换行，保留 Markdown 段落格式
        String cleaned = cleanAiResponse(aiResponse);

        // 保存 AI 消息
        ChatMessage aiMessage = buildMessage(cleaned, sessionId, userId, "ai", "delivered");
        redisChatOperator.saveMessage(aiMessage);

        return aiMessage;
    }

    @Override
    public Flux<ServerSentEvent<String>> sendMessageStream(ChatRequest chatRequest, Long userId) {
        final String sessionId = resolveSessionId(chatRequest, userId);

        // 保存用户消息
        ChatMessage userMessage = buildMessage(chatRequest.getContent(), sessionId, userId, "user", "sent");
        redisChatOperator.saveMessage(userMessage);

        StringBuilder fullContent = new StringBuilder();

        Flux<ServerSentEvent<String>> messageFlux = chatClient.prompt()
                .user(chatRequest.getContent())
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
                .stream()
                .content()
                .map(chunk -> {
                    if (chunk == null) return "";
                    String cleaned = chunk.trim();
                    if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                        cleaned = cleaned.substring(1, cleaned.length() - 1);
                    }
                    fullContent.append(cleaned);
                    return cleaned;
                })
                .filter(chunk -> !chunk.isEmpty())
                // 每个 chunk 包装为 event: message
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build());

        // 流结束后追加 event: done，并持久化完整 AI 消息
        Flux<ServerSentEvent<String>> doneFlux = Flux.defer(() -> {
            String completeResponse = fullContent.toString();
            if (!completeResponse.isBlank()) {
                ChatMessage aiMessage = buildMessage(completeResponse, sessionId, userId, "ai", "delivered");
                redisChatOperator.saveMessage(aiMessage);
            }
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("done")
                    .data("[DONE]")
                    .build());
        });

        return messageFlux
                .concatWith(doneFlux)
                .onErrorResume(e -> {
                    log.error("流式响应异常, sessionId={}", sessionId, e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("AI 响应异常，请稍后重试")
                            .build());
                });
    }

    @Override
    public List<ChatSession> getSessionList(Long userId) {
        return redisChatOperator.getUserSessions(userId);
    }

    @Override
    public ChatSession getSession(String sessionId, Long userId) {
        ChatSession session = redisChatOperator.getSession(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return null;
        }
        return session;
    }

    @Override
    public ChatSession createSession(String sessionName, Long userId) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setSessionName(sessionName != null && !sessionName.isBlank() ? sessionName : "新会话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setStatus("active");
        session.setMessageCount(0);
        redisChatOperator.saveSession(session);
        return session;
    }

    @Override
    public ChatSession updateSession(String sessionId, String sessionName, Long userId) {
        ChatSession session = getSession(sessionId, userId);
        if (session == null) return null;
        if (sessionName != null && !sessionName.isBlank()) {
            session.setSessionName(sessionName);
            session.setUpdateTime(LocalDateTime.now());
            redisChatOperator.saveSession(session);
        }
        return session;
    }

    @Override
    public boolean deleteSession(String sessionId, Long userId) {
        ChatSession session = getSession(sessionId, userId);
        if (session == null) return false;
        return redisChatOperator.deleteSession(sessionId, userId);
    }

    @Override
    public List<ChatMessage> getMessageList(String sessionId, Long userId, int page, int size) {
        if (getSession(sessionId, userId) == null) return List.of();
        return redisChatOperator.getSessionMessages(sessionId, page, size);
    }

    @Override
    public boolean markMessageAsRead(String messageId, Long userId) {
        return true;
    }

    // ----------------------------- 私有工具方法 -----------------------------

    /**
     * 解析或创建 sessionId
     */
    private String resolveSessionId(ChatRequest chatRequest, Long userId) {
        String sid = chatRequest.getSessionId();
        if (sid == null || sid.isBlank()) {
            return createSession(chatRequest.getSessionName(), userId).getSessionId();
        }
        return sid;
    }

    /**
     * 统一构建消息对象，消除重复的 createUserMessage / createAIMessage
     */
    private ChatMessage buildMessage(String content, String sessionId, Long userId,
                                     String senderType, String status) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setStatus(status);
        message.setTimestamp(LocalDateTime.now());
        message.setStreaming(false);
        message.setCompleted(true);
        return message;
    }

    /**
     * 清理 AI 非流式回复内容。
     * 修复点3：保留双换行（Markdown 段落分隔），不再强制压缩为单换行。
     */
    private String cleanAiResponse(String response) {
        if (response == null) return "";
        response = response.trim();
        // 仅去除首尾成对引号
        if (response.length() >= 2 && response.startsWith("\"") && response.endsWith("\"")) {
            response = response.substring(1, response.length() - 1);
        }
        return response;
    }
}
