package com.example.usercenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类，同时配置了key-value和hash的序列化方式
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        // 创建RedisTemplate对象，泛型指定：key=String, value=Object(支持任意数据)
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        //2. 设置Redis连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        
        // 设置普通键的序列化方式：使用字符串序列化器实现Redis key清晰可读，无乱码
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // 设置普通值的序列化方式：使用JSON序列化器实现Redis value可将任意数据类型，如对象、集合、Map等自动转换为JSON存储
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        // 设置哈希键的序列化方式
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // 设置哈希值的序列化方式
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        //初始化RedisTemplate
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
