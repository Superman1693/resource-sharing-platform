-- ============================================================================
-- V5：为「积分对账」报表补时间索引
--
-- 背景：新增管理端对账视图（GET /api/points/reconcile），需要按时间范围扫描
--   points_log 与三张成交表（note_purchase / note_tip / resource_download）。
--   这些查询的过滤条件仅有 create_time，而现有索引均以 user_id / note_id 为前缀
--   （如 points_log 的 idx_user_time = user_id + create_time），无法命中，只能全表扫描。
--
-- 变更：补 4 个单列时间索引。纯索引变更，不动数据、不动列，可安全执行。
-- ============================================================================

ALTER TABLE `points_log`        ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `note_purchase`     ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `note_tip`          ADD INDEX `idx_create_time` (`create_time`);
ALTER TABLE `resource_download` ADD INDEX `idx_create_time` (`create_time`);
