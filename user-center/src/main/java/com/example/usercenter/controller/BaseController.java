package com.example.usercenter.controller;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;

/**
 * 抽象父类，通过继承就能用权限方法
 */
public abstract class BaseController {

    /**
     * 获取当前登录用户 DTO，未登录抛 NOT_LOGIN
     */
    protected LoginUserDTO getLoginUser() {
        //从线程中拿用户信息
        LoginUserDTO user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return user;
    }

    /**
     * 判断当前用户是否为管理员
     */
    protected boolean isAdmin() {
        LoginUserDTO user = UserContext.get();
        return user != null && Integer.valueOf(1).equals(user.getUserRole());
    }

    /**
     * 断言当前用户为管理员，否则抛 NO_AUTH
     */
    protected void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
    }
}
