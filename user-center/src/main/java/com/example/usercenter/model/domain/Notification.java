package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 站内通知实体
 * @TableName notification
 */
@TableName("notification")
@Data
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者用户ID */
    private Long receiverId;

    /** 发送者用户ID（系统通知为 null） */
    private Long senderId;

    /** 发送者用户名（冗余） */
    private String senderName;

    /** 发送者头像（冗余） */
    private String senderAvatar;

    /**
     * 通知类型：
     * like_note    = 笔记被点赞
     * comment_note = 笔记被评论
     * reply_comment= 评论被回复
     * system       = 系统通知
     */
    private String type;

    /** 关联目标ID（noteId / commentId） */
    private Long targetId;

    /** 关联目标标题（冗余，便于展示） */
    private String targetTitle;

    /** 通知内容 */
    private String content;

    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;

    private Date createTime;

    @TableLogic
    private Integer isDelete;
}
