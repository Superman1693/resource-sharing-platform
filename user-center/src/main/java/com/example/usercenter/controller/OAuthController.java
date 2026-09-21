package com.example.usercenter.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.contant.UserConstant;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.service.UserService;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.GitHubLoginRequest;
import com.example.usercenter.model.domain.request.QQLoginRequest;
import com.example.usercenter.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
    private final StarMemberMapper starMemberMapper;
    private final UserService userService;
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

    private final Environment environment;

    /**
     * 启动时校验第三方登录配置。
     *
     * <p><b>为什么需要：</b>OAuth 最典型的故障是「回调地址与开放平台登记的不一致」，
     * 而这类错误只有等用户点击登录、被重定向之后才会暴露（症状是浏览器报
     * 「localhost 拒绝连接」或跳回登录页）。把校验提前到启动阶段，可以更早发现问题。</p>
     *
     * <p>校验规则：</p>
     * <ol>
     *   <li>打印本次生效的 profile 与两个回调地址，便于与开放平台逐字比对；</li>
     *   <li>client-id/secret 为空 → 告警（对应渠道登录将不可用）；</li>
     *   <li><b>prod 环境回调地址仍指向 localhost → 报错</b>（线上必然失败）；</li>
     *   <li>dev 环境则提示需保持前端 5173 端口与开放平台登记一致。</li>
     * </ol>
     */
    @PostConstruct
    public void validateOAuthConfig() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        boolean prod = activeProfiles.contains("prod");

        log.info("[OAuth] 配置检查 —— profile={}, GitHub 回调地址={}, QQ 回调地址={}",
                activeProfiles.isEmpty() ? "default" : String.join(",", activeProfiles),
                redirectUri, qqRedirectUri);

        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            log.warn("[OAuth] GitHub 的 client-id / client-secret 未配置，GitHub 登录将不可用");
        }
        if (StringUtils.isBlank(qqAppId) || StringUtils.isBlank(qqAppKey)) {
            log.warn("[OAuth] QQ 的 app-id / app-key 未配置，QQ 登录将不可用");
        }

        if (prod && (isLocalhost(redirectUri) || isLocalhost(qqRedirectUri))) {
            log.error("[OAuth] 生产环境（prod）的回调地址仍指向 localhost，第三方登录必定失败！"
                            + "GitHub={}, QQ={}。请在 application-prod.yml 中改为真实域名，"
                            + "并确保与开放平台登记的回调地址完全一致。",
                    redirectUri, qqRedirectUri);
        } else if (!prod && isLocalhost(redirectUri)) {
            log.info("[OAuth] 本地开发回调地址为 {}；请确认 GitHub OAuth App 的 "
                            + "Authorization callback URL 与此完全一致，且前端 dev server 使用 "
                            + "vite.config.js 中固定的 5173 端口（strictPort 已开启，端口被占用会直接报错而非静默改端口）",
                    redirectUri);
        }
    }

    /**
     * 判断回调地址是否指向本机
     */
    private boolean isLocalhost(String uri) {
        if (StringUtils.isBlank(uri)) {
            return false;
        }
        String lower = uri.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }

    /**
     * 获取 GitHub OAuth 授权 URL
     */
    @GetMapping("/github/url")
    @Operation(summary = "获取 GitHub 授权地址")
    public BaseResponse<Map<String, String>> getGithubAuthUrl() {
        // redirect_uri 必须与 GitHub OAuth App 里登记的 Authorization callback URL 完全一致；
        // 这里做 URL 编码，避免 :// 等字符干扰 query 参数解析（与下方 QQ 的处理保持一致）。
        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String url = "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + encodedRedirectUri
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
            user.setUserPassword(UserConstant.OAUTH_PASSWORD_PLACEHOLDER); // 第三方登录账号无可用密码
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
        // 多租户：带上用户的「当前星球」ID（未加入任何星球时为 null），
        // 与密码登录保持同一套取值逻辑，避免两条登录路径签出的 starId 不一致
        Long starId = userService.resolveCurrentStarId(user.getId());
        String token = jwtUtils.generateToken(user.getId(), user.getUserRole(), starId, expirationSeconds);

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
            user.setUserPassword(UserConstant.OAUTH_PASSWORD_PLACEHOLDER); // 第三方登录账号无可用密码
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
        // 多租户：带上用户的「当前星球」ID（未加入任何星球时为 null），
        // 与密码登录保持同一套取值逻辑，避免两条登录路径签出的 starId 不一致
        Long starId = userService.resolveCurrentStarId(user.getId());
        String jwtToken = jwtUtils.generateToken(user.getId(), user.getUserRole(), starId, expirationSeconds);

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
