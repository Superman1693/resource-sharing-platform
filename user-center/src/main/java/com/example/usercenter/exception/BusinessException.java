package com.example.usercenter.exception;

import com.example.usercenter.common.ErrorCode;

/**
 * 自定义异常类
 * @Author: zy
 * @CreateTime: 2025-09-29
 * @Description: 全局异常处理
 * @Version: 1.0
 */
public class BusinessException extends  RuntimeException{

    private int code;
    private final String description;

    public BusinessException(String message,int code,String description){
        super(message);//调用父类RuntimeException的构造方法
        this.code=code;
        this.description=description;
    }
    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.code=errorCode.getCode();
        this.description=errorCode.getDescription();
    }
    public BusinessException(ErrorCode errorCode,String description){
        super(errorCode.getMessage());
        this.code=errorCode.getCode();
        this.description=description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
