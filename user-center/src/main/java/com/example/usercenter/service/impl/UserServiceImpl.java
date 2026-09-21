package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.contant.UserConstant;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.StarMember;
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
import java.util.List;
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
    private com.example.usercenter.mapper.StarMemberMapper starMemberMapper;

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

    /**
     * 邮箱格式。
     *
     * <p>用途：登录时区分「账号」与「邮箱」两种登录标识——
     * 含 {@code @} 的按邮箱校验格式，否则按账号规则「禁止特殊字符」校验。</p>
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

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

    /**
     * 校验原始密码是否与库中口令一致，兼容 BCrypt 与历史 MD5 两种格式。
     *
     * <p>MD5 校验通过后会<b>就地升级为 BCrypt</b>（与改造前的行为一致）。</p>
     *
     * @param rawPassword 用户输入的明文密码
     * @param user        候选用户（可能出现多条，见 userLogin 中的说明）
     * @return 口令是否匹配；第三方登录账号的占位符口令永远不会匹配成功
     */
    private boolean matchesPassword(String rawPassword, User user) {
        String stored = user.getUserPassword();
        if (StringUtils.isBlank(stored)) {
            return false;
        }
        if (stored.startsWith("$2a$")) {
            // BCrypt 格式：直接用 matches 验证
            return bCryptPasswordEncoder.matches(rawPassword, stored);
        }
        // 旧 MD5 格式：用原方式验证
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + rawPassword).getBytes());
        if (encryptPassword.equals(stored)) {
            // 验证成功后自动迁移为 BCrypt 格式
            user.setUserPassword(bCryptPasswordEncoder.encode(rawPassword));
            userMapper.updateById(user);
            return true;
        }
        return false;
    }

    @Override
    public User userLogin(String loginId, String userPassword, HttpServletRequest request) {
        // 1.校验
        if (StringUtils.isAnyBlank(loginId, userPassword)) {
            return null; // 返回null让Controller层处理
        }
        // 登录标识 = 账号 或 邮箱，先去掉首尾空格。
        // 注意：这里必须用一个**新的局部变量**承载 trim 结果，不能给入参重新赋值——
        // 被重新赋值的变量不再是 "effectively final"，无法在下面的 lambda 中引用。
        String loginKey = loginId.trim();
        if (loginKey.length() < 4) {
            return null; // 返回null让Controller层处理
        }
        if (userPassword.length() < 8) {
            return null; // 返回null让Controller层处理
        }

        // 2.按输入形态分流校验
        //    含 @ → 视为邮箱，校验邮箱格式；
        //    否则 → 视为账号，沿用「不能包含特殊字符」的规则。
        //    注意：原来的账号规则会把 @ 和 . 判为非法字符，若不分流，邮箱将永远无法登录。
        boolean emailLogin = loginKey.contains("@");
        if (emailLogin) {
            if (!EMAIL_PATTERN.matcher(loginKey).matches()) {
                log.trace("user login failed, invalid email format: {}", loginKey);
                return null;
            }
        } else {
            String validPattern = "[~!@#$%^&*()_+{}:\"<>?`\\-=\\[\\]\\\\;',./ ]";
            Matcher matcher = Pattern.compile(validPattern).matcher(loginKey);
            if (matcher.find()) {
                return null; // 返回null让Controller层处理
            }
        }

        // 3.查询候选用户：账号 或 邮箱，任一命中即为候选
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.and(w -> w.eq("user_account", loginKey).or().eq("email", loginKey));
        List<User> candidates = userMapper.selectList(queryWrapper);
        if (candidates.isEmpty()) {
            log.trace("user login failed, no user matches loginId: {}", loginKey);
            return null;
        }

        // 4.以「密码匹配」为唯一判定依据，从候选中确定真正的用户。
        //   为什么不要求唯一命中：email 列没有唯一索引，历史数据里存在多个账号共用同一邮箱
        //   （例如自主注册的账号与 GitHub 自动建号共用一个邮箱）。用密码匹配既能避免
        //   selectOne 命中多条时抛 TooManyResultsException，也保证不会「猜」到别人的账号
        //   ——密码对不上就不放行。单账号场景下与改造前完全等价。
        User user = null;
        for (User candidate : candidates) {
            if (matchesPassword(userPassword, candidate)) {
                user = candidate;
                break;
            }
        }
        if (user == null) {
            // 第三方登录账号没有可用密码：给出明确提示，而不是笼统的「账号/邮箱或密码错误」
            if (candidates.size() == 1
                    && UserConstant.OAUTH_PASSWORD_PLACEHOLDER.equals(candidates.get(0).getUserPassword())) {
                throw new BusinessException(ErrorCode.LOGIN_FAILED, "该账号由第三方平台创建，请使用 GitHub 登录");
            }
            log.trace("user login failed, password not match for loginId: {}", loginKey);
            return null;
        }
        if (candidates.size() > 1) {
            log.info("登录标识 {} 命中 {} 个账号，已按密码匹配选中 id={}；"
                            + "建议为 user.email 建唯一索引并清理重复邮箱，以消除歧义",
                    loginKey, candidates.size(), user.getId());
        }
        // 检查用户封禁状态
        if (Integer.valueOf(1).equals(user.getUserStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁");
        }
        // 3.用户脱敏
        User safetyUser = getSafetyUser(user);
        return safetyUser;
    }

    @Override
    public Long resolveCurrentStarId(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user != null && user.getCurrentStarId() != null) {
            return user.getCurrentStarId();
        }
        // 回退：取最早加入的星球，并回写为「当前星球」，后续无需重复推导
        Long fallbackStarId = starMemberMapper.selectPrimaryStarId(userId);
        if (fallbackStarId != null) {
            User patch = new User();
            patch.setId(userId);
            patch.setCurrentStarId(fallbackStarId);
            userMapper.updateById(patch);
        }
        return fallbackStarId;
    }

    @Override
    public String switchCurrentStar(Long starId) {
        if (starId == null || starId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球ID不合法");
        }
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }

        // 校验成员身份：必须是该星球的成员才能切换
        QueryWrapper<StarMember> wrapper = new QueryWrapper<>();
        wrapper.eq("star_id", starId).eq("user_id", loginUser.getUserId());
        Long count = starMemberMapper.selectCount(wrapper);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NO_AUTH, "你尚未加入该星球");
        }

        // 更新当前星球
        User patch = new User();
        patch.setId(loginUser.getUserId());
        patch.setCurrentStarId(starId);
        userMapper.updateById(patch);

        // 重新签发 Token：旧 Token 里的 starId 是登录时的快照，必须刷新
        User user = userMapper.selectById(loginUser.getUserId());
        long expirationSeconds = jwtUtils.getExpiration();
        return jwtUtils.generateToken(user.getId(), user.getUserRole(), starId, expirationSeconds);
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
