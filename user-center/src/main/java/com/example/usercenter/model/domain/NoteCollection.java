package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 笔记收藏实体（单层，无分组）
 * @TableName note_collection
 */
@TableName("note_collection")
@Data
public class NoteCollection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏者用户ID */
    private Long userId;

    /** 被收藏的笔记ID */
    private Long noteId;

    /** 收藏时间 */
    private Date createTime;

    /** 是否删除（逻辑删除） */
    @TableLogic
    private Integer isDelete;
}
