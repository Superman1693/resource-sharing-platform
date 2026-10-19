package com.example.usercenter.model.enums;

/**
 * 积分等级。
 *
 * <p>设计要点：等级按「累计获得」（{@code total_earned}）而非「余额」（{@code balance}）计算 ——
 * 这样用户消费积分（解锁笔记、打赏作者）不会掉级，才敢放心花。</p>
 *
 * <p><b>阈值与售价上限必须与前端 {@code demo1/src/utils/pointsLevel.js} 保持一致</b>，
 * 前端只做展示与预校验，最终校验以后端为准（见 {@code PointsService#getPriceLimit}）。</p>
 *
 * @author zy
 */
public enum PointsLevel {

    L1(1, "新手", 0, 0),
    L2(2, "活跃", 100, 50),
    L3(3, "贡献", 500, 100),
    L4(4, "资深", 2000, 200),
    L5(5, "核心", 10000, 500);

    /** 等级序号 1~5 */
    private final int level;

    /** 等级名称 */
    private final String name;

    /** 达到该等级所需的累计获得积分 */
    private final int minEarned;

    /**
     * 该等级可设置的售价上限（笔记解锁价 / 资源下载价）。
     *
     * <p>L1 为 0，即新手不能给自己的内容定价 —— 避免刷号后立刻挂高价内容。</p>
     */
    private final int priceLimit;

    PointsLevel(int level, String name, int minEarned, int priceLimit) {
        this.level = level;
        this.name = name;
        this.minEarned = minEarned;
        this.priceLimit = priceLimit;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public int getMinEarned() {
        return minEarned;
    }

    public int getPriceLimit() {
        return priceLimit;
    }

    /**
     * 按累计获得积分取等级（null / 负数按 0 处理）。
     */
    public static PointsLevel of(Integer totalEarned) {
        int total = totalEarned == null ? 0 : totalEarned;
        PointsLevel current = L1;
        for (PointsLevel lv : values()) {
            if (total >= lv.minEarned) {
                current = lv;
            }
        }
        return current;
    }
}
