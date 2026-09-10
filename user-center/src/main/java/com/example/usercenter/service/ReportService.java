package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Report;

/**
 * 举报服务：创建举报、列表、处理
 */
public interface ReportService {

    /** 创建举报（同一举报人对同一目标 pending 不重复） */
    void create(Long reporterId, String targetType, Long targetId, String reason);

    /** 举报列表（status 可选） */
    PageResult<Report> list(String status, int page, int pageSize);

    /** 处理举报 */
    boolean handle(Long id, String status, String remark, Long handlerId);
}
