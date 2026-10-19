package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 资源下载 / 解锁记录。
 *
 * <p>唯一键 {@code uk_user_resource(user_id, resource_id)} 有两个作用：
 * ① 付费资源不会被重复扣积分（幂等）；
 * ② 下载数只按「首次下载」累加，不再被刷新刷爆。</p>
 *
 * @TableName resource_download
 */
@TableName("resource_download")
@Data
public class ResourceDownload {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 下载者ID */
    private Long userId;

    /** 资源ID */
    private Long resourceId;

    /** 上传者ID（冗余，便于对账） */
    private Long uploaderId;

    /** 成交价（积分），0 = 免费 */
    private Integer price;

    /** 上传者实得积分（扣除平台抽成后） */
    private Integer uploaderGain;

    /** 首次下载时间 */
    private Date createTime;
}
