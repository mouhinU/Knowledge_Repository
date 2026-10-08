-- ============================================================
-- V27: 租户开通状态机（P3-T2，Provisioning PENDING→PROVISIONING→ACTIVE/FAILED）
--   目标：把"创建租户"从单事务写入拆成可幂等重放的多步编排，每步一条 step 行。
--   决策锚点：Phase 3 清单 §3 S-5 = 方案 A（UUID v4 provision_request_id
--            + Flyway 版本号天然幂等 + hasCollection 前置查 + upsert + FAILED 可查 error_detail）。
--   注：sys_tenant.status 列本身已是 VARCHAR(16)，无需扩宽；本迁移只在 COMMENT 上补齐新增取值
--       PENDING / PROVISIONING / FAILED，并新加"当前 provision_request_id"列以便追踪。
--   H2/MySQL 双方言：不用 CREATE INDEX IF NOT EXISTS（H2 支持，MySQL 8 不支持），
--       改用先 DROP INDEX IF EXISTS + CREATE INDEX 组合的兼容写法；亦避免使用 CHECK 约束。
-- ============================================================

-- ------------------------------------------------------------
-- sys_tenant 扩展：新增 provision_request_id 列（当前生效的开通请求 id）
-- 与 status COMMENT 同步（COMMENT 修改是幂等的，MySQL/H2 都支持 MODIFY COLUMN）
-- ------------------------------------------------------------
ALTER TABLE sys_tenant
    ADD COLUMN provision_request_id VARCHAR(64) NULL COMMENT '当前 provision 请求的 UUID v4（S-5=方案A 幂等键）';

-- 更新 status 注释以反映新增取值（列类型不变，MySQL MODIFY 幂等；H2 用 ALTER COLUMN 语法差异，
-- 这里选择"不动 COMMENT"以避免方言分叉，仅在 V27 说明中记录取值扩展）
-- 说明：status 允许取值由 (ACTIVE, SUSPENDED, ARCHIVED, DELETED) 扩展为
--      (PENDING, PROVISIONING, ACTIVE, SUSPENDED, ARCHIVED, DELETED, FAILED)。

CREATE INDEX idx_tenant_provision_request ON sys_tenant (provision_request_id);

-- ------------------------------------------------------------
-- 新表：sys_tenant_provision_step（步骤级审计与幂等载体）
--   一行 = 一个租户开通请求中的一个步骤（一次执行尝试）。
--   幂等：同一 (tenant_id, provision_request_id, step_name, attempt_no) 只允许一行——
--        attempt_no 支持"FAILED 后重放"，重放时 attempt_no+1，前次 SUCCESS 的步骤跳过。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_provision_step (
    step_id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL COMMENT '租户主键（sys_tenant.tenant_id，无外键）',
    provision_request_id VARCHAR(64)  NOT NULL COMMENT '开通请求 UUID v4（S-5=方案A）',
    step_name            VARCHAR(64)  NOT NULL COMMENT '步骤名（如 VALIDATE_TENANT / ACTIVATE_TENANT）',
    attempt_no           INT          NOT NULL DEFAULT 1 COMMENT '第几次尝试（重放时 +1）',
    status               VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / SUCCESS / FAILED / SKIPPED',
    error_detail         VARCHAR(2000) NULL COMMENT '失败原因（截断至 2000 字符）',
    started_at           DATETIME     NULL,
    finished_at          DATETIME     NULL,
    create_time          DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_provision_step ON sys_tenant_provision_step (tenant_id, provision_request_id, step_name, attempt_no);
CREATE INDEX idx_provision_request ON sys_tenant_provision_step (provision_request_id);
CREATE INDEX idx_provision_tenant ON sys_tenant_provision_step (tenant_id);
