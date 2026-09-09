package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前端错误上报接口
 * 接收前端 JavaScript 错误和性能指标
 */
@RestController
@RequestMapping("/error")
@Slf4j
public class ErrorReportController {

    /**
     * 批量接收前端错误上报
     */
    @PostMapping("/report")
    public BaseResponse<Void> reportErrors(@RequestBody ErrorReportRequest request) {
        if (request == null || request.getErrors() == null || request.getErrors().isEmpty()) {
            return ResultUtils.success(null);
        }

        for (ErrorInfo error : request.getErrors()) {
            log.warn("[前端错误] type={}, message={}, source={}, userId={}, url={}",
                    error.getType(),
                    error.getMessage(),
                    error.getSource(),
                    error.getUserId(),
                    error.getUrl());
        }

        return ResultUtils.success(null);
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
