-- ============================================================
-- V22: 学生多会话支持（P0-T5）
--   历史：kb_student.session_token 每次登录覆盖旧值，导致「同账号并发登录互踢」。
--   方案：新增 kb_student_session 表，每次登录落一行 token；旧字段保留 1 个版本作
--   兼容读回退（legacy），后续 Phase 移除。
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_student_session (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token VARCHAR(128) NOT NULL COMMENT '会话令牌（UUID 32 位，客户端持有）',
    student_id    BIGINT       NOT NULL COMMENT '考生主键（kb_student.id）',
    expires_at    TIMESTAMP    NOT NULL COMMENT '令牌过期时间',
    revoked_at    TIMESTAMP    NULL     COMMENT '主动吊销时间；NULL=未吊销',
    user_agent    VARCHAR(256) NULL     COMMENT '登录时 UA，便于运维排查（可空）',
    ip_address    VARCHAR(64)  NULL     COMMENT '登录时 IP，便于运维排查（可空）',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_student_session_token ON kb_student_session(session_token);
CREATE INDEX idx_student_session_student ON kb_student_session(student_id);
CREATE INDEX idx_student_session_expiry ON kb_student_session(expires_at);
