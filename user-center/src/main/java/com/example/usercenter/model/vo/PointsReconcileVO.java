package com.example.usercenter.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 积分对账视图（管理端）。
 *
 * <p>平台抽成不落任何账户，统一由三张成交表反推（成交额 − 收款方实得），
 * 因此「抽成」不需要额外的账目表即可对账。</p>
 *
 * @author zy
 */
@Data
public class PointsReconcileVO {

    /** 总览：totalBalance / totalIssued / totalSpent / platformFee / accountCount / orderCount / todayIssued */
    private Map<String, Object> overview;

    /** 抽成分渠道：[{channel, fee, orders}] */
    private List<Map<String, Object>> feeByChannel;

    /** 近 N 天：[{date, earned, spent, fee, orders}] */
    private List<Map<String, Object>> daily;

    /** 收入榜：[{userId, username, amount}] */
    private List<Map<String, Object>> topEarners;

    /** 消费榜：[{userId, username, amount}] */
    private List<Map<String, Object>> topSpenders;
}
