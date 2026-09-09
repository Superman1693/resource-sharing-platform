package com.example.usercenter.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
//元注解：定义@AdminRequired注解既能贴在类上，也能贴在方法上
@Retention(RetentionPolicy.RUNTIME)
//注解的保留时间：运行时保留
@Documented
public @interface AdminRequired {}
//自定义标记注解，给某个类或方法贴上标记，系统自动检查当前用户是否为管理员，
// 不是管理员拒绝操作，表示该类或方法需要管理员权限
