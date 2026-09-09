package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.RateLimit;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.UserChangePasswordRequest;
import com.example.usercenter.model.domain.request.UserLoginRequest;
import com.example.usercenter.model.domain.request.UserRegisterRequest;
import com.example.usercenter.model.domain.request.UserResetPasswordRequest;
import com.example.usercenter.service.UserService;
import com.example.usercenter.utils.JwtUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户接口
 * 
 * @author zy
 */

@RestController // 适用于编写restful风格的api，返回值默认为json类型
// @CrossOrigin //解决前端与后端的跨域请求问题问题
@RequestMapping("/user") // 定义一个请求路径
@Slf4j
public class UserController extends BaseController {

    @Resource // 引入service
    private UserService userService;

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private StarMemberMapper starMemberMapper;

    @PostMapping("/register") // 请求方式为post地址为register
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {// 将前端请求的body数据和userRegisterRequest
                                                                                                  // 做一个关联，自动映射到userRegisterRequest对象中
        if (userRegisterRequest == null) {// 判断传入的数据是否为空
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {// 检查这几个字符串参数中是否至少有一个为空或空白字符串
            throw new BusinessException(ErrorCode.PARAMS_ERROR);

        }

        long result = userService.userRegister(userRegisterRequest);// 调用userService.userRegister方法，将其作为参数传入，并返回该方法执行结果
        return ResultUtils.success(result);
    }

    @PostMapping("/login")
    @RateLimit(key = "login", windowSeconds = 60, maxRequests = 10)
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 参数校验
        if (userLoginRequest == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // 2. 基础参数校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户名或密码不能为空");
        }
        if (userAccount.length() < 4) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户名长度不能少于4位");
        }
        if (userPassword.length() < 8) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "密码长度不能少于8位");
        }

        // 3. 调用 Service 层
        User user = userService.userLogin(userAccount, userPassword, request);

        // 4. 处理 Service 层的返回结果
        if (user == null) {
            // 登录失败，返回错误的 BaseResponse 对象
            return ResultUtils.error(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }

        // 5. 生成 JWT token（记住我：30天；普通：7天）
        boolean rememberMe = userLoginRequest.isRememberMe();
        long expirationSeconds = rememberMe ? 30L * 24 * 3600 : jwtUtils.getExpiration();
        String token = jwtUtils.generateToken(user.getId(), user.getUserRole(), expirationSeconds);
        user.setToken(token);

        // 6. 登录成功，返回成功的 BaseResponse 对象
        return ResultUtils.success(user);
    }

    /**
     * 账号注销（彻底销户）
     */
    @PostMapping("/closeAccount")
    public BaseResponse<Boolean> closeAccount(@RequestBody Map<String, String> requestBody, HttpServletRequest request) {
        // 1. 参数校验
        if (requestBody == null || !requestBody.containsKey("userPassword")) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }

        String userPassword = requestBody.get("userPassword");
        if (StringUtils.isBlank(userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "密码不能为空");
        }

        // 2. 调用Service层
        try {
            boolean success = userService.closeAccount(userPassword, request);
            if (success) {
                return ResultUtils.success(true);
            } else {
                return ResultUtils.error(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        } catch (Exception e) {
            log.error("账号注销失败", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "网络错误，注销失败");
        }
    }

    @PostMapping("/userLogout")
    @LoginRequired
    public BaseResponse<Integer> userLogout(HttpServletRequest request) {// 将前端传来的数据和userRegisterRequest 做一个关联
        if (request == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String userAccount, HttpServletRequest request) {
        // 仅管理员可查询
        requireAdmin();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(userAccount)) {
            queryWrapper.like("userAccount", userAccount);
        }
        List<User> userList = userService.list(queryWrapper);
        List<User> list = userList.stream().map(user -> {
            user.setUserPassword(null);
            return userService.getSafetyUser(user);

        }).collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUsers(@RequestBody Long id, HttpServletRequest request) {
        // 仅管理员可查询
        requireAdmin();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);

    }

    /**
     * 查询个人信息
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request) {
        // 获取当前登录用户
        Long userId = getLoginUser().getUserId();

        // 查询完整的用户信息
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        // 脱敏处理，移除密码等敏感信息
        User safetyUser = userService.getSafetyUser(user);
        return ResultUtils.success(safetyUser);
    }

    /**
     * 获取用户公开信息（免登录）
     */
    @GetMapping("/public/{id}")
    public BaseResponse<User> getPublicUser(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不合法");
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return ResultUtils.success(userService.getSafetyUser(user));
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public BaseResponse<String> updateUser(@RequestBody User user, HttpServletRequest request) {
        // 1. 基础参数校验
        if (user == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户信息不能为空");
        }
        if (user.getId() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        }

        // 2. 获取当前登录用户
        Long loginUserId = getLoginUser().getUserId();

        // 3. 校验权限（只能修改自己的信息）
        if (!loginUserId.equals(user.getId())) {
            return ResultUtils.error(ErrorCode.NO_AUTH, "只能修改自己的信息");
        }

        // 4. 调用Service层更新
        boolean success = userService.updateUser(user, request);
        if (success) {
            return ResultUtils.success("更新成功");
        } else {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "更新失败，请检查输入信息是否正确");
        }
    }
    /**
     * 修改密码
     */
    @PostMapping("/changePassword")
    public BaseResponse<String> changePassword(@RequestBody UserChangePasswordRequest userChangePasswordRequest, HttpServletRequest request) {
        // 1. 参数校验
        if (userChangePasswordRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        String oldPassword = userChangePasswordRequest.getOldPassword();
        String newPassword = userChangePasswordRequest.getNewPassword();
        String checkPassword = userChangePasswordRequest.getCheckPassword();

        // 2. 调用 Service 层修改密码
        boolean success = userService.changePassword(oldPassword, newPassword, checkPassword, request);

        // 3. 返回结果
        if (success) {
            return ResultUtils.success("密码修改成功");
        } else {
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "密码修改失败，请稍后重试");
        }
    }

    /**
     * 重置密码（忘记密码，不需要登录）
     */
    @PostMapping("/resetPassword")
    public BaseResponse<String> resetPassword(@RequestBody UserResetPasswordRequest resetRequest) {
        if (resetRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        boolean success = userService.resetPassword(resetRequest);
        if (success) {
            return ResultUtils.success("密码重置成功");
        } else {
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "密码重置失败，请稍后重试");
        }
    }

    /**
     * 获取当前用户的成长统计数据
     */
    @GetMapping("/growth")
    public BaseResponse<Map<String, Object>> getUserGrowth() {
        Long userId = getLoginUser().getUserId();

        // 发布内容数
        QueryWrapper<Note> publishWrapper = new QueryWrapper<>();
        publishWrapper.eq("author_id", userId).eq("status", "published").eq("is_delete", 0);
        long publishCount = noteMapper.selectCount(publishWrapper);

        // 获赞总数
        QueryWrapper<Note> likeWrapper = new QueryWrapper<>();
        likeWrapper.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .select("COALESCE(SUM(like_count), 0) as total");
        Object likeObj = noteMapper.selectObjs(likeWrapper).stream().filter(Objects::nonNull).findFirst().orElse(0);
        long totalLikes = toLong(likeObj);

        // 浏览量总数
        QueryWrapper<Note> viewWrapper = new QueryWrapper<>();
        viewWrapper.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .select("COALESCE(SUM(view_count), 0) as total");
        Object viewObj = noteMapper.selectObjs(viewWrapper).stream().filter(Objects::nonNull).findFirst().orElse(0);
        long totalViews = toLong(viewObj);

        // 评论总数（收到的评论）
        QueryWrapper<Note> commentWrapper = new QueryWrapper<>();
        commentWrapper.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .select("COALESCE(SUM(comment_count), 0) as total");
        Object commentObj = noteMapper.selectObjs(commentWrapper).stream().filter(Objects::nonNull).findFirst().orElse(0);
        long totalComments = toLong(commentObj);

        // 加入的星球数
        QueryWrapper<com.example.usercenter.model.domain.StarMember> starWrapper = new QueryWrapper<>();
        starWrapper.eq("user_id", userId).eq("is_delete", 0);
        long joinedStars = starMemberMapper.selectCount(starWrapper);

        // 贡献值 = 发布*10 + 获赞*2 + 评论*1
        long contribution = publishCount * 10 + totalLikes * 2 + totalComments;
        // 等级：每100贡献值升一级，最低1级
        int level = (int) Math.max(1, contribution / 100 + 1);

        // 最近发布的笔记（时间线）
        QueryWrapper<Note> recentWrapper = new QueryWrapper<>();
        recentWrapper.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .orderByDesc("publish_time").last("LIMIT 10");
        List<Note> recentNotes = noteMapper.selectList(recentWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("publishCount", publishCount);
        result.put("totalLikes", totalLikes);
        result.put("totalViews", totalViews);
        result.put("totalComments", totalComments);
        result.put("joinedStars", joinedStars);
        result.put("contribution", contribution);
        result.put("level", level);
        result.put("nextLevelContribution", (long)(level) * 100);
        result.put("recentNotes", recentNotes);
        return ResultUtils.success(result);
    }

    private long toLong(Object obj) {
        if (obj instanceof BigDecimal) return ((BigDecimal) obj).longValue();
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        return 0L;
    }
}
