package com.example.usercenter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 积分对账统计 Mapper（只读）。
 *
 * <p>平台抽成不落任何账户（避免多出一个要特殊排除的系统账户），因此抽成金额统一由
 * 三张成交表反推：{@code 成交额 - 收款方实得}。</p>
 *
 * @author zy
 */
@Mapper
public interface PointsReconcileMapper {

    /** 全平台未消费存量 / 累计发放（total_earned 之和） / 账户数 */
    @Select("SELECT COALESCE(SUM(balance), 0) AS totalBalance, "
            + "COALESCE(SUM(total_earned), 0) AS totalEarned, "
            + "COUNT(*) AS accountCount FROM points_account")
    Map<String, Object> selectAccountSummary();

    /**
     * 平台抽成合计（分渠道）。
     *
     * <p>三条子查询各自返回「渠道 + 抽成额」，UNION ALL 后在 Java 侧按渠道聚合。</p>
     */
    @Select("SELECT 'note_unlock' AS channel, COALESCE(SUM(price - author_gain), 0) AS fee, COUNT(*) AS orders "
            + "  FROM note_purchase "
            + "UNION ALL "
            + "SELECT 'note_tip', COALESCE(SUM(amount - author_gain), 0), COUNT(*) FROM note_tip "
            + "UNION ALL "
            + "SELECT 'resource_download', COALESCE(SUM(price - uploader_gain), 0), COUNT(*) FROM resource_download")
    List<Map<String, Object>> selectPlatformFeeByChannel();

    /**
     * 平台抽成按日趋势（近 N 天）。
     */
    @Select("SELECT DATE_FORMAT(d, '%Y-%m-%d') AS date, SUM(fee) AS fee, SUM(orders) AS orders FROM ( "
            + "  SELECT DATE(create_time) AS d, price - author_gain AS fee, 1 AS orders "
            + "    FROM note_purchase WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "  UNION ALL "
            + "  SELECT DATE(create_time), amount - author_gain, 1 "
            + "    FROM note_tip WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "  UNION ALL "
            + "  SELECT DATE(create_time), price - uploader_gain, 1 "
            + "    FROM resource_download WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + ") t GROUP BY d ORDER BY d")
    List<Map<String, Object>> selectPlatformFeeDaily(@Param("days") int days);

    /**
     * 每日获取 / 消耗趋势（来自 points_log）。
     *
     * <p>⚠️ SELECT 与 GROUP BY 必须使用<b>完全相同的表达式</b>：MySQL 8 默认开启
     * {@code only_full_group_by}，若 SELECT 写 {@code DATE_FORMAT(create_time,'%Y-%m-%d')}
     * 而 GROUP BY 写 {@code DATE(create_time)}，会被判定为「非聚合列未参与分组」直接报错。</p>
     *
     * @param earnTypes  计入「发放」的 type（平台发放，不含用户间转移）
     * @param spendTypes 计入「消耗」的 type
     */
    @Select("<script>"
            + "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS date, "
            + "  COALESCE(SUM(CASE WHEN `change` &gt; 0 AND `type` IN "
            + "    <foreach collection='earnTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "  THEN `change` ELSE 0 END), 0) AS earned, "
            + "  COALESCE(SUM(CASE WHEN `change` &lt; 0 AND `type` IN "
            + "    <foreach collection='spendTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "  THEN -`change` ELSE 0 END), 0) AS spent "
            + "FROM points_log WHERE create_time &gt;= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY date"
            + "</script>")
    List<Map<String, Object>> selectEarnSpendDaily(@Param("days") int days,
                                                   @Param("earnTypes") List<String> earnTypes,
                                                   @Param("spendTypes") List<String> spendTypes);

    /** 累计消耗（按消耗类型白名单，防止把退款等正数算进来） */
    @Select("<script>"
            + "SELECT COALESCE(SUM(-`change`), 0) AS totalSpent FROM points_log "
            + "WHERE `change` &lt; 0 AND `type` IN "
            + "<foreach collection='spendTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</script>")
    Map<String, Object> selectTotalSpent(@Param("spendTypes") List<String> spendTypes);

    /** 累计发放（仅平台发放类型，不含用户间转移） */
    @Select("<script>"
            + "SELECT COALESCE(SUM(`change`), 0) AS totalIssued FROM points_log "
            + "WHERE `change` &gt; 0 AND `type` IN "
            + "<foreach collection='earnTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</script>")
    Map<String, Object> selectTotalIssued(@Param("earnTypes") List<String> earnTypes);

    /** 收入榜：靠内容赚到积分的用户（解锁/打赏/资源收入） */
    @Select("SELECT user_id AS userId, SUM(`change`) AS amount FROM points_log "
            + "WHERE `change` > 0 AND `type` IN ('unlock_income', 'tip_income', 'resource_income') "
            + "GROUP BY user_id ORDER BY amount DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopEarners(@Param("limit") int limit);

    /** 消费榜：花掉积分最多的用户 */
    @Select("<script>"
            + "SELECT user_id AS userId, SUM(-`change`) AS amount FROM points_log "
            + "WHERE `change` &lt; 0 AND `type` IN "
            + "<foreach collection='spendTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "GROUP BY user_id ORDER BY amount DESC LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectTopSpenders(@Param("limit") int limit, @Param("spendTypes") List<String> spendTypes);

    /** 今日已发放额度汇总（当日平台发放类型的正数流水之和） */
    @Select("<script>"
            + "SELECT COALESCE(SUM(`change`), 0) AS todayIssued FROM points_log "
            + "WHERE `change` &gt; 0 AND create_time &gt;= CURDATE() AND `type` IN "
            + "<foreach collection='earnTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</script>")
    Map<String, Object> selectTodayIssued(@Param("earnTypes") List<String> earnTypes);
}
