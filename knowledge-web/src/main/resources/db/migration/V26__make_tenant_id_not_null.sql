-- ============================================================
-- V26: tenant_id 收紧为 NOT NULL + 过渡默认值（P1-T6，SaaS 租户脊柱）
--   前置：V24 已把 15 张业务表全部回填到 default 租户（无 NULL），故可安全转 NOT NULL。
--   为何带 DEFAULT：saas.enabled=false 阶段 TenantLineInnerInterceptor(P1-T7) 尚未注册，
--   存量写路径不会显式填 tenant_id；若只 NOT NULL 无默认则所有 INSERT 违约、单租户功能立即崩。
--   DEFAULT 指向 V23 种子的默认租户 id（fresh 库恒为 1；FlywaySmokeTest 断言 default id==1 兜底）。
--   P1-T7 上线后由拦截器显式写入真实租户，本 DEFAULT 退化为"漏填兜底"，不改变正确租户归属。
--   列名对齐既有库约定；MODIFY COLUMN 语法 H2 MODE=MySQL 与 MySQL 8 均支持（冒烟已验）。
--
--   复合索引重建：V25 已为 6 张表建 UNIQUE(tenant_id, key)，其最左前缀即 tenant_id，
--   足以服务拦截器 `WHERE tenant_id=?`；这 6 张表 V24 的单列 idx_<t>_tenant 遂冗余，此处 DROP
--   以降写放大。其余 9 张无 tenant 前导唯一的表保留单列 tenant 索引（拦截器等值过滤够用），
--   更细粒度的 (tenant_id, 高频过滤列) 复合优化留待 P1-T10 依实测查询画像再定，避免投机建索引。
-- ============================================================

-- ---------- NOT NULL + 过渡默认 ----------
ALTER TABLE kb_document        MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_document_chunk  MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_document_image  MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_category        MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_exam_history    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_exam_session    MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_exam_question   MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_exam_answer     MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_student         MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_student_session MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE kb_writing_history MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_user           MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_role           MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_user_role      MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_department     MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;

-- ---------- 清理 6 张表冗余单列 tenant 索引（已由 V25 的 UNIQUE(tenant_id,…) 最左前缀覆盖）----------
DROP INDEX idx_category_tenant ON kb_category;
DROP INDEX idx_exam_history_tenant ON kb_exam_history;
DROP INDEX idx_exam_session_tenant ON kb_exam_session;
DROP INDEX idx_exam_question_tenant ON kb_exam_question;
DROP INDEX idx_student_tenant ON kb_student;
DROP INDEX idx_writing_history_tenant ON kb_writing_history;
