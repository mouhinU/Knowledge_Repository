-- ============================================================
-- V28: sys_config 租户化（提前 P4-T1，C-4=方案 A） + 邀请表 + sys_user 首登强制改密标记
--   为 P3-T3 "建库默认配置与根部门 + 首位 admin" 提供数据基座：
--
--   1) sys_config：加 tenant_id NOT NULL DEFAULT 1，唯一键从 (config_key) 改为 (tenant_id, config_key)。
--      - 存量 6 行 V21 种子 + 运行期新增行由 DEFAULT 1 全部落 default 租户，与既有单租户读路径等价。
--      - 从此同一 config_key 可在不同租户各存一行；SystemConfigGatewayImpl 由 P3-T3 起显式按
--        TenantContext 追加 WHERE tenant_id=?，KnowledgeTenantLineHandler 白名单同步移除 sys_config。
--      - 删旧唯一键用 ALTER TABLE ... DROP INDEX uk_config_key（非独立式 DROP INDEX ... ON）：
--        uk_config_key 是 V21 建表时内联 UNIQUE KEY，H2 MODE=MySQL 把它建成"约束名 uk_config_key +
--        索引名 uk_config_key_INDEX_C"，独立式 DROP INDEX 按字面索引名找不到；ALTER TABLE DROP INDEX
--        在 H2 按约束名解析、MySQL 8 按索引名解析，双方言均命中（已用 H2 2.2.224 实测）。
--
--   2) sys_user_invite：一次性设密邀请表（P3-T3 邀请式引导，避免响应体回显长期令牌）。
--      - invite_code_hash 存 SHA-256(raw code) hex 64 字符，raw code 仅在 provision 响应体一次性返回，
--        不落库、不落日志（日志只落 invite_id + hash 前 8 位）。即便库被拖也无法逆推可用 code。
--      - UNIQUE(invite_code_hash) 支持 O(1) 校验查找；INDEX(tenant_id, user_id) 支持"某用户当前有效邀请"查询。
--      - tenant_id 是"被邀请加入的租户"业务列，与 sys_tenant_provision_step 同性质；进 P1-T7 IGNORE_TABLES 白名单，
--        避免自引用悖论（V28 同批 KnowledgeTenantLineHandler 更新）。
--
--   3) sys_user.must_change_password：TINYINT(1) NOT NULL DEFAULT 0
--      - 邀请式设密完成后由 SetPasswordByInviteCmdExe 置 0；未来"平台运维人工重置密码"路径亦置 1。
--      - 存量用户（含 V15 种子的 admin）默认 0，行为无变化。
--
--   Phase 3 双闸门（saas.enabled=false + platform.enabled=false）下本迁移只做 schema 演进，不改变运行期
--   行为——所有读路径仍命 tenant_id=1 的存量行。真正启用由 P3-T1~T4 交付后运维书面签署再拨闸。
-- ============================================================

-- ---------- 1) sys_config 租户化（P4-T1 提前） ----------
ALTER TABLE sys_config ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_config DROP INDEX uk_config_key;
CREATE UNIQUE INDEX uk_config_tenant_key ON sys_config (tenant_id, config_key);
CREATE INDEX idx_config_tenant ON sys_config (tenant_id);

-- ---------- 2) sys_user_invite 新表 ----------
CREATE TABLE IF NOT EXISTS sys_user_invite (
    invite_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL                COMMENT '被邀请加入的租户 id（业务列，进 IGNORE_TABLES 白名单）',
    user_id          BIGINT       NOT NULL                COMMENT '目标 sys_user.id',
    invite_code_hash CHAR(64)     NOT NULL                COMMENT 'SHA-256(raw code) hex；raw code 不入库',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / USED / EXPIRED / REVOKED',
    expires_at       DATETIME     NOT NULL                COMMENT '过期时间（默认签发 +72h）',
    used_at          DATETIME     NULL                    COMMENT '消费时间',
    revoked_at       DATETIME     NULL                    COMMENT '撤销时间',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_invite_code_hash ON sys_user_invite (invite_code_hash);
CREATE INDEX idx_invite_tenant_user ON sys_user_invite (tenant_id, user_id);
CREATE INDEX idx_invite_status ON sys_user_invite (status);

-- ---------- 3) sys_user 首登强制改密标记 ----------
ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0;
