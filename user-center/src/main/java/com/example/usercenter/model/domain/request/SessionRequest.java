package com.example.usercenter.model.domain.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 会话管理请求模型
 */
@Data
public class SessionRequest {

    /**
     * 会话ID
     */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 会话状态
     */
    private String status;
}
