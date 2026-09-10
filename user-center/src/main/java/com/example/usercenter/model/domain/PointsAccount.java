package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 积分账户实体（主键 user_id，非自增）
 * @TableName points_account
 */
@TableName("points_account")
@Data
public class PointsAccount {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer balance;

    private Integer totalEarned;

    private Date updateTime;
}
