package com.example.usercenter.model.domain.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求模型
 */
@Data
public class ChatRequest {

    /**
     * 会话ID，如果为空则创建新会话
     */
    private String sessionId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 会话名称，创建新会话时使用
     */
    private String sessionName;

    /**
     * 是否使用流式响应
     */
    private boolean streaming = true;
}
