package com.example.usercenter.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一的API响应类
 * @Author: zy
 * @CreateTime: 2025-09-27
 * @Description: 自定义状态码
 * @Version: 1.0
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;
    private T data;
    private  String message;
    private String description;

    public BaseResponse(int code, T data, String message,String description) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.description = description;
    }
    public BaseResponse(int code, T data,String message) {
        this(code,data,message,"");
    }
    public BaseResponse(int code, T data) {
        this(code,data,"","");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(),null,errorCode.getMessage(),errorCode.getDescription());
    }
}

