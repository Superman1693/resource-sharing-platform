package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 评论实体类
 * @TableName comment
 */
@TableName(value = "comment")
@Data
public class Comment {
    /**
     * 评论ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 笔记/内容ID
     */
    private Long noteId;

    /**
     * 笔记标题
     */
    private String noteTitle;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 状态：approved-已通过, pending-待审核, hidden-已屏蔽
     */
    private String status;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 回复列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Comment> replies;

    /**
     * 父评论ID（用于回复）
     */
    private Long parentId;

    @TableField(exist = false)
    private Boolean isAuthor; // 是否为笔记作者
    @TableField(exist = false)
    private String createTimeStr; // 格式化后的时间

}
