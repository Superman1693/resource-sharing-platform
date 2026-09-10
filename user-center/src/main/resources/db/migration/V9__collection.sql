USE yiya;

-- V9 专栏/合集（星球内专栏）：作者把系列笔记串成专栏，笔记详情页展示"所属专栏"
-- note 表已自带 status/publish_time/is_top，本脚本只加专栏归属字段

-- 专栏表（星球内归属，作者在某星球内串联自己的系列笔记）
CREATE TABLE IF NOT EXISTS note_column (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '专栏ID',
  title       VARCHAR(100) NOT NULL COMMENT '专栏标题',
  description VARCHAR(500)          DEFAULT NULL COMMENT '专栏描述',
  cover_image VARCHAR(255)          DEFAULT NULL COMMENT '封面图',
  author_id   BIGINT       NOT NULL COMMENT '作者用户ID',
  star_id     BIGINT       NOT NULL COMMENT '所属星球ID',
  note_count  INT          NOT NULL DEFAULT 0 COMMENT '专栏内笔记数量',
  status      VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active-连载中 archived-已完结',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_delete   TINYINT      NOT NULL DEFAULT 0 COMMENT '0-未删 1-已删',
  PRIMARY KEY (id),
  KEY idx_author_id (author_id),
  KEY idx_star_id (star_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记专栏（星球内合集）';

-- note 表加专栏归属字段（单归属 + 章节序号）
ALTER TABLE note ADD COLUMN collection_id BIGINT DEFAULT NULL COMMENT '所属专栏ID' AFTER star_id;
ALTER TABLE note ADD COLUMN collection_sort INT NOT NULL DEFAULT 0 COMMENT '专栏内章节序号' AFTER collection_id;
ALTER TABLE note ADD KEY idx_collection_id (collection_id);
