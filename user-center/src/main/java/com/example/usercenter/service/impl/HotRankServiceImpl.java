package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.HotRankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class HotRankServiceImpl implements HotRankService {

    private static final String HOT_RANK_KEY_PREFIX = "note:hot:";
    private static final int HOT_RANK_SIZE = 20;
    private static final long HOT_RANK_DAY_TTL_MINUTES = 10;
    private static final long HOT_RANK_WEEK_TTL_MINUTES = 70;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private NoteMapper noteMapper;

    @Override
    public List<Note> getHotRank(String period) {
        String key = HOT_RANK_KEY_PREFIX + (period == null ? "day" : period);

        try {
            // 从 Redis Sorted Set 获取热榜（按分数降序）
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, HOT_RANK_SIZE - 1);

            if (tuples != null && !tuples.isEmpty()) {
                List<Note> result = new ArrayList<>();
                for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                    Object value = tuple.getValue();
                    if (value instanceof Long) {
                        Note note = noteMapper.selectById((Long) value);
                        if (note != null && "published".equals(note.getStatus())) {
                            result.add(note);
                        }
                    } else if (value instanceof Integer) {
                        Note note = noteMapper.selectById(((Integer) value).longValue());
                        if (note != null && "published".equals(note.getStatus())) {
                            result.add(note);
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("从 Redis 获取热榜失败，降级到数据库查询: {}", e.getMessage());
        }

        // 降级：从数据库查询热榜
        return queryHotRankFromDb();
    }

    @Override
    public void updateHotScore(Long noteId, double score) {
        try {
            String dayKey = HOT_RANK_KEY_PREFIX + "day";
            String weekKey = HOT_RANK_KEY_PREFIX + "week";
            redisTemplate.opsForZSet().add(dayKey, noteId, score);
            redisTemplate.expire(dayKey, HOT_RANK_DAY_TTL_MINUTES, TimeUnit.MINUTES);
            redisTemplate.opsForZSet().add(weekKey, noteId, score);
            redisTemplate.expire(weekKey, HOT_RANK_WEEK_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("更新热度分失败, noteId={}: {}", noteId, e.getMessage());
        }
    }

    /**
     * 从数据库查询热榜（降级方案）
     */
    private List<Note> queryHotRankFromDb() {
        QueryWrapper<Note> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "published")
                    .eq("is_delete", 0)
                    .orderByDesc("view_count + like_count * 3 + comment_count * 2")
                    .last("LIMIT " + HOT_RANK_SIZE);
        return noteMapper.selectList(queryWrapper);
    }
}
