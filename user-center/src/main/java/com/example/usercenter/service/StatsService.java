package com.example.usercenter.service;

import com.example.usercenter.model.domain.Stats;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 统计数据服务接口
 * @author zy
 */
public interface StatsService {
    /**
     * 获取数据统计概览
     */
    Stats getDataStats(String startDate, String endDate, HttpServletRequest request);

    /**
     * 获取用户统计数据
     */
    Stats getUserStats(String startDate, String endDate, String source, HttpServletRequest request);


}
