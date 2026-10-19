-- ============================================================================
-- V3：移除评论人工审核 + 引入积分消耗（付费笔记解锁 / 打赏）
--
-- 背景：
--   1) 评论原先发布即为 pending（待审核），需管理员在后台逐条放行。现改为
--      「只做敏感词过滤」——命中即拒绝发布，否则立即可见，不再有人工审核环节。
--   2) 积分此前只增不减（签到/发布/被赞/评论均有获取，但没有任何消耗出口），
--      现引入两个消耗场景：付费笔记解锁、打赏作者。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) 历史数据修正：停留在 pending / rejected 的评论直接放行
--    新逻辑下这两种状态不再产生，若不清洗，这些评论会因「只查 approved」
--    而对所有用户不可见，等于凭空消失。
-- ----------------------------------------------------------------------------
UPDATE `comment` SET `status` = 'approved' WHERE `status` IN ('pending', 'rejected');

-- 评论状态语义收敛：只保留 approved（正常）/ hidden（举报或敏感词拦截）
ALTER TABLE `comment`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'approved'
    COMMENT '状态：approved=正常，hidden=已隐藏';

-- ----------------------------------------------------------------------------
-- 2) note 增加「解锁所需积分」列（0 = 免费）
-- ----------------------------------------------------------------------------
ALTER TABLE `note`
    ADD COLUMN `price` INT NOT NULL DEFAULT 0 COMMENT '解锁所需积分：0=免费' AFTER `is_top`;

-- ----------------------------------------------------------------------------
-- 3) 笔记购买记录
--    uk_user_note 唯一键保证「同一用户对同一笔记只扣一次积分」，
--    并发重复提交时靠唯一键冲突兜底，无需额外加锁。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_purchase` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '购买者ID',
    `note_id`     BIGINT   NOT NULL COMMENT '笔记ID',
    `author_id`   BIGINT   DEFAULT NULL COMMENT '作者ID（冗余，便于对账）',
    `price`       INT      NOT NULL COMMENT '成交价（积分）',
    `author_gain` INT      NOT NULL DEFAULT 0 COMMENT '作者实得积分（扣除平台抽成后）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
    INDEX `idx_note_id` (`note_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_author_id` (`author_id`),
    CONSTRAINT `fk_purchase_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_purchase_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记购买记录（积分解锁）';

-- ----------------------------------------------------------------------------
-- 4) 打赏记录
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_tip` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT   NOT NULL COMMENT '打赏者ID',
    `note_id`     BIGINT   NOT NULL COMMENT '笔记ID',
    `author_id`   BIGINT   DEFAULT NULL COMMENT '作者ID（冗余，便于对账）',
    `amount`      INT      NOT NULL COMMENT '打赏积分',
    `author_gain` INT      NOT NULL DEFAULT 0 COMMENT '作者实得积分（扣除平台抽成后）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打赏时间',
    PRIMARY KEY (`id`),
    INDEX `idx_note_id` (`note_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_author_id` (`author_id`),
    CONSTRAINT `fk_tip_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_tip_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记打赏记录';

-- ============================================================================
-- 说明：
--   * 平台抽成 10%，即作者实得 = floor(成交额 * 0.9)，抽成部分仅记流水不计入
--     任何用户账户（用 remark 标注），避免污染用户榜单。
--   * 积分扣减统一走 PointsAccountMapper#incrementAtomically，
--     余额用 GREATEST(balance + change, 0) 兜底，不会出现负数。
-- ============================================================================
