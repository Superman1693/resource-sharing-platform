package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 签到记录实体
 * @TableName sign_in_record
 */
@TableName("sign_in_record")
@Data
public class SignInRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Date signDate;

    private Integer continuousDays;

    private Date createTime;
}
