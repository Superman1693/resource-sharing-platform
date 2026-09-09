package com.example.usercenter.task;

import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.service.HotRankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * 定时任务：将 Redis 中缓冲的浏览量增量批量刷新到数据库
 */
@Component
@Slf4j
public class FlushViewCountTask {

    private static final String VIEW_KEY_PREFIX = "note:view:";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private HotRankService hotRankService;

    /**
     * 每 60 秒执行一次，将 Redis 中的浏览量增量批量写入数据库
     * 使用 SCAN 替代 KEYS，避免大数据量时阻塞 Redis
     */
    @Scheduled(fixedDelay = 60000)
    public void flushViewCount() {
        org.springframework.data.redis.core.ScanOptions options =
                org.springframework.data.redis.core.ScanOptions.scanOptions()
                        .match(VIEW_KEY_PREFIX + "*")
                        .count(100)
                        .build();

        try (org.springframework.data.redis.core.Cursor<String> cursor =
                     redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    Object value = redisTemplate.opsForValue().getAndDelete(key);
                    if (value == null) continue;

                    long delta = Long.parseLong(value.toString());
                    if (delta <= 0) continue;

                    Long noteId = Long.parseLong(key.substring(VIEW_KEY_PREFIX.length()));
                    noteMapper.incrementViewCountByDelta(noteId, delta);

                    try {
                        hotRankService.updateHotScore(noteId, delta);
                    } catch (Exception e) {
                        log.warn("热度分更新失败, noteId={}: {}", noteId, e.getMessage());
                    }
                } catch (Exception e) {
                    log.error("刷新浏览量失败, key={}: {}", key, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("SCAN 浏览量 key 失败: {}", e.getMessage(), e);
        }
    }
}
