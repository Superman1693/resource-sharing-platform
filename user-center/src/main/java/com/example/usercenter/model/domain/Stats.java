package com.example.usercenter.model.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 统计数据总实体（全量完善，包含用户活跃度明细）
 * @author zy
 */
@Data
public class Stats {
    /**
     * 概览统计项（顶部卡片：总用户、活跃用户、总笔记数等）
     */
    private List<OverviewItem> overview;

    /**
     * 数据明细（兼容：平台数据日报表 / 用户活跃度排行表）
     */
    private List<?> details;

    /**
     * 1. 概览项子实体（顶部统计卡片）
     */
    @Data
    public static class OverviewItem {
        private String title;   // 标题：累计用户、活跃用户、总笔记数
        private Object value;   // 数值：1250、856、12500
        private String unit;    // 单位：个、次、篇
        private String trend;   // 趋势：+12.5%、+0.0%
    }

    /**
     * ✅ 新增核心：用户活跃度明细子实体（1:1匹配前端表格字段）
     */
    @Data
    public static class UserActivityItem {
        private Long id;                // 主键ID
        private String userAccount;     // 用户账号
        private String username;        // 用户名
        private String avatar;          // 用户头像
        private Integer publishCount;   // 发布内容数
        private Integer commentCount;   // 评论数
        private Integer likeCount;      // 获赞数
        private Long viewCount;         // 浏览量
        private Integer contributionValue; // 贡献值
        private Integer activeDays;     // 活跃天数
        private Integer level;          // 用户等级
        private String lastActiveTime;  // 最后活跃时间
    }
}