package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 笔记打赏记录。
 *
 * <p>与「购买」不同，打赏不计入解锁状态，纯粹是读者对作者的认可，可重复打赏。</p>
 *
 * @TableName note_tip
 */
@TableName("note_tip")
@Data
public class NoteTip {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 打赏者ID */
    private Long userId;

    /** 笔记ID */
    private Long noteId;

    /** 作者ID（冗余，便于对账） */
    private Long authorId;

    /** 打赏积分 */
    private Integer amount;

    /** 作者实得积分（扣除平台抽成后） */
    private Integer authorGain;

    private Date createTime;
}
