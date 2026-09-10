USE yiya;

-- V8: 举报记录表（笔记/评论/用户举报统一落库）
CREATE TABLE IF NOT EXISTS `report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id` BIGINT NOT NULL COMMENT '举报人ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：note/comment/user',
  `target_id` BIGINT NOT NULL COMMENT '目标ID',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '举报理由',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/resolved/ignored',
  `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID',
  `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_reporter` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报记录表';
