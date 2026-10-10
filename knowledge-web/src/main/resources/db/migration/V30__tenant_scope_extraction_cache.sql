-- ============================================================
-- V30: kb_extraction_cache 加租户维（P4-T3，修复 G7 跨租户解析缓存泄漏）
--   现状：解析结果缓存的唯一键 uk_extraction_cache_key 为 (checksum, strategy, model_name,
--   prompt_hash, render_mode) 全局唯一，且表在 KnowledgeTenantLineHandler.IGNORE_TABLES 名单内、
--   无 tenant_id 列。多租户下 A 租户已解析过的同一文件（内容摘要相同 + 策略/模型/提示词/渲染一致），
--   B 租户命中缓存直接读到 A 的 result_json —— 跨租户数据泄漏（Phase 3 提案 §11 G7）。
--   本迁移把缓存键收敛为「租户内唯一」，并交回 P1-T7 的 TenantLineInnerInterceptor 自动注入
--   tenant_id（下一步代码从 IGNORE_TABLES 移除本表）。
--
--   三段式（单表，对齐 V24→V25→V26 的分步口径，此处合一）：
--     1) 先 ADD nullable 列，避免存量行违约；
--     2) scalar 子查询回填 default 租户 id（ANSI，H2 MODE=MySQL / MySQL 8 双方言，不假设 id=1 常量）；
--     3) 切换唯一键为 (tenant_id, …)：回填后所有存量行同属 default 单一租户，跨租户重复不存在，
--        新复合唯一与旧全局唯一此刻等价，无损切换；
--     4) 收紧 NOT NULL + 过渡 DEFAULT 1：saas.enabled=false 阶段拦截器虽已注册但存量写路径可能漏填，
--        DEFAULT 兜底防 INSERT 违约；fresh 库 default 租户 id 恒为 1（FlywaySmokeTest 断言）。
--   保留 idx_extraction_cache_checksum（普通索引，非约束，不构成读泄漏，不删以免扩大 scope）。
-- ============================================================

-- 1) 加列（先 nullable）
ALTER TABLE kb_extraction_cache ADD COLUMN tenant_id BIGINT NULL;

-- 2) 回填：现有全部存量行归属默认租户
UPDATE kb_extraction_cache
   SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');

-- 3) 唯一键从「全局唯一」切换为「租户内唯一」
DROP INDEX uk_extraction_cache_key ON kb_extraction_cache;
CREATE UNIQUE INDEX uk_extraction_cache_tenant_key
    ON kb_extraction_cache(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode);

-- 4) 收紧 NOT NULL + 过渡默认值
ALTER TABLE kb_extraction_cache MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
