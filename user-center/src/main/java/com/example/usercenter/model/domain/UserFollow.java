package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 用户关注关系实体
 * @TableName user_follow
 */
@TableName("user_follow")
@Data
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者用户ID */
    private Long userId;

    /** 被关注者用户ID */
    private Long followUserId;

    /** 创建时间 */
    private Date createTime;

    /** 是否删除 */
    @TableLogic
    private Integer isDelete;
}
