package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 星球实体类
 * @TableName star
 */
@TableName(value = "star")
@Data
public class Star {
    /**
     * 星球ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 星球名称
     */
    private String name;

    /**
     * 星球描述
     */
    private String description;

    /**
     * 星球公告
     */
    private String announcement;

    /**
     * 封面图片
     */
    private String coverImage;

    /**
     * 星球创建者ID
     */
    private Long ownerId;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 内容数量
     */
    private Integer contentCount;

    /**
     * 加入价格（元），0=免费
     */
    private Integer price;

    /**
     * 状态：active=活跃，inactive=停用
     */
    private String status;

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





