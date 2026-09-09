package com.example.usercenter.model.domain.request;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求体
 * @author  zy
 */


@Data
public class UserRegisterRequest implements Serializable {


    @Serial  //标记序列化版本号，表示该字段用于序列化版本控制
    private static final long serialVersionUID = -543286974259314538L;
    //UUID类似于class的版本号

    private String userAccount;

    private String userPassword;

    private String checkPassword;
    private String username;   // 昵称
    private String phone;
    private String email;
    private String avatarUrl;
    private Integer gender;    // 建议用 Integer，避免 "2" / 2 类型不一致


}
