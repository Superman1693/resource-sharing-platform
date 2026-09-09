package com.example.usercenter.model.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话模型
 */
@Data
public class ChatSession implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 会话创建时间
     */
    private LocalDateTime createTime;

    /**
     * 会话最后更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 会话状态：active, closed
     */
    private String status;

    /**
     * 消息数量
     */
    private Integer messageCount;

    /**
     * 最后一条消息内容
     */
    private String lastMessage;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageTime;
}
