package com.example.usercenter.service.impl;

import com.example.usercenter.mapper.PointsAccountMapper;
import com.example.usercenter.mapper.PointsReconcileMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.enums.PointsLevel;
import com.example.usercenter.model.vo.PointsQuotaVO;
import com.example.usercenter.model.vo.PointsReconcileVO;
import com.example.usercenter.service.PointsReconcileService;
import com.example.usercenter.service.PointsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 积分对账服务实现（只读）。
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li><b>发放</b>（{@code totalIssued}）只算平台凭空发放的类型（签到/发布/被赞/评论），
 *       不含用户间转移；否则「解锁收入」会被重复计入发放，账目虚高。</li>
 *   <li><b>消耗</b>（{@code totalSpent}）按消耗类型白名单统计，避免把「重复下载退回」等
 *       正数流水算进去。</li>
 *   <li><b>平台抽成</b>由成交表反推（成交额 − 收款方实得），因为抽成刻意不落任何账户。</li>
 * </ul>
 *
 * @author zy
 */
@Service
@Slf4j
public class PointsReconcileServiceImpl implements PointsReconcileService {

    @Resource
    private PointsReconcileMapper reconcileMapper;
    @Resource
    private PointsAccountMapper pointsAccountMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PointsService pointsService;

    @Override
    public PointsReconcileVO getReconcile(int days) {
        int d = Math.max(1, Math.min(days, 90));
        PointsReconcileVO vo = new PointsReconcileVO();

        // ---------- 总览 ----------
        Map<String, Object> acc = reconcileMapper.selectAccountSummary();
        Map<String, Object> issued = reconcileMapper.selectTotalIssued(PointsService.EARN_TYPES);
        Map<String, Object> spent = reconcileMapper.selectTotalSpent(PointsService.SPEND_TYPES);
        Map<String, Object> todayIssued = reconcileMapper.selectTodayIssued(PointsService.EARN_TYPES);

        List<Map<String, Object>> feeRows = reconcileMapper.selectPlatformFeeByChannel();
        int totalFee = 0;
        int totalOrders = 0;
        for (Map<String, Object> r : feeRows) {
            totalFee += toInt(r.get("fee"));
            totalOrders += toInt(r.get("orders"));
        }

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("accountCount", toInt(get(acc, "accountCount")));
        overview.put("totalBalance", toInt(get(acc, "totalBalance")));
        overview.put("totalEarned", toInt(get(acc, "totalEarned")));
        overview.put("totalIssued", toInt(get(issued, "totalIssued")));
        overview.put("totalSpent", toInt(get(spent, "totalSpent")));
        overview.put("platformFee", totalFee);
        overview.put("orderCount", totalOrders);
        overview.put("todayIssued", toInt(get(todayIssued, "todayIssued")));
        overview.put("dailyEarnLimit", PointsService.DAILY_EARN_LIMIT);
        overview.put("days", d);
        vo.setOverview(overview);
        vo.setFeeByChannel(feeRows);

        // ---------- 按日趋势（发放/消耗 与 抽成 合并到同一日期轴） ----------
        Map<String, Map<String, Object>> dayMap = new TreeMap<>();
        for (Map<String, Object> r : reconcileMapper.selectEarnSpendDaily(
                d, PointsService.EARN_TYPES, PointsService.SPEND_TYPES)) {
            String key = str(r.get("date"));
            Map<String, Object> row = dayMap.computeIfAbsent(key, PointsReconcileServiceImpl::blankDay);
            row.put("earned", toInt(r.get("earned")));
            row.put("spent", toInt(r.get("spent")));
        }
        for (Map<String, Object> r : reconcileMapper.selectPlatformFeeDaily(d)) {
            String key = str(r.get("date"));
            Map<String, Object> row = dayMap.computeIfAbsent(key, PointsReconcileServiceImpl::blankDay);
            row.put("fee", toInt(r.get("fee")));
            row.put("orders", toInt(r.get("orders")));
        }
        vo.setDaily(new ArrayList<>(dayMap.values()));

        // ---------- 榜单 ----------
        vo.setTopEarners(fillUsernames(reconcileMapper.selectTopEarners(10)));
        vo.setTopSpenders(fillUsernames(reconcileMapper.selectTopSpenders(10, PointsService.SPEND_TYPES)));
        return vo;
    }

    @Override
    public PointsQuotaVO getQuota(Long userId) {
        PointsQuotaVO vo = new PointsQuotaVO();
        PointsAccount acc = userId == null ? null : pointsAccountMapper.selectById(userId);
        int totalEarned = acc == null || acc.getTotalEarned() == null ? 0 : acc.getTotalEarned();
        PointsLevel lv = PointsLevel.of(totalEarned);

        vo.setLevel(lv.getLevel());
        vo.setLevelName(lv.getName());
        vo.setTotalEarned(totalEarned);
        vo.setBalance(acc == null || acc.getBalance() == null ? 0 : acc.getBalance());
        vo.setPriceLimit(lv.getPriceLimit());

        // 下一个等级（已满级则为 null）
        for (PointsLevel l : PointsLevel.values()) {
            if (l.getMinEarned() > totalEarned) {
                vo.setNextLevel(l.getLevel());
                vo.setNextLevelName(l.getName());
                vo.setNextLevelMinEarned(l.getMinEarned());
                break;
            }
        }

        int limit = PointsService.DAILY_EARN_LIMIT;
        int today = pointsService.getTodayEarned(userId);
        vo.setDailyEarnLimit(limit);
        vo.setTodayEarned(today);
        vo.setTodayRemain(Math.max(0, limit - today));
        return vo;
    }

    // ------------------------- 私有工具 -------------------------

    /** 榜单补用户名（批量查询，避免逐条查库） */
    private List<Map<String, Object>> fillUsernames(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("userId"));
            if (id != null) {
                ids.add(id);
            }
        }
        Map<Long, String> nameMap = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User u : userMapper.selectBatchIds(ids)) {
                nameMap.put(u.getId(),
                        StringUtils.isNotBlank(u.getUsername()) ? u.getUsername() : u.getUserAccount());
            }
        }
        for (Map<String, Object> r : rows) {
            Long id = toLong(r.get("userId"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", id);
            m.put("username", nameMap.getOrDefault(id, "用户" + id));
            m.put("amount", toInt(r.get("amount")));
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> blankDay(String date) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", date);
        row.put("earned", 0);
        row.put("spent", 0);
        row.put("fee", 0);
        row.put("orders", 0);
        return row;
    }

    private static Object get(Map<String, Object> row, String key) {
        return row == null ? null : row.get(key);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /** MySQL 的 SUM 会返回 BigDecimal / Long，这里统一收敛为 int */
    private static int toInt(Object o) {
        return (int) toLong(o);
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
