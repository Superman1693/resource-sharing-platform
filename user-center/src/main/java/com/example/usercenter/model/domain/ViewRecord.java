package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 浏览记录实体类
 * @TableName view_record
 */
@TableName(value = "view_record")
@Data
public class ViewRecord {
    /**
     * 浏览记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（NULL表示匿名用户）
     */
    private Long userId;

    /**
     * 笔记ID
     */
    private Long noteId;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 创建时间
     */
    private Date createTime;
    private String targetType;  // 目标类型：note/resource
    private Date updateTime;
    @TableLogic(value = "0", delval = "1")
    private Integer isDelete;
}





