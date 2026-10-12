-- ============================================================
-- V33: 套餐限额表（P6-T2）
--   新建 tenant_plan_limit 表，存储每个租户每个指标限额配置。
--   UNIQUE(tenant_id, metric) 保证每租户每指标一行。
--   quota=-1 表示无限额。policy 默认 ALERT。
-- ============================================================

CREATE TABLE tenant_plan_limit (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    metric      VARCHAR(64)  NOT NULL,
    quota       BIGINT       NOT NULL,
    policy      VARCHAR(16)  NOT NULL DEFAULT 'ALERT',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_metric (tenant_id, metric)
);
