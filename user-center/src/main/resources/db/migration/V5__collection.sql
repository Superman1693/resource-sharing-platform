USE yiya;

-- V5: 笔记收藏（单层，无分组）
-- 用户级收藏关系，不走星球多租户（已在 TenantInterceptor.IGNORE_TABLES 忽略）
CREATE TABLE IF NOT EXISTS `note_collection` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` BIGINT NOT NULL COMMENT '收藏者用户ID',
  `note_id` BIGINT NOT NULL COMMENT '被收藏的笔记ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记收藏表';
