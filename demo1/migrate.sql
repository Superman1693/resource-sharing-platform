-- ============================================
-- 增量迁移脚本（对已有数据库执行）
-- ============================================

-- 1. note 表：添加 is_top 字段
ALTER TABLE `note`
  ADD COLUMN IF NOT EXISTS `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0=否，1=是';

-- 2. note 表：添加复合索引（优化列表查询）
ALTER TABLE `note`
  ADD INDEX IF NOT EXISTS `idx_status_publish_time` (`status`, `publish_time`),
  ADD INDEX IF NOT EXISTS `idx_is_top` (`is_top`);

-- 3. comment 表：添加复合索引（优化评论列表查询）
ALTER TABLE `comment`
  ADD INDEX IF NOT EXISTS `idx_note_status` (`note_id`, `status`);

-- 4. view_record 表：添加复合索引
ALTER TABLE `view_record`
  ADD INDEX IF NOT EXISTS `idx_user_note` (`user_id`, `note_id`);

-- 5. 创建 admin_log 表（需求13：管理员操作日志）
CREATE TABLE IF NOT EXISTS `admin_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operator_id` BIGINT NOT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `action` VARCHAR(50) NOT NULL,
  `target_type` VARCHAR(20) NOT NULL,
  `remark` VARCHAR(200) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 创建 like_record 表（用于防止重复点赞）
CREATE TABLE IF NOT EXISTS `like_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：note=笔记，comment=评论',
  `target_id` BIGINT NOT NULL COMMENT '目标ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- 7. resource 表：添加 uploader_id 字段（用于"我的资源"归属查询与统计）
-- MySQL 8.0 的 ALTER TABLE 不支持 IF NOT EXISTS，这里不重复执行即可保持幂等
ALTER TABLE `resource`
  ADD COLUMN `uploader_id` BIGINT DEFAULT NULL COMMENT '上传者ID' AFTER `id`;
ALTER TABLE `resource`
  ADD INDEX `idx_uploader_id` (`uploader_id`);

-- ============================================
-- 新增：通知表（站内消息通知）
-- 如果已执行过请跳过此段
-- ============================================
CREATE TABLE IF NOT EXISTS `notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `receiver_id` BIGINT NOT NULL COMMENT '接收者用户ID',
  `sender_id` BIGINT DEFAULT NULL COMMENT '发送者用户ID（系统通知为NULL）',
  `sender_name` VARCHAR(32) DEFAULT NULL COMMENT '发送者用户名（冗余）',
  `sender_avatar` VARCHAR(255) DEFAULT NULL COMMENT '发送者头像（冗余）',
  `type` VARCHAR(30) NOT NULL COMMENT '通知类型：like_note/comment_note/reply_comment/join_star/system',
  `target_id` BIGINT DEFAULT NULL COMMENT '关联目标ID',
  `target_title` VARCHAR(200) DEFAULT NULL COMMENT '关联目标标题（冗余）',
  `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  INDEX `idx_receiver_id` (`receiver_id`),
  INDEX `idx_is_read` (`receiver_id`, `is_read`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';
