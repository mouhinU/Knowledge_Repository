-- ============================================================
-- V16: 文档内嵌图片资产（看图写话 / 看图题配图 · 阶段 1）
--   新增 kb_document_image：知识库文档摄入时提取并落盘的位图元数据，
--   供试卷校对页检索选图、绑定到题目（后续阶段），以及浏览器经
--   /api/exam/assets/{asset_key} 拉取展示。二进制存文件系统，本表只存定位与展示元数据。
--   兼容 H2 与 MySQL 8.0：CREATE TABLE IF NOT EXISTS、TIMESTAMP 默认值、
--   索引独立 CREATE、不写 COMMENT / UNSIGNED / ON UPDATE / 内联 INDEX / AFTER。
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_document_image (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_key    VARCHAR(64)   NOT NULL,
    document_id  BIGINT        NOT NULL,
    document_key VARCHAR(64)   NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    sha256       VARCHAR(64)   NOT NULL,
    mime_type    VARCHAR(64),
    page_no      INT,
    seq_on_page  INT           DEFAULT 0,
    width        INT,
    height       INT,
    byte_size    BIGINT,
    create_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_doc_image_asset_key ON kb_document_image(asset_key);
CREATE UNIQUE INDEX uk_doc_image_doc_sha ON kb_document_image(document_id, sha256);
CREATE INDEX idx_doc_image_document ON kb_document_image(document_id);
