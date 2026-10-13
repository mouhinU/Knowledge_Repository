-- ============================================================
-- V35 · 种子运行时配置键（系统管理页面全量可见）
--
-- 把 SystemConfigDefaults 中已注册但 DB 无行的配置键补入 sys_config，
-- 使管理页面 GET /api/admin/system/configs 能一次性展示全部可调参数。
-- 仅 INSERT 不存在键（NOT EXISTS 守卫），幂等安全。
-- tenant_id = 1（default 平台租户），与 V21/V28 种子一致。
-- ============================================================

-- ── feature-toggle：视觉 / 重排序开关 ──
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'feature.vision.enabled', 'false', 'BOOLEAN',
       'PDF 视觉解析（扫描件 OCR + 混合提取）总开关', 'feature-toggle', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.vision.enabled' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'feature.reranker.enabled', 'false', 'BOOLEAN',
       '检索结果重排序（BGE-Reranker 二次精排）', 'feature-toggle', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'feature.reranker.enabled' AND tenant_id = 1);

-- ── llm：模型运行参数 ──
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'llm.chat.temperature', '0.7', 'DECIMAL',
       '对话角色温度（0.0-2.0，越高越随机）', 'llm', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.chat.temperature' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'llm.chat.max-tokens', '4096', 'INTEGER',
       '对话角色单次最大输出 Token 数', 'llm', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.chat.max-tokens' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'llm.chat.streaming-max-tokens', '16384', 'INTEGER',
       '对话流式角色最大输出 Token 数（须大于非流式以容纳思考链）', 'llm', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.chat.streaming-max-tokens' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'llm.embedding.model-name', 'bge-m3', 'STRING',
       '向量模型名称（切换后须重建集合）', 'llm', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.embedding.model-name' AND tenant_id = 1);

-- ── search：检索参数 ──
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'search.max-results', '20', 'INTEGER',
       '知识库语义检索最大返回条数', 'search', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'search.max-results' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'search.min-score', '0.3', 'DECIMAL',
       '知识库语义检索最低相似度阈值', 'search', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'search.min-score' AND tenant_id = 1);

-- ── quality-gate：黑板流水线质量门禁 ──
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'quality-gate.research.min-chunks', '1', 'INTEGER',
       '检索阶段最低结果条数（低于即建议重试）', 'quality-gate', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'quality-gate.research.min-chunks' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'quality-gate.research.low-confidence-threshold', '3', 'INTEGER',
       '检索阶段低置信度条数阈值（低于标记中等置信度）', 'quality-gate', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'quality-gate.research.low-confidence-threshold' AND tenant_id = 1);

INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'quality-gate.writing.min-length', '50', 'INTEGER',
       '写作阶段最低输出字符数（过短视为无效输出）', 'quality-gate', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'quality-gate.writing.min-length' AND tenant_id = 1);

-- ── extraction：文档解析参数 ──
INSERT INTO sys_config (config_key, config_value, value_type, description, category, editable, tenant_id)
SELECT 'extraction.max-pages-per-doc', '20', 'INTEGER',
       '单文档最大解析页数（PDF 超出部分截断）', 'extraction', TRUE, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'extraction.max-pages-per-doc' AND tenant_id = 1);
