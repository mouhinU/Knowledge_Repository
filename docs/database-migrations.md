# Knowledge Repository 数据库迁移脚本全览（V1 → V35）

> 汇总 Knowledge_Repository 迄今全部 35 个 Flyway 迁移版本：版本号、文件名、关联任务、动作摘要、涉及表与关键 SQL。
> 权威决策以 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 为准；逐 Phase 门禁以对应 go-checklist
> 为准。
> SaaS 租户脊柱相关迁移（V23+）的深入分析见 [saas-migration-summary.md](saas-migration-summary.md)。

---

## 总览

| 阶段         | 版本范围    | 定位                                   | 表数量变化     |
|------------|---------|--------------------------------------|-----------|
| 单租户基线      | V1–V22  | 核心业务 schema 建立（文档/考试/RBAC/配置）        | 0 → 18 表  |
| SaaS 租户脊柱  | V23–V26 | 多租户地基：建租户表 → 加列 → 切唯一键 → 收紧 NOT NULL | 18 → 20 表 |
| 生命周期       | V27–V29 | 开通状态机 / 邀请 / 归档 & 删除                 | 20 → 25 表 |
| Phase 4 治理 | V30–V31 | 解析缓存租户化 / per-tenant LLM profile     | 25 → 26 表 |
| Phase 6 计量计费 | V32–V33 | 租户用量计量 / 套餐限额                    | 26 → 28 表 |
| 核心外键 / 配置种子 | V34–V35 | 核心表外键约束 / 运行时配置键种子（无新表）        | 28 → 28 表 |

**当前表总数**：28 张（含 flyway_schema_history）

---

## 一、单租户基线（V1–V22）

### V1 — `init_schema.sql`

**定位**：初始化核心 schema

**新建表**（6 张）：

| 表名                  | 用途       |
|---------------------|----------|
| `sys_department`    | 部门表      |
| `sys_user`          | 用户表      |
| `sys_role`          | 角色表      |
| `sys_user_role`     | 用户-角色关联表 |
| `kb_document`       | 文档表      |
| `kb_document_chunk` | 文档分块表    |

**种子数据**：4 个默认部门 + 3 个默认角色（ADMIN/MANAGER/MEMBER）+ 1 个 admin 用户 + 管理员角色关联

---

### V2 — `add_chunking_strategy.sql`

**定位**：文档切分策略字段

**变更**：`kb_document` 加 `chunking_strategy VARCHAR(32) DEFAULT 'FIXED_SIZE'`

---

### V3 — `add_writing_history.sql`

**定位**：AI 写作历史

**新建表**：`kb_writing_history`（session_id 唯一索引，记录写作会话的提问/成稿/评分/反馈）

---

### V4 — `add_category.sql`

**定位**：知识库分类体系

**新建表**：`kb_category`（name 全局唯一）

**变更**：`kb_document` 加 `category` 列；`kb_document_chunk` 加 `category` 列（冗余，供 Milvus 元数据）

**种子数据**：4 个默认分类（工作/学习/休闲/其他）

---

### V5 — `add_exam_history.sql`

**定位**：AI 出卷历史记录

**新建表**：`kb_exam_history`（session_id 唯一索引，记录出卷主题/难度/试卷/答案/评分）

---

### V6 — `add_online_exam.sql`

**定位**：在线考试系统核心表

**新建表**（3 张）：

| 表名                | 用途                                               |
|-------------------|--------------------------------------------------|
| `kb_student`      | 考生表（username 唯一）                                 |
| `kb_exam_session` | 考试场次表（session_key 唯一，状态 IN_PROGRESS→SUBMITTED→…） |
| `kb_exam_answer`  | 单题答题记录表                                          |

---

### V7 — `add_exam_duration.sql`

**定位**：考试时长支持

**变更**：`kb_exam_history` 加 `duration_minutes`；`kb_exam_session` 加 `duration_minutes`

---

### V8 — `add_exam_plan.sql`

**定位**：题型分布方案落库

**变更**：`kb_exam_history` 加 `exam_plan TEXT`；`kb_exam_session` 加 `exam_plan TEXT`

---

### V9 — `add_exam_grading_trace.sql`

**定位**：评分过程透明化

**变更**：`kb_exam_answer` 加 `ai_input TEXT`（发给模型的完整 prompt）+ `ai_raw_output TEXT`（模型原始返回）

---

### V10 — `add_exam_score_detail.sql`

**定位**：出卷质量评分六维度明细

**变更**：`kb_exam_history` 加 `score_detail TEXT`（JSON：accuracy/wording/coverage/typeReasonable/difficulty/format/total）

---

### V11 — `add_exam_question.sql`

**定位**：出卷即切分——结构化题目落库

**新建表**：`kb_exam_question`（`UNIQUE(session_key, question_number)`，结构化存储题干/选项/标准答案/分析）

**变更**：`kb_exam_answer` 加 `question_number INT`（对齐印刷题号）

---

### V12 — `add_exam_paper_review.sql`

**定位**：试卷校对关口

**变更**：`kb_exam_history` 加 `reviewed_by` + `reviewed_time`；`status`
语义扩展为试卷生命周期（DRAFT/REVIEWABLE/PUBLISHED/VALIDATION_FAILED）

---

### V13 — `add_exam_question_scoring_criteria.sql`

**定位**：错题本结构化收尾

**变更**：`kb_exam_question` 加 `scoring_criteria TEXT`（消除 answer_key 自由文本重复解析）

---

### V14 — `add_exam_grading_token.sql`

**定位**：评分并发围栏令牌（CONC-1）

**变更**：`kb_exam_session` 加 `grading_token VARCHAR(64)`（防慢速旧评分者与新评分者交叉写）

---

### V15 — `add_user_auth.sql`

**定位**：管理端登录认证改造（HTTP Basic → 账号密码 + JWT）

**变更**：`sys_user` 加 `password_hash VARCHAR(256)` + `status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'`

**种子数据**：admin 用户设默认口令 `admin123` 的 BCrypt 哈希

---

### V16 — `add_document_image.sql`

**定位**：文档内嵌图片资产（看图写话/看图题配图）

**新建表**：`kb_document_image`（`UNIQUE(asset_key)` + `UNIQUE(document_id, sha256)`，二进制存文件系统，表只存定位与展示元数据）

---

### V17 — `add_exam_question_images.sql`

**定位**：看图题配图绑定

**变更**：`kb_exam_question` 加 `images_json TEXT`（人工绑定的配图 assetKey 有序数组）

---

### V18 — `add_exam_session_voided.sql`

**定位**：试卷作废级联

**变更**：`kb_exam_session` 加 `voided BOOLEAN DEFAULT FALSE` + 复合索引 `(student_id, exam_history_id)`

---

### V19 — `create_extraction_cache.sql`

**定位**：解析结果持久缓存

**新建表**：`kb_extraction_cache`（`UNIQUE(checksum, strategy, model_name, prompt_hash, render_mode)`，避免同一扫描件重复触发视觉识别）

---

### V20 — `exam_score_precision.sql`

**定位**：出卷质量评分精度提升

**变更**：`kb_exam_history.quality_score` 从 `INT` 改为 `DECIMAL(5,2)`（支持两位小数）

---

### V21 — `create_sys_config.sql`

**定位**：系统配置表（特性开关 + 运行时可调参数）

**新建表**：`sys_config`（`UNIQUE(config_key)`，三层获取：内存缓存 → 数据库 → 系统默认值）

**种子数据**：6 条特性开关与可调参数（exam.plan.auto-balance / quality-score-threshold / review-required / grading-delay /
grading-timeout）

---

### V22 — `create_student_session.sql`

**定位**：学生多会话支持（P0-T5，解决同账号并发登录互踢）

**新建表**：`kb_student_session`（`UNIQUE(session_token)`，每次登录落一行 token，支持并发会话）

---

## 二、SaaS 租户脊柱（V23–V26）

> 详见 [saas-migration-summary.md](saas-migration-summary.md) §1

### V23 — `create_sys_tenant.sql`（P1-T1）

**定位**：租户脊柱地基

**新建表**（2 张）：

| 表名                     | 用途                                   |
|------------------------|--------------------------------------|
| `sys_tenant`           | 租户主表（`UNIQUE(tenant_key)`）           |
| `sys_tenant_placement` | 放置策略表（POOL/SILO，`UNIQUE(tenant_id)`） |

**种子数据**：`tenant_key='default'` 默认租户 + POOL 放置

**白名单**：两表进 `KnowledgeTenantLineHandler.IGNORE_TABLES`（平台元数据，无 tenant_id 列）

---

### V24 — `add_tenant_id_columns.sql`（P1-T4）

**定位**：15 张业务表加 `tenant_id BIGINT NULL` + 单列索引 + scalar 子查询回填 default

**涉及表**：`kb_document` / `kb_document_chunk` / `kb_document_image` / `kb_category` / `kb_exam_history` /
`kb_exam_session` / `kb_exam_question` / `kb_exam_answer` / `kb_student` / `kb_student_session` / `kb_writing_history` /
`sys_user` / `sys_role` / `sys_user_role` / `sys_department`

**刻意排除**：`sys_config`（P4-T1 才租户化）、`kb_extraction_cache`（P4-T3 才租户化）

---

### V25 — `switch_unique_indexes.sql`（P1-T5）

**定位**：6 张表唯一键从"全局唯一"切"租户内唯一" `(tenant_id, <原键>)`

| 表                    | 旧唯一键                             | 新唯一键                                        |
|----------------------|----------------------------------|---------------------------------------------|
| `kb_category`        | `(name)`                         | `(tenant_id, name)`                         |
| `kb_student`         | `(username)`                     | `(tenant_id, username)`                     |
| `kb_writing_history` | `(session_id)`                   | `(tenant_id, session_id)`                   |
| `kb_exam_history`    | `(session_id)`                   | `(tenant_id, session_id)`                   |
| `kb_exam_session`    | `(session_key)`                  | `(tenant_id, session_key)`                  |
| `kb_exam_question`   | `(session_key, question_number)` | `(tenant_id, session_key, question_number)` |

**保留全局唯一**：`kb_document_image.asset_key` / `kb_document_image(document_id, sha256)` /
`kb_student_session.session_token` / `sys_user_role(user_id, role_id)`

---

### V26 — `make_tenant_id_not_null.sql`（P1-T6）

**定位**：15 张表 `tenant_id` 收紧为 `NOT NULL DEFAULT 1` + 清理 6 张冗余单列 tenant 索引

**DEFAULT 1 理由**：`saas.enabled=false` 阶段拦截器未注册，存量写路径不显式填 tenant_id，DEFAULT 防 INSERT 违约

---

## 三、生命周期（V27–V29）

### V27 — `provision_state_machine.sql`（P3-T2）

**定位**：租户开通状态机（PENDING→PROVISIONING→ACTIVE/FAILED）

**变更**：`sys_tenant` 加 `provision_request_id VARCHAR(64)`（UUID v4 幂等键，S-5=方案 A）

**新建表**：`sys_tenant_provision_step`（`UNIQUE(tenant_id, provision_request_id, step_name, attempt_no)`，步骤级审计与幂等载体）

**白名单**：`sys_tenant_provision_step` 进 IGNORE_TABLES

---

### V28 — `tenant_scope_sys_config_and_invite.sql`（P3-T3，C-4=方案 A 提前 P4-T1）

**定位**：三件事合一

**变更**：

1. `sys_config` 加 `tenant_id NOT NULL DEFAULT 1`，唯一键从 `(config_key)` 切为 `(tenant_id, config_key)`
2. `sys_user` 加 `must_change_password TINYINT(1) NOT NULL DEFAULT 0`

**新建表**：`sys_user_invite`（一次性设密邀请，`UNIQUE(invite_code_hash)`，SHA-256 存 hash，raw code 不入库）

**白名单变动**：`sys_config` 移出 IGNORE_TABLES；`sys_user_invite` 加入（业务列非过滤维度）

---

### V29 — `tenant_archive_and_deletion.sql`（P3-T5，S-4=方案 A）

**定位**：租户归档与删除生命周期

**变更**：`sys_tenant` 加 `archived_at` / `deleted_at` / `deletion_request_id` 三列

**新建表**（3 张）：

| 表名                           | 用途                                                                          |
|------------------------------|-----------------------------------------------------------------------------|
| `sys_tenant_delete_approval` | 一次性删除审批令牌（双人复核，SHA-256 hash）                                                |
| `sys_tenant_deletion_step`   | 删除步骤级审计与幂等（`UNIQUE(tenant_id, deletion_request_id, step_name, attempt_no)`） |
| `sys_audit_log`              | 平台端高敏操作 DB 持久化审计                                                            |

**白名单**：三张新表均进 IGNORE_TABLES（tenant_id/tenant_key 是业务列非过滤维度）

---

## 四、Phase 4 治理（V30–V31）

### V30 — `tenant_scope_extraction_cache.sql`（P4-T3）

**定位**：修复 G7 跨租户解析缓存泄漏

**变更**：`kb_extraction_cache` 四段式合一——加 nullable `tenant_id` → scalar 子查询回填 → 唯一键从
`(checksum, strategy, model_name, prompt_hash, render_mode)` 切为
`(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)` → 收紧 `NOT NULL DEFAULT 1`

**白名单变动**：`kb_extraction_cache` 移出 IGNORE_TABLES（交回拦截器自动注入 tenant_id）

---

### V31 — `tenant_llm_profile.sql`（P4-T4，B-2 方案 B）

**定位**：per-tenant LLM/embedding 配置

**新建表**：`sys_tenant_llm_profile`

| 列                 | 类型                | 说明                                                            |
|-------------------|-------------------|---------------------------------------------------------------|
| `id`              | BIGINT PK         | 自增主键                                                          |
| `tenant_id`       | BIGINT NOT NULL   | 租户 id（`UNIQUE`，每租户最多一行）                                       |
| `api_key`         | VARCHAR(512) NULL | LLM API Key（mode=local 时 AES-256-GCM 密文 base64；mode=none 时明文） |
| `base_url`        | VARCHAR(255) NULL | API 基地址                                                       |
| `model`           | VARCHAR(128) NULL | 聊天模型名                                                         |
| `embedding_model` | VARCHAR(128) NULL | 向量模型名                                                         |
| `dimension`       | INT NULL          | 向量维度                                                          |
| `updated_at`      | DATETIME NOT NULL | 更新时间                                                          |

**关联代码**：`SecretCipher` 端口（domain）→ `LocalSecretCipher`（AES-256-GCM，mode=local）/ `NoopSecretCipher`（mode=none）→
`LlmProfileGatewayImpl` 在 DO↔Entity 边界加解密 → `TenantLlmProfileResolver`（L1 ConcurrentHashMap 30s TTL 热缓存）

---

## 五、Phase 6 计量计费与收尾治理（V32–V35）

### V32 — `create_tenant_usage.sql`（P6-T1）

**定位**：租户用量计量

**新建表**：`tenant_usage`（按月聚合记录每租户用量指标，`UNIQUE(tenant_id, metric, period)` 保证每租户每月每指标一行）

| 列            | 类型                       | 说明                          |
|--------------|--------------------------|-----------------------------|
| `id`         | BIGINT PK AUTO_INCREMENT | 自增主键                        |
| `tenant_id`  | BIGINT NOT NULL          | 租户 id                       |
| `metric`     | VARCHAR(64) NOT NULL     | 用量指标名                       |
| `period`     | DATE NOT NULL            | 聚合月份                        |
| `usage_value`| BIGINT NOT NULL DEFAULT 0| 用量值                         |
| `create_time`| DATETIME NOT NULL        | 创建时间（DEFAULT CURRENT_TIMESTAMP） |
| `update_time`| DATETIME NOT NULL        | 更新时间                        |

---

### V33 — `tenant_plan_limit.sql`（P6-T2）

**定位**：套餐限额

**新建表**：`tenant_plan_limit`（存储每租户每指标限额配置，`UNIQUE(tenant_id, metric)` 保证每租户每指标一行；`quota=-1` 表示无限额，`policy` 默认 `ALERT`）

| 列           | 类型                          | 说明                          |
|-------------|-----------------------------|-----------------------------|
| `id`        | BIGINT PK AUTO_INCREMENT    | 自增主键                        |
| `tenant_id` | BIGINT NOT NULL             | 租户 id                       |
| `metric`    | VARCHAR(64) NOT NULL        | 限额指标名                       |
| `quota`     | BIGINT NOT NULL             | 限额值（-1 = 无限额）               |
| `policy`    | VARCHAR(16) NOT NULL DEFAULT 'ALERT' | 超限策略                 |
| `create_time`| DATETIME NOT NULL           | 创建时间（DEFAULT CURRENT_TIMESTAMP） |
| `update_time`| DATETIME NOT NULL           | 更新时间                        |

---

### V34 — `add_core_foreign_keys.sql`（R3 风险收口）

**定位**：核心表外键约束（无新表）

**变更**：为核心文档/考试层级表与平台元数据表补 FK 安全网，防止意外删除父行产生孤儿数据。项目刻意不对租户业务数据加 FK（由 app 层删除 reconciler 保证一致性）

- **Group 1 · ON DELETE CASCADE**（子行随父行销毁）：`kb_document_chunk`/`kb_document_image` → `kb_document`；`kb_exam_answer` → `kb_exam_session`；`sys_user_role` → `sys_user`/`sys_role`；`kb_student_session` → `kb_student`；`sys_tenant_placement`/`sys_tenant_provision_step`/`sys_tenant_deletion_step`/`sys_tenant_delete_approval`/`sys_tenant_llm_profile`/`tenant_usage`/`tenant_plan_limit` → `sys_tenant`
- **Group 2 · ON DELETE RESTRICT**（阻止删除仍有子行的父行）：`sys_department.parent_id` → `sys_department`；`kb_exam_session.exam_history_id` → `kb_exam_history`
- **刻意不加 FK 的例外**：`kb_exam_history.user_id`（审计轨迹须 survives 用户删除）、`kb_document.owner_id`/`department_id`（与业务键类型不匹配）、`kb_exam_question.session_key`（VARCHAR 宽度不匹配）

---

### V35 — `seed_runtime_config_keys.sql`

**定位**：种子运行时配置键（无新表）

**变更**：把 `SystemConfigDefaults` 中已注册但 DB 无行的配置键补入 `sys_config`，使管理页面 `GET /api/admin/system/configs` 一次性展示全部可调参数。仅 `INSERT ... WHERE NOT EXISTS` 守卫（幂等安全），`tenant_id=1`（default 平台租户，与 V21/V28 种子一致）

**种子键**（按 category）：feature-toggle（`feature.vision.enabled`/`feature.reranker.enabled`）、llm（`llm.chat.temperature`/`llm.chat.max-tokens`/`llm.chat.streaming-max-tokens`/`llm.embedding.model-name`）、search（`search.max-results`/`search.min-score`）、quality-gate（`quality-gate.research.min-chunks`/`quality-gate.research.low-confidence-threshold`/`quality-gate.writing.min-length`）、extraction（`extraction.max-pages-per-doc`）

---

## 附录 A：租户白名单（IGNORE_TABLES）演进

| 版本         | 加入白名单                                                                             | 移出白名单                 |
|------------|-----------------------------------------------------------------------------------|-----------------------|
| P1-T7 初版   | `sys_tenant`、`sys_tenant_placement`、`flyway_schema_history`、`kb_extraction_cache` | —                     |
| P3-T2（V27） | `sys_tenant_provision_step`                                                       | —                     |
| P3-T3（V28） | `sys_user_invite`                                                                 | `sys_config`          |
| P3-T5（V29） | `sys_tenant_delete_approval`、`sys_tenant_deletion_step`、`sys_audit_log`           | —                     |
| P4-T3（V30） | —                                                                                 | `kb_extraction_cache` |

**当前白名单**（8 张）：`sys_tenant`、`sys_tenant_placement`、`sys_tenant_provision_step`、`sys_tenant_delete_approval`、
`sys_tenant_deletion_step`、`sys_audit_log`、`sys_user_invite`、`flyway_schema_history`

---

## 附录 B：全部表清单（V35 落地后）

| #  | 表名                           | 建表版本      | 有 tenant_id          | 在 IGNORE 白名单 |
|----|------------------------------|-----------|----------------------|--------------|
| 1  | `sys_department`             | V1        | ✔ (V24)              | —            |
| 2  | `sys_user`                   | V1        | ✔ (V24)              | —            |
| 3  | `sys_role`                   | V1        | ✔ (V24)              | —            |
| 4  | `sys_user_role`              | V1        | ✔ (V24)              | —            |
| 5  | `kb_document`                | V1        | ✔ (V24)              | —            |
| 6  | `kb_document_chunk`          | V1        | ✔ (V24)              | —            |
| 7  | `kb_writing_history`         | V3        | ✔ (V24)              | —            |
| 8  | `kb_category`                | V4        | ✔ (V24)              | —            |
| 9  | `kb_exam_history`            | V5        | ✔ (V24)              | —            |
| 10 | `kb_student`                 | V6        | ✔ (V24)              | —            |
| 11 | `kb_exam_session`            | V6        | ✔ (V24)              | —            |
| 12 | `kb_exam_answer`             | V6        | ✔ (V24)              | —            |
| 13 | `kb_exam_question`           | V11       | ✔ (V24)              | —            |
| 14 | `kb_document_image`          | V16       | ✔ (V24)              | —            |
| 15 | `kb_extraction_cache`        | V19       | ✔ (V30)              | —            |
| 16 | `sys_config`                 | V21       | ✔ (V28)              | —            |
| 17 | `kb_student_session`         | V22       | ✔ (V24)              | —            |
| 18 | `sys_tenant`                 | V23       | — (平台主数据)            | ✔            |
| 19 | `sys_tenant_placement`       | V23       | — (平台元数据)            | ✔            |
| 20 | `sys_tenant_provision_step`  | V27       | — (业务列)              | ✔            |
| 21 | `sys_user_invite`            | V28       | ✔ (业务列)              | ✔            |
| 22 | `sys_tenant_delete_approval` | V29       | — (业务列)              | ✔            |
| 23 | `sys_tenant_deletion_step`   | V29       | — (业务列)              | ✔            |
| 24 | `sys_audit_log`              | V29       | — (平台级)              | ✔            |
| 25 | `sys_tenant_llm_profile`     | V31       | — (UNIQUE tenant_id) | —            |
| 26 | `tenant_usage`               | V32       | ✔ (V32)              | —            |
| 27 | `tenant_plan_limit`          | V33       | ✔ (V33)              | —            |
| 28 | `flyway_schema_history`      | Flyway 内部 | —                    | ✔            |

---

## 附录 C：双方言惯用法与纪律

**四段式（租户化迁移标准模式）**：

1. `ADD COLUMN tenant_id BIGINT NULL` — 先 nullable
2. `UPDATE ... SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default')` — scalar 子查询回填
3. `DROP INDEX uk_x ON <t>` + `CREATE UNIQUE INDEX uk_tenant_x ON <t>(tenant_id, ...)` — 唯一键切换
4. `MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1` — 收紧 + 过渡默认

**方言纪律**：`DROP INDEX ... ON <table>`（MySQL 形态，H2 MODE=MySQL 兼容）；不用 `CREATE INDEX IF NOT EXISTS`（MySQL 8
不支持）；不用 `COMMENT`/`UNSIGNED`/`ON UPDATE`/内联 `INDEX`/`AFTER`。

---

*v0.1 · 2026-10-12 · 归档 V1–V35 全部迁移；V31 新增 sys_tenant_llm_profile 表（P4-T4 per-tenant LLM profile）。*
