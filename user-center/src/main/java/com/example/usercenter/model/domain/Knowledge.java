package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 知识地图实体类
 * @TableName knowledge_map
 */
@TableName(value = "knowledge_map")
@Data
public class Knowledge {
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
     * 图谱配置（节点、连线等JSON数据，数据库字段：config）
     */
    @TableField("config")
    private String config;

    /**
     * 节点数据（JSON字符串存储，非数据库字段，用于兼容旧代码）
     */
    @TableField(exist = false)
    private String nodes;

    /**
     * 边数据（JSON字符串存储，非数据库字段，用于兼容旧代码）
     */
    @TableField(exist = false)
    private String edges;

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
     * 节点列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<KnowledgeNode> nodeList;

    /**
     * 边列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<KnowledgeEdge> edgeList;

    /**
     * 知识节点内部类
     */
    @Data
    public static class KnowledgeNode {
        private String id;
        private String label;
        private String type; // core/topic/concept
        private Integer x;
        private Integer y;
        /** 关联笔记 ID（自动生成的节点有值） */
        private Long noteId;
        /** 分类（从笔记同步，用于自动连线） */
        private String category;
        /** 标签列表（从笔记同步，用于自动连线） */
        private java.util.List<String> tags;
        /** 是否自动生成（true=随笔记存在，不可手删） */
        private Boolean auto;
        /** 管理员隐藏标记（用户端地图不显示） */
        private Boolean hidden;
    }

    /**
     * 知识边内部类
     */
    @Data
    public static class KnowledgeEdge {
        private String source;
        private String target;
        /** 是否自动生成（同分类/共享标签自动连线） */
        private Boolean auto;
    }


}
