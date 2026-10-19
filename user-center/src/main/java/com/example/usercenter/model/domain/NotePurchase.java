package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 笔记购买记录（积分解锁）。
 *
 * <p>唯一键 {@code uk_user_note(user_id, note_id)} 保证「同一用户对同一笔记只扣一次积分」，
 * 并发重复提交时靠唯一键冲突兜底，无需额外加锁。</p>
 *
 * @TableName note_purchase
 */
@TableName("note_purchase")
@Data
public class NotePurchase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 购买者ID */
    private Long userId;

    /** 笔记ID */
    private Long noteId;

    /** 作者ID（冗余，便于对账） */
    private Long authorId;

    /** 成交价（积分） */
    private Integer price;

    /** 作者实得积分（扣除平台抽成后） */
    private Integer authorGain;

    private Date createTime;
}
