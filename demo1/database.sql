-- ============================================
-- 知识星球系统数据库设计
-- 基于前端代码分析生成
-- 创建时间: 2025-01-XX
-- ============================================

-- 设置字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 1. 用户表 (user)
-- ============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_account` VARCHAR(32) NOT NULL UNIQUE COMMENT '登录账号（4-20字符）',
  `user_password` VARCHAR(256) NOT NULL COMMENT '登录密码（加密存储）',
  `username` VARCHAR(32) NOT NULL COMMENT '用户名（网名）',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
  `bio` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  `skills` VARCHAR(500) DEFAULT NULL COMMENT '擅长技术栈（逗号分隔）',
  `services` VARCHAR(500) DEFAULT NULL COMMENT '可提供的服务描述',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender` TINYINT DEFAULT NULL COMMENT '性别：0=女，1=男，NULL=未知',
  `user_status` TINYINT NOT NULL DEFAULT 0 COMMENT '用户状态：0=正常，1=封禁，2=注销',
  `user_role` TINYINT NOT NULL DEFAULT 0 COMMENT '用户角色：0=普通用户，1=管理员，2=超级管理员',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_user_account` (`user_account`),
  INDEX `idx_user_status` (`user_status`),
  INDEX `idx_user_role` (`user_role`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 2. 内容/笔记表 (note)
-- ============================================
DROP TABLE IF EXISTS `note`;
CREATE TABLE `note` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content_type` VARCHAR(20) NOT NULL COMMENT '内容类型：article=文章，question=问题，note=笔记',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '分类：frontend/backend/algorithm/database/other',
  `content` TEXT NOT NULL COMMENT '内容（Markdown格式）',
  `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  `author` VARCHAR(32) DEFAULT NULL COMMENT '作者名称（冗余字段，便于查询）',
  `author_id` BIGINT NOT NULL COMMENT '作者ID（关联user表）',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
  `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：published=已发布，draft=草稿，deleted=已删除',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片URL',
  `tags` JSON DEFAULT NULL COMMENT '标签列表（JSON数组）',
  `star_id` BIGINT DEFAULT NULL COMMENT '所属星球ID（关联star表，可选）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0=否，1=是',
  PRIMARY KEY (`id`),
  INDEX `idx_author_id` (`author_id`),
  INDEX `idx_content_type` (`content_type`),
  INDEX `idx_category` (`category`),
  INDEX `idx_status` (`status`),
  INDEX `idx_star_id` (`star_id`),
  INDEX `idx_publish_time` (`publish_time`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_status_publish_time` (`status`, `publish_time`),
  INDEX `idx_is_top` (`is_top`),
  FULLTEXT INDEX `idx_title_content` (`title`, `content`),
  CONSTRAINT `fk_note_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容/笔记表';

-- ============================================
-- 3. 评论表 (comment)
-- ============================================
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `note_id` BIGINT NOT NULL COMMENT '笔记/内容ID（关联note表）',
  `note_title` VARCHAR(200) DEFAULT NULL COMMENT '笔记标题（冗余字段，便于查询）',
  `user_id` BIGINT NOT NULL COMMENT '评论用户ID（关联user表）',
  `username` VARCHAR(32) DEFAULT NULL COMMENT '用户名（冗余字段）',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '用户头像（冗余字段）',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：approved=已审核，pending=待审核，hidden=已屏蔽',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（用于回复，NULL表示顶级评论）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_note_id` (`note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_note_status` (`note_id`, `status`),
  CONSTRAINT `fk_comment_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ============================================
-- 4. 资源表 (resource)
-- ============================================
DROP TABLE IF EXISTS `resource`;
CREATE TABLE `resource` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  `uploader_id` BIGINT DEFAULT NULL COMMENT '上传者ID',
  `name` VARCHAR(200) NOT NULL COMMENT '资源名称',
  `tag` VARCHAR(50) DEFAULT NULL COMMENT '资源类型：pdf=PDF文档，video=视频教程，code=代码示例，tool=工具软件',
  `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件URL或路径',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  `download_count` INT NOT NULL DEFAULT 0 COMMENT '下载次数',
  `status` VARCHAR(20) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled=启用，disabled=停用',
  `description` TEXT DEFAULT NULL COMMENT '资源描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_tag` (`tag`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`),
  FULLTEXT INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表';
-- ============================================
-- 8. 星球表 (star)
-- ============================================
DROP TABLE IF EXISTS `star`;
CREATE TABLE `star` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '星球ID',
  `name` VARCHAR(100) NOT NULL COMMENT '星球名称',
  `description` TEXT DEFAULT NULL COMMENT '星球描述',
  `announcement` TEXT DEFAULT NULL COMMENT '星球公告',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图片',
  `owner_id` BIGINT NOT NULL COMMENT '星球创建者ID（关联user表）',
  `member_count` INT NOT NULL DEFAULT 0 COMMENT '成员数量',
  `content_count` INT NOT NULL DEFAULT 0 COMMENT '内容数量',
  `price` INT NOT NULL DEFAULT 0 COMMENT '加入价格（元），0=免费',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active=活跃，inactive=停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_owner_id` (`owner_id`),
  INDEX `idx_status` (`status`),
  CONSTRAINT `fk_star_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星球表';

-- ============================================
-- 9. 星球成员表 (star_member)
-- ============================================
DROP TABLE IF EXISTS `star_member`;
CREATE TABLE `star_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员关系ID',
  `star_id` BIGINT NOT NULL COMMENT '星球ID（关联star表）',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（关联user表）',
  `role` VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '角色：owner=创建者，admin=管理员，member=普通成员',
  `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_star_user` (`star_id`, `user_id`),
  INDEX `idx_star_id` (`star_id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_star_member_star` FOREIGN KEY (`star_id`) REFERENCES `star` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_star_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星球成员表';

-- ============================================
-- 10. 知识图谱表 (knowledge_map)
-- ============================================
DROP TABLE IF EXISTS `knowledge_map`;
CREATE TABLE `knowledge_map` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '知识图谱ID',
  `star_id` BIGINT NOT NULL COMMENT '所属星球ID（关联star表）',
  `name` VARCHAR(100) NOT NULL COMMENT '知识图谱名称',
  `description` TEXT DEFAULT NULL COMMENT '描述',
  `config` JSON DEFAULT NULL COMMENT '图谱配置（节点、连线等JSON数据）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_star_id` (`star_id`),
  CONSTRAINT `fk_knowledge_map_star` FOREIGN KEY (`star_id`) REFERENCES `star` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱表';

-- ============================================
-- 11. 知识节点表 (knowledge_node)
-- ============================================
DROP TABLE IF EXISTS `knowledge_node`;
CREATE TABLE `knowledge_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `map_id` BIGINT NOT NULL COMMENT '知识图谱ID（关联knowledge_map表）',
  `node_id` VARCHAR(50) NOT NULL COMMENT '节点标识（在图谱中的唯一ID）',
  `title` VARCHAR(200) DEFAULT NULL COMMENT '节点标题',
  `content` TEXT DEFAULT NULL COMMENT '节点内容',
  `position_x` DECIMAL(10, 2) DEFAULT NULL COMMENT '节点X坐标',
  `position_y` DECIMAL(10, 2) DEFAULT NULL COMMENT '节点Y坐标',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_map_id` (`map_id`),
  INDEX `idx_node_id` (`node_id`),
  CONSTRAINT `fk_knowledge_node_map` FOREIGN KEY (`map_id`) REFERENCES `knowledge_map` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识节点表';

-- ============================================
-- 12. 点赞记录表 (like_record)
-- ============================================
DROP TABLE IF EXISTS `like_record`;
CREATE TABLE `like_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID（关联user表）',
  `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：note=笔记，comment=评论',
  `target_id` BIGINT NOT NULL COMMENT '目标ID（笔记ID或评论ID）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- ============================================
-- 13. 浏览记录表 (view_record)
-- ============================================
DROP TABLE IF EXISTS `view_record`;
CREATE TABLE `view_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '浏览记录ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID（关联user表，NULL表示匿名用户）',
  `note_id` BIGINT NOT NULL COMMENT '笔记ID（关联note表）',
  `ip_address` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_note_id` (`note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`),
  CONSTRAINT `fk_view_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_view_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览记录表';

-- ============================================
-- 初始化数据
-- ============================================

-- 插入默认管理员账号（密码需要后端加密后存储）
-- 示例：密码为 "admin123456"，实际使用时需要MD5/SHA256等加密
INSERT INTO `user` (`user_account`, `user_password`, `username`, `user_role`, `user_status`) 
VALUES ('admin', '请使用后端加密后的密码', '系统管理员', 1, 0);

SET FOREIGN_KEY_CHECKS = 1;


-- ============================================
-- 管理员操作日志表 (admin_log) - 需求13
-- ============================================
DROP TABLE IF EXISTS `admin_log`;
CREATE TABLE `admin_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
  `target_id` BIGINT DEFAULT NULL COMMENT '操作目标ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型：ban/unban/approve/reject',
  `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：user/note/comment',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================================
-- 站内通知表 (notification)
-- ============================================
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
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
