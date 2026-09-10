package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 笔记专栏实体（星球内合集：作者把系列笔记串成专栏）
 * @author zy
 */
@Data
@TableName("note_column")
public class NoteColumn {

    /** 专栏ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 专栏标题 */
    private String title;

    /** 专栏描述 */
    private String description;

    /** 封面图 */
    private String coverImage;

    /** 作者用户ID */
    private Long authorId;

    /** 所属星球ID */
    private Long starId;

    /** 专栏内笔记数量 */
    private Integer noteCount;

    /** 状态：active-连载中 archived-已完结 */
    private String status;

    /** 专栏下的笔记列表（非数据库字段，详情接口填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<Note> notes;

    /** 作者名（非数据库字段，填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String authorName;

    /** 作者头像（非数据库字段，填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String authorAvatar;

    /** 星球名（非数据库字段，填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String starName;

    private Date createTime;

    private Date updateTime;

    /** 逻辑删除：0-未删 1-已删 */
    @TableLogic
    private Integer isDelete;
}
