package com.example.usercenter.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类
 * 使用单机模式连接 Redis，复用现有的 Redis 配置
 */
@Configuration
public class RedissonConfig {
    //读取配置文件中Redis信息

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;



    @Bean
    //创建Redisson客户端核心
    public RedissonClient redissonClient() {
        //告诉Redisson连接那台redis
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        //连接的是redis单机模式，只有一台redis，所有缓存、分布式锁都存在这一台redis上
        config.useSingleServer().setAddress(address).setDatabase(database);

        // 如果有密码则设置
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }

        // 连接池配置（节省内存）
        config.useSingleServer()
                .setConnectionMinimumIdleSize(2) // 最小空闲连接
                .setConnectionPoolSize(5)  // 最大连接数
                .setIdleConnectionTimeout(10000) // 空闲超时
                .setConnectTimeout(3000) // 连接超时
                .setTimeout(3000);   // 等待响应超时

        return Redisson.create(config);
    }
}
