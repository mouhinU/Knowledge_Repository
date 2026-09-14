-- ============================================================
-- V4: 知识库分类表 + 文档/分块增加分类字段
-- ============================================================

-- 分类表
CREATE TABLE IF NOT EXISTS kb_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    sort_order INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_category_name ON kb_category(name);

-- 默认分类
INSERT INTO kb_category (name, sort_order) VALUES ('工作', 1);
INSERT INTO kb_category (name, sort_order) VALUES ('学习', 2);
INSERT INTO kb_category (name, sort_order) VALUES ('休闲', 3);
INSERT INTO kb_category (name, sort_order) VALUES ('其他', 4);

-- 文档表增加分类字段
ALTER TABLE kb_document ADD COLUMN category VARCHAR(64) DEFAULT '其他';

-- 文档分块表增加分类字段（冗余，用于 Milvus 元数据）
ALTER TABLE kb_document_chunk ADD COLUMN category VARCHAR(64) DEFAULT '其他';
