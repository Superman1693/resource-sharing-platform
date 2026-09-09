package com.example.usercenter.utils;

import com.example.usercenter.model.domain.ChatMessage;
import com.example.usercenter.model.domain.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis聊天操作工具类
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisChatOperator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis键前缀
    private static final String SESSION_PREFIX = "chat:session:";
    private static final String MESSAGE_PREFIX = "chat:message:";
    private static final String USER_SESSIONS_PREFIX = "chat:user:sessions:";
    private static final String SESSION_MESSAGES_PREFIX = "chat:session:messages:";

    // TTL配置（单位：秒）
    private static final long SESSION_TTL = 7 * 24 * 60 * 60; // 7天
    private static final long MESSAGE_TTL = 30 * 24 * 60 * 60; // 30天

    /**
     * 保存会话
     */
    public void saveSession(ChatSession session) {
        try {
            String sessionKey = SESSION_PREFIX + session.getSessionId();
            String userSessionsKey = USER_SESSIONS_PREFIX + session.getUserId();

            // 保存会话详情
            redisTemplate.opsForHash().putAll(sessionKey, convertSessionToMap(session));
            redisTemplate.expire(sessionKey, SESSION_TTL, TimeUnit.SECONDS);

            // 将会话ID添加到用户会话列表
            redisTemplate.opsForSet().add(userSessionsKey, session.getSessionId());
            redisTemplate.expire(userSessionsKey, SESSION_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("保存会话失败", e);
        }
    }

    /**
     * 获取会话
     */
    public ChatSession getSession(String sessionId) {
        try {
            String sessionKey = SESSION_PREFIX + sessionId;
            Map<Object, Object> sessionMap = redisTemplate.opsForHash().entries(sessionKey);
            if (sessionMap.isEmpty()) {
                return null;
            }
            return convertMapToSession(sessionMap);
        } catch (Exception e) {
            log.error("获取会话失败", e);
            return null;
        }
    }

    /**
     * 获取用户会话列表
     */
    public List<ChatSession> getUserSessions(Long userId) {
        try {
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
            if (sessionIds == null || sessionIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<ChatSession> sessions = new ArrayList<>();
            for (Object sessionIdObj : sessionIds) {
                String sessionId = (String) sessionIdObj;
                ChatSession session = getSession(sessionId);
                if (session != null) {
                    sessions.add(session);
                }
            }

            // 按最后更新时间排序
            sessions.sort((s1, s2) -> s2.getUpdateTime().compareTo(s1.getUpdateTime()));
            return sessions;
        } catch (Exception e) {
            log.error("获取用户会话列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId, Long userId) {
        try {
            String sessionKey = SESSION_PREFIX + sessionId;
            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            String sessionMessagesKey = SESSION_MESSAGES_PREFIX + sessionId;

            // 删除会话详情
            redisTemplate.delete(sessionKey);
            // 从用户会话列表中移除
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            // 删除会话消息
            Set<Object> messageIds = redisTemplate.opsForSet().members(sessionMessagesKey);
            if (messageIds != null) {
                for (Object messageIdObj : messageIds) {
                    String messageId = (String) messageIdObj;
                    redisTemplate.delete(MESSAGE_PREFIX + messageId);
                }
            }
            redisTemplate.delete(sessionMessagesKey);

            return true;
        } catch (Exception e) {
            log.error("删除会话失败", e);
            return false;
        }
    }

    /**
     * 保存消息
     */
    public void saveMessage(ChatMessage message) {
        try {
            String messageKey = MESSAGE_PREFIX + message.getMessageId();
            String sessionMessagesKey = SESSION_MESSAGES_PREFIX + message.getSessionId();

            // 保存消息详情
            redisTemplate.opsForHash().putAll(messageKey, convertMessageToMap(message));
            redisTemplate.expire(messageKey, MESSAGE_TTL, TimeUnit.SECONDS);

            // 将消息ID添加到会话消息列表
            redisTemplate.opsForSet().add(sessionMessagesKey, message.getMessageId());
            redisTemplate.expire(sessionMessagesKey, MESSAGE_TTL, TimeUnit.SECONDS);

            // 更新会话的最后消息信息
            updateSessionLastMessage(message);
        } catch (Exception e) {
            log.error("保存消息失败", e);
        }
    }

    /**
     * 获取会话消息列表
     */
    public List<ChatMessage> getSessionMessages(String sessionId, int page, int size) {
        try {
            String sessionMessagesKey = SESSION_MESSAGES_PREFIX + sessionId;
            Set<Object> messageIds = redisTemplate.opsForSet().members(sessionMessagesKey);
            if (messageIds == null || messageIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<ChatMessage> messages = new ArrayList<>();
            for (Object messageIdObj : messageIds) {
                String messageId = (String) messageIdObj;
                ChatMessage message = getMessage(messageId);
                if (message != null) {
                    messages.add(message);
                }
            }

            // 按时间戳排序
            messages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));

            // 分页
            int start = (page - 1) * size;
            int end = Math.min(start + size, messages.size());
            if (start >= messages.size()) {
                return Collections.emptyList();
            }
            return messages.subList(start, end);
        } catch (Exception e) {
            log.error("获取会话消息列表失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取消息
     */
    public ChatMessage getMessage(String messageId) {
        try {
            String messageKey = MESSAGE_PREFIX + messageId;
            Map<Object, Object> messageMap = redisTemplate.opsForHash().entries(messageKey);
            if (messageMap.isEmpty()) {
                return null;
            }
            return convertMapToMessage(messageMap);
        } catch (Exception e) {
            log.error("获取消息失败", e);
            return null;
        }
    }

    /**
     * 更新会话的最后消息信息
     */
    private void updateSessionLastMessage(ChatMessage message) {
        try {
            String sessionKey = SESSION_PREFIX + message.getSessionId();
            redisTemplate.opsForHash().put(sessionKey, "lastMessage", message.getContent());
            redisTemplate.opsForHash().put(sessionKey, "lastMessageTime", message.getTimestamp().toString());
            redisTemplate.opsForHash().put(sessionKey, "updateTime", LocalDateTime.now().toString());
            redisTemplate.expire(sessionKey, SESSION_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("更新会话最后消息失败", e);
        }
    }

    /**
     * 将会话转换为Map
     * 修复点5：Redis Hash 不允许 null value，对可空字段做保护
     */
    private Map<String, Object> convertSessionToMap(ChatSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", session.getSessionId());
        map.put("userId", session.getUserId());
        map.put("sessionName", session.getSessionName() != null ? session.getSessionName() : "");
        map.put("createTime", session.getCreateTime().toString());
        map.put("updateTime", session.getUpdateTime().toString());
        map.put("status", session.getStatus() != null ? session.getStatus() : "active");
        map.put("messageCount", session.getMessageCount() != null ? session.getMessageCount() : 0);
        map.put("lastMessage", session.getLastMessage() != null ? session.getLastMessage() : "");
        map.put("lastMessageTime", session.getLastMessageTime() != null ? session.getLastMessageTime().toString() : "");
        return map;
    }

    /**
     * 将Map转换为会话
     */
    private ChatSession convertMapToSession(Map<Object, Object> map) {
        ChatSession session = new ChatSession();
        session.setSessionId((String) map.get("sessionId"));
        session.setUserId(Long.valueOf(map.get("userId").toString()));
        session.setSessionName((String) map.get("sessionName"));
        session.setCreateTime(LocalDateTime.parse((String) map.get("createTime")));
        session.setUpdateTime(LocalDateTime.parse((String) map.get("updateTime")));
        session.setStatus((String) map.get("status"));
        session.setMessageCount(Integer.valueOf(map.get("messageCount").toString()));
        session.setLastMessage((String) map.get("lastMessage"));
        String lastMsgTime = (String) map.get("lastMessageTime");
        if (lastMsgTime != null && !lastMsgTime.isEmpty()) {
            session.setLastMessageTime(LocalDateTime.parse(lastMsgTime));
        }
        return session;
    }

    /**
     * 将消息转换为Map
     */
    private Map<String, Object> convertMessageToMap(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageId", message.getMessageId());
        map.put("sessionId", message.getSessionId());
        map.put("userId", message.getUserId());
        map.put("senderType", message.getSenderType());
        map.put("content", message.getContent());
        map.put("status", message.getStatus());
        map.put("timestamp", message.getTimestamp().toString());
        map.put("isStreaming", message.getStreaming());
        map.put("isCompleted", message.getCompleted());
        map.put("errorMessage", message.getErrorMessage() != null ? message.getErrorMessage() : "");
        return map;
    }

    /**
     * 将Map转换为消息
     */
    private ChatMessage convertMapToMessage(Map<Object, Object> map) {
        ChatMessage message = new ChatMessage();
        message.setMessageId((String) map.get("messageId"));
        message.setSessionId((String) map.get("sessionId"));
        message.setUserId(Long.valueOf(map.get("userId").toString()));
        message.setSenderType((String) map.get("senderType"));
        message.setContent((String) map.get("content"));
        message.setStatus((String) map.get("status"));
        message.setTimestamp(LocalDateTime.parse((String) map.get("timestamp")));
        message.setStreaming(Boolean.valueOf(map.get("isStreaming").toString()));
        message.setCompleted(Boolean.valueOf(map.get("isCompleted").toString()));
        message.setErrorMessage((String) map.get("errorMessage"));
        return message;
    }

    /**
     * 清理超过 inactiveDays 天未活跃的会话
     * @return 清理数量
     */
    public int cleanInactiveSessions(int inactiveDays) {
        int count = 0;
        LocalDateTime threshold = LocalDateTime.now().minusDays(inactiveDays);

        org.springframework.data.redis.core.ScanOptions options =
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(SESSION_PREFIX + "*")
                        .count(100)
                        .build();

        try (org.springframework.data.redis.core.Cursor<String> cursor =
                     redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Object updateTimeObj = redisTemplate.opsForHash().get(key, "updateTime");
                    if (updateTimeObj == null) continue;
                    LocalDateTime updateTime = LocalDateTime.parse(updateTimeObj.toString());
                    if (updateTime.isBefore(threshold)) {
                        Object sessionIdObj = redisTemplate.opsForHash().get(key, "sessionId");
                        Object userIdObj = redisTemplate.opsForHash().get(key, "userId");
                        if (sessionIdObj != null && userIdObj != null) {
                            deleteSession(sessionIdObj.toString(), Long.valueOf(userIdObj.toString()));
                            count++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("清理会话失败, key={}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("SCAN 会话 key 失败: {}", e.getMessage(), e);
        }
        return count;
    }
}
