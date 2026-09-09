package com.example.usercenter.config;

import com.example.usercenter.interceptor.AuthInterceptor;
import com.example.usercenter.interceptor.RateLimitInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *
 * 注册自定义的权限拦截器AuthInterceptor，配置拦截规则
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/user/login",
                    "/user/register",
                    "/error"
                );

        // 限流拦截器（全局注册，仅对标注 @RateLimit 的接口生效）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**");
    }
}
