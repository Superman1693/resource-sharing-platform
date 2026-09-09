package com.example.usercenter.service;

import com.example.usercenter.model.domain.Note;
import java.util.List;

/**
 * 热榜服务接口
 */
public interface HotRankService {
    /**
     * 获取热榜列表
     * @param period day-今日 week-本周
     * @return 热榜笔记列表（最多20条，按热度降序）
     */
    List<Note> getHotRank(String period);

    /**
     * 更新笔记热度分
     * @param noteId 笔记ID
     * @param score 热度分
     */
    void updateHotScore(Long noteId, double score);
}
