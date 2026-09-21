package com.example.usercenter.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 热搜词衰减任务
 *
 * <p><b>解决什么问题：</b>热搜词云原先是「只增不减」的 ZSET
 * （每次搜索 {@code ZINCRBY search:hot 1 keyword}），
 * 运行一段时间后历史高频词会长期占榜，无法反映「当前」热点。</p>
 *
 * <p><b>做法：</b>每天凌晨低峰期对所有词频乘以衰减因子 0.9，
 * 并剔除衰减后低于阈值的词。由于 Redis 没有「整体乘以系数」的原语，
 * 这里采用「读 → 在临时 key 上重建 → RENAME 原子替换」的方式实现。</p>
 *
 * <p><b>一致性说明：</b>重建期间（毫秒级）若恰好有新的 {@code ZINCRBY} 写入原 key，
 * 该次自增会被覆盖。任务放在凌晨 4 点执行，影响可忽略；
 * 若要彻底消除，可改为「按日分桶 + 读取时加权合并」的方案。</p>
 *
 * @author zy
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchHotDecayTask {

    private final StringRedisTemplate redisTemplate;

    /** 热搜词 ZSET 的 key（与 EsSearchServiceImpl / SearchController 保持一致） */
    private static final String HOT_KEY = "search:hot";

    /** 重建用的临时 key */
    private static final String TEMP_KEY = HOT_KEY + ":decay-tmp";

    /** 衰减因子：每天保留 90% 权重，约 7 天后老词权重降到一半 */
    private static final double DECAY_FACTOR = 0.9;

    /** 低于该分值的词直接淘汰，避免长尾词永久驻留 */
    private static final double MIN_SCORE = 0.5;

    /**
     * 每天 04:00 执行衰减
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void decayHotSearchWords() {
        try {
            ZSetOperations<String, String> ops = redisTemplate.opsForZSet();
            Set<ZSetOperations.TypedTuple<String>> tuples = ops.rangeWithScores(HOT_KEY, 0, -1);
            if (tuples == null || tuples.isEmpty()) {
                return;
            }

            redisTemplate.delete(TEMP_KEY);

            int kept = 0;
            int dropped = 0;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String keyword = tuple.getValue();
                Double score = tuple.getScore();
                if (keyword == null || score == null) {
                    continue;
                }
                double decayed = score * DECAY_FACTOR;
                if (decayed < MIN_SCORE) {
                    dropped++;
                    continue;
                }
                ops.add(TEMP_KEY, keyword, decayed);
                kept++;
            }

            if (kept == 0) {
                // 全部低于阈值：直接清空热搜
                redisTemplate.delete(HOT_KEY);
                log.info("[热搜衰减] 全部词条低于阈值，已清空 {}（淘汰 {} 条）", HOT_KEY, dropped);
                return;
            }

            // 原子替换，避免读取过程中出现「半个榜单」的中间态
            redisTemplate.rename(TEMP_KEY, HOT_KEY);
            log.info("[热搜衰减] 完成：保留 {} 条，淘汰 {} 条，衰减因子 {}", kept, dropped, DECAY_FACTOR);
        } catch (Exception e) {
            // 衰减失败不影响搜索主流程，仅告警
            log.warn("[热搜衰减] 执行失败：{}", e.getMessage(), e);
            redisTemplate.delete(TEMP_KEY);
        }
    }
}
