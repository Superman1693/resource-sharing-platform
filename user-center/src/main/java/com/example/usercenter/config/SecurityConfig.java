package com.example.usercenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity // 开启Spring Security的web安全功能
public class SecurityConfig {

    @Bean
    // 注册全局密码加密器，实现自动加盐，不可逆，安全强度高
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 禁用 Spring Security 默认的登录重定向和 CSRF，
     * 认证逻辑完全由自定义 AuthInterceptor（JWT）处理。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（前后端分离 + JWT 不需要）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置 CORS（允许前端 5173 跨域）
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 关闭 Session（使用 JWT 无状态）
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 放行所有请求，认证由 AuthInterceptor 处理
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // 禁用默认的表单登录和 HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 创建跨域配置对象
        CorsConfiguration config = new CorsConfiguration();
        // 允许的请求源
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174", "https://www.e-ren.icu"));
        // 允许的请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 允许的请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许携带Cookie凭证
        config.setAllowCredentials(true);
        // 预检请求有效期
        config.setMaxAge(3600L);

        // 注册跨域配置：所有接口/** 都允许跨域
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
