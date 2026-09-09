package com.example.usercenter.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具类
 * 封装 Redisson 分布式锁的常用操作，简化使用
 */
@Slf4j
@Component
public class DistributedLockUtils {

    private final RedissonClient redissonClient;

    public DistributedLockUtils(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 尝试获取锁并执行业务逻辑（推荐使用）
     *
     * @param lockKey    锁的 key
     * @param waitTime   等待获取锁的最大时间（秒）
     * @param leaseTime  锁的自动过期时间（秒）
     * @param business   业务逻辑
     * @param failAction 获取锁失败时的处理
     * @return 业务逻辑的返回值
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime,
                                  Supplier<T> business, Supplier<T> failAction) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                try {
                    log.debug("获取分布式锁成功: {}", lockKey);
                    return business.get();
                } finally {
                    // 只释放自己持有的锁，不会删除别人的锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("释放分布式锁成功: {}", lockKey);
                    }
                }
            } else {
                log.warn("获取分布式锁失败: {}", lockKey);
                return failAction.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断: {}", lockKey, e);
            return failAction.get();
        }
    }

    /**
     * 尝试获取锁并执行业务逻辑（无返回值）
     */
    public void executeWithLock(String lockKey, long waitTime, long leaseTime,
                                 Runnable business, Runnable failAction) {
        executeWithLock(lockKey, waitTime, leaseTime,
                () -> { business.run(); return null; },
                () -> { failAction.run(); return null; });
    }

    /**
     * 尝试获取锁并执行业务逻辑（默认参数：等待3秒，锁过期10秒）
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> business, Supplier<T> failAction) {
        return executeWithLock(lockKey, 3L, 10L, business, failAction);
    }

    /**
     * 尝试获取锁并执行业务逻辑（默认参数，无返回值）
     */
    public void executeWithLock(String lockKey, Runnable business, Runnable failAction) {
        executeWithLock(lockKey, 3L, 10L, business, failAction);
    }

    /**
     * 强制获取锁（阻塞等待直到获取成功）
     * 谨慎使用，可能导致长时间阻塞
     */
    public void executeWithLockBlocking(String lockKey, long leaseTime, Runnable business) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock(leaseTime, TimeUnit.SECONDS);
        try {
            business.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 检查锁是否被持有
     */
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }
}
