package com.example.usercenter.model.domain.request;

import lombok.Data;

/**
 * 用户重置密码请求体（忘记密码场景）
 */
@Data
public class UserResetPasswordRequest {
    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    /**
     * 验证码
     */
    private String captchaCode;

    /**
     * 验证码唯一标识
     */
    private String captchaKey;
}
