package com.example.usercenter.service;

import com.example.usercenter.model.domain.Note;

import java.util.List;

/**
 * 个性化推荐服务接口（标签偏好 + 热门兜底）
 * @author zy
 */
public interface RecommendService {

    /**
     * 个性化推荐笔记
     * <p>有 userId 且有行为记录：按用户历史浏览/点赞笔记的标签偏好推荐同标签笔记，排除已浏览/已点赞。
     * <p>新用户/无行为/结果不足/异常：兜底返回热门笔记（like_count desc, view_count desc）。
     * @param userId 当前用户ID，可空（匿名走热门兜底）
     * @param size 返回数量
     */
    List<Note> recommend(Long userId, int size);
}
