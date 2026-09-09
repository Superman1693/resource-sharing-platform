package com.example.usercenter.config;

import com.example.usercenter.interceptor.WebSocketAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 配置
 * 实现实时通知推送（点赞、评论、关注等）
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    /**
     * 注册 STOMP 入站拦截器：CONNECT 帧校验 JWT，绑定用户 principal
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    /**
     * 配置消息代理
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户端订阅前缀：客户端订阅 /topic/xxx 接收服务端推送
        config.enableSimpleBroker("/topic");
        // 客户端发送前缀：客户端发送消息到 /app/xxx
        config.setApplicationDestinationPrefixes("/app");
        // 用户目标前缀（点对点推送）
        config.setUserDestinationPrefix("/user");
    }

    /**
     * 注册 STOMP 端点
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 连接端点，支持 SockJS 降级
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:5173", "http://localhost:5174", "https://www.e-ren.icu")
                .withSockJS();
    }
}
