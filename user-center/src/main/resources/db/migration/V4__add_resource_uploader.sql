USE yiya;
-- 给 resource 表添加 uploader_id（上传者ID）字段，用于"我的资源"归属查询与统计
ALTER TABLE `resource` ADD COLUMN `uploader_id` BIGINT DEFAULT NULL COMMENT '上传者ID' AFTER `id`;
ALTER TABLE `resource` ADD INDEX `idx_uploader_id` (`uploader_id`);
