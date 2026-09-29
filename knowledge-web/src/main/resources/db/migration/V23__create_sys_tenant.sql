-- ============================================================
-- V23: SaaS 多租户平台元数据表（P1-T1，租户脊柱地基）
--   新增平台级 sys_tenant（租户主表）+ sys_tenant_placement（放置策略 POOL/SILO）。
--   两张表属"平台级"元数据，本身不带 tenant_id 列（后续 P1-T7 拦截器 ignore 名单登记）。
--   种子：插入 tenant_key='default' 的默认租户 + 其 POOL 放置，保证 saas.enabled=false
--   时单租户语义可平滑回退（运行时只有 default 租户）。
--   命名对齐既有库表约定（create_time / update_time），非提案示意中的 created_at。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_tenant (
    tenant_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_key  VARCHAR(64)  NOT NULL COMMENT '租户业务标识（子域名/存储前缀用），全局唯一',
    name        VARCHAR(128) NOT NULL COMMENT '租户显示名',
    plan        VARCHAR(32)  NOT NULL DEFAULT 'standard' COMMENT '套餐标识（配额计费 Phase 6 使用）',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUSPENDED/ARCHIVED/DELETED',
    data_region VARCHAR(32)  NOT NULL DEFAULT 'cn' COMMENT '数据驻留区域（合规预留）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_key (tenant_key)
);

CREATE TABLE IF NOT EXISTS sys_tenant_placement (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL COMMENT '租户主键（sys_tenant.tenant_id，无外键）',
    placement_type VARCHAR(8)   NOT NULL DEFAULT 'POOL' COMMENT 'POOL=共享库 / SILO=独立库',
    silo_ref       VARCHAR(128) NULL COMMENT 'SILO 独立库/collection 定位串；POOL 为 NULL',
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_placement_tenant ON sys_tenant_placement(tenant_id);
CREATE INDEX idx_placement_type ON sys_tenant_placement(placement_type);

-- 种子：默认租户（单租户语义地基），placement 恒为 POOL
INSERT INTO sys_tenant (tenant_key, name, plan, status, data_region)
VALUES ('default', '默认租户', 'standard', 'ACTIVE', 'cn');

INSERT INTO sys_tenant_placement (tenant_id, placement_type, silo_ref)
SELECT tenant_id, 'POOL', NULL FROM sys_tenant WHERE tenant_key = 'default';
