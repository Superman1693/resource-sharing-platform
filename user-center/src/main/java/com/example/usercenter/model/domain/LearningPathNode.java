package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 学习路径节点实体
 * @TableName learning_path_node
 */
@TableName("learning_path_node")
@Data
public class LearningPathNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属路径ID */
    private Long pathId;

    /** 父节点ID，NULL为顶级 */
    private Long parentId;

    /** 节点标题 */
    private String title;

    /** 节点描述 */
    private String description;

    /** 状态：pending / in_progress / completed */
    private String status;

    /** 排序顺序 */
    private Integer sortOrder;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    @TableLogic
    private Integer isDelete;
}
