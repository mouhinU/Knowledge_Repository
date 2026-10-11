-- ============================================================
-- V31: per-tenant LLM/embedding 配置表（P4-T4 · B-2 方案 B）
--   新建 sys_tenant_llm_profile 表，支持每个租户独立配置 LLM API Key / base_url / model 等。
--   api_key 列在 knowledge.secret.cipher.mode=local 时存 AES-256-GCM 密文（base64），
--   mode=none 时存明文（启动 WARN）。密文列 VARCHAR(512) 已预留 4x 余量。
--   UNIQUE(tenant_id) 保证每租户最多一行。
-- ============================================================

CREATE TABLE sys_tenant_llm_profile (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    api_key       VARCHAR(512) NULL,
    base_url      VARCHAR(255) NULL,
    model         VARCHAR(128) NULL,
    embedding_model VARCHAR(128) NULL,
    dimension     INT          NULL,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_llm_profile (tenant_id)
);
