package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.SignInRecord;

import java.util.List;

/**
 * 积分服务：签到、积分账户、流水
 */
public interface PointsService {

    /** 签到（今日已签返回 false） */
    boolean signIn(Long userId);

    /** 获取积分账户（不存在返回 null） */
    PointsAccount getAccount(Long userId);

    /** 今日是否已签到 */
    boolean isSignedToday(Long userId);

    /** 积分流水（分页） */
    PageResult<PointsLog> getPointsLog(Long userId, int page, int pageSize);

    /** 某月签到日历（month 格式 yyyy-MM，null 取当月） */
    List<SignInRecord> getSignCalendar(Long userId, String month);

    /** 通用加分（发布/获赞/评论触发），失败不阻塞主流程 */
    void addPoints(Long userId, int change, String type, String refType, Long refId, String remark);

    /** 按现有贡献值初始化积分账户（管理员，幂等：已存在账户跳过） */
    int migrateFromContribution();
}
