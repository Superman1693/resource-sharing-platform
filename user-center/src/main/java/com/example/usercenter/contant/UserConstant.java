package com.example.usercenter.contant;

/**
 *用户常量
 */
public interface UserConstant {
    /*
    用户登录键
     */
    String USER_LOGIN_STATE="userLoginState";
    //-----权限-----
    /*
    默认权限
     */
    int DEFAULT_ROLE=0;
    /*
    管理员权限
     */
    int ADMIN_ROLE=1;

    //-----第三方登录账号的密码占位符-----
    /**
     * 第三方（GitHub / QQ）登录自动创建的账号没有可用密码。
     * <p>这里写入一个**永不匹配任何输入**的占位串，而不是空字符串：
     * <ul>
     *   <li>数据库列 `user_password` 是 NOT NULL，空串语义不清；</li>
     *   <li>占位串既不是 BCrypt 格式（不以 $2a$ 开头），也不是 32 位 MD5，
     *       因此不会与任何密码校验分支匹配，天然无法被密码登录；</li>
     *   <li>登录时命中该值会返回「请使用第三方平台登录」的明确提示，而不是「用户名或密码错误」。</li>
     * </ul>
     */
    String OAUTH_PASSWORD_PLACEHOLDER = "!oauth-no-password!";
}
