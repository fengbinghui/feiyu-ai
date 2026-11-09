-- =====================================================================
-- Flyway V2: 审计日志增强
-- 1) aigc_log.params 由 varchar(255) 扩为 TEXT，支持更完整的审计参数
-- 2) 为审计日志查询（按时间/类型/用户筛选）补充索引
-- 说明：aigc_log 实际列名以 SHOW COLUMNS 为准（type/username/create_time 均存在）
-- =====================================================================

ALTER TABLE `aigc_log`
    MODIFY COLUMN `params` TEXT NULL COMMENT '操作参数';

CREATE INDEX `idx_aigc_log_create_time` ON `aigc_log` (`create_time`);
CREATE INDEX `idx_aigc_log_type` ON `aigc_log` (`type`);
CREATE INDEX `idx_aigc_log_username` ON `aigc_log` (`username`);
