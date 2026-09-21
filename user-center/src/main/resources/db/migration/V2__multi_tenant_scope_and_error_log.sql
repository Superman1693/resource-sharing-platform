-- ============================================================================
--  V2 —— 多租户「星球作用域」改造 + 前端错误日志落库
--  ----------------------------------------------------------------------------
--  变更内容：
--    1) user 表新增 current_star_id —— 记录用户「当前选中的星球」，
--       作为签发 JWT 时 starId claim 的来源（用户在多个星球间可切换）；
--    2) comment 表新增 star_id —— 由所属笔记派生，使评论能随星球作用域一起过滤；
--    3) 新增 error_log 表 —— 承载前端错误上报，形成「采集→上报→存储→回溯」闭环；
--    4) 历史数据修正 —— 第三方登录账号的空字符串密码改为明确的占位符。
--
--  背景：原实现里 note / comment / resource 被整体排除在租户过滤之外，
--        且 JWT 未写 starId，导致多租户形同虚设。改造后采用
--        「请求级星球作用域 + 表白名单（note / comment / note_column / knowledge_map）」
--        的方式让隔离真正生效，同时不影响首页、搜索、热榜等跨星球公开读取。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) user.current_star_id
-- ----------------------------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN `current_star_id` BIGINT DEFAULT NULL COMMENT '当前所在星球ID（多租户上下文，为空时回退为最早加入的星球）' AFTER `user_role`;

ALTER TABLE `user`
  ADD INDEX `idx_current_star_id` (`current_star_id`);

-- ----------------------------------------------------------------------------
-- 2) comment.star_id
-- ----------------------------------------------------------------------------
ALTER TABLE `comment`
  ADD COLUMN `star_id` BIGINT DEFAULT NULL COMMENT '所属星球ID（由所属笔记派生，多租户列）' AFTER `note_id`;

ALTER TABLE `comment`
  ADD INDEX `idx_star_id` (`star_id`);

-- 回填历史评论：star_id 取自所属笔记
UPDATE `comment` c
  JOIN `note` n ON c.`note_id` = n.`id`
  SET c.`star_id` = n.`star_id`
  WHERE c.`star_id` IS NULL
    AND n.`star_id` IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 3) error_log（前端错误日志）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `error_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `error_type`     VARCHAR(40)  DEFAULT NULL COMMENT '错误类型：js-error/unhandled-rejection/vue-error/resource-error/manual',
  `message`        VARCHAR(1000) DEFAULT NULL COMMENT '错误消息',
  `stack`          TEXT         DEFAULT NULL COMMENT '错误堆栈',
  `source`         VARCHAR(500) DEFAULT NULL COMMENT '错误来源文件',
  `lineno`         INT          DEFAULT NULL COMMENT '行号',
  `colno`          INT          DEFAULT NULL COMMENT '列号',
  `component_info` VARCHAR(500) DEFAULT NULL COMMENT 'Vue 组件信息',
  `component_name` VARCHAR(200) DEFAULT NULL COMMENT 'Vue 组件名',
  `tag_name`       VARCHAR(100) DEFAULT NULL COMMENT '资源标签名',
  `user_id`        BIGINT       DEFAULT NULL COMMENT '发生错误的用户ID（未登录为空）',
  `url`            VARCHAR(1000) DEFAULT NULL COMMENT '页面URL',
  `user_agent`     VARCHAR(500) DEFAULT NULL COMMENT 'User Agent',
  `client_time`    BIGINT       DEFAULT NULL COMMENT '客户端上报时间戳（毫秒）',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '服务端落库时间',
  PRIMARY KEY (`id`),
  INDEX `idx_error_type` (`error_type`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前端错误日志表';

-- ----------------------------------------------------------------------------
-- 4) 历史数据修正：第三方登录账号的空密码 → 明确的占位符
--    占位符既不是 BCrypt 也不是 MD5，天然无法被密码登录命中；
--    登录时命中该值会提示「请使用 GitHub / QQ 登录」。
-- ----------------------------------------------------------------------------
UPDATE `user`
  SET `user_password` = '!oauth-no-password!'
  WHERE `user_password` = ''
     OR `user_password` IS NULL;

-- ============================================================================
--  至此，多租户作用域所需的表结构已就绪：
--    note.star_id（V1 已有）、comment.star_id（本脚本新增）、
--    note_column.star_id（V1 已有）、knowledge_map.star_id（V1 已有）
-- ============================================================================
