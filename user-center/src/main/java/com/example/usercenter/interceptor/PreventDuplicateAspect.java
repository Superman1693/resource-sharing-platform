package com.example.usercenter.interceptor;

import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 * 拦截带有 @PreventDuplicate 注解的方法，实现分布式锁防重复提交
 */
@Slf4j
@Aspect
@Component
public class PreventDuplicateAspect {

    private final RedissonClient redissonClient;

    public PreventDuplicateAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(com.example.usercenter.annotation.PreventDuplicate)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        PreventDuplicate annotation = method.getAnnotation(PreventDuplicate.class);

        // 构建锁的 key
        String lockKey = buildLockKey(annotation, method);

        // 获取锁
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(annotation.waitTime(), annotation.leaseTime(), TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("防重复提交拦截: {}", lockKey);
                throw new BusinessException(ErrorCode.OPERATION_TOO_FREQUENT, annotation.message());
            }

            log.debug("防重复提交获取锁成功: {}", lockKey);
            return joinPoint.proceed();

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("防重复提交释放锁: {}", lockKey);
            }
        }
    }

    /**
     * 构建锁的 key
     * 格式：prefix:userId:uri:methodName
     */
    private String buildLockKey(PreventDuplicate annotation, Method method) {
        StringBuilder keyBuilder = new StringBuilder();

        // 前缀
        if (annotation.keyPrefix() != null && !annotation.keyPrefix().isEmpty()) {
            keyBuilder.append(annotation.keyPrefix());
        } else {
            keyBuilder.append("prevent_duplicate");
        }

        // 用户 ID
        if (annotation.useUserId()) {
            try {
                LoginUserDTO loginUser = UserContext.get();
                if (loginUser != null && loginUser.getUserId() != null) {
                    keyBuilder.append(":user:").append(loginUser.getUserId());
                }
            } catch (Exception e) {
                // 未登录用户使用 IP
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String ip = getClientIp(request);
                    keyBuilder.append(":ip:").append(ip);
                }
            }
        }

        // 请求 URI
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            keyBuilder.append(":uri:").append(attributes.getRequest().getRequestURI());
        }

        // 方法名
        keyBuilder.append(":method:").append(method.getName());

        return keyBuilder.toString();
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
