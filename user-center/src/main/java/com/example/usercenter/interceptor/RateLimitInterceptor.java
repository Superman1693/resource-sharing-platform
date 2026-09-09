package com.example.usercenter.interceptor;

import com.example.usercenter.annotation.RateLimit;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 计数器的滑动窗口限流拦截器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;

        RateLimit rateLimit = method.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) return true;

        String identity = resolveIdentity(request);
        String redisKey = "rate:" + rateLimit.key() + ":" + identity;

        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            // 第一次请求，设置过期时间
            stringRedisTemplate.expire(redisKey, rateLimit.windowSeconds(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.maxRequests()) {
            log.warn("限流触发: key={}, count={}, identity={}", redisKey, count, identity);
            writeError(response, "请求过于频繁，请稍后再试");
            return false;
        }
        return true;
    }

    /** 优先用 userId，未登录则用 IP */
    private String resolveIdentity(HttpServletRequest request) {
        LoginUserDTO user = UserContext.get();
        if (user != null) return "u:" + user.getUserId();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return "ip:" + ip;
    }

    private void writeError(HttpServletResponse response, String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(429);
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ResultUtils.error(ErrorCode.SYSTEM_ERROR, msg)));
    }
}
