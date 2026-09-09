package com.example.usercenter.common;

import lombok.Data;

/**
 * @Author: zy
 * @CreateTime: 2025-09-27
 * @Description: 返回工具类
 * @Version: 1.0
 */

public class ResultUtils {
    /**
     * 成功
     * 
     * @param data
     * @return
     * @param <T>
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 失败
     * 
     * @param errorCode
     * @return
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 失败
     * 
     * @param code
     * @return
     */
    public static <T> BaseResponse<T> error(int code, String message, String description) {
        return new BaseResponse<>(code, null, message, description);
    }

    /**
     * 失败
     * 
     * @param errorCode
     * @return
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message, String description) {
        return new BaseResponse<>(errorCode.getCode(), null, message, description);
    }

    /**
     * 失败
     * 
     * @param errorCode
     * @return
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String description) {
        return new BaseResponse<>(errorCode.getCode(), null, errorCode.getMessage(), description);
    }
}
