-- V2: 新增文档切分策略字段
ALTER TABLE kb_document ADD COLUMN chunking_strategy VARCHAR(32) DEFAULT 'FIXED_SIZE';
