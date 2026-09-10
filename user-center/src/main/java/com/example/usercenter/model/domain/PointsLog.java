package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 积分流水实体
 * @TableName points_log
 */
@TableName("points_log")
@Data
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 变动积分（正数）。列名 change 是 MySQL 保留字，用反引号转义避免 INSERT/SELECT 报错 */
    @TableField(value = "`change`")
    private Integer change;

    /** 类型：sign/publish/like/comment */
    private String type;

    private String refType;

    private Long refId;

    private String remark;

    private Date createTime;
}
