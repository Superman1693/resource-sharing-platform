package com.example.usercenter.model.domain.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Author: zy
 * @CreateTime: 2025-09-20
 * @Description:
 * @Version: 1.0
 */
@Data
public class UserLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3479133601033257817L;

    private String userAccount;

    private String userPassword;

    /**
     * 记住我：true=30天，false=7天（默认）
     */
    private boolean rememberMe = false;


}
