-- ============================================================
-- V29: 租户归档与删除生命周期（P3-T5，S-4=方案 A）
--   目标：把"归档 → 30 天保留期 → 双人复核 → 硬删"落成可幂等重放、可续跑、可审计的数据基座。
--   决策锚点：Phase 3 清单 §3 S-4 = 方案 A
--            （Archive 30 天保留期 + 项目负责人临时 token 双人复核 + 审计落 sys_audit_log；
--             文件层物理清理受 P5-T1 MinIO 约束延后，本期只落 DB / Milvus 两处）。
--
--   本迁移做四件事：
--     1) sys_tenant 扩展：archived_at（保留期起点）/ deleted_at（终态时间）/ deletion_request_id（删除幂等键）；
--        status 取值由 (PENDING,PROVISIONING,ACTIVE,SUSPENDED,ARCHIVED,DELETED,FAILED)
--        扩展增补 DELETING（ARCHIVED→DELETING→DELETED 的中间态，reconciler 认领标记）。
--     2) 新表 sys_tenant_delete_approval：一次性删除审批令牌（双人复核载体，SHA-256 存 hash，raw token 不入库）。
--     3) 新表 sys_tenant_deletion_step：删除步骤级审计与幂等载体（镜像 sys_tenant_provision_step）。
--     4) 新表 sys_audit_log：平台端高敏操作的 DB 持久化审计（Phase 4 合规替换 SLF4J 审计的落点）。
--
--   H2/MySQL 双方言：沿用 V27/V28 写法——CREATE TABLE IF NOT EXISTS + 裸 CREATE INDEX（不用 IF NOT EXISTS，
--     MySQL 8 不支持）；不使用 CHECK 约束；不使用外键（与既有租户表一致，硬删顺序由应用层保证）。
--
--   Phase 3 双闸门（saas.enabled=false + platform.enabled=false）下本迁移只做 schema 演进，不改变运行期行为。
-- ============================================================

-- ------------------------------------------------------------
-- 1) sys_tenant 扩展：归档 / 删除生命周期列
-- ------------------------------------------------------------
ALTER TABLE sys_tenant
    ADD COLUMN archived_at DATETIME NULL COMMENT '归档时间（S-4 30 天保留期起点）';
ALTER TABLE sys_tenant
    ADD COLUMN deleted_at DATETIME NULL COMMENT '硬删完成时间（DELETED 终态）';
ALTER TABLE sys_tenant
    ADD COLUMN deletion_request_id VARCHAR(64) NULL COMMENT '本次删除请求 UUID v4（删除幂等键）';

-- reconciler 扫描"已归档且过保留期"的租户：按 (status, archived_at) 走索引
CREATE INDEX idx_tenant_status_archived ON sys_tenant (status, archived_at);
CREATE INDEX idx_tenant_deletion_request ON sys_tenant (deletion_request_id);

-- 说明：status 允许取值增补 DELETING（列类型 VARCHAR(16) 不变，仅记录取值扩展，避免方言分叉改 COMMENT）。

-- ------------------------------------------------------------
-- 2) 新表：sys_tenant_delete_approval（一次性删除审批令牌，双人复核）
--   一行 = 项目负责人为某租户签发的一次删除授权。raw token 仅在签发响应体一次性返回，
--   落库只存 SHA-256(token) hex 64 字符（与 sys_user_invite.invite_code_hash 同模式）。
--   第二操作者持 raw token 确认删除时按 token_hash 精确查，校验 status=PENDING 且未过期，
--   且确认者 != 签发者（approver_operator_id），消费后置 USED + used_at + used_by_operator_id。
--   tenant_id 是"被删除的租户"业务列，进 P1-T7 IGNORE_TABLES 白名单（同 sys_tenant_provision_step）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_delete_approval (
    approval_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL                COMMENT '目标租户 id（业务列，进 IGNORE_TABLES 白名单）',
    approver_operator_id  VARCHAR(64)  NOT NULL                COMMENT '签发者（项目负责人）operator id',
    token_hash            CHAR(64)     NOT NULL                COMMENT 'SHA-256(raw token) hex；raw token 不入库',
    status                VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / USED / EXPIRED / REVOKED',
    expires_at            DATETIME     NOT NULL                COMMENT '过期时间（默认签发 +24h）',
    used_at               DATETIME     NULL                    COMMENT '消费（确认删除）时间',
    used_by_operator_id   VARCHAR(64)  NULL                    COMMENT '确认者（第二操作者）operator id',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_delete_approval_token ON sys_tenant_delete_approval (token_hash);
CREATE INDEX idx_delete_approval_tenant ON sys_tenant_delete_approval (tenant_id, status);

-- ------------------------------------------------------------
-- 3) 新表：sys_tenant_deletion_step（删除步骤级审计与幂等载体，镜像 sys_tenant_provision_step）
--   一行 = 一个租户删除请求中的一个步骤（一次执行尝试）。
--   幂等：同一 (tenant_id, deletion_request_id, step_name, attempt_no) 只允许一行——
--        attempt_no 支持"FAILED 后续跑"，续跑时 attempt_no+1，前次 SUCCESS 的步骤跳过。
--   rows_affected 记录本步实际影响行数（硬删审计友好；向量步记删除向量数，DB 步记删除总行数）。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_deletion_step (
    step_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL COMMENT '租户主键（sys_tenant.tenant_id，无外键）',
    deletion_request_id VARCHAR(64)  NOT NULL COMMENT '删除请求 UUID v4（幂等键）',
    step_name           VARCHAR(64)  NOT NULL COMMENT '步骤名（PURGE_DB_ROWS / PURGE_VECTORS）',
    attempt_no          INT          NOT NULL DEFAULT 1 COMMENT '第几次尝试（续跑时 +1）',
    status              VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / SUCCESS / FAILED / SKIPPED',
    rows_affected       BIGINT       NULL COMMENT '本步影响行数（硬删审计用）',
    error_detail        VARCHAR(2000) NULL COMMENT '失败原因（截断至 2000 字符）',
    started_at          DATETIME     NULL,
    finished_at         DATETIME     NULL,
    create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_deletion_step ON sys_tenant_deletion_step (tenant_id, deletion_request_id, step_name, attempt_no);
CREATE INDEX idx_deletion_request ON sys_tenant_deletion_step (deletion_request_id);
CREATE INDEX idx_deletion_tenant ON sys_tenant_deletion_step (tenant_id);

-- ------------------------------------------------------------
-- 4) 新表：sys_audit_log（平台端高敏操作 DB 持久化审计，S-4=方案 A）
--   一行 = 一次平台端租户生命周期操作（创建/开通/软停/恢复/归档/删除审批/删除）。
--   detail 已在上游做日志注入清理与截断（500 字符）；本表只承载审计检索，不参与业务查询。
--   平台级表，无 tenant_id 过滤维度（tenant_key 是业务列），进 IGNORE_TABLES 白名单。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_audit_log (
    audit_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator    VARCHAR(64)  NULL COMMENT '操作者标识（平台令牌 claim）',
    action      VARCHAR(64)  NOT NULL COMMENT '动作名（TENANT_ARCHIVE / TENANT_DELETE 等）',
    tenant_key  VARCHAR(64)  NULL COMMENT '目标租户业务键',
    outcome     VARCHAR(16)  NOT NULL COMMENT 'SUCCESS / REJECTED / FAILED',
    detail      VARCHAR(500) NULL COMMENT '补充说明（状态迁移、拒绝原因；已脱敏截断）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_tenant_key ON sys_audit_log (tenant_key);
CREATE INDEX idx_audit_action ON sys_audit_log (action);
CREATE INDEX idx_audit_create_time ON sys_audit_log (create_time);
