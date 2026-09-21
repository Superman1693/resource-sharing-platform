package com.example.usercenter.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.mapper.StarMemberMapper;
import com.example.usercenter.model.domain.StarMember;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.JwtUtils;
import com.example.usercenter.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    /** 星球上下文请求头：前端进入某星球页面时携带，用于把查询范围收窄到该星球 */
    public static final String STAR_SCOPE_HEADER = "X-Star-Id";

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private StarMemberMapper starMemberMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) return true;

        boolean needLogin = method.hasMethodAnnotation(LoginRequired.class)
                || method.getBeanType().isAnnotationPresent(LoginRequired.class);
        boolean needAdmin = method.hasMethodAnnotation(AdminRequired.class)
                || method.getBeanType().isAnnotationPresent(AdminRequired.class);

        String token = extractToken(request);

        // 只要有 token 就尝试解析并设置 UserContext（无论接口是否需要认证）
        if (token != null && jwtUtils.validateToken(token)) {
            boolean blacklisted = Boolean.TRUE.equals(stringRedisTemplate.hasKey("blacklist:" + token));
            if (!blacklisted) {
                Claims claims = jwtUtils.parseToken(token);
                Long userId = claims.get("userId", Long.class);
                Integer userRole = claims.get("userRole", Integer.class);
                Long starId = claims.get("starId", Long.class);
                UserContext.set(new LoginUserDTO(userId, userRole, token, starId));
            }
        }

        // 解析可选的星球上下文（请求头 X-Star-Id），校验成员身份后写入 UserContext
        applyStarScope(request);

        // 需要登录但未设置 UserContext，拒绝访问
        if ((needLogin || needAdmin) && UserContext.get() == null) {
            writeError(response, ErrorCode.NOT_LOGIN);
            return false;
        }

        // 需要管理员但角色不符，拒绝访问
        if (needAdmin && !Integer.valueOf(1).equals(UserContext.get().getUserRole())) {
            writeError(response, ErrorCode.NO_AUTH);
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 解析并校验星球上下文。
     *
     * <p>前端在星球相关页面（星球详情、专栏、知识图谱等）请求时会带上
     * {@code X-Star-Id} 请求头；这里校验当前用户确实是该星球的成员后，
     * 才把它写入 {@link UserContext} 的星球作用域，
     * 从而触发 {@link TenantInterceptor} 对 note / comment / note_column / knowledge_map
     * 注入 {@code star_id} 条件。</p>
     *
     * <p>校验不通过时**只忽略、不报错**：避免客户端残留的星球上下文
     * （例如用户刚退出星球）导致整个页面 500。</p>
     */
    private void applyStarScope(HttpServletRequest request) {
        String raw = request.getHeader(STAR_SCOPE_HEADER);
        if (StringUtils.isBlank(raw)) {
            return;
        }
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser == null) {
            // 未登录不接受星球作用域，避免匿名请求被错误收窄
            return;
        }
        Long scopeStarId;
        try {
            scopeStarId = Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("非法的 {} 请求头: {}", STAR_SCOPE_HEADER, raw);
            return;
        }
        if (scopeStarId <= 0) {
            log.warn("非法的 {} 请求头（必须为正整数）: {}", STAR_SCOPE_HEADER, raw);
            return;
        }

        QueryWrapper<StarMember> wrapper = new QueryWrapper<>();
        wrapper.eq("star_id", scopeStarId).eq("user_id", loginUser.getUserId());
        Long count = starMemberMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            UserContext.setStarScope(scopeStarId);
        } else {
            log.debug("忽略无效的星球作用域: starId={}, userId={}", scopeStarId, loginUser.getUserId());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(200);
        String json = objectMapper.writeValueAsString(ResultUtils.error(errorCode));
        response.getWriter().write(json);
    }
}
