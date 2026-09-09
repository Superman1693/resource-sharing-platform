package com.example.usercenter.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解
 * 基于 Redis 滑动窗口，按用户 IP 或 userId 限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 时间窗口（秒），默认 60 秒 */
    int windowSeconds() default 60;

    /** 窗口内最大请求次数 */
    int maxRequests() default 100;

    /** 限流 key 前缀，用于区分不同接口 */
    String key() default "default";
}
