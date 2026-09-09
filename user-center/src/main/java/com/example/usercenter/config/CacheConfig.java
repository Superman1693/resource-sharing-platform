package com.example.usercenter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存配置
 * L1: Caffeine 本地缓存（JVM 内存，微秒级访问）
 * L2: Redis 分布式缓存（网络访问，毫秒级访问）
 *
 * 读取顺序: L1 → L2 → DB
 * 写入/驱逐: 同时清除 L1 和 L2
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * L1: Caffeine 本地缓存管理器
     * 特点：JVM 内存，访问速度极快（微秒级），但多实例间不共享
     *
     * 热点数据策略：
     *   - 热榜（hotRank）: L1 TTL 30s，高频变化
     *   - 笔记详情（noteDetail）: L1 TTL 1min，命中率优先
     *   - 笔记列表（noteList）: L1 TTL 2min，平衡策略
     *   - 用户信息（userInfo）: L1 TTL 5min，相对稳定
     */
    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 使用 Caffeine.newBuilder() 自定义缓存配置
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 初始容量
                .initialCapacity(200)
                // 最大缓存条数（防止 OOM），热点数据场景适当放大
                .maximumSize(5000)
                // 写入后过期时间（默认 5 分钟，具体由各缓存名覆盖）
                .expireAfterWrite(5, TimeUnit.MINUTES)
                // 访问后过期时间（热点数据延长过期）
                .expireAfterAccess(2, TimeUnit.MINUTES)
                // 开启统计（用于监控命中率）
                .recordStats());

        // 不缓存 null 值
        cacheManager.setAllowNullValues(false);

        log.info("Caffeine 本地缓存管理器初始化完成: maxSize=5000, initialCapacity=200");
        return cacheManager;
    }

    /**
     * L2: Redis 分布式缓存管理器
     * 特点：网络访问，毫秒级，多实例共享，数据持久化
     */
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 序列化配置
        RedisSerializationContext.SerializationPair<String> keySerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());
        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

        // 默认缓存配置（全局）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 默认 TTL: 10 分钟
                .entryTtl(Duration.ofMinutes(10))
                // Key 序列化
                .serializeKeysWith(keySerializer)
                // Value 序列化
                .serializeValuesWith(valueSerializer)
                // 不缓存 null 值
                .disableCachingNullValues()
                // Key 前缀（便于管理和区分）
                .computePrefixWith(cacheName -> "cache:" + cacheName + ":");

        // 为不同缓存名称设置独立的 TTL
        java.util.Map<String, RedisCacheConfiguration> configMap = new java.util.HashMap<>();

        // 笔记列表: L2 TTL = 5 分钟（变化较频繁）
        configMap.put("noteList", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 笔记详情: L2 TTL = 10 分钟（读多写少）
        configMap.put("noteDetail", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 热榜: L2 TTL = 2 分钟（实时性要求高）
        configMap.put("hotRank", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        // 用户信息: L2 TTL = 30 分钟（相对稳定）
        configMap.put("userInfo", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 星球列表: L2 TTL = 15 分钟
        configMap.put("starList", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configMap)
                // 启动时不删除已有缓存
                .transactionAware()
                .build();

        log.info("Redis 缓存管理器初始化完成");
        return cacheManager;
    }

    /**
     * 主缓存管理器: CompositeCacheManager（多级缓存）
     * 查询顺序: Caffeine (L1) → Redis (L2) → DB
     * 驱逐顺序: 同时清除 L1 和 L2
     */
    @Bean
    @Primary
    public CacheManager compositeCacheManager(
            CaffeineCacheManager caffeineCacheManager,
            RedisCacheManager redisCacheManager) {

        CompositeCacheManager cacheManager = new CompositeCacheManager();
        // 设置缓存管理器列表（按优先级排序：先 L1，后 L2）
        cacheManager.setCacheManagers(java.util.List.of(caffeineCacheManager, redisCacheManager));
        // 不允许在没有缓存管理器时降级到 NoOpCacheManager
        cacheManager.setFallbackToNoOpCache(false);

        log.info("多级缓存管理器初始化完成: L1=Caffeine, L2=Redis");
        return cacheManager;
    }
}
