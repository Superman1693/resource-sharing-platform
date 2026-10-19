package com.example.usercenter.model.vo;

import lombok.Data;

/**
 * 积分额度视图（发布页 / 个人主页用）。
 *
 * <p>把「等级 → 售价上限」与「每日获取额度」的规则统一由后端下发，
 * 前端只做展示与预校验，避免两边硬编码阈值不一致。</p>
 *
 * @author zy
 */
@Data
public class PointsQuotaVO {

    /** 当前等级序号 1~5 */
    private int level;

    /** 当前等级名称 */
    private String levelName;

    /** 累计获得积分（定级依据） */
    private int totalEarned;

    /** 当前可用余额 */
    private int balance;

    /** 本等级可设置的内容售价上限（0 表示不可定价） */
    private int priceLimit;

    /** 下一等级序号（已满级为 null） */
    private Integer nextLevel;

    /** 下一等级名称 */
    private String nextLevelName;

    /** 下一等级所需累计积分 */
    private Integer nextLevelMinEarned;

    /** 每日可获得的积分上限 */
    private int dailyEarnLimit;

    /** 今日已获得的积分（仅计平台发放类型） */
    private int todayEarned;

    /** 今日剩余可获取额度 */
    private int todayRemain;
}
