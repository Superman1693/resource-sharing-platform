package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.UserRegisterRequest;
import com.example.usercenter.model.domain.request.UserResetPasswordRequest;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.UserService;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.utils.JwtUtils;
import com.example.usercenter.utils.SensitiveWordChecker;
import com.example.usercenter.utils.UserContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户服务实现类
 * 
 * @author zengyi
 * @description 针对表【user】的数据库操作Service实现
 * @createDate 2025-09-14 15:32:43
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Resource
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Resource
    private SensitiveWordChecker sensitiveWordChecker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private com.example.usercenter.service.CaptchaService captchaService;
    // 新增：定义登录状态常量（原登录方法中已使用，需补充定义）
    public static final String USER_LOGIN_STATE = "userLoginState";
    // 新增：管理员角色标识（假设1为管理员，0为普通用户）
    private static final int ADMIN_ROLE = 1;
    /*
     * 加盐值混淆密码
     */
    private static final String SALT = "yiyi";

    @Override
    public long userRegister(UserRegisterRequest request) {
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        String username = request.getUsername();
        String phone = request.getPhone();
        String email = request.getEmail();
        String avatarUrl = request.getAvatarUrl();
        Integer gender = request.getGender();
        // 1.校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }

        // 账号不能包含特殊字符
        String validPattern = "[~!@#$%^&*()_+{}:\"<>?`\\-=\\[\\]\\\\;',./ ]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号包含特殊字符");
        }
        // 昵称敏感词校验
        sensitiveWordChecker.check("用户名", username);
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码与校验密码不同");
        }
        // 手机号格式校验（非空时才校验）
        if (StringUtils.isNotBlank(phone)) {
            String phonePattern = "^1[3-9]\\d{9}$";
            if (!Pattern.matches(phonePattern, phone)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "手机号格式错误");
            }
        }
        // 邮箱格式校验（非空时才校验）
        if (StringUtils.isNotBlank(email)) {
            String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
            if (!Pattern.matches(emailPattern, email)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式错误");
            }
        }
        // 账号不能重复（先快速检查，再依赖数据库唯一约束兜底）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册");
        }

        // 2.密码加密
        String encryptPassword = bCryptPasswordEncoder.encode(userPassword);
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setGender(gender);

        try {
            boolean saveResult = this.save(user);
            if (!saveResult) {
                return -1;
            }
        } catch (DuplicateKeyException e) {
            // 并发注册同一账号时，数据库唯一约束兜底
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册");
        }
        return user.getId();
    }

    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1.校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null; // 返回null让Controller层处理
        }
        if (userAccount.length() < 4) {
            return null; // 返回null让Controller层处理
        }
        if (userPassword.length() < 8) {
            return null; // 返回null让Controller层处理
        }

        // 账号不能包含特殊字符
        String validPattern = "[~!@#$%^&*()_+{}:\"<>?`\\-=\\[\\]\\\\;',./ ]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return null; // 返回null让Controller层处理
        }
        // 2.查询用户并验证密码（兼容 BCrypt 和旧 MD5 格式）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.trace("user login failed, userAccount not found");
            return null;
        }
        // 验证密码：判断是否为 BCrypt 格式
        boolean passwordMatch;
        if (user.getUserPassword().startsWith("$2a$")) {
            // BCrypt 格式：直接用 matches 验证
            passwordMatch = bCryptPasswordEncoder.matches(userPassword, user.getUserPassword());
        } else {
            // 旧 MD5 格式：用原方式验证
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            passwordMatch = encryptPassword.equals(user.getUserPassword());
            if (passwordMatch) {
                // 验证成功后自动迁移为 BCrypt 格式
                user.setUserPassword(bCryptPasswordEncoder.encode(userPassword));
                userMapper.updateById(user);
            }
        }
        if (!passwordMatch) {
            log.trace("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 检查用户封禁状态
        if (Integer.valueOf(1).equals(user.getUserStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁");
        }
        // 3.用户脱敏
        User safetyUser = getSafetyUser(user);
        return safetyUser;
    }

    /**
     * 用户脱敏
     * 密码通过 @JsonProperty(WRITE_ONLY) 自动排除
     * 手机号/邮箱通过 @MaskSensitive 注解自动脱敏
     *
     * @param originUser 原始用户
     * @return 脱敏后的用户（密码已清除）
     */
    @Override
    public User getSafetyUser(User originUser) {
        // 密码字段由 @JsonProperty(access = WRITE_ONLY) 控制不序列化
        // 手机号/邮箱由 @MaskSensitive 注解自动脱敏（138****5678 / z***@qq.com）
        // 此处额外清除密码作为纵深防御
        originUser.setUserPassword(null);
        return originUser;
    }

    @Override
    public boolean updateUser(User user, HttpServletRequest request) {
        // 1. 基础参数校验
        if (user == null || user.getId() == null) {
            log.error("更新用户失败，用户信息或用户ID为空");
            return false;
        }

        // 2. 验证用户是否存在
        Long userId = user.getId();
        User oldUser = userMapper.selectById(userId);
        if (oldUser == null) {
            log.error("更新用户失败，用户不存在，userId: " + userId);
            return false;
        }

        // 3. 权限校验（仅本人或管理员可更新）
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null) {
            log.error("更新用户失败，未登录");
            return false;
        }
        // 非本人且非管理员，无权更新
        if (!loginUser.getUserId().equals(userId) && !Integer.valueOf(ADMIN_ROLE).equals(loginUser.getUserRole())) {
            log.error("权限不足, userId: " + userId + ", loginUserId: " + loginUser.getUserId());
            return false;
        }

        // 4. 敏感字段处理（不允许修改用户账号）
        user.setUserAccount(oldUser.getUserAccount()); // 强制保持原账号

        // 5. 密码更新处理（若有新密码则加密）
        String newPassword = user.getUserPassword();
        if (StringUtils.isNotBlank(newPassword)) {
            // 密码长度校验
            if (newPassword.length() < 8) {
                log.error("更新用户失败，密码长度不足8位");
                return false;
            }
            // 密码加密（BCrypt）
            user.setUserPassword(bCryptPasswordEncoder.encode(newPassword));
        } else {
            // 不更新密码时保持原密码
            user.setUserPassword(oldUser.getUserPassword());
        }

        // 6. 其他字段格式校验
        // 手机号校验（非空时验证）
        if (StringUtils.isNotBlank(user.getPhone())) {
            String phonePattern = "^1[3-9]\\d{9}$";
            if (!Pattern.matches(phonePattern, user.getPhone())) {
                log.error("更新用户失败，手机号格式错误: " + user.getPhone());
                return false;
            }
        }
        // 邮箱校验（非空时验证）
        if (StringUtils.isNotBlank(user.getEmail())) {
            String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
            if (!Pattern.matches(emailPattern, user.getEmail())) {
                log.error("更新用户失败，邮箱格式错误: " + user.getEmail());
                return false;
            }
        }
        // 个人简介/技能/服务长度校验
        if (user.getBio() != null && user.getBio().length() > 500) {
            return false;
        }
        if (user.getSkills() != null && user.getSkills().length() > 500) {
            return false;
        }
        if (user.getServices() != null && user.getServices().length() > 500) {
            return false;
        }
        // 敏感词校验（仅校验本次提交的字段）
        sensitiveWordChecker.check("用户名", user.getUsername());
        sensitiveWordChecker.check("个人简介", user.getBio());
        sensitiveWordChecker.check("擅长技术栈", user.getSkills());
        sensitiveWordChecker.check("可提供的服务", user.getServices());

        // 7. 执行更新操作
        int updateRows = userMapper.updateById(user);

        return updateRows > 0;
    }

    /**
     * 用户注销
     * 
     * @param request
     */
    @Override
    public int userLogout(HttpServletRequest request) {
        // 将 token 加入 Redis 黑名单
        LoginUserDTO currentUser = UserContext.get();
        if (currentUser == null || StringUtils.isBlank(currentUser.getToken())) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        String token = currentUser.getToken();
        long remainingSeconds = jwtUtils.getRemainingSeconds(token);
        if (remainingSeconds > 0) {
            stringRedisTemplate.opsForValue().set("blacklist:" + token, "1", remainingSeconds, TimeUnit.SECONDS);
        }
        return 1;
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return user != null && ADMIN_ROLE == user.getUserRole();
    }

    /**
     * 账号注销（彻底销户）
     */
    @Override
    public boolean closeAccount(String userPassword, HttpServletRequest request) {
        // 1. 获取当前登录用户
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null) {
            log.error("账号注销失败，用户未登录");
            return false;
        }
        Long loginUserId = loginUser.getUserId();

        // 2. 参数校验
        if (StringUtils.isBlank(userPassword)) {
            log.error("账号注销失败，密码不能为空");
            return false;
        }

        // 3. 验证密码（兼容 BCrypt 和旧 MD5 格式）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", loginUserId);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.error("账号注销失败，用户不存在");
            return false;
        }
        boolean passwordMatch;
        if (user.getUserPassword().startsWith("$2a$")) {
            passwordMatch = bCryptPasswordEncoder.matches(userPassword, user.getUserPassword());
        } else {
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            passwordMatch = encryptPassword.equals(user.getUserPassword());
        }
        if (!passwordMatch) {
            log.error("账号注销失败，密码错误");
            return false;
        }

        // 4. 删除用户（逻辑删除）
        boolean result = this.removeById(loginUserId);
        if (result) {
            // 5. 将当前 JWT token 加入 Redis 黑名单，立即失效
            String token = loginUser.getToken();
            if (StringUtils.isNotBlank(token)) {
                long remainingSeconds = jwtUtils.getRemainingSeconds(token);
                if (remainingSeconds > 0) {
                    stringRedisTemplate.opsForValue().set("blacklist:" + token, "1", remainingSeconds, TimeUnit.SECONDS);
                }
            }
            log.info("账号注销成功，userId: " + loginUserId);
        } else {
            log.error("账号注销失败，删除用户失败，userId: " + loginUserId);
        }

        return result;
    }

    /**
     * 修改密码
     */
    @Override
    public boolean changePassword(String oldPassword, String newPassword, String checkPassword, HttpServletRequest request) {
        // 1. 获取当前登录用户
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null) {
            log.error("修改密码失败，用户未登录");
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        Long loginUserId = loginUser.getUserId();

        // 2. 参数校验
        if (StringUtils.isAnyBlank(oldPassword, newPassword, checkPassword)) {
            log.error("修改密码失败，参数为空");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }

        // 3. 新密码长度校验
        if (newPassword.length() < 8) {
            log.error("修改密码失败，新密码长度不足8位");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能少于8位");
        }

        // 4. 新密码与确认密码一致性校验
        if (!newPassword.equals(checkPassword)) {
            log.error("修改密码失败，新密码与确认密码不一致");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码与确认密码不一致");
        }

        // 5. 新旧密码不能相同
        if (oldPassword.equals(newPassword)) {
            log.error("修改密码失败，新旧密码相同");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码不能与旧密码相同");
        }

        // 6. 验证旧密码是否正确（兼容 BCrypt 和旧 MD5 格式）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", loginUserId);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            log.error("修改密码失败，用户不存在");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        boolean oldPasswordMatch;
        if (user.getUserPassword().startsWith("$2a$")) {
            oldPasswordMatch = bCryptPasswordEncoder.matches(oldPassword, user.getUserPassword());
        } else {
            String encryptOldPassword = DigestUtils.md5DigestAsHex((SALT + oldPassword).getBytes());
            oldPasswordMatch = encryptOldPassword.equals(user.getUserPassword());
        }
        if (!oldPasswordMatch) {
            log.error("修改密码失败，旧密码错误");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码错误");
        }

        // 7. 加密新密码并更新
        String encryptNewPassword = bCryptPasswordEncoder.encode(newPassword);
        User updateUser = new User();
        updateUser.setId(loginUserId);
        updateUser.setUserPassword(encryptNewPassword);

        int updateRows = userMapper.updateById(updateUser);
        if (updateRows > 0) {
            log.info("修改密码成功，userId: " + loginUserId);
            return true;
        } else {
            log.error("修改密码失败，更新数据库失败，userId: " + loginUserId);
            return false;
        }
    }

    @Override
    public boolean resetPassword(UserResetPasswordRequest resetRequest) {
        String userAccount = resetRequest.getUserAccount();
        String newPassword = resetRequest.getNewPassword();
        String checkPassword = resetRequest.getCheckPassword();
        String captchaCode = resetRequest.getCaptchaCode();
        String captchaKey = resetRequest.getCaptchaKey();

        // 参数校验
        if (StringUtils.isAnyBlank(userAccount, newPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (StringUtils.isAnyBlank(captchaCode, captchaKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能小于8位");
        }
        if (!newPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // 校验验证码
        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
        }

        // 查询账号是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号不存在");
        }

        // 加密新密码并更新
        String encryptPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserPassword(encryptPassword);
        updateUser.setUpdateTime(new Date());

        int updateRows = userMapper.updateById(updateUser);
        if (updateRows > 0) {
            log.info("重置密码成功，userAccount: {}", userAccount);
            return true;
        } else {
            log.error("重置密码失败，更新数据库失败，userAccount: {}", userAccount);
            return false;
        }
    }

}
