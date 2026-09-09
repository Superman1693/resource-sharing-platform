package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 点赞记录实体类
 * @TableName like_record
 */
@TableName(value = "like_record")
@Data
public class LikeRecord {
    /**
     * 点赞记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 目标类型：note=笔记，comment=评论
     */
    private String targetType;

    /**
     * 目标ID（笔记ID或评论ID）
     */
    private Long targetId;

    /**
     * 创建时间
     */
    private Date createTime;
}





