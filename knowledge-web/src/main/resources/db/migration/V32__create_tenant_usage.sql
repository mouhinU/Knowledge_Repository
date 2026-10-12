-- ============================================================
-- V32: 租户用量计量表（P6-T1）
--   新建 tenant_usage 表，按月聚合记录每个租户的用量指标。
--   UNIQUE(tenant_id, metric, period) 保证每租户每月每指标一行。
-- ============================================================

CREATE TABLE tenant_usage (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    metric      VARCHAR(64)  NOT NULL,
    period      DATE         NOT NULL,
    usage_value BIGINT       NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_metric_period (tenant_id, metric, period)
);
