package com.example.usercenter.service;

import com.example.usercenter.model.domain.ChatMessage;
import com.example.usercenter.model.domain.ChatSession;
import com.example.usercenter.model.domain.request.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天服务接口
 */
public interface ChatService {

    /**
     * 发送聊天消息
     * @param chatRequest 聊天请求
     * @param userId 用户ID
     * @return 聊天消息
     */
    ChatMessage sendMessage(ChatRequest chatRequest, Long userId);

    /**
     * 流式发送聊天消息
     * @return 符合 SSE 标准的事件流，event 类型：message / done / error
     */
    Flux<ServerSentEvent<String>> sendMessageStream(ChatRequest chatRequest, Long userId);

    /**
     * 获取会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> getSessionList(Long userId);

    /**
     * 获取会话详情
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 会话详情
     */
    ChatSession getSession(String sessionId, Long userId);

    /**
     * 创建新会话
     * @param sessionName 会话名称
     * @param userId 用户ID
     * @return 会话
     */
    ChatSession createSession(String sessionName, Long userId);

    /**
     * 更新会话
     * @param sessionId 会话ID
     * @param sessionName 会话名称
     * @param userId 用户ID
     * @return 会话
     */
    ChatSession updateSession(String sessionId, String sessionName, Long userId);

    /**
     * 删除会话
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteSession(String sessionId, Long userId);

    /**
     * 获取会话消息列表
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 消息列表
     */
    List<ChatMessage> getMessageList(String sessionId, Long userId, int page, int size);

    /**
     * 标记消息为已读
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean markMessageAsRead(String messageId, Long userId);
}
