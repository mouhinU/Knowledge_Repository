-- ============================================================
-- V6: 在线考试系统 — 考生、考试场次、答题记录
-- ============================================================

-- 考生表
CREATE TABLE IF NOT EXISTS kb_student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    display_name VARCHAR(128),
    student_no VARCHAR(64),
    department_id VARCHAR(64),
    session_token VARCHAR(128),
    token_expiry TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_student_username ON kb_student(username);
CREATE INDEX idx_student_token ON kb_student(session_token);
CREATE INDEX idx_student_dept ON kb_student(department_id);

-- 考试场次表（学生答题记录）
CREATE TABLE IF NOT EXISTS kb_exam_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key VARCHAR(128) NOT NULL,
    student_id BIGINT NOT NULL,
    exam_history_id BIGINT,
    topic VARCHAR(512) NOT NULL,
    difficulty VARCHAR(32),
    exam_paper TEXT,
    answer_key TEXT,
    questions_json TEXT,
    total_score INT DEFAULT 100,
    ai_score INT,
    final_score INT,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    start_time TIMESTAMP,
    submit_time TIMESTAMP,
    grade_time TIMESTAMP,
    publish_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_exam_session_key ON kb_exam_session(session_key);
CREATE INDEX idx_exam_session_student ON kb_exam_session(student_id);
CREATE INDEX idx_exam_session_status ON kb_exam_session(status);
CREATE INDEX idx_exam_session_create ON kb_exam_session(create_time);

-- 单题答题记录表
CREATE TABLE IF NOT EXISTS kb_exam_answer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_index INT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    question_content TEXT NOT NULL,
    options_json TEXT,
    max_score INT NOT NULL DEFAULT 0,
    correct_answer TEXT,
    student_answer TEXT,
    is_correct BOOLEAN,
    ai_score INT,
    ai_feedback TEXT,
    review_score INT,
    review_feedback TEXT,
    reviewed_by VARCHAR(64),
    review_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exam_answer_session ON kb_exam_answer(session_id);
CREATE INDEX idx_exam_answer_review ON kb_exam_answer(review_score);
