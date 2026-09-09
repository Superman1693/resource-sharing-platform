package com.example.usercenter.interceptor;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
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

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
