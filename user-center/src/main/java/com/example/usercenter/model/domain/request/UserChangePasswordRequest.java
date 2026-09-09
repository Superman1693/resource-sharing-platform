package com.example.usercenter.model.domain.request;

import lombok.Data;

/**
 * 用户修改密码请求体
 */
@Data
public class UserChangePasswordRequest {
    /**
     * 旧密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;
}
