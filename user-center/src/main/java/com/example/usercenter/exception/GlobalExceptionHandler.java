package com.example.usercenter.exception;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Author: zy
 * @CreateTime: 2025-09-29
 * @Description: 全局异常处理器
 * @Version: 1.0
 */
@RestControllerAdvice // 声明这是RESTFUL风格的全局异常处理器
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class) // 声明要处理的异常类型
    @ResponseBody // 确保返回的是 JSON
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("businessException：{}", e.getMessage(), e);
        // 从异常对象中获取自定义的错误码和错误信息
        return ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException:{}", e.getMessage(), e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage(), "");

    }

    @ExceptionHandler(io.jsonwebtoken.ExpiredJwtException.class)
    public BaseResponse<?> handleExpiredJwt(io.jsonwebtoken.ExpiredJwtException e) {
        log.warn("JWT 已过期: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN, "登录已过期，请重新登录");
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public BaseResponse<?> handleInvalidJwt(io.jsonwebtoken.JwtException e) {
        log.warn("JWT 无效: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_LOGIN, "无效的认证信息");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public BaseResponse<?> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(java.util.stream.Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, msg);
    }

    /**
     * 上传文件超过大小限制（spring.servlet.multipart.max-file-size = 100MB）时
     * 返回友好提示，而不是 500 系统异常
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public BaseResponse<?> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "上传文件过大，请压缩后重试（图片/封面不超过 10MB，资源文件不超过 100MB）");
    }

}
