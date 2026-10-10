# SaaS 化改造 · 数据迁移总结（V23 → V30）

> 汇总 Knowledge_Repository SaaS 多租户改造迄今落地的所有 Flyway 迁移：版本、任务锚点、动作、方言注意点、验证方式与残余项。
> 权威决策以 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 为准，逐 Phase 门禁以对应 go-checklist 为准。
> 本文档为**归档与操作参考**，非新决策来源。

---

## 0. 适用范围与口径

本总结只覆盖 **租户脊柱 + 生命周期** 相关的 DDL/种子迁移（V23 起），V1–V22 属单租户基线，不在本文档。截至 v0.1：

- 已交付迁移版本：`V23`–`V30`（8 个版本）
- 关联任务：P1-T1~T10（租户脊柱）+ P3-T1~T5（生命周期）+ P3-T7（OIDC，无迁移）+ P4-T1/T3（本轮）
- 兼容方言：**H2 `MODE=MySQL;DATABASE_TO_LOWER=TRUE`** 与 **MySQL 8** 双跑；每次迁移落地必须 `FlywayMigrationSmokeTest`（H2）与 `TenantIsolationIntegrationTest`（Testcontainers MySQL 8）同时通过
- 三闸门（saas/platform/oidc）默认全 `false`，本目录所有迁移在闸门关闭时也必须能无损应用（红线 #11 回退）

## 1. 迁移时间线（逐版本）

### V23 — `create_sys_tenant.sql`（P1-T1，租户脊柱地基）

建平台元数据表 `sys_tenant` 与 `sys_tenant_placement`（POOL/SILO 放置策略），插默认租户 `default` + POOL 放置种子。种子保证 `saas.enabled=false` 时单租户语义可平滑回退（运行时只有 default 租户）。列命名对齐既有库（`create_time`/`update_time`），非提案示意的 `created_at`。`sys_tenant`/`sys_tenant_placement` 天然无 `tenant_id` 列（自引用悖论），后续进 IGNORE_TABLES。

### V24 — `add_tenant_id_columns.sql`（P1-T4，15 张业务表加租户列）

15 张业务表（`kb_document`/`kb_document_chunk`/`kb_document_image`/`kb_category`/`kb_exam_*`/`kb_student*`/`kb_writing_history`/`sys_user`/`sys_role`/`sys_user_role`/`sys_department`）加 `tenant_id BIGINT NULL`，为每张表建单列索引 `idx_<t>_tenant`，然后 scalar 子查询回填：

```
UPDATE <table> SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default');
```

**口径**：先 nullable + 回填 default 让 `saas.enabled=false` 阶段行为零变化；NOT NULL 收紧与复合索引重建留给 V26（P1-T6）；唯一键切换在 V25（P1-T5）。不假设 `id=1` 常量，纯 ANSI。

**刻意排除**：`sys_config`（P4-T1 才租户化）与 `kb_extraction_cache`（P4-T3 才租户化）此处不加列，避免跨 Phase 漂移。V30 落地后本条排除已完成使命。

### V25 — `switch_unique_indexes.sql`（P1-T5，唯一键从"全局唯一"切"租户内唯一"）

6 张表复合唯一键切换：`kb_category(name)`、`kb_student(username)`、`kb_writing_history(session_id)`、`kb_exam_history(session_id)`、`kb_exam_session(session_key)`、`kb_exam_question(session_key, question_number)` → 全部改为 `(tenant_id, <原键>)` 形态。

**语义**：单租户时代全局唯一；多租户下不同租户应各自拥有同名/同键业务对象。切换前 V24 已把全部存量行回填到 default 单一租户，此刻不存在跨租户重复，加复合唯一与旧全局唯一等价，安全无损。真正引入第 2 个租户在 Phase 3。

**保留全局唯一**（不切）：`kb_document_image.asset_key`（内容哈希去重）、`kb_document_image(document_id, sha256)`、`kb_student_session.session_token`（UUID）、`sys_user_role(user_id, role_id)`（两端已随各自租户）。

**方言**：`DROP INDEX uk_x ON <table>`（MySQL 形态），H2 MODE=MySQL 兼容；`CREATE UNIQUE INDEX` 双方言均已验证。

### V26 — `make_tenant_id_not_null.sql`（P1-T6，NOT NULL + 过渡默认值）

15 张业务表 `tenant_id` 从 nullable 收紧为 `NOT NULL DEFAULT 1`。

**为什么带 DEFAULT**：`saas.enabled=false` 阶段 P1-T7 的 `TenantLineInnerInterceptor` 尚未注册，存量写路径不会显式填 `tenant_id`；只 NOT NULL 无默认会让所有 INSERT 违约、单租户功能立即崩。`DEFAULT 1` 指向 V23 种子的默认租户 id（fresh 库恒为 1，`FlywaySmokeTest` 断言 `default` id==1 兜底）。P1-T7 上线后由拦截器显式写入真实租户，本 DEFAULT 退化为"漏填兜底"。

**冗余索引清理**：V25 已为 6 张表建 `UNIQUE(tenant_id, key)`，其最左前缀即 `tenant_id`，足以服务拦截器等值过滤，V24 的这 6 张单列 `idx_<t>_tenant` 遂冗余，此处 DROP 以降写放大。其余 9 张无 tenant 前导唯一的表保留单列 tenant 索引。

### V27 — `provision_state_machine.sql`（P3-T2，租户开通状态机）

`sys_tenant.status` VARCHAR(16) 扩宽取值域加 `PENDING`/`PROVISIONING`/`FAILED`（原 `ACTIVE`/`SUSPENDED`/`ARCHIVED`/`DELETED` 保留），`COMMENT` 补齐；新加"当前 `provision_request_id`"列。新建 `sys_tenant_provision_step` 步骤审计表：每次开通把"创建租户"从单事务写入拆成可幂等重放的多步编排，每步一条 step 行。

**决策锚点**：Phase 3 清单 §3 **S-5 = 方案 A**（UUID v4 `provision_request_id` + Flyway 版本号天然幂等 + `hasCollection` 前置查 + upsert + FAILED 可查 `error_detail`）。

**方言纪律**：不用 `CREATE INDEX IF NOT EXISTS`（H2 支持、MySQL 8 不支持），改由 Flyway 版本号幂等保证重放安全。

### V28 — `tenant_scope_sys_config_and_invite.sql`（P3-T3，C-4=方案 A 提前 P4-T1）

三件事合成一个迁移：

- `sys_config` 加 `tenant_id NOT NULL DEFAULT 1`，唯一键 `(config_key)` 改 `(tenant_id, config_key)`。V21 存量 6 行 + 运行期新增由 DEFAULT 1 全部落 default，与既有单租户读路径等价。从此 `KnowledgeTenantLineHandler` 白名单同步移除 `sys_config`；`SystemConfigGatewayImpl` 显式追加 `WHERE tenant_id=?`（不依赖拦截器，保 `saas.enabled=false` 阶段行为一致）。
- 新增 `sys_user_invite`：一次性设密邀请表，P3-T7 邀请式设密回访子切片消费。`tenant_id` 在此表是"被邀请加入的租户"业务列，非过滤维度，`UserInviteGatewayImpl` 显式加谓词；本表进白名单。
- `sys_user.must_change_password` 列：支撑 P3-T7 邀请式设密首登强制改密。

**为什么提前 P4-T1**：Phase 3 清单 §5 **C-4 = 方案 A**（提前 P4-T1 到 P3-T3 内交付），使"建库默认配置与根部门 + 首位 admin"的开通链路有数据基座。

### V29 — `tenant_archive_and_deletion.sql`（P3-T5，S-4=方案 A）

四件事：`sys_tenant` 加 `archived_at` / `deleted_at` / `deletion_request_id` 三列；新建 `sys_tenant_delete_approval`（一次性删除审批令牌）、`sys_tenant_deletion_step`（步骤级幂等硬删续跑审计）、`sys_audit_log`（高敏操作审计）三张平台端表；`sys_tenant_deletion_step` 建复合唯一 `uk_deletion_step(tenant_id, deletion_request_id, step_name, attempt_no)` 承载步骤级幂等；三张新表因 `tenant_id`/`tenant_key` 均是"被操作的租户"业务列（非过滤维度），进 IGNORE_TABLES 并由各自 `GatewayImpl` 显式追加谓词。

**决策锚点**：Phase 3 清单 §3 **S-4 = 方案 A**（Archive 30 天保留期 + 项目负责人临时 token 双人复核 + 审计落 `sys_audit_log`；文件层物理清理受 P5-T1 MinIO 约束延后，本期只落 DB / Milvus 两处）。

### V30 — `tenant_scope_extraction_cache.sql`（P4-T3，修复 G7 跨租户解析缓存泄漏）

`kb_extraction_cache` 表加 `tenant_id` 列并把唯一键从全局 `uk_extraction_cache_key(checksum, strategy, model_name, prompt_hash, render_mode)` 切换为 `uk_extraction_cache_tenant_key(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)`。V30 把 V24→V25→V26 的三段口径**合一**处理这一张在 V24 被刻意排除的表：加 nullable 列 → scalar 子查询回填 default → 切换唯一键 → 收紧 `NOT NULL DEFAULT 1`。

**修复面**：G7 泄漏源自 A 租户已解析过的同一文件（内容摘要 + 策略 + 模型 + 提示词摘要 + 渲染模式一致），B 租户命中缓存直接读到 A 的 `result_json`。V30 + `KnowledgeTenantLineHandler` 白名单同步移除 + `ExtractionCacheDO` 加 `tenantId` 字段 + 拦截器自动追加谓词与列值，端到端把泄漏闭合。

**刻意保留**：`idx_extraction_cache_checksum`（普通索引，非约束，不构成读泄漏）不删，避免扩大 scope。

## 2. 双方言与四段式惯用法

所有租户化迁移均遵循同一模式，未来加租户维（如 Phase 4 潜在新增表）应保持口径：

**四段式（分步或合一，单表可合一）**：

1. `ALTER TABLE <t> ADD COLUMN tenant_id BIGINT NULL` — 先 nullable 避免存量违约
2. `UPDATE <t> SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key = 'default')` — scalar 子查询回填，不假设 id 常量
3. 唯一键切换：`DROP INDEX uk_x ON <t>` + `CREATE UNIQUE INDEX uk_tenant_x ON <t>(tenant_id, <原键>)` — 存量已同属一租户，切换无损
4. `ALTER TABLE <t> MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1` — 收紧并留过渡默认

**方言纪律**：

- `DROP INDEX ... ON <table>`（MySQL 形态），H2 MODE=MySQL 兼容；不用 `ALTER TABLE ... DROP INDEX`（H2 不稳）
- `CREATE UNIQUE INDEX` 双方言均支持；不用 `CREATE INDEX IF NOT EXISTS`（MySQL 8 不支持）
- `MODIFY COLUMN ... NOT NULL DEFAULT 1` 双方言均支持
- 不用 `COMMENT`/`UNSIGNED`/`ON UPDATE`/内联 `INDEX` 保持基线一致（V19 起约定）
- `information_schema` 查询用 `LOWER()` 归一化（H2 `DATABASE_TO_LOWER=TRUE`）

**白名单联动**：每次迁移把某表加入租户维度时，`KnowledgeTenantLineHandler.IGNORE_TABLES` 与 Javadoc 同步更新；平台元数据 / 业务列 tenant 语义（非过滤维度）的表**保留**在 IGNORE_TABLES 且由 `GatewayImpl` 显式加谓词。

## 3. 租户白名单（IGNORE_TABLES）演进

| 版本 | 加入白名单 | 移出白名单 |
| ---- | ---------- | ---------- |
| P1-T7 初版 | `sys_tenant`、`sys_tenant_placement`、`flyway_schema_history`、`kb_extraction_cache` | — |
| P3-T2（V27 配套） | `sys_tenant_provision_step` | — |
| P3-T3（V28 配套） | `sys_user_invite` | `sys_config` |
| P3-T4 | — | — |
| P3-T5（V29 配套） | `sys_tenant_delete_approval`、`sys_tenant_deletion_step`、`sys_audit_log` | — |
| P4-T3（V30 配套） | — | `kb_extraction_cache` |

当前白名单：`sys_tenant`、`sys_tenant_placement`、`sys_tenant_provision_step`、`sys_tenant_delete_approval`、`sys_tenant_deletion_step`、`sys_audit_log`、`sys_user_invite`、`flyway_schema_history` — 共 8 张，全部为平台元数据 / 业务列非过滤维度 / Flyway 内部。

## 4. 验证与门禁

**每次迁移落地必须同时通过**：

- `FlywayMigrationSmokeTest`（H2 MODE=MySQL）— 干净库回放 V1→V<max>，断言 `EXPECTED_MIGRATION_COUNT`、最终版本号、`flyway_schema_history` 成功记录数、种子行（`default` 租户 `id==1` / 状态 `ACTIVE` / placement `POOL`）、逐版本抽查关键列与复合唯一索引（含"旧索引已消失"的反向断言）
- `TenantUniqueKeyDuplicationTest`（H2 原生 SQL）— 逐表验证"跨租户同键共存 / 同租户同键被拒"语义
- `ExtractionCacheTenantScopeMigrationTest`（H2，V30 起）— 缓存表专项
- `TenantIsolationIntegrationTest`（Testcontainers MySQL 8）— 拦截器 SELECT / SELECT by id / UPDATE / DELETE / INSERT 五条路径端到端；`disabledWithoutDocker=true`
- `ExtractionCacheTenantIsolationIntegrationTest`（H2 + 拦截器挂载，V30 起）— G7 行为闭合
- `TenantProvisioningIntegrationTest` / `TenantProvisioningInviteIntegrationTest` / `TenantSuspendResumeIntegrationTest` / `TenantArchiveDeleteIntegrationTest` — Phase 3 生命周期端到端
- 全 reactor `./mvnw -B test checkstyle:check spotless:check` EXIT 0，`BUILD SUCCESS`

**新增迁移的 canonical 计数**：`FlywayMigrationSmokeTest.EXPECTED_MIGRATION_COUNT` 常量 + `@DisplayName` 版本区间 + 最终版本断言 + 幂等注释四处必须同步更新。V30 落地即从 29 → 30。

## 5. 已闭合的数据出口泄漏与残余项

**已闭合**：

- 提案 §11 **G7**（跨租户解析缓存泄漏）— V30 端到端修复
- 提案 §9 单租户业务键全局唯一导致的跨租户互斥 — V25 已放开为租户内唯一
- 生命周期审计与硬删幂等基座 — V27/V29 已就位
- `sys_config` 读取按租户作用域 — V28 已加列并切唯一键

**残余（受 Phase 4 门禁未启）**：

- `sys_user.password_hash` 密钥保管（B-2，P4-T4 per-tenant LLM profile 加密字段依赖）— 待 KMS/主密钥决策签署
- 配置缓存跨节点失效广播（B-1 Redis pub/sub，P4-T2）— 待 Redis 引入方案签署
- Milvus partition-key 切换 / SILO 逐库 runner（Phase 3 尾分支，B-2 cluster 扩容 + 备份硬前置）— 见 [saas-phase3-b2-cluster-backup-plan.md](saas-phase3-b2-cluster-backup-plan.md)

## 6. 未来迁移占位（V31+ 候选场景）

按当前 Phase 4/5 排产，可能出现的新迁移：

- **V31（候选，P4-T4）**：per-tenant LLM / embedding profile 表 + `SecretCipher` 密文列 + checksum 加盐字段
- **V32（候选，Phase 5）**：MinIO 对象存储元数据表 + `kb_document_image.storage_backend` 列（承接 P5-T1）
- **V33+**：SILO placement 相关元数据（承接 Phase 3 尾分支 P3-T6，视 B-2 决策可能落在独立命名段）

新增迁移必须继续遵守 §2 四段式与 §4 验证清单，且同步更新 `EXPECTED_MIGRATION_COUNT`。

## 7. 相关文档

- 权威决策：[saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)
- Phase 门禁：[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) · [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) · [saas-phase4-go-checklist.md](saas-phase4-go-checklist.md)
- Phase 3 尾分支决策包：[saas-phase3-b2-cluster-backup-plan.md](saas-phase3-b2-cluster-backup-plan.md)
- 会审决策建议一页纸：[saas-phase3-review-brief.md](saas-phase3-review-brief.md)
- 排产：[saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)
- 数据库与迁移细则：[data-and-migration-guideline.md](data-and-migration-guideline.md)
- 安全红线：[security-guideline.md](security-guideline.md)

---

*v0.1 · 2026-10-10 · 归档 V23–V30 全部迁移；随每次新增迁移递增版本并回读 §3 白名单表 + §4 计数四处同步纪律。*
