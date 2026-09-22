-- ============================================================
-- V19: 解析结果持久缓存（Phase C）
--   新增 kb_extraction_cache：跨进程持久缓存文档解析结果，按「文件内容摘要 +
--   策略 + 模型 + 提示词摘要 + 渲染模式」复合键去重。避免同一扫描件在重解析 / reindex 时
--   重复触发昂贵的视觉识别。result_json 存序列化的 ExtractionResult。
--   兼容 H2 与 MySQL 8.0：CREATE TABLE IF NOT EXISTS、TIMESTAMP 默认值、
--   索引独立 CREATE、TEXT 存 JSON、不写 COMMENT / UNSIGNED / ON UPDATE / 内联 INDEX。
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_extraction_cache (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    checksum     VARCHAR(64)  NOT NULL,
    strategy     VARCHAR(30)  NOT NULL,
    model_name   VARCHAR(100) NOT NULL DEFAULT '',
    prompt_hash  VARCHAR(64)  NOT NULL DEFAULT '',
    render_mode  VARCHAR(20)  NOT NULL DEFAULT '',
    result_json  TEXT         NOT NULL,
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_extraction_cache_key
    ON kb_extraction_cache(checksum, strategy, model_name, prompt_hash, render_mode);
CREATE INDEX idx_extraction_cache_checksum ON kb_extraction_cache(checksum);
