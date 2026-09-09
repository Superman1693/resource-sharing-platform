package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 星球成员实体类
 * @TableName star_member
 */
@TableName(value = "star_member")
@Data
public class StarMember {
    /**
     * 成员关系ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 星球ID
     */
    private Long starId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色：owner=创建者，admin=管理员，member=普通成员
     */
    private String role;

    /**
     * 加入时间
     */
    private Date joinTime;

    /**
     * 是否删除（逻辑删除）
     */
    @TableLogic
    private Integer isDelete;
}





