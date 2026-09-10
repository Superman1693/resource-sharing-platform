USE yiya;

-- V7: 积分体系（签到 + 积分账户 + 流水）
-- 让 GrowthTimeline 的"假成长"（level 实时算）变为可积累可消费
CREATE TABLE IF NOT EXISTS `points_account` (
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `balance` INT NOT NULL DEFAULT 0 COMMENT '当前积分余额',
  `total_earned` INT NOT NULL DEFAULT 0 COMMENT '累计获得积分',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分账户表';

CREATE TABLE IF NOT EXISTS `points_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `change` INT NOT NULL COMMENT '变动积分（正数）',
  `type` VARCHAR(20) NOT NULL COMMENT '类型：sign/publish/like/comment',
  `ref_type` VARCHAR(20) DEFAULT NULL COMMENT '关联类型：note/comment',
  `ref_id` BIGINT DEFAULT NULL COMMENT '关联ID',
  `remark` VARCHAR(100) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

CREATE TABLE IF NOT EXISTS `sign_in_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `sign_date` DATE NOT NULL COMMENT '签到日期',
  `continuous_days` INT NOT NULL DEFAULT 1 COMMENT '连续签到天数',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';
