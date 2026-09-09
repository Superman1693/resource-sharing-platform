package com.example.usercenter.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息模型
 */
@Data
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String sessionId;
    private Long userId;

    /** 消息发送者类型：user 或 ai */
    private String senderType;

    private String content;

    /** 消息状态：sent, delivered, read */
    private String status;

    private LocalDateTime timestamp;

    /**
     * 修复点1：boolean 基本类型在 Lombok @Data 下生成 isXxx() getter，
     * JSON/Redis 序列化字段名会变成 "streaming"/"completed"，
     * 与 Redis Hash key "isStreaming"/"isCompleted" 不一致导致反序列化失败。
     * 改用 Boolean 包装类并用 @JsonProperty 明确字段名。
     */
    @JsonProperty("isStreaming")
    private Boolean streaming;

    @JsonProperty("isCompleted")
    private Boolean completed;

    private String errorMessage;
}
