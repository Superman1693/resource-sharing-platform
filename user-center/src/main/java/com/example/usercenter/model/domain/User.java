package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.example.usercenter.annotation.MaskSensitive;
import com.example.usercenter.serializer.MaskType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName user
 */
@TableName(value = "user")
@Data
public class User {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String avatarUrl;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 密码（JSON 序列化时忽略，不返回给前端）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String userPassword;

    /**
     * 电话（脱敏: 138****5678）
     */
    @MaskSensitive(MaskType.PHONE)
    private String phone;

    /**
     * 邮箱（脱敏: z***@qq.com）
     */
    @MaskSensitive(MaskType.EMAIL)
    private String email;

    /**
     * 个人简介
     */
    private String bio;

    /**
     * 擅长技术栈（逗号分隔）
     */
    private String skills;

    /**
     * 可提供的服务描述
     */
    private String services;

    /**
     * 状态 0 -正常
     */
    private Integer userStatus;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     *
     * 角色 0-普通用户 1-管理员用户
     */
    private Integer userRole;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic // 用来让mybatis辨认是逻辑删除的字段
    private Integer isDelete;

    /**
     * 登录 token（非数据库字段，登录成功后由后端生成返回给前端）
     */
    @TableField(exist = false)
    private String token;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}