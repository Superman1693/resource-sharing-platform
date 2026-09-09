USE yiya;
-- 关注关系表
CREATE TABLE IF NOT EXISTS `user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关注者ID',
  `follow_user_id` bigint NOT NULL COMMENT '被关注者ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_user_follow` (`user_id`, `follow_user_id`),
  KEY `idx_follow_user_id` (`follow_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系';

-- 私信会话表
CREATE TABLE IF NOT EXISTS `message_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user1_id` bigint NOT NULL COMMENT '用户1ID',
  `user2_id` bigint NOT NULL COMMENT '用户2ID',
  `last_message_content` varchar(500) DEFAULT NULL COMMENT '最后一条消息内容',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_conversation` (`user1_id`, `user2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话';

-- 私信消息表
CREATE TABLE IF NOT EXISTS `message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `content` text NOT NULL COMMENT '消息内容',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0未读1已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_sender` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息';

-- 学习路径表
CREATE TABLE IF NOT EXISTS `learning_path` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户',
  `title` varchar(200) NOT NULL COMMENT '路径标题',
  `description` varchar(1000) DEFAULT NULL COMMENT '路径描述',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径';

-- 学习路径节点表
CREATE TABLE IF NOT EXISTS `learning_path_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `path_id` bigint NOT NULL COMMENT '所属路径ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID，NULL为顶级',
  `title` varchar(200) NOT NULL COMMENT '节点标题',
  `description` varchar(1000) DEFAULT NULL COMMENT '节点描述',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/in_progress/completed',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_path_id` (`path_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径节点';
