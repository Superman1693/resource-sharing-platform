package com.example.usercenter.model.dto;

import lombok.Data;

import java.util.Date;

/**
 * 会话列表视图对象（含对方用户信息）
 */
@Data
public class ConversationVO {

    private Long id;

    /** 对方用户ID */
    private Long otherUserId;

    /** 对方用户名 */
    private String otherUsername;

    /** 对方头像 */
    private String otherAvatarUrl;

    /** 最后一条消息内容 */
    private String lastMessage;

    /** 最后消息时间 */
    private Date lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;
}
