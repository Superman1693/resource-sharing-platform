-- ============================================================================
--  V1 —— 基线结构（baseline）
--  ----------------------------------------------------------------------------
--  由 Flyway 管理。对应「多租户作用域化改造之前」的库结构（25 张表）。
--
--  说明：
--    · 全新数据库：Flyway 会依次执行 V1 → V2 → …，建出完整结构；
--    · 已有数据库：配置了 baseline-on-migrate，Flyway 会把当前库标记为基线版本 1，
--      不会重复执行本文件，只从 V2 开始增量升级；
--    · 已移除 CREATE DATABASE / USE / DROP TABLE 语句 —— 库名由连接串决定，
--      且迁移脚本不应包含破坏性操作；
--    · 人类可读的「全量快照」见仓库根目录 db/schema.sql（等价于 V1 + V2 + …）。
-- ============================================================================


-- ============================================================================
--  1. 初始化
-- ============================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
--  2. 用户体系
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 2.1 用户表 user
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_account`  VARCHAR(32)  NOT NULL COMMENT '登录账号（4-20字符）',
  `user_password` VARCHAR(256) NOT NULL COMMENT '登录密码（BCrypt 加密；兼容旧 MD5，登录时自动升级）',
  `username`      VARCHAR(32)  NOT NULL COMMENT '用户名（网名）',
  `phone`         VARCHAR(20)  DEFAULT NULL COMMENT '手机号（输出时脱敏）',
  `email`         VARCHAR(64)  DEFAULT NULL COMMENT '邮箱（输出时脱敏）',
  `bio`           VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  `skills`        VARCHAR(500) DEFAULT NULL COMMENT '擅长技术栈（逗号分隔）',
  `services`      VARCHAR(500) DEFAULT NULL COMMENT '可提供的服务描述',
  `avatar_url`    VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender`        TINYINT      DEFAULT NULL COMMENT '性别：0=女，1=男，NULL=未知',
  `user_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '用户状态：0=正常，1=封禁，2=注销',
  `user_role`     TINYINT      NOT NULL DEFAULT 0 COMMENT '用户角色：0=普通用户，1=管理员，2=超级管理员',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`user_account`),
  INDEX `idx_user_account` (`user_account`),
  INDEX `idx_user_status` (`user_status`),
  INDEX `idx_user_role` (`user_role`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------------------------------------------------------------------------
-- 2.2 用户关注关系表 user_follow                （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `user_follow` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注关系ID',
  `user_id`        BIGINT   NOT NULL COMMENT '关注者ID',
  `follow_user_id` BIGINT   NOT NULL COMMENT '被关注者ID',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  `is_delete`      TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`),
  INDEX `idx_follow_user_id` (`follow_user_id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_follow_user`      FOREIGN KEY (`user_id`)        REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_following` FOREIGN KEY (`follow_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';

-- ============================================================================
--  3. 星球体系（多租户隔离单元）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 3.1 星球表 star
-- ---------------------------------------------------------------------------
CREATE TABLE `star` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '星球ID',
  `name`         VARCHAR(100) NOT NULL COMMENT '星球名称',
  `description`  TEXT         DEFAULT NULL COMMENT '星球描述',
  `announcement` TEXT         DEFAULT NULL COMMENT '星球公告',
  `cover_image`  VARCHAR(255) DEFAULT NULL COMMENT '封面图片',
  `owner_id`     BIGINT       NOT NULL COMMENT '星球创建者ID（关联 user 表）',
  `member_count` INT          NOT NULL DEFAULT 0 COMMENT '成员数量',
  `content_count` INT         NOT NULL DEFAULT 0 COMMENT '内容数量',
  `price`        INT          NOT NULL DEFAULT 0 COMMENT '加入价格（元），0=免费',
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '状态：active=活跃，inactive=停用',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_owner_id` (`owner_id`),
  INDEX `idx_status` (`status`),
  CONSTRAINT `fk_star_owner` FOREIGN KEY (`owner_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星球表';

-- ---------------------------------------------------------------------------
-- 3.2 星球成员表 star_member
-- ---------------------------------------------------------------------------
CREATE TABLE `star_member` (
  `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '成员关系ID',
  `star_id`   BIGINT      NOT NULL COMMENT '星球ID（关联 star 表）',
  `user_id`   BIGINT      NOT NULL COMMENT '用户ID（关联 user 表）',
  `role`      VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '角色：owner=创建者，admin=管理员，member=普通成员',
  `join_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `is_delete` TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_star_user` (`star_id`, `user_id`),
  INDEX `idx_star_id` (`star_id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_star_member_star` FOREIGN KEY (`star_id`) REFERENCES `star` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_star_member_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='星球成员表';

-- ============================================================================
--  4. 内容体系
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.1 专栏/合集表 note_column                  （星球内专栏，作者串联系列笔记）
-- ---------------------------------------------------------------------------
CREATE TABLE `note_column` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '专栏ID',
  `title`       VARCHAR(100) NOT NULL COMMENT '专栏标题',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '专栏描述',
  `cover_image` VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `author_id`   BIGINT       NOT NULL COMMENT '作者用户ID',
  `star_id`     BIGINT       NOT NULL COMMENT '所属星球ID',
  `note_count`  INT          NOT NULL DEFAULT 0 COMMENT '专栏内笔记数量',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active=连载中，archived=已完结',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_author_id` (`author_id`),
  INDEX `idx_star_id` (`star_id`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记专栏（星球内合集）';

-- ---------------------------------------------------------------------------
-- 4.2 内容/笔记表 note
-- ---------------------------------------------------------------------------
CREATE TABLE `note` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `title`           VARCHAR(200) NOT NULL COMMENT '标题',
  `content_type`    VARCHAR(20)  NOT NULL COMMENT '内容类型：article=文章，question=问题，note=笔记',
  `category`        VARCHAR(50)  DEFAULT NULL COMMENT '分类：frontend/backend/algorithm/database/other',
  `content`         TEXT         NOT NULL COMMENT '内容（Markdown 格式）',
  `summary`         VARCHAR(500) DEFAULT NULL COMMENT '摘要',
  `author`          VARCHAR(32)  DEFAULT NULL COMMENT '作者名称（冗余字段，便于查询）',
  `author_id`       BIGINT       NOT NULL COMMENT '作者ID（关联 user 表）',
  `view_count`      INT          NOT NULL DEFAULT 0 COMMENT '浏览量（Redis 计数 + 定时刷盘）',
  `comment_count`   INT          NOT NULL DEFAULT 0 COMMENT '评论数',
  `like_count`      INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'draft' COMMENT '状态：published=已发布，draft=草稿，deleted=已删除',
  `publish_time`    DATETIME     DEFAULT NULL COMMENT '发布时间',
  `cover_image`     VARCHAR(255) DEFAULT NULL COMMENT '封面图片URL',
  `tags`            JSON         DEFAULT NULL COMMENT '标签列表（JSON数组，兼容保留）',
  `star_id`         BIGINT       DEFAULT NULL COMMENT '所属星球ID（关联 star 表，多租户列）',
  `collection_id`   BIGINT       DEFAULT NULL COMMENT '所属专栏ID（关联 note_column 表）',
  `collection_sort` INT          NOT NULL DEFAULT 0 COMMENT '专栏内章节序号',
  `is_top`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：0=否，1=是（最多 10 条）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
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
  INDEX `idx_collection_id` (`collection_id`),
  FULLTEXT INDEX `idx_title_content` (`title`, `content`),
  CONSTRAINT `fk_note_author` FOREIGN KEY (`author_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容/笔记表';

-- ---------------------------------------------------------------------------
-- 4.3 评论表 comment
-- ---------------------------------------------------------------------------
CREATE TABLE `comment` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `note_id`     BIGINT       NOT NULL COMMENT '笔记/内容ID（关联 note 表）',
  `note_title`  VARCHAR(200) DEFAULT NULL COMMENT '笔记标题（冗余字段，便于查询）',
  `user_id`     BIGINT       NOT NULL COMMENT '评论用户ID（关联 user 表）',
  `username`    VARCHAR(32)  DEFAULT NULL COMMENT '用户名（冗余字段）',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '用户头像（冗余字段）',
  `content`     TEXT         NOT NULL COMMENT '评论内容',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending=待审核，approved=已通过，rejected=已拒绝，hidden=已隐藏',
  `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父评论ID（回复用，NULL=一级评论）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_note_id` (`note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_parent_id` (`parent_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_note_status` (`note_id`, `status`),
  CONSTRAINT `fk_comment_note`   FOREIGN KEY (`note_id`)   REFERENCES `note` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_comment_user`   FOREIGN KEY (`user_id`)   REFERENCES `user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- ---------------------------------------------------------------------------
-- 4.4 标签表 tag                               （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `tag` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name`        VARCHAR(32) NOT NULL COMMENT '标签名（唯一）',
  `usage_count` INT         NOT NULL DEFAULT 0 COMMENT '使用次数（被多少笔记使用）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ---------------------------------------------------------------------------
-- 4.5 笔记-标签关联表 note_tag                  （note.tags JSON 保留兼容，关联表用于标签广场聚合）
-- ---------------------------------------------------------------------------
CREATE TABLE `note_tag` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `note_id`     BIGINT   NOT NULL COMMENT '笔记ID',
  `tag_id`      BIGINT   NOT NULL COMMENT '标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_tag` (`note_id`, `tag_id`),
  INDEX `idx_tag_id` (`tag_id`),
  INDEX `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记-标签关联表';

-- ============================================================================
--  5. 资源体系
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 5.1 资源表 resource
-- ---------------------------------------------------------------------------
CREATE TABLE `resource` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资源ID',
  `uploader_id`    BIGINT       DEFAULT NULL COMMENT '上传者ID（用于"我的资源"归属查询）',
  `name`           VARCHAR(200) NOT NULL COMMENT '资源名称',
  `tag`            VARCHAR(50)  DEFAULT NULL COMMENT '资源类型：pdf=PDF文档，video=视频教程，code=代码示例，tool=工具软件',
  `file_url`       VARCHAR(500) DEFAULT NULL COMMENT '文件URL（阿里云 OSS）',
  `file_size`      BIGINT       DEFAULT NULL COMMENT '文件大小（字节）',
  `download_count` INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled=启用，disabled=停用',
  `description`    TEXT         DEFAULT NULL COMMENT '资源描述',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_uploader_id` (`uploader_id`),
  INDEX `idx_tag` (`tag`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`),
  FULLTEXT INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源表';

-- ============================================================================
--  6. 互动体系
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 6.1 点赞记录表 like_record                     （唯一索引防止重复点赞）
-- ---------------------------------------------------------------------------
CREATE TABLE `like_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
  `user_id`     BIGINT      NOT NULL COMMENT '用户ID（关联 user 表）',
  `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型：note=笔记，comment=评论',
  `target_id`   BIGINT      NOT NULL COMMENT '目标ID（笔记ID或评论ID）',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_user_id` (`user_id`),
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞记录表';

-- ---------------------------------------------------------------------------
-- 6.2 浏览记录表 view_record
-- ---------------------------------------------------------------------------
CREATE TABLE `view_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '浏览记录ID',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '用户ID（NULL=匿名用户）',
  `note_id`     BIGINT       NOT NULL COMMENT '笔记ID（关联 note 表）',
  `ip_address`  VARCHAR(50)  DEFAULT NULL COMMENT 'IP地址',
  `user_agent`  VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_note_id` (`note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_user_note` (`user_id`, `note_id`),
  CONSTRAINT `fk_view_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_view_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览记录表';

-- ---------------------------------------------------------------------------
-- 6.3 笔记收藏表 note_collection                （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `note_collection` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`     BIGINT   NOT NULL COMMENT '收藏者用户ID',
  `note_id`     BIGINT   NOT NULL COMMENT '被收藏的笔记ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `is_delete`   TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_note` (`user_id`, `note_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记收藏表';

-- ---------------------------------------------------------------------------
-- 6.4 举报记录表 report                          （笔记/评论/用户举报统一落库）
-- ---------------------------------------------------------------------------
CREATE TABLE `report` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '举报ID',
  `reporter_id`   BIGINT       NOT NULL COMMENT '举报人ID',
  `target_type`   VARCHAR(20)  NOT NULL COMMENT '目标类型：note/comment/user',
  `target_id`     BIGINT       NOT NULL COMMENT '目标ID',
  `reason`        VARCHAR(500) DEFAULT NULL COMMENT '举报理由',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/resolved/ignored',
  `handler_id`    BIGINT       DEFAULT NULL COMMENT '处理人ID',
  `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  `handle_time`   DATETIME     DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_reporter` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报记录表';

-- ============================================================================
--  7. 私信与通知
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 7.1 私信会话表 message_conversation            （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `message_conversation` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user1_id`             BIGINT       NOT NULL COMMENT '用户1ID',
  `user2_id`             BIGINT       NOT NULL COMMENT '用户2ID',
  `last_message_content` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息内容',
  `last_message_time`    DATETIME     DEFAULT NULL COMMENT '最后消息时间',
  `create_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation` (`user1_id`, `user2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- ---------------------------------------------------------------------------
-- 7.2 私信消息表 message                         （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `message` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` BIGINT   NOT NULL COMMENT '会话ID',
  `sender_id`       BIGINT   NOT NULL COMMENT '发送者ID',
  `content`         TEXT     NOT NULL COMMENT '消息内容',
  `is_read`         TINYINT  NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_delete`       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_conversation` (`conversation_id`),
  INDEX `idx_sender` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';

-- ---------------------------------------------------------------------------
-- 7.3 站内通知表 notification                    （全局表，不做多租户隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE `notification` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `receiver_id`   BIGINT       NOT NULL COMMENT '接收者用户ID',
  `sender_id`     BIGINT       DEFAULT NULL COMMENT '发送者用户ID（系统通知为NULL）',
  `sender_name`   VARCHAR(32)  DEFAULT NULL COMMENT '发送者用户名（冗余）',
  `sender_avatar` VARCHAR(255) DEFAULT NULL COMMENT '发送者头像（冗余）',
  `type`          VARCHAR(30)  NOT NULL COMMENT '通知类型：like_note/comment_note/reply_comment/mention/join_star/system',
  `target_id`     BIGINT       DEFAULT NULL COMMENT '关联目标ID',
  `target_title`  VARCHAR(200) DEFAULT NULL COMMENT '关联目标标题（冗余）',
  `content`       VARCHAR(500) NOT NULL COMMENT '通知内容',
  `is_read`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0=未读，1=已读',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_delete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_receiver_id` (`receiver_id`),
  INDEX `idx_is_read` (`receiver_id`, `is_read`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';

-- ============================================================================
--  8. 知识图谱
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 8.1 知识图谱表 knowledge_map
-- ---------------------------------------------------------------------------
CREATE TABLE `knowledge_map` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识图谱ID',
  `star_id`     BIGINT       NOT NULL COMMENT '所属星球ID（关联 star 表）',
  `name`        VARCHAR(100) NOT NULL COMMENT '知识图谱名称',
  `description` TEXT         DEFAULT NULL COMMENT '描述',
  `config`      JSON         DEFAULT NULL COMMENT '图谱配置（节点、连线等 JSON 数据）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_star_id` (`star_id`),
  CONSTRAINT `fk_knowledge_map_star` FOREIGN KEY (`star_id`) REFERENCES `star` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱表';

-- ---------------------------------------------------------------------------
-- 8.2 知识节点表 knowledge_node
-- ---------------------------------------------------------------------------
CREATE TABLE `knowledge_node` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `map_id`      BIGINT        NOT NULL COMMENT '知识图谱ID（关联 knowledge_map 表）',
  `node_id`     VARCHAR(50)   NOT NULL COMMENT '节点标识（图谱内唯一ID）',
  `title`       VARCHAR(200)  DEFAULT NULL COMMENT '节点标题',
  `content`     TEXT          DEFAULT NULL COMMENT '节点内容',
  `position_x`  DECIMAL(10,2) DEFAULT NULL COMMENT '节点X坐标',
  `position_y`  DECIMAL(10,2) DEFAULT NULL COMMENT '节点Y坐标',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_map_id` (`map_id`),
  INDEX `idx_node_id` (`node_id`),
  CONSTRAINT `fk_knowledge_node_map` FOREIGN KEY (`map_id`) REFERENCES `knowledge_map` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识节点表';

-- ============================================================================
--  9. 学习路径
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 9.1 学习路径表 learning_path
-- ---------------------------------------------------------------------------
CREATE TABLE `learning_path` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '路径ID',
  `user_id`     BIGINT        NOT NULL COMMENT '所属用户',
  `title`       VARCHAR(200)  NOT NULL COMMENT '路径标题',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '路径描述',
  `icon`        VARCHAR(100)  DEFAULT NULL COMMENT '图标',
  `sort_order`  INT           DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径表';

-- ---------------------------------------------------------------------------
-- 9.2 学习路径节点表 learning_path_node
-- ---------------------------------------------------------------------------
CREATE TABLE `learning_path_node` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `path_id`     BIGINT        NOT NULL COMMENT '所属路径ID',
  `parent_id`   BIGINT        DEFAULT NULL COMMENT '父节点ID，NULL为顶级',
  `title`       VARCHAR(200)  NOT NULL COMMENT '节点标题',
  `description` VARCHAR(1000) DEFAULT NULL COMMENT '节点描述',
  `status`      VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT '状态：pending/in_progress/completed',
  `sort_order`  INT           DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete`   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_path_id` (`path_id`),
  INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习路径节点表';

-- ============================================================================
--  10. 积分体系
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 10.1 积分账户表 points_account
-- ---------------------------------------------------------------------------
CREATE TABLE `points_account` (
  `user_id`      BIGINT   NOT NULL COMMENT '用户ID',
  `balance`      INT      NOT NULL DEFAULT 0 COMMENT '当前积分余额',
  `total_earned` INT      NOT NULL DEFAULT 0 COMMENT '累计获得积分',
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分账户表';

-- ---------------------------------------------------------------------------
-- 10.2 积分流水表 points_log
-- ---------------------------------------------------------------------------
CREATE TABLE `points_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `change`      INT          NOT NULL COMMENT '变动积分（正数）',
  `type`        VARCHAR(20)  NOT NULL COMMENT '类型：sign/publish/like/comment',
  `ref_type`    VARCHAR(20)  DEFAULT NULL COMMENT '关联类型：note/comment',
  `ref_id`      BIGINT       DEFAULT NULL COMMENT '关联ID',
  `remark`      VARCHAR(100) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

-- ---------------------------------------------------------------------------
-- 10.3 签到记录表 sign_in_record
-- ---------------------------------------------------------------------------
CREATE TABLE `sign_in_record` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id`         BIGINT   NOT NULL COMMENT '用户ID',
  `sign_date`       DATE     NOT NULL COMMENT '签到日期',
  `continuous_days` INT      NOT NULL DEFAULT 1 COMMENT '连续签到天数',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';

-- ============================================================================
--  11. 后台管理
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 11.1 管理员操作日志表 admin_log
-- ---------------------------------------------------------------------------
CREATE TABLE `admin_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id` BIGINT       NOT NULL COMMENT '操作人ID',
  `target_id`   BIGINT       DEFAULT NULL COMMENT '操作目标ID',
  `action`      VARCHAR(50)  NOT NULL COMMENT '操作类型：ban/unban/approve/reject',
  `target_type` VARCHAR(20)  NOT NULL COMMENT '目标类型：user/note/comment',
  `remark`      VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  INDEX `idx_operator_id` (`operator_id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================================================================
--  12. 初始化数据
-- ============================================================================
SET FOREIGN_KEY_CHECKS = 1;

-- 默认管理员账号（密码 123456 的 MD5 值；首次登录时后端会自动升级为 BCrypt）
-- ⚠️ 生产环境请务必登录后立即修改密码
INSERT INTO `user` (`user_account`, `user_password`, `username`, `user_role`, `user_status`, `star_id`)
VALUES
  ('admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 1, 0, NULL),
  ('test',  'e10adc3949ba59abbe56e057f20f883e', '测试用户',   0, 0, NULL);

-- ============================================================================
