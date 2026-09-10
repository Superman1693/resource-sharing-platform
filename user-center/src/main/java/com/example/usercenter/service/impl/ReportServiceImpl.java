package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.mapper.ReportMapper;
import com.example.usercenter.model.domain.Report;
import com.example.usercenter.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 举报服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private final ReportMapper reportMapper;

    @Override
    public void create(Long reporterId, String targetType, Long targetId, String reason) {
        if (reporterId == null || StringUtils.isBlank(targetType) || targetId == null) return;
        // 同一举报人对同一目标 pending 不重复
        Long exists = this.count(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, "pending"));
        if (exists != null && exists > 0) return;
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReason(reason);
        r.setStatus("pending");
        r.setCreateTime(new Date());
        this.save(r);
    }

    @Override
    public PageResult<Report> list(String status, int page, int pageSize) {
        LambdaQueryWrapper<Report> w = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreateTime);
        if (StringUtils.isNotBlank(status)) {
            w.eq(Report::getStatus, status);
        }
        IPage<Report> p = this.page(new Page<>(page, pageSize), w);
        return new PageResult<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    public boolean handle(Long id, String status, String remark, Long handlerId) {
        if (id == null || StringUtils.isBlank(status)) return false;
        Report r = this.getById(id);
        if (r == null) return false;
        r.setStatus(status);
        r.setHandlerId(handlerId);
        r.setHandleRemark(remark);
        r.setHandleTime(new Date());
        return this.updateById(r);
    }
}
