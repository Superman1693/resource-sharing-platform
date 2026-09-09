package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 私信会话实体
 * @TableName message_conversation
 */
@TableName("message_conversation")
@Data
public class MessageConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户1ID（较小的ID） */
    private Long user1Id;

    /** 用户2ID（较大的ID） */
    private Long user2Id;

    /** 最后一条消息内容 */
    private String lastMessageContent;

    /** 最后消息时间 */
    private Date lastMessageTime;

    /** 创建时间 */
    private Date createTime;

    @TableLogic
    private Integer isDelete;
}
