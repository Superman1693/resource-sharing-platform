package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 知识节点实体类
 * @TableName knowledge_node
 */
@TableName(value = "knowledge_node")
@Data
public class KnowledgeNode {
    /**
     * 节点ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识图谱ID
     */
    private Long mapId;

    /**
     * 节点标识（在图谱中的唯一ID）
     */
    private String nodeId;

    /**
     * 节点标题
     */
    private String title;

    /**
     * 节点内容
     */
    private String content;

    /**
     * 节点X坐标
     */
    private BigDecimal positionX;

    /**
     * 节点Y坐标
     */
    private BigDecimal positionY;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}





