package com.example.usercenter.service;

import com.example.usercenter.model.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.model.domain.request.UserRegisterRequest;
import com.example.usercenter.model.domain.request.UserResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author zengyi
* @description 针对表【user】的数据库操作Service
* @createDate 2025-09-14 15:32:43
*/
@Service
public interface UserService extends IService<User> {
    /*
    用户登录键
     */
    public static final String USER_LOGIN_STATE = "userLoginState";
    /**
     *用户注册
     * @param userRegisterRequest 用户注册数据
     * @param
     * @param
     * @return 用户id
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /*
    用户登录校验密码
    userAccount
    userPassword
    脱敏后的用户信息
     */

    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     * @param originUser
     * @return
     */
    User getSafetyUser(User originUser);

    /**
     * 用户更新个人信息
     * @param user  用户更新数据
     * @param request
     * @return
     */


    boolean updateUser(User user, HttpServletRequest request);
    /**
     * 用户注销
     */
    int userLogout(HttpServletRequest request);

    /**
     * 账号注销（彻底销户）
     * @param userPassword 用户密码（用于二次确认）
     * @param request HTTP请求
     * @return 是否成功
     */
    boolean closeAccount(String userPassword, HttpServletRequest request);

    /**
     * 修改密码
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @param checkPassword 确认新密码
     * @param request HTTP请求
     * @return 是否成功
     */
    boolean changePassword(String oldPassword, String newPassword, String checkPassword, HttpServletRequest request);

    /**
     * 重置密码（忘记密码场景，不需要登录）
     * @param request 重置密码请求
     * @return 是否成功
     */
    boolean resetPassword(UserResetPasswordRequest request);
}
