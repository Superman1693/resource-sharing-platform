package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 标签实体
 * @TableName tag
 */
@TableName("tag")
@Data
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名（唯一） */
    private String name;

    /** 使用次数（被多少笔记使用） */
    private Integer usageCount;

    /** 创建时间 */
    private Date createTime;
}
