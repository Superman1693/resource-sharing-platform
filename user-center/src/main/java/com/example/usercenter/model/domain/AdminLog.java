package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.util.Date;

@TableName(value = "admin_log")
@Data
public class AdminLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private Long targetId;
    private String action;
    private String targetType;
    private String remark;
    private Date createTime;
}
