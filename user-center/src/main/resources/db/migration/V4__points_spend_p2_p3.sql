-- ============================================================================
-- V4：积分消耗 P2 / P3
--   ① 自助置顶：note.top_until（消耗积分置顶 24 小时，到期由定时任务自动下架）
--   ② 资源付费下载：resource.price + resource_download
--      —— 顺带修复既有缺陷「下载计数可被刷新无限刷」：改为按 (user_id, resource_id)
--         去重，只有首次记录才 +1
--   ③ 创建星球门槛：消耗积分创建星球（沉淀在 points_log，无需新增表）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 笔记自助置顶的到期时间
--    is_top 仍表示「当前是否置顶」，top_until 为空表示是管理员手动置顶（不自动过期）
-- ----------------------------------------------------------------------------
ALTER TABLE `note`
    ADD COLUMN `top_until` DATETIME DEFAULT NULL COMMENT '自助置顶到期时间（NULL=不过期，通常为管理员置顶）' AFTER `is_top`;

-- ----------------------------------------------------------------------------
-- 2) 资源下载所需积分（0 = 免费）
-- ----------------------------------------------------------------------------
ALTER TABLE `resource`
    ADD COLUMN `price` INT NOT NULL DEFAULT 0 COMMENT '下载所需积分：0=免费' AFTER `download_count`;

-- ----------------------------------------------------------------------------
-- 3) 资源下载/解锁记录
--    uk_user_resource：同一用户对同一资源只记一次（既保证付费资源不重复扣分，
--    也让「下载数」不再被刷新刷爆）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resource_download` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT   NOT NULL COMMENT '下载者ID',
    `resource_id`   BIGINT   NOT NULL COMMENT '资源ID',
    `uploader_id`   BIGINT   DEFAULT NULL COMMENT '上传者ID（冗余，便于对账）',
    `price`         INT      NOT NULL DEFAULT 0 COMMENT '成交价（积分），0=免费',
    `uploader_gain` INT      NOT NULL DEFAULT 0 COMMENT '上传者实得积分（扣除平台抽成后）',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次下载时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`),
    INDEX `idx_resource_id` (`resource_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_uploader_id` (`uploader_id`),
    CONSTRAINT `fk_rd_resource` FOREIGN KEY (`resource_id`) REFERENCES `resource` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_rd_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源下载/解锁记录';

-- ============================================================================
-- 说明：
--   * 积分消费的价目（改这里即可调整，代码里读常量）：
--       自助置顶 50 积分 / 24 小时；创建星球 500 积分；打赏 1~500；笔记解锁由作者定价（0~100）
--   * 平台抽成统一 10%（资源下载同样适用），抽成部分不落任何账户，仅在流水备注标注。
-- ============================================================================
