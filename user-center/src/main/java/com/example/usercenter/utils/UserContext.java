package com.example.usercenter.utils;

import com.example.usercenter.model.dto.LoginUserDTO;

/**
 * 创建线程局变量，用于保存当前登录用户
 */
public class UserContext {
    //创建一个ThreadLocal变量，专门存当前登录用户
    private static final ThreadLocal<LoginUserDTO> HOLDER = new ThreadLocal<>();

    //存用户：把当前登录用户放进线程里
    public static void set(LoginUserDTO user) { HOLDER.set(user); }
    //取用户：从线程里取出当前登录用户
    public static LoginUserDTO get() { return HOLDER.get(); }
    //删除用户：删除线程里的用户
    public static void clear() { HOLDER.remove(); }
}
