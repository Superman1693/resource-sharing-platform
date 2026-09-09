package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 私信消息实体
 * @TableName message
 */
@TableName("message")
@Data
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 发送者用户ID */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    /** 创建时间 */
    private Date createTime;

    @TableLogic
    private Integer isDelete;
}
