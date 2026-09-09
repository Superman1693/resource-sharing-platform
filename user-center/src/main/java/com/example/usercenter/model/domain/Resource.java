package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.usercenter.serializer.TagsDeserializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 资源实体类
 * @TableName resource
 */
@TableName(value = "resource")
@Data
public class Resource {
    /**
     * 资源ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 上传者用户ID（资源归属，用于"我的资源"查询与统计）
     */
    private Long uploaderId;

    /**
     * 资源名称
     */
    private String name;

    /**
     * 资源标题
     */
    private String title;

    /**
     * 资源描述
     */
    private String description;

    /**
     * 资源类型：document/video/code/other
     */
    private String resourceType;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签：pdf/video/code/tool
     */
    private String tag;
    /**
     * 下载链接（非数据库字段，用于API返回）
     */

    private String downloadUrl;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 文件大小（字节，数据库字段：file_size，BIGINT类型）
     */
    @TableField("file_size")
    private Long fileSizeBytes;

    /**
     * 文件大小（格式化字符串，非数据库字段，用于API返回）
     */
    @TableField(exist = false)
    private String fileSize;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 状态：enabled/disabled
     */
    private String status;

    /**
     * 标签列表（JSON字符串存储）
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
     * 是否公开
     */
    private Boolean isPublic;

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

}
