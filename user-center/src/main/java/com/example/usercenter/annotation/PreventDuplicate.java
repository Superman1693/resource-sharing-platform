package com.example.usercenter.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交注解
 * 标记在 Controller 方法上，防止用户快速重复提交
 *
 * 使用示例：
 * {@code @PreventDuplicate(waitTime = 3, leaseTime = 5)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicate {

    /**
     * 等待获取锁的最大时间（秒）
     * 默认 0 表示不等待，获取失败直接返回
     */
    long waitTime() default 0;

    /**
     * 锁的自动过期时间（秒）
     * 默认 5 秒
     */
    long leaseTime() default 5;

    /**
     * 锁的 key 前缀
     * 默认使用方法全限定名
     */
    String keyPrefix() default "";

    /**
     * 是否使用用户 ID 作为 key 的一部分
     * 默认 true，表示每个用户独立加锁
     */
    boolean useUserId() default true;

    /**
     * 提示信息
     */
    String message() default "操作过于频繁，请稍后再试";
}
