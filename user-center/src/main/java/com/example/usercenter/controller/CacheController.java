package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.cache.caffeine.CaffeineCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存监控接口（仅管理员可用）
 */
@RestController
@RequestMapping("/cache")
@Slf4j
@AdminRequired
public class CacheController {

    private final CacheManager cacheManager;
    private final CaffeineCacheManager caffeineCacheManager;

    public CacheController(CacheManager cacheManager, CaffeineCacheManager caffeineCacheManager) {
        this.cacheManager = cacheManager;
        this.caffeineCacheManager = caffeineCacheManager;
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public BaseResponse<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        // 获取 Caffeine 缓存统计
        try {
            org.springframework.cache.Cache springCache = caffeineCacheManager.getCache("noteDetail");
            if (springCache != null && springCache instanceof CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) springCache;
                Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
                CacheStats cacheStats = nativeCache.stats();
                Map<String, Object> caffeineStats = new HashMap<>();
                caffeineStats.put("hitCount", cacheStats.hitCount());
                caffeineStats.put("missCount", cacheStats.missCount());
                caffeineStats.put("hitRate", String.format("%.2f%%", cacheStats.hitRate() * 100));
                caffeineStats.put("evictionCount", cacheStats.evictionCount());
                caffeineStats.put("loadCount", cacheStats.loadCount());
                caffeineStats.put("averageLoadPenalty", String.format("%.2f ms", cacheStats.averageLoadPenalty() / 1_000_000.0));
                caffeineStats.put("size", nativeCache.estimatedSize());
                stats.put("caffeine", caffeineStats);
            }
        } catch (Exception e) {
            stats.put("caffeine", "未启用或无数据");
        }

        // 缓存管理器信息
        Map<String, Object> managerInfo = new HashMap<>();
        managerInfo.put("type", cacheManager.getClass().getSimpleName());
        managerInfo.put("cacheNames", cacheManager.getCacheNames());
        stats.put("cacheManager", managerInfo);

        return ResultUtils.success(stats);
    }

    /**
     * 清除指定缓存
     */
    @PostMapping("/evict/{cacheName}")
    public BaseResponse<String> evictCache(@PathVariable String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("手动清除缓存: {}", cacheName);
            return ResultUtils.success("缓存 " + cacheName + " 已清除");
        }
        return ResultUtils.success("缓存 " + cacheName + " 不存在");
    }

    /**
     * 清除所有缓存
     */
    @PostMapping("/evictAll")
    public BaseResponse<String> evictAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            org.springframework.cache.Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        log.info("手动清除所有缓存");
        return ResultUtils.success("所有缓存已清除");
    }
}
