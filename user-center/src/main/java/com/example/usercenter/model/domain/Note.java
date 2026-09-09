package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.usercenter.serializer.TagsDeserializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 笔记/内容实体类
 * @TableName note
 */
@TableName(value = "note")
@Data
public class Note {
    /**
     * 笔记ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 分类：frontend/backend/algorithm/database/other
     */
    private String category;

    /**
     * 内容类型：article/question/note
     */
    private String contentType;

    /**
     * 内容（Markdown格式）
     */
    private String content;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 作者
     */
    private String author;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 作者头像URL（非数据库字段，查询时填充）
     */
    @TableField(exist = false)
    private String authorAvatar;

    /**
     * 内容是否被锁定（付费星球内容且当前用户未加入时为 true，非数据库字段）
     */
    @TableField(exist = false)
    private Boolean locked;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 状态：published/draft/deleted
     */
    private String status;

    /**
     * 发布时间
     */
    private Date publishTime;

    /**
     * 发布时间戳（毫秒）
     */
    @TableField(exist = false)
    private Long publishTimestamp;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 标签列表（JSON字符串存储，查询时转换为List）
     * 兼容前端传入数组 ["tag1","tag2"] 或字符串 "[\"tag1\",\"tag2\"]"
     */
    @JsonDeserialize(using = TagsDeserializer.class)
    private String tags;

    /**
     * 标签列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<String> tagList;

    /**
     * 所属星球ID
     */
    private Long starId;

    /**
     * 收藏ID（用于判断是否已收藏）
     */
    @TableField(exist = false)
    private Long starIdField;

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

    /** 是否置顶 0-否 1-是 */
    private Integer isTop;

}
