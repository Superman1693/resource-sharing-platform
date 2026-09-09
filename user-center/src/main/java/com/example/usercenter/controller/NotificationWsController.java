package com.example.usercenter.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * WebSocket 消息控制器
 * 处理客户端通过 STOMP 发送的消息
 */
@Controller
@Slf4j
public class NotificationWsController {

    /**
     * 客户端发送心跳到 /app/heartbeat
     * 用于保持 WebSocket 连接活跃
     */
    @MessageMapping("/heartbeat")
    public void handleHeartbeat(@Payload String userId) {
        log.debug("收到 WebSocket 心跳: userId={}", userId);
        // 心跳不需要回复，SockJS 内置心跳机制
    }
}
