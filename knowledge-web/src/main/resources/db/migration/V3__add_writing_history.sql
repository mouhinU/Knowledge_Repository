-- ============================================================
-- V3: AI 写作历史记录表
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_writing_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    question TEXT NOT NULL,
    final_article TEXT,
    draft_article TEXT,
    quality_score INT,
    retrieved_chunks INT DEFAULT 0,
    key_findings TEXT,
    review_feedback TEXT,
    user_id VARCHAR(64),
    department_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    error_message VARCHAR(1024),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_writing_session ON kb_writing_history(session_id);
CREATE INDEX idx_writing_user ON kb_writing_history(user_id);
CREATE INDEX idx_writing_create ON kb_writing_history(create_time);
