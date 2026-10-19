package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    // ---------- 以下为列表展示用的派生字段，不对应数据库列 ----------

    /** 举报目标标题：笔记标题 / 评论内容摘要 / 用户昵称；目标已删除时为 null */
    @TableField(exist = false)
    private String targetTitle;

    /** 评论所归属的笔记ID（仅 targetType=comment 时有值），供管理端「查看」跳转 */
    @TableField(exist = false)
    private Long targetNoteId;

    /** 举报人昵称 */
    @TableField(exist = false)
    private String reporterName;
}
