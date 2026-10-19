package com.example.usercenter.service;

import com.example.usercenter.model.vo.PointsQuotaVO;
import com.example.usercenter.model.vo.PointsReconcileVO;

/**
 * 积分对账服务（管理端只读统计）。
 *
 * @author zy
 */
public interface PointsReconcileService {

    /**
     * 积分对账总览。
     *
     * @param days 趋势统计天数（1~90，超出自动收敛）
     */
    PointsReconcileVO getReconcile(int days);

    /** 某用户的积分额度（等级 / 售价上限 / 今日获取剩余） */
    PointsQuotaVO getQuota(Long userId);
}
