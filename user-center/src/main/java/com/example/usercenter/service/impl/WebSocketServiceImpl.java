package com.example.usercenter.service.impl;

import com.example.usercenter.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 推送服务实现
 * 通过 SimpMessagingTemplate 向指定用户/主题推送消息
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendToUser(Long userId, Object payload) {
        try {
            // 推送到 /user/{userId}/queue/notification
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notification",
                    payload
            );
            log.debug("WebSocket 推送成功: userId={}, payload={}", userId, payload);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Override
    public void broadcast(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("WebSocket 广播成功: destination={}", destination);
        } catch (Exception e) {
            log.warn("WebSocket 广播失败: destination={}, error={}", destination, e.getMessage());
        }
    }
}
