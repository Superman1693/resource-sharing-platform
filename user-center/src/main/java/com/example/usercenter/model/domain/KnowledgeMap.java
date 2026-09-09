package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 知识图谱实体类
 * @TableName knowledge_map
 */
@TableName(value = "knowledge_map")
@Data
public class KnowledgeMap {
    /**
     * 知识图谱ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属星球ID
     */
    private Long starId;

    /**
     * 知识图谱名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 图谱配置（节点、连线等JSON数据）
     */
    private String config;

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





