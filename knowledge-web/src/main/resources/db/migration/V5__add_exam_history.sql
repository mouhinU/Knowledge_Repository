-- ============================================================
-- V5: AI 出卷历史记录表
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_exam_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    topic VARCHAR(512) NOT NULL,
    difficulty VARCHAR(32),
    question_config TEXT,
    exam_paper TEXT,
    answer_key TEXT,
    quality_score INT,
    retrieved_chunks INT DEFAULT 0,
    key_findings TEXT,
    review_feedback TEXT,
    difficulty_assessment TEXT,
    deduplication_report TEXT,
    user_id VARCHAR(64),
    department_id VARCHAR(64),
    category VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    error_message VARCHAR(1024),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_exam_session ON kb_exam_history(session_id);
CREATE INDEX idx_exam_user ON kb_exam_history(user_id);
CREATE INDEX idx_exam_create ON kb_exam_history(create_time);
