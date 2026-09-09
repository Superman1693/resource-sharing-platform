package com.example.usercenter.common;

import lombok.Data;
import lombok.Getter;

/**
 * 错误码
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "ok", ""),
    PARAMS_ERROR(40000, "请求参数错误", ""),
    NULL_ERROR(40001, "请求参数为空", ""),
    NOT_LOGIN(40100, "未登录", ""),
    NO_AUTH(40101, "无权限", ""),
    SYSTEM_ERROR(50000, "系统内部异常", ""),
    LOGIN_FAILED(40002, "数据错误", ""), NOT_FOUND_ERROR(400003, "数据不存在", ""),
    FORBIDDEN(40300, "账号已被封禁", ""),
    TOO_MANY_TOPS(40301, "置顶笔记数量已达上限", ""),
    OPERATION_TOO_FREQUENT(42900, "操作过于频繁，请稍后再试", ""),
    CONCURRENT_OPERATION_FAILED(42901, "并发操作失败，请重试", "");

    //状态码
    private final int code;
    // 状态码 信息
    private final String message;
    // 状态码描述
    private final String description;

    ErrorCode(int code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }

}
