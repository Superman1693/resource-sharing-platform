package com.example.usercenter.interceptor;

import com.example.usercenter.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * WebSocket 连接鉴权拦截器
 *
 * 在 STOMP CONNECT 帧校验 JWT，并把 userId 绑定为连接 principal。作用：
 * 1. 未携带有效 token 的连接直接被拒绝（防止任意客户端建立连接、拉取数据）；
 * 2. 绑定 principal 后，Spring 会把 /user/queue/notification 订阅解析为
 *    /user/{userId}/queue/notification，保证用户只能收到自己的通知，
 *    无法越权订阅他人队列。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        // 仅在 CONNECT 帧鉴权；SUBSCRIBE/SEND 的 user 目的地校验由 Spring 基于 principal 处理
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
            if (token != null && jwtUtils.validateToken(token)) {
                boolean blacklisted = Boolean.TRUE.equals(stringRedisTemplate.hasKey("blacklist:" + token));
                if (!blacklisted) {
                    Claims claims = jwtUtils.parseToken(token);
                    Long userId = claims.get("userId", Long.class);
                    // 绑定 principal（name 即 userId 字符串），作为用户目的地解析与路由依据
                    accessor.setUser(new UsernamePasswordAuthenticationToken(userId, token, Collections.emptyList()));
                    return message;
                }
            }
            log.warn("WebSocket 连接鉴权失败，已拒绝 CONNECT");
            throw new MessagingException("未授权：请先登录");
        }
        return message;
    }

    /** 从 Authorization header 提取 Bearer token，无有效值返回 null */
    private String resolveToken(String header) {
        if (StringUtils.isBlank(header)) {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
