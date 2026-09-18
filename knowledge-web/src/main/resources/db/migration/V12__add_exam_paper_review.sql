-- ============================================================
-- V12: 试卷校对关口（阶段 1-D）——出卷历史增加校对审计字段
--   状态列 status 语义扩展为试卷生命周期：
--   DRAFT / REVIEWABLE / PUBLISHED / VALIDATION_FAILED（复用原 VARCHAR(32)）
--   新增审核人与审核时间，索引加速「待校对 / 已发布」列表查询。
--   语法 H2 + MySQL 兼容：ADD COLUMN 不用 AFTER，索引独立语句。
-- ============================================================

ALTER TABLE kb_exam_history ADD COLUMN reviewed_by VARCHAR(64) DEFAULT NULL;
ALTER TABLE kb_exam_history ADD COLUMN reviewed_time TIMESTAMP DEFAULT NULL;

CREATE INDEX idx_exam_history_status ON kb_exam_history(status);
