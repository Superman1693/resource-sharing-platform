package com.example.usercenter.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.GitHubLoginRequest;
import com.example.usercenter.model.domain.request.QQLoginRequest;
import com.example.usercenter.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OAuth 第三方登录接口（GitHub、QQ）
 */
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "OAuth 接口")
public class OAuthController {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    @Value("${github.oauth.client-id}")
    private String clientId;

    @Value("${github.oauth.client-secret}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${qq.oauth.app-id}")
    private String qqAppId;

    @Value("${qq.oauth.app-key}")
    private String qqAppKey;

    @Value("${qq.oauth.redirect-uri}")
    private String qqRedirectUri;

    /**
     * 获取 GitHub OAuth 授权 URL
     */
    @GetMapping("/github/url")
    @Operation(summary = "获取 GitHub 授权地址")
    public BaseResponse<Map<String, String>> getGithubAuthUrl() {
        String url = "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=read:user+user:email";
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        return ResultUtils.success(result);
    }

    /**
     * GitHub OAuth 登录（前端拿到 code 后调用此接口）
     */
    @PostMapping("/github")
    @Operation(summary = "GitHub 登录")
    public BaseResponse<Map<String, Object>> githubLogin(@RequestBody GitHubLoginRequest request) {
        String code = request.getCode();
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "授权码不能为空");
        }

        // 1. 用 code 换取 access_token
        String tokenResponse = HttpRequest.post("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .form("client_id", clientId)
                .form("client_secret", clientSecret)
                .form("code", code)
                .form("redirect_uri", redirectUri)
                .timeout(10000)
                .execute()
                .body();

        JSONObject tokenJson = JSONUtil.parseObj(tokenResponse);
        String accessToken = tokenJson.getStr("access_token");
        if (StringUtils.isBlank(accessToken)) {
            log.error("GitHub 获取 access_token 失败: {}", tokenResponse);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "GitHub 授权失败");
        }

        // 2. 用 access_token 获取用户信息
        String userResponse = HttpRequest.get("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(10000)
                .execute()
                .body();

        JSONObject githubUser = JSONUtil.parseObj(userResponse);
        String githubLogin = githubUser.getStr("login");
        Long githubId = githubUser.getLong("id");
        String avatarUrl = githubUser.getStr("avatar_url");
        String name = githubUser.getStr("name");

        if (StringUtils.isBlank(githubLogin)) {
            log.error("GitHub 获取用户信息失败: {}", userResponse);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取 GitHub 用户信息失败");
        }

        // 3. 尝试获取邮箱（GitHub 可能隐藏邮箱）
        String email = githubUser.getStr("email");
        if (StringUtils.isBlank(email)) {
            // 尝试从 /user/emails 获取
            try {
                String emailsResponse = HttpRequest.get("https://api.github.com/user/emails")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .timeout(10000)
                        .execute()
                        .body();
                var emails = JSONUtil.parseArray(emailsResponse);
                for (int i = 0; i < emails.size(); i++) {
                    JSONObject emailObj = emails.getJSONObject(i);
                    if (emailObj.getBool("primary", false)) {
                        email = emailObj.getStr("email");
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("获取 GitHub 邮箱失败", e);
            }
        }

        // 4. 查找或创建用户（用 github_{id} 作为账号标识）
        String account = "github_" + githubId;
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", account);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            // 新用户注册
            user = new User();
            user.setUserAccount(account);
            user.setUsername(StringUtils.isNotBlank(name) ? name : githubLogin);
            user.setAvatarUrl(avatarUrl);
            user.setEmail(email);
            user.setUserPassword(""); // GitHub 登录用户无密码
            user.setUserRole(0); // 普通用户
            user.setUserStatus(0); // 正常状态
            user.setCreateTime(new Date());
            user.setUpdateTime(new Date());
            user.setIsDelete(0);
            userMapper.insert(user);
            log.info("GitHub OAuth 新用户注册: {}", account);
        } else {
            // 已有用户，更新头像和昵称
            user.setAvatarUrl(avatarUrl);
            if (StringUtils.isNotBlank(name)) {
                user.setUsername(name);
            }
            user.setUpdateTime(new Date());
            userMapper.updateById(user);
            log.info("GitHub OAuth 用户登录: {}", account);
        }

        // 5. 生成 JWT token
        long expirationSeconds = 7L * 24 * 3600; // 7 天
        String token = jwtUtils.generateToken(user.getId(), user.getUserRole(), expirationSeconds);

        // 6. 组装返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userAccount", user.getUserAccount());
        userInfo.put("username", user.getUsername());
        userInfo.put("avatarUrl", user.getAvatarUrl());
        userInfo.put("userRole", user.getUserRole());
        userInfo.put("userStatus", user.getUserStatus());
        result.put("userInfo", userInfo);

        return ResultUtils.success(result);
    }

    /**
     * 获取 QQ OAuth 授权 URL
     */
    @GetMapping("/qq/url")
    @Operation(summary = "获取 QQ 授权地址")
    public BaseResponse<Map<String, String>> getQQAuthUrl() {
        String encodedRedirectUri = URLEncoder.encode(qqRedirectUri, StandardCharsets.UTF_8);
        String url = "https://graph.qq.com/oauth2.0/authorize"
                + "?response_type=code"
                + "&client_id=" + qqAppId
                + "&redirect_uri=" + encodedRedirectUri
                + "&scope=get_user_info"
                + "&state=qq_login";
        Map<String, String> result = new HashMap<>();
        result.put("url", url);
        return ResultUtils.success(result);
    }

    /**
     * QQ OAuth 登录（前端拿到 code 后调用此接口）
     * QQ OAuth2 流程：code → access_token → get_openid → get_user_info
     */
    @PostMapping("/qq")
    @Operation(summary = "QQ 登录")
    public BaseResponse<Map<String, Object>> qqLogin(@RequestBody QQLoginRequest request) {
        String code = request.getCode();
        if (StringUtils.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "授权码不能为空");
        }

        // 1. 用 code 换取 access_token
        // QQ 返回格式：access_token=xxx&expires_in=7776000&refresh_token=xxx（URL 编码）
        String encodedTokenRedirectUri = URLEncoder.encode(qqRedirectUri, StandardCharsets.UTF_8);
        String tokenResponse = HttpRequest.get("https://graph.qq.com/oauth2.0/token"
                + "?grant_type=authorization_code"
                + "&client_id=" + qqAppId
                + "&client_secret=" + qqAppKey
                + "&code=" + code
                + "&redirect_uri=" + encodedTokenRedirectUri)
                .timeout(10000)
                .execute()
                .body();

        // 解析 URL 编码格式的响应
        String accessToken = extractParam(tokenResponse, "access_token");
        if (StringUtils.isBlank(accessToken)) {
            log.error("QQ 获取 access_token 失败: {}", tokenResponse);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "QQ 授权失败");
        }

        // 2. 用 access_token 获取 openid
        // QQ 返回格式：callback( {"client_id":"...","openid":"..."} );（JSONP 格式）
        String openidResponse = HttpRequest.get("https://graph.qq.com/oauth2.0/me"
                + "?access_token=" + accessToken)
                .timeout(10000)
                .execute()
                .body();

        String openid = extractOpenid(openidResponse);
        if (StringUtils.isBlank(openid)) {
            log.error("QQ 获取 openid 失败: {}", openidResponse);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取 QQ 用户标识失败");
        }

        // 3. 用 access_token + openid 获取用户信息
        String userInfoResponse = HttpRequest.get("https://graph.qq.com/user/get_user_info"
                + "?access_token=" + accessToken
                + "&oauth_consumer_key=" + qqAppId
                + "&openid=" + openid)
                .timeout(10000)
                .execute()
                .body();

        JSONObject qqUser = JSONUtil.parseObj(userInfoResponse);
        Integer ret = qqUser.getInt("ret");
        if (ret == null || ret != 0) {
            log.error("QQ 获取用户信息失败: {}", userInfoResponse);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取 QQ 用户信息失败");
        }

        String nickname = qqUser.getStr("nickname");
        String avatarUrl = qqUser.getStr("figureurl_qq_2"); // 高清头像
        if (StringUtils.isBlank(avatarUrl)) {
            avatarUrl = qqUser.getStr("figureurl_qq_1"); // 普通头像兜底
        }

        // 4. 查找或创建用户（用 qq_{openid} 作为账号标识）
        String account = "qq_" + openid;
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", account);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            // 新用户注册
            user = new User();
            user.setUserAccount(account);
            user.setUsername(StringUtils.isNotBlank(nickname) ? nickname : "QQ用户");
            user.setAvatarUrl(avatarUrl);
            user.setUserPassword(""); // QQ 登录用户无密码
            user.setUserRole(0); // 普通用户
            user.setUserStatus(0); // 正常状态
            user.setCreateTime(new Date());
            user.setUpdateTime(new Date());
            user.setIsDelete(0);
            userMapper.insert(user);
            log.info("QQ OAuth 新用户注册: {}", account);
        } else {
            // 已有用户，更新头像和昵称
            user.setAvatarUrl(avatarUrl);
            if (StringUtils.isNotBlank(nickname)) {
                user.setUsername(nickname);
            }
            user.setUpdateTime(new Date());
            userMapper.updateById(user);
            log.info("QQ OAuth 用户登录: {}", account);
        }

        // 5. 生成 JWT token
        long expirationSeconds = 7L * 24 * 3600; // 7 天
        String jwtToken = jwtUtils.generateToken(user.getId(), user.getUserRole(), expirationSeconds);

        // 6. 组装返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtToken);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userAccount", user.getUserAccount());
        userInfo.put("username", user.getUsername());
        userInfo.put("avatarUrl", user.getAvatarUrl());
        userInfo.put("userRole", user.getUserRole());
        userInfo.put("userStatus", user.getUserStatus());
        result.put("userInfo", userInfo);

        return ResultUtils.success(result);
    }

    /**
     * 从 URL 编码格式的响应中提取参数值
     * 例如：access_token=xxx&expires_in=7776000 → 提取 access_token 的值
     */
    private String extractParam(String response, String paramName) {
        String[] pairs = response.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(paramName)) {
                return kv[1].trim();
            }
        }
        return null;
    }

    /**
     * 从 QQ JSONP 格式的响应中提取 openid
     * 例如：callback( {"client_id":"...","openid":"..."} );
     */
    private String extractOpenid(String response) {
        // 用正则提取 JSON 部分
        Pattern pattern = Pattern.compile("\\{.*?\"openid\"\\s*:\\s*\"(.*?)\".*?\\}");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 兜底：尝试直接解析（某些情况下可能直接返回 JSON）
        try {
            JSONObject json = JSONUtil.parseObj(response);
            return json.getStr("openid");
        } catch (Exception e) {
            log.warn("解析 QQ openid 响应失败: {}", response, e);
            return null;
        }
    }
}
