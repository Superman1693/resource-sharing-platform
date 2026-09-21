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

    /**
     * 登录。
     *
     * @param loginId      登录标识，<b>可以是用户名，也可以是邮箱</b>；
     *                     含 {@code @} 时按邮箱处理，否则按用户名处理
     * @param userPassword 明文密码（内部按 BCrypt / 历史 MD5 两种格式校验）
     * @param request      用于记录登录 IP 等
     * @return 脱敏后的用户；账号不存在或密码不匹配时返回 null
     */
    User userLogin(String loginId, String userPassword, HttpServletRequest request);

    /**
     * 解析用户的「当前星球」ID，用于签发 JWT 的 starId claim。
     *
     * <p>取值顺序：</p>
     * <ol>
     *   <li>{@code user.current_star_id}（用户在多个星球间主动选定的那一个）；</li>
     *   <li>为空时回退到 {@code star_member} 中<b>最早加入</b>的星球，并回写到 {@code current_star_id}；</li>
     *   <li>仍为空说明用户未加入任何星球，返回 {@code null}。</li>
     * </ol>
     *
     * @param userId 用户ID
     * @return 当前星球ID，可能为 null
     */
    Long resolveCurrentStarId(Long userId);

    /**
     * 切换用户的当前星球，并重新签发携带新 starId 的 JWT。
     *
     * @param starId 目标星球ID，必须已加入
     * @return 新的 JWT token
     */
    String switchCurrentStar(Long starId);

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
