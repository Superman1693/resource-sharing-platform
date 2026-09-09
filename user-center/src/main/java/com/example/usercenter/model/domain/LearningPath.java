package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 学习路径实体
 * @TableName learning_path
 */
@TableName("learning_path")
@Data
public class LearningPath {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 路径标题 */
    private String title;

    /** 路径描述 */
    private String description;

    /** 图标 */
    private String icon;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
