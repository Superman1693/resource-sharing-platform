package com.example.usercenter.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.usercenter.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器配置
 * 功能：
 *   1. 分页插件（物理分页）
 *   2. 多租户插件（行级数据隔离）
 */
@Configuration
public class MybatisPlusConfig {

    private final TenantInterceptor tenantInterceptor;

    public MybatisPlusConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（必须在分页插件之前）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantInterceptor));

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
