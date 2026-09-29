-- ============================================================
-- V25: 唯一约束从"全局唯一"切换为"租户内唯一"（P1-T5，SaaS 租户脊柱）
--   单租户时代这些键全局唯一；多租户下不同租户应各自拥有同名/同键业务对象
--   （例如每个租户都可有名为"数学"的分类、名为"张三"的考生）。改为 (tenant_id, key)。
--   切换前预扫描跨租户重复：V24 已把全部存量行回填到 default 单一租户，故此刻不存在跨租户重复，
--   加 (tenant_id, key) 复合唯一与旧全局唯一等价，安全无损。真正引入第 2 个租户是在 Phase 3。
--   保留全局唯一的键（不在此列）：kb_document_image.asset_key(内容哈希去重)、
--   kb_document_image(document_id,sha256)、kb_student_session.session_token(UUID)、
--   sys_user_role(user_id,role_id)(两端已随各自租户)。
--   DROP INDEX 采用 MySQL 的 `ON <table>` 形态；H2 MODE=MySQL 兼容。ADD 用 CREATE UNIQUE
--   INDEX（H2/MySQL 双方言均已验证，见 V11/V16/V22）。
-- ============================================================

-- kb_category：分类名 → 租户内唯一
DROP INDEX uk_category_name ON kb_category;
CREATE UNIQUE INDEX uk_category_tenant_name ON kb_category(tenant_id, name);

-- kb_student：考生用户名 → 租户内唯一
DROP INDEX uk_student_username ON kb_student;
CREATE UNIQUE INDEX uk_student_tenant_username ON kb_student(tenant_id, username);

-- kb_writing_history：写作会话号 → 租户内唯一
DROP INDEX uk_writing_session ON kb_writing_history;
CREATE UNIQUE INDEX uk_writing_tenant_session ON kb_writing_history(tenant_id, session_id);

-- kb_exam_history：考试会话号 → 租户内唯一
DROP INDEX uk_exam_session ON kb_exam_history;
CREATE UNIQUE INDEX uk_exam_history_tenant_session ON kb_exam_history(tenant_id, session_id);

-- kb_exam_session：在线考试 session_key → 租户内唯一
DROP INDEX uk_exam_session_key ON kb_exam_session;
CREATE UNIQUE INDEX uk_exam_session_tenant_key ON kb_exam_session(tenant_id, session_key);

-- kb_exam_question：题目 (session_key, question_number) → 租户内唯一
DROP INDEX uk_exam_question_session_num ON kb_exam_question;
CREATE UNIQUE INDEX uk_exam_question_tenant_session_num
    ON kb_exam_question(tenant_id, session_key, question_number);
