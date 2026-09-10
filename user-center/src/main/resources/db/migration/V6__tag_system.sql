USE yiya;

-- V6: 标签体系（标签实体 + 笔记-标签关联）
-- 全局共享，不走多租户（已在 TenantInterceptor.IGNORE_TABLES 忽略）
-- 方案B：note.tags JSON 保留（兼容现有查询），关联表用于标签广场聚合与按标签检索
CREATE TABLE IF NOT EXISTS `tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` VARCHAR(32) NOT NULL COMMENT '标签名（唯一）',
  `usage_count` INT NOT NULL DEFAULT 0 COMMENT '使用次数（被多少笔记使用）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

CREATE TABLE IF NOT EXISTS `note_tag` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `note_id` BIGINT NOT NULL COMMENT '笔记ID',
  `tag_id` BIGINT NOT NULL COMMENT '标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_tag` (`note_id`, `tag_id`),
  INDEX `idx_tag_id` (`tag_id`),
  INDEX `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记-标签关联表';
