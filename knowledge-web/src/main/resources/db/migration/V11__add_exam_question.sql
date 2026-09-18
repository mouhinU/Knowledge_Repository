-- ============================================================
-- V11: 出卷即切分 — 结构化题目落库（V2 阶段 1-A）
--   新增 kb_exam_question：试卷生成/开考时一次性切分并绑定标准答案，
--   下游（评分/展示/错题本）纯读结构化行，废弃对 answer_key 自由文本的重复解析。
--   权威题号 = question_number（印刷号，全局连续）。
--   兼容 H2 与 MySQL 8.0：CREATE TABLE IF NOT EXISTS、TIMESTAMP 默认值、
--   索引独立 CREATE、不写 COMMENT / UNSIGNED / ON UPDATE / 内联 INDEX / AFTER。
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_exam_question (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key     VARCHAR(64)  NOT NULL,
    question_number INT          NOT NULL,
    section_label   VARCHAR(64),
    question_type   VARCHAR(32)  NOT NULL,
    stem            TEXT         NOT NULL,
    options_json    TEXT,
    blank_count     INT          DEFAULT 0,
    max_score       INT          NOT NULL,
    correct_answer  TEXT,
    analysis        TEXT,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_exam_question_session_num ON kb_exam_question(session_key, question_number);
CREATE INDEX idx_exam_question_session ON kb_exam_question(session_key);

-- 答题记录表补充印刷题号（与 kb_exam_question.question_number 对齐，替代不稳定的位置序号）
ALTER TABLE kb_exam_answer ADD COLUMN question_number INT DEFAULT NULL;
CREATE INDEX idx_exam_answer_session_num ON kb_exam_answer(session_id, question_number);
