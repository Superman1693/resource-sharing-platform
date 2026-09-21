package com.example.usercenter.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring Security 配置
 *
 * @author zy
 */
@Configuration
@EnableWebSecurity // 开启Spring Security的web安全功能
@Slf4j
public class SecurityConfig {

    /**
     * 允许跨域的前端来源（逗号分隔），配置项为 {@code center.cors.allowed-origins}。
     *
     * <h3>为什么必须显式登记——一个真实踩过的坑</h3>
     * <p>前端生产环境与 API 其实是<b>同源</b>的（页面 {@code https://e-ren.icu}、接口 {@code https://e-ren.icu/api}），
     * 直觉上"同源不需要 CORS"。但浏览器对<b>非简单请求</b>（例如 {@code POST + Content-Type: application/json}）
     * <b>即使同源也会携带 {@code Origin} 请求头</b>；而 Spring 的 CORS 处理器只要看到 {@code Origin}
     * 存在就会去核对白名单，未登记就直接返回 {@code 403 Invalid CORS request}。</p>
     *
     * <p>此前该列表被硬编码为 {@code localhost:5173/5174 + https://www.e-ren.icu}，
     * 而实际访问的是<b>顶级域</b> {@code https://e-ren.icu}——顶级域与 {@code www} 是两个不同的来源，
     * 于是线上登录/注册全部 403，本地却正常（本地走 Vite 代理，Origin 是 {@code localhost:5173}，恰好在白名单里）。</p>
     *
     * <h3>维护须知</h3>
     * <ul>
     *   <li>顶级域与 www <b>必须分别列出</b>；</li>
     *   <li>端口不同（如 5173 / 5174）也属于不同来源；</li>
     *   <li>换了新域名只需改配置、无需改代码；若忘改，症状就是 403 + 响应体 {@code Invalid CORS request}。</li>
     * </ul>
     */
    @Value("${center.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
    private String allowedOriginsConfig;

    /**
     * 注册全局密码加密器，实现自动加盐，不可逆，安全强度高
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 启动时打印生效的跨域白名单。
     *
     * <p>CORS 配错的症状（403 + {@code Invalid CORS request}）只会在浏览器里出现，
     * 服务端日志一片安静。把白名单提前打进启动日志，便于对照排查。</p>
     */
    @PostConstruct
    public void logAllowedOrigins() {
        log.info("[CORS] 允许的前端来源：{}。注意：顶级域与 www 需分别列出，"
                + "否则对应域名下的请求会返回 403 Invalid CORS request",
                resolveAllowedOrigins());
    }

    /**
     * 解析配置为去重后的来源列表，并做基本格式校验
     */
    private List<String> resolveAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        for (String raw : Arrays.asList(allowedOriginsConfig.split(","))) {
            String origin = raw.trim();
            if (origin.isEmpty()) {
                continue;
            }
            // 常见误配：末尾多写 "/"（如 https://e-ren.icu/）。Origin 头不带路径，
            // 带斜杠会导致永远匹配不上，这里自动纠正并提示。
            if (origin.endsWith("/")) {
                String fixed = origin.substring(0, origin.length() - 1);
                log.warn("[CORS] 来源配置 '{}' 末尾多余的 '/' 已自动去除，请修正为 '{}'", origin, fixed);
                origin = fixed;
            }
            origins.add(origin);
        }
        return new ArrayList<>(origins);
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
                // 配置 CORS（来源白名单见 center.cors.allowed-origins）
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
        // 允许的请求源（从配置读取，见类注释）
        config.setAllowedOrigins(resolveAllowedOrigins());
        // 允许的请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
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
