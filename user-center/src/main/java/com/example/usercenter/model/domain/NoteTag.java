package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 笔记-标签关联实体
 * @TableName note_tag
 */
@TableName("note_tag")
@Data
public class NoteTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    private Long tagId;

    private Date createTime;
}
