-- ============================================================
-- V24: 15 张业务表加 tenant_id 列（P1-T4，SaaS 租户脊柱；先 nullable）
--   设计：nullable + 回填 default，使 saas.enabled=false 阶段行为零变化；NOT NULL 收紧与复合
--   索引重建在 V26（P1-T6）完成，唯一键切换在 V25（P1-T5）完成。
--   平台表 sys_tenant / sys_tenant_placement 不在此列（本身即租户主数据，进 P1-T7 ignore 名单）。
--   Phase 4 才租户化的 sys_config（P4-T1）、kb_extraction_cache（P4-T3）此处不加列，避免跨 Phase 漂移。
--   回填：以 scalar 子查询取默认租户 id（ANSI，H2/MySQL 双方言均可），不假设 id=1 常量。
-- ============================================================

-- ---------- 知识库 / 文档 ----------
ALTER TABLE kb_document          ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_document_chunk    ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_document_image    ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_category          ADD COLUMN tenant_id BIGINT NULL;

-- ---------- 考试 / 考生 ----------
ALTER TABLE kb_exam_history      ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_exam_session      ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_exam_question     ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_exam_answer       ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_student           ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_student_session   ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE kb_writing_history   ADD COLUMN tenant_id BIGINT NULL;

-- ---------- RBAC / 组织 ----------
ALTER TABLE sys_user             ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE sys_role             ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE sys_user_role        ADD COLUMN tenant_id BIGINT NULL;
ALTER TABLE sys_department       ADD COLUMN tenant_id BIGINT NULL;

-- ---------- 单列租户索引（先建最小可用；V26 再重建复合 (tenant_id, ...)）----------
CREATE INDEX idx_document_tenant        ON kb_document(tenant_id);
CREATE INDEX idx_document_chunk_tenant  ON kb_document_chunk(tenant_id);
CREATE INDEX idx_document_image_tenant  ON kb_document_image(tenant_id);
CREATE INDEX idx_category_tenant        ON kb_category(tenant_id);
CREATE INDEX idx_exam_history_tenant    ON kb_exam_history(tenant_id);
CREATE INDEX idx_exam_session_tenant    ON kb_exam_session(tenant_id);
CREATE INDEX idx_exam_question_tenant   ON kb_exam_question(tenant_id);
CREATE INDEX idx_exam_answer_tenant     ON kb_exam_answer(tenant_id);
CREATE INDEX idx_student_tenant         ON kb_student(tenant_id);
CREATE INDEX idx_student_session_tenant ON kb_student_session(tenant_id);
CREATE INDEX idx_writing_history_tenant ON kb_writing_history(tenant_id);
CREATE INDEX idx_user_tenant            ON sys_user(tenant_id);
CREATE INDEX idx_role_tenant            ON sys_role(tenant_id);
CREATE INDEX idx_user_role_tenant       ON sys_user_role(tenant_id);
CREATE INDEX idx_department_tenant      ON sys_department(tenant_id);

-- ---------- 回填：现有全部存量行归属默认租户 ----------
UPDATE kb_document        SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_document_chunk  SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_document_image  SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_category        SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_exam_history    SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_exam_session    SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_exam_question   SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_exam_answer     SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_student         SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_student_session SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE kb_writing_history SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE sys_user           SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE sys_role           SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE sys_user_role      SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
UPDATE sys_department     SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
