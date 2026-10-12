-- ============================================================
-- V34: 核心表外键约束（R3 风险收口）
--   项目刻意对租户数据不加 FK（由 app 层删除 reconciler 保证一致性），
--   但核心文档/考试层级表与平台元数据表需要 FK 安全网，
--   防止意外删除父行导致孤儿数据。
--
--   Group 1 — ON DELETE CASCADE：子行随父行一起销毁（子行脱离父行无意义）。
--   Group 2 — ON DELETE RESTRICT：阻止意外删除仍有子行的父行。
--
--   不加 FK 的已知例外：
--     kb_exam_history.user_id   — 审计轨迹须 survives 用户删除
--     kb_document.owner_id / department_id — 与业务键类型不匹配
--     kb_exam_question.session_key — VARCHAR(64) vs VARCHAR(128) 宽度不匹配
--
--   H2（MODE=MySQL）与 MySQL 8.0 双方言兼容：标准 ALTER TABLE ADD CONSTRAINT 语法。
-- ============================================================

-- ----- Group 1: ON DELETE CASCADE -----

-- 文档分块随文档销毁
ALTER TABLE kb_document_chunk
    ADD CONSTRAINT fk_chunk_document
    FOREIGN KEY (document_id) REFERENCES kb_document (id)
    ON DELETE CASCADE;

-- 文档图片随文档销毁
ALTER TABLE kb_document_image
    ADD CONSTRAINT fk_doc_image_document
    FOREIGN KEY (document_id) REFERENCES kb_document (id)
    ON DELETE CASCADE;

-- 考试答案随考试会话销毁
ALTER TABLE kb_exam_answer
    ADD CONSTRAINT fk_exam_answer_session
    FOREIGN KEY (session_id) REFERENCES kb_exam_session (id)
    ON DELETE CASCADE;

-- 用户角色分配随用户销毁
ALTER TABLE sys_user_role
    ADD CONSTRAINT fk_user_role_user
    FOREIGN KEY (user_id) REFERENCES sys_user (id)
    ON DELETE CASCADE;

-- 用户角色分配随角色销毁
ALTER TABLE sys_user_role
    ADD CONSTRAINT fk_user_role_role
    FOREIGN KEY (role_id) REFERENCES sys_role (id)
    ON DELETE CASCADE;

-- 学生会话随学生销毁
ALTER TABLE kb_student_session
    ADD CONSTRAINT fk_student_session_student
    FOREIGN KEY (student_id) REFERENCES kb_student (id)
    ON DELETE CASCADE;

-- 租户放置元数据随租户销毁
ALTER TABLE sys_tenant_placement
    ADD CONSTRAINT fk_placement_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户开通步骤审计随租户销毁
ALTER TABLE sys_tenant_provision_step
    ADD CONSTRAINT fk_provision_step_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户删除步骤审计随租户销毁
ALTER TABLE sys_tenant_deletion_step
    ADD CONSTRAINT fk_deletion_step_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户删除审批令牌随租户销毁
ALTER TABLE sys_tenant_delete_approval
    ADD CONSTRAINT fk_delete_approval_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户 LLM 配置随租户销毁
ALTER TABLE sys_tenant_llm_profile
    ADD CONSTRAINT fk_llm_profile_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户用量计量随租户销毁
ALTER TABLE tenant_usage
    ADD CONSTRAINT fk_tenant_usage_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- 租户套餐限额随租户销毁
ALTER TABLE tenant_plan_limit
    ADD CONSTRAINT fk_tenant_plan_limit_tenant
    FOREIGN KEY (tenant_id) REFERENCES sys_tenant (tenant_id)
    ON DELETE CASCADE;

-- ----- Group 2: ON DELETE RESTRICT -----

-- 阻止删除仍有子部门的父部门
ALTER TABLE sys_department
    ADD CONSTRAINT fk_department_parent
    FOREIGN KEY (parent_id) REFERENCES sys_department (id)
    ON DELETE RESTRICT;

-- 保护考试试卷血缘：阻止删除仍被会话引用的考试历史
ALTER TABLE kb_exam_session
    ADD CONSTRAINT fk_exam_session_history
    FOREIGN KEY (exam_history_id) REFERENCES kb_exam_history (id)
    ON DELETE RESTRICT;
