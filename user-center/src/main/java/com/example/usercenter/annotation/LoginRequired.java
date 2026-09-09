package com.example.usercenter.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginRequired {}
//自定义标记注解，给某个类或方法贴上标记，系统自动检查当前用户是否登录，
// 没有登录拒绝操作，表示该类或方法需要登录权限