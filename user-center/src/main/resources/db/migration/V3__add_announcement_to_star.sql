
USE yiya;
-- 给 star 表添加 announcement（星球公告）字段
ALTER TABLE `star` ADD COLUMN `announcement` TEXT DEFAULT NULL COMMENT '星球公告' AFTER `description`;
