package com.example.usercenter.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 布隆过滤器配置
 * 防止缓存穿透：查询不存在的 ID 时直接拦截，不穿透到数据库
 */
@Slf4j
@Configuration
public class BloomFilterConfig {

    /** 笔记布隆过滤器名称 */
    public static final String NOTE_BLOOM_FILTER = "noteBloomFilter";

    /** 预计笔记数量 */
    private static final long EXPECTED_INSERTIONS = 100_000L;

    /** 误判率 */
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final RedissonClient redissonClient;
    private final NoteMapper noteMapper;

    public BloomFilterConfig(RedissonClient redissonClient, NoteMapper noteMapper) {
        this.redissonClient = redissonClient;
        this.noteMapper = noteMapper;
    }

    /**
     * 笔记布隆过滤器 Bean
     */
    @Bean
    public RBloomFilter<Long> noteBloomFilter() {
        RBloomFilter<Long> filter = redissonClient.getBloomFilter(NOTE_BLOOM_FILTER);
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
        log.info("笔记布隆过滤器初始化完成: expectedInsertions={}, falsePositiveRate={}",
                EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);
        return filter;
    }

    /**
     * 应用启动时加载所有已发布笔记 ID 到布隆过滤器
     */
    @PostConstruct
    public void loadNoteIds() {
        try {
            RBloomFilter<Long> filter = noteBloomFilter();
            // 如果过滤器已有数据则跳过（避免重复加载）
            if (filter.count() > 0) {
                log.info("布隆过滤器已包含 {} 条记录，跳过加载", filter.count());
                return;
            }

            QueryWrapper<Note> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("id")
                    .eq("status", "published")
                    .eq("is_delete", 0);
            List<Note> notes = noteMapper.selectList(queryWrapper);

            List<Long> ids = notes.stream()
                    .map(Note::getId)
                    .toList();

            if (!ids.isEmpty()) {
                filter.add(ids);
                log.info("布隆过滤器加载完成: 共加载 {} 条笔记 ID", ids.size());
            }
        } catch (Exception e) {
            log.warn("布隆过滤器加载笔记 ID 失败（Redis 可能未启动）: {}", e.getMessage());
        }
    }
}
