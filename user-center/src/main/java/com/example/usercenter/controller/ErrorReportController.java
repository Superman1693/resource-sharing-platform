package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.mapper.ErrorLogMapper;
import com.example.usercenter.model.domain.ErrorLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 前端错误上报接口
 *
 * <p>接收前端 JavaScript 错误与性能指标，并**落库到 error_log 表**，
 * 同时提供管理员分页查询入口，形成「采集 → 上报 → 存储 → 回溯」的完整闭环。</p>
 *
 * @author zy
 */
@RestController
@RequestMapping("/error")
@RequiredArgsConstructor
@Slf4j
public class ErrorReportController {

    private final ErrorLogMapper errorLogMapper;

    /** 单次上报的最大条数，防止被滥用写入大量数据 */
    private static final int MAX_BATCH_SIZE = 50;

    /**
     * 批量接收前端错误上报
     *
     * <p>该接口不要求登录（页面可能在未登录状态就崩溃），
     * 因此只做长度截断与条数限制，不做身份校验。</p>
     *
     * @return 成功落库的条数
     */
    @PostMapping("/report")
    public BaseResponse<Integer> reportErrors(@RequestBody ErrorReportRequest request) {
        if (request == null || request.getErrors() == null || request.getErrors().isEmpty()) {
            return ResultUtils.success(0);
        }

        List<ErrorInfo> errors = request.getErrors();
        int accepted = Math.min(errors.size(), MAX_BATCH_SIZE);

        List<ErrorLog> logs = new ArrayList<>(accepted);
        for (int i = 0; i < accepted; i++) {
            ErrorInfo error = errors.get(i);
            if (error == null) {
                continue;
            }
            ErrorLog entity = new ErrorLog();
            entity.setErrorType(truncate(error.getType(), 40));
            entity.setMessage(truncate(error.getMessage(), 1000));
            entity.setStack(truncate(error.getStack(), 8000));
            entity.setSource(truncate(error.getSource(), 500));
            entity.setLineno(error.getLineno());
            entity.setColno(error.getColno());
            entity.setComponentInfo(truncate(error.getComponentInfo(), 500));
            entity.setComponentName(truncate(error.getComponentName(), 200));
            entity.setTagName(truncate(error.getTagName(), 100));
            entity.setUserId(error.getUserId());
            entity.setUrl(truncate(error.getUrl(), 1000));
            entity.setUserAgent(truncate(error.getUserAgent(), 500));
            entity.setClientTime(error.getTimestamp());
            entity.setCreateTime(new Date());
            logs.add(entity);
        }

        int saved = 0;
        for (ErrorLog entity : logs) {
            try {
                errorLogMapper.insert(entity);
                saved++;
            } catch (Exception e) {
                // 单条失败不影响其余上报，避免日志写入问题反过来拖垮前端
                log.warn("[前端错误] 落库失败: type={}, message={}", entity.getErrorType(), entity.getMessage(), e);
            }
        }

        log.warn("[前端错误] 本次接收 {} 条，成功落库 {} 条（单次上限 {} 条）", errors.size(), saved, MAX_BATCH_SIZE);
        return ResultUtils.success(saved);
    }

    /**
     * 管理员查询前端错误日志（分页，按时间倒序）
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数，1~100
     * @param type     可选，按错误类型过滤
     */
    @GetMapping("/list")
    @AdminRequired
    public BaseResponse<PageResult<ErrorLog>> listErrors(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String type) {

        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }

        QueryWrapper<ErrorLog> wrapper = new QueryWrapper<>();
        if (type != null && !type.isBlank()) {
            wrapper.eq("error_type", type);
        }
        wrapper.orderByDesc("create_time");

        Page<ErrorLog> result = errorLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return ResultUtils.success(new PageResult<>(
                result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords()));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Data
    public static class ErrorReportRequest {
        private List<ErrorInfo> errors;
    }

    @Data
    public static class ErrorInfo {
        /** 错误类型: js-error / unhandled-rejection / vue-error / resource-error / manual */
        private String type;
        /** 错误消息 */
        private String message;
        /** 错误堆栈 */
        private String stack;
        /** 错误来源文件 */
        private String source;
        /** 行号 */
        private Integer lineno;
        /** 列号 */
        private Integer colno;
        /** Vue 组件信息 */
        @JsonProperty("componentInfo")
        private String componentInfo;
        /** Vue 组件名 */
        private String componentName;
        /** 资源标签名 */
        private String tagName;
        /** 用户 ID */
        private Long userId;
        /** 页面 URL */
        private String url;
        /** User Agent */
        private String userAgent;
        /** 上报时间戳 */
        private Long timestamp;
    }
}
