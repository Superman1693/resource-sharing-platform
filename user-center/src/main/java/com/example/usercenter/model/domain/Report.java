package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 举报记录实体
 * @TableName report
 */
@TableName("report")
@Data
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    /** note/comment/user */
    private String targetType;

    private Long targetId;

    private String reason;

    /** pending/resolved/ignored */
    private String status;

    private Long handlerId;

    private String handleRemark;

    private Date createTime;

    private Date handleTime;
}
