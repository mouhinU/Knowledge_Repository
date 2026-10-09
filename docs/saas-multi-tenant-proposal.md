# SaaS 化改造 · 变更提案（Multi-Tenant Transformation Proposal）

> 状态：**已批准 · 执行中（Phase 0/1/2 已完成交付归档；Phase 3 全量授权开工 2026-10-09——P3-T1~T4 已交付，P3-T5/T6/T7 已解锁）**
> 依据：根目录 `AGENTS.md` §0 红线 #11「重大框架/架构变更须提交变更提案（影响评估、回退计划、测试覆盖）并经架构/负责人审批」。本文件即该提案。
> 审批记录：项目负责人 mouhinU 于 2026-09-29 以 Phase 0 同款「口头同意先行」方式授权进入 Phase 1（运行时仅 `default` 租户、开关默认关闭、行为与主干等价可回退）；2026-09-30 同模式追加 Phase 2 开工授权（S-1 方案 B + S-2 方案 b），并于同日完成 P2-T1~P2-T5 交付归档。**2026-10-09 Phase 3 全量授权**：五方书面齐签（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星），运维/基础设施侧 B-1/B-2/B-3 三项硬门槛已正式书面承接（现网 Milvus 2.5.4 standalone 建/drop 集合权限已备；cluster 扩容 + 备份方案列为 P3-T5 起前硬前置条件），清单升 v0.3、解锁 P3-T5/T6/T7；全程维持双门控 `knowledge.saas.enabled=false` + `knowledge.platform.enabled=false`。详见 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) §6 签署页 + §7 第 13 条。
> 版本：v0.4（2026-10-09） · 作者：QoderWork（调研） · 审批：五方书面齐签（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星）；Phase 3 全量授权开工
> 基线技术栈：COLA 5.0 · Spring Boot 3.4 / Java 21 · MyBatis-Plus 3.5.17 · LangChain4j 1.0.1 · Milvus 2.5.4 · MySQL 8 + H2 · Flyway
> 配套：**任务级拆解见** [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（提案批准后按此执行）

---

## 0. 决策摘要（本轮已锁定）

| 维度 | 选定方案 | 说明 |
|---|---|---|
| 隔离模型 | **混合模式（Hybrid）** | 中小租户走「共享库 + `tenant_id` 列」（pool），少数大/合规租户走「独立库」（silo/DB-per-tenant），由租户放置表路由。 |
| 改造范围 | **全量**：数据+鉴权+向量隔离 · 租户开通生命周期 · 配额计费 · 水平扩展运维 | 四项全选，故本提案覆盖四个工作流（WL-A/B/C/D）。 |
| 租户定义 | **租户 = 公司/组织（B 端）** | 一个付费组织即一个 tenant；现有 `department` 降级为**租户内部**的组织单元（院系/年级），不再充当租户边界。 |
| 本轮产出 | **变更提案文档** | 即本文件；审批后再按路线图分阶段编码。 |

> 关键前提：**当前代码库零租户概念**。全库 `grep tenant/organization/org_id` 零命中，现有隔离是「单组织内 RBAC + 文档级 ACL」。本次是从「单租户应用」到「多租户平台」的架构跃迁，非局部增强。

---

## 1. 现状与缺口（Evidence-Based）

### 1.1 现有隔离资产（可复用）

- **文档 ACL 四元组**：`kb_document.visibility(PUBLIC/INTERNAL/RESTRICTED/PRIVATE) + owner_id + department_id + allowed_roles`，领域判定 `PermissionDomainService.hasAccess(...)` 已完整且有测试。
- **检索出口统一收口**：`AuthorizedSearchSupport` 是所有语义检索的必经后置过滤点——租户过滤天然可在此叠加。
- **清晰接缝**：`VectorStoreGateway`、`SystemConfigGateway` 等 domain 端口已存在；租户上下文可经「Filter/Interceptor → TenantContext → Gateway 参数」穿透。
- **令牌链路可注入**：Admin JWT（`AdminJwtGatewayImpl`，手写 HS256）与 Student DB 令牌（`kb_student.session_token`）注入点明确，便于加 `tenant` claim。

### 1.2 缺口清单（改造必修）

| # | 缺口 | 证据 | 归属工作流 |
|---|---|---|---|
| G1 | 15 张业务表 0 个租户列 | V1–V21 全部迁移、所有 `*DO` | WL-A |
| G2 | 无 MyBatis-Plus 拦截器体系（无 `MyBatisPlusConfig`/`TenantLineInnerInterceptor`/分页插件） | 全库 grep 零命中 | WL-A |
| G3 | 单一 Milvus collection `knowledge_chunks`，metadata 无租户键；`buildFilter` 对 ACL 直接 `return null` | `MilvusVectorStoreService.java:193-200`、`application.yml` | WL-A |
| G4 | 关系完整性无外键，`exam_answer/exam_question` 无归属列 | V1/V6/V11 DDL | WL-A |
| G5 | 角色链路未接线：`AdminPrincipalSupport.toPermission` 恒传 `roles=null, admin=true` | `AdminPrincipalSupport.java:36` | WL-A/安全 |
| G6 | 列表查询默认全局可见：`ExamHistoryGatewayImpl.listPublished/listPage` 无 user/dept/tenant 谓词 | `ExamHistoryGatewayImpl.java:60-118` | 安全 |
| G7 | 全局共享资源无租户命名空间：`sys_config`（config_key 全局唯一 + JVM 内存缓存）、`kb_extraction_cache`（按 checksum 跨租户命中=内容泄漏）、单 JWT secret | V21、V19 DDL | WL-A/D |
| G8 | 学生令牌模型不适配多租户：单列落库、互踢、无 tenant 上下文 | `StudentLoginCmdExe.java:53-57` | WL-B |
| G9 | `Permission` 值对象无 `tenantId` 字段 | `Permission.java` | WL-A |
| G10 | SearchCmd 身份可伪造（`userId/departmentId/isAdmin` 来自请求体而非令牌回读），多租户下即横向越权入口 | `KnowledgeSearchQryExe.java:55-57` | 安全（**改造前必修**） |
| G11 | 单实例假设遍布：固定 collection/`container_name`/端口/bind mount、`AbstractRoutingDataSource` 缺失、上传会话在 JVM 内存 `ConcurrentHashMap`、`SystemConfig` 进程内缓存脑裂 | compose + `UploadSessionManager` + `SystemConfigService` | WL-D |
| G12 | 对象存储缺位：文件走本地磁盘扁平 UUID 命名，无租户 prefix；MinIO 仅 Milvus 内部依赖，应用不可控 | `DocumentIngestionSupport.copyToStorage()` | WL-D |

> G5/G6/G10 属**改造前就应修复的存量安全项**，建议作为 Phase 0 先行（见 §7）。

---

## 2. 目标架构：混合多租户模型

### 2.1 租户放置策略（Placement）

新增平台级元数据库/表 `sys_tenant` + `sys_tenant_placement`：

- `sys_tenant`：`tenant_id (PK)`, `tenant_key`, `name`, `plan`, `status(ACTIVE/SUSPENDED/ARCHIVED/DELETED)`, `data_region`, `created_at`。
- `placement_type`：`POOL`（共享库）或 `SILO`（独立库）。判定规则（可配）：合同/合规要求 SILO → SILO；否则默认 POOL；POOL 租户数据量或 QPS 越阈值 → 触发 **silo 迁移**（见 §5.3）。

```
        ┌──────────────────────────────────────────────┐
Request │ TenantResolverFilter (从 JWT claim / 域名 /   │
        │ header 解析 tenantId → TenantContext)          │
        └───────────────┬──────────────────────────────┘
                        ▼
        ┌──────────────────────────────────────────────┐
        │ DataSourceRouting (AbstractRoutingDataSource) │
        │   POOL  → primary shared DataSource           │
        │   SILO  → 该租户独立 DataSource（注册表懒加载）│
        └───────────────┬──────────────────────────────┘
                        ▼
   POOL 路径：MyBatis TenantLineInnerInterceptor 自动注入 tenant_id
   SILO 路径：独立库/独立 collection，无需列过滤（仍可保留做纵深）
```

### 2.2 租户上下文传播（TenantContext）

- domain 层新增 `TenantContext`（`ThreadLocal`，或迁移至 JDK 21 `ScopedValue`；异步/虚拟线程需用 `ContextSnapshot` 装饰 `Executor`，防止线程池串租户）。
- **红线合规**：`ScopedValue`/`ThreadLocal` 的清理必须置于 `finally`，异步用例（出卷/文章/索引的 `CompletableFuture.runAsync`）必须显式传递租户快照，否则跨租户写入。现有 `ExamGenerationSupport`/`ArticleGenerationSupport` 的线程池提交点都要包装。
- 严禁在 `@Async`/定时任务（`ExamGradingScheduler`）里隐式依赖上下文，需显式带 `tenantId` 参数并遍历租户。

### 2.3 数据模型改造（WL-A）

**共享表（POOL）统一加 `tenant_id BIGINT NOT NULL` + 复合索引**，覆盖：
`sys_user`, `sys_department`, `kb_document`, `kb_document_chunk`, `kb_student`, `kb_exam_history`, `kb_exam_session`, `kb_exam_answer`, `kb_exam_question`, `kb_writing_history`, `kb_document_image`, `sys_config`, `kb_category`, `kb_user_role`(经 user 间接) 等 15 表。

- 唯一约束从全局唯一改回**租户内唯一**：`kb_document.document_key`、`sys_user.username`、`kb_student.username`、`sys_config.config_key` 等由 `UNIQUE(x)` → `UNIQUE(tenant_id, x)`。
- 逻辑删除列补齐（现 `deleted` 字段 DO 缺失），配合租户隔离做软删归档。
- `sys_tenant` 与平台管理员表 `sys_platform_admin` 属**平台级**，不加 tenant_id（它们是租户的容器）。

**MyBatis-Plus 改造**：

- 新增 `MyBatisPlusConfig`，装配 `MybatisPlusInterceptor`：`TenantLineInnerInterceptor`（`TenantLineHandler` 用 `TenantContext.getTenantId()`，ignore 列表登记 `sys_tenant`/`sys_platform_admin`/Flyway 表）置于 `PaginationInnerInterceptor` 之前（当前无分页插件，顺序负担小）。
- **DDL/迁移与 SQL 规范红线**：所有手写 `Mapper XML`/`${}` 拼接（尤其动态列名/排序）必须白名单校验 + 强制带 `tenant_id` 谓词；DO 不越 infrastructure。

### 2.4 向量库隔离（WL-A，Milvus）

混合模型下两条路径：

- **POOL 租户**：collection `knowledge_chunks` 增加标量字段 `tenant_id`，建 **partition-key = tenant_id**（Milvus 2.5.x 支持 partition key isolation），检索强制带 `tenant_id` 等值过滤。同时**修复 G3**：把租户条件真正**下推**到 LangChain4j `Filter`（`IsEqualTo("tenant_id", ...)`），复杂 ACL 维持应用层后过滤。
- **SILO 租户**：独立 collection（命名 `kc_{tenantKey}`）或独立 Milvus database，物理隔离。
- `EmbeddingStore` 现为进程级单例 bean（collection/database 静态 `@Value`）。**必须重构为 `EmbeddingStoreProvider` 注册表模式**，运行时按 `tenantId` 解析目标 store/collection；这是改造量最高的基础设施点（障碍等级：高）。
- over-fetch 后过滤性能随租户数据量放大，下推 partition-key 是主要缓解手段；需本地冒烟验证 Milvus 2.5.4 partition-key 与 bge-m3 1024 维的召回正确性。

### 2.5 文件与对象存储隔离（WL-D）

- 引入真正对象存储（可用现有 MinIO，改造为应用侧 SDK 直连），bucket/`prefix = {tenantKey}/documents/...`、`{tenantKey}/exam-assets/...`；替代本地磁盘扁平 UUID。
- 上传会话（`UploadSessionManager` 内存 `ConcurrentHashMap`）**外置到 Redis**（带 `tenant:` 前缀与 TTL），否则多副本即失效。
- `/api/exam/assets/{assetKey}` 当前靠随机 assetKey 放行的文件出口，需叠加租户校验（防跨租户读图），保留 filesystem-oracle 路径归一化防护。

### 2.6 鉴权与身份改造（WL-B）

- **三层身份主体**：平台管理员（`sys_platform_admin`，跨租户运维/开通）→ 租户管理员（`sys_user.is_admin`，限本租户）→ 租户内用户（教师/学生）。
- **JWT 加 `tenant` claim**：`AdminJwtGatewayImpl.issue/verify` 载荷扩 `{sub, username, admin, tenantId, roles, iat, exp}`；`AdminPrincipalSupport.toPermission` 从令牌回读 `tenantId` 与**真实 roles**（修复 G5），不再恒 `admin=true`。
- **学生令牌**（G8）：从单列 `session_token` 改为独立会话表（支持多会话/吊销/限流），令牌自包含或哈希存储并携带 `tenantId`。
- **租户解析入口**（择一，建议域名+claim 双验）：`{tenant}.example.com` 子域 → 反查 placement；或 `X-Tenant-Id` 头且必须与 JWT claim 一致（不一致拒绝，防伪造）。
- **修复 G10**：所有查询身份只信令牌回读，禁止从请求体取 `userId/isAdmin`。

### 2.7 全局配置与缓存加租户维度（WL-A/D）

- `sys_config`：`config_key` 全局唯一 → `(tenant_id, config_key)`，支持「平台默认 + 租户覆盖」两级；缓存 key 加租户维度并改**分布式失效**（Redis pub/sub 或版本号），消除进程内脑裂。
- `kb_extraction_cache`：键加 `tenant_id`，杜绝跨租户命中导致内容泄漏（G7）。
- `knowledge.llm.*`：由全局一套 → 平台默认 + 租户级覆盖（`{api_key, base_url, model}` 加密存储），支撑 per-tenant 供应商/配额。
- JWT `secret`：全局一套保留（仅签票，claim 已含 tenant），或 per-tenant 派生（更强隔离但复杂，列为可选增强）。

### 2.8 水平扩展与运维（WL-D）

- 无状态化：上传会话、`sys_config`/`extraction_cache` 进程内缓存全部外置（§2.5/§2.7），方可多副本。
- 去固定化：`docker-compose` 移除 `container_name`/固定端口冲突、副本化 `knowledge-app`（N 实例 + 网关），Milvus 视租户量考虑 cluster 模式。
- 定时任务（`ExamGradingScheduler`）改为「遍历租户 + 分片」，避免全局串行与租户饥饿。

---

## 3. 租户生命周期（WL-B：开通/停用/删除）

新增平台管理端 `PlatformTenantController`（`/api/platform/**`，平台管理员鉴权）：

- **开通（Provisioning）**：建 `sys_tenant` → 按 placement 决定「仅打 pool 标签」或「异步建独立库/collection/bucket」→ 落默认 `sys_config`（租户级）→ 建租户根部门 → 建首个租户管理员（引导邮件/首登改密）。开通为**幂等 + 可重放**（失败可安全重试，用状态机 `PENDING→PROVISIONING→ACTIVE/FAILED`）。
- **停用（Suspend）**：`status=SUSPENDED`，鉴权层拒绝该租户所有请求，数据保留。
- **归档/删除（Archive/Delete）**：软删 → 保留期 → 物理清理（逐租户遍历 `kb_document`→Milvus 删向量→对象存储删文件→级联删 DB 行）。因 G4 无外键，删除靠应用层编排 + 后台对账任务，需**dry-run 预览 + 双人复核**。
- 生命周期动作全部进 `BlackboardAuditLog` 同源审计 + 指标。

---

## 4. 配额与计费（WL-C：计量/套餐/限额）

- **计量表** `tenant_usage`（`tenant_id`, `metric`, `period`, `value`）：文档数、向量 chunk 数、存储字节、LLM chat/embedding token、出卷/文章调用次数、学生活跃数。
- **采集点**：在 infra 边界（向量写入、LLM 网关、文件落库、出题执行器）埋 `TenantUsageRecorder`（复用现有 Micrometer 基建，低基数 tag 加 `tenant` 需谨慎——`tenant` 基数高，计量走独立计数/落库，**不进 Prometheus 高基数标签**）。
- **套餐/限额** `tenant_plan_limit`（`tenant_id`, `metric`, `quota`, `policy=BLOCK|THROTTLE|ALERT`）；在写入类用例前置 `QuotaGuard` 判定，超限按策略拦截或降级。
- **计费对接**（可选）：预留聚合导出接口，具体支付供应商集成列为后续里程碑（本提案不含支付实现）。

---

## 5. 数据迁移与回填（关键风险区）

### 5.1 存量单组织数据 → 默认租户

- 新增 Flyway **平台基线迁移**：建 `sys_tenant` 并插入一条 `tenant_key='default'`；所有业务表 `ADD COLUMN tenant_id`（先 nullable + 默认回填 `default`，再 `NOT NULL` + 复合索引）。
- **迁移顺序**：schema 变更（加列，向后兼容）→ 代码灰度（读带 tenant、写赋 tenant）→ 唯一索引切换（全局唯一→租户内唯一，需先查重）。H2（测试）与 MySQL（生产）双方言都要覆盖，遵 `data-and-migration-guideline.md`。

### 5.2 SILO 独立库的 Flyway 策略

- 独立库需**逐库执行同一套迁移**：引入 `TenantMigrationsRunner`，对每个 SILO 租户的数据源跑 Flyway（错峰、可续跑、失败隔离）。POOOL 库仍走主迁移链。
- 版本号与主链对齐，避免 schema 漂移；提供「租户 schema 版本一致性」体检任务。

### 5.3 POOL→SILO 提升迁移

- 触发后：新建独立库 → 双写窗口 → 数据搬迁校验（行数/checksum 抽样）→ 路由切流 → 回收 pool 中该租户数据。回退窗口保留原 pool 数据直至验收。

---

## 6. 影响评估（红线 #11 必备）

- **侵入面**：约 **94 个端点 / 17 个 controller** 需携带租户上下文（除 2 个登录前 auth 端点）；15 张表 ALTER；`EmbeddingStore` 单例重构为 provider；新增平台/租户/配额三组表与拦截器链。
- **性能**：POOL 后过滤放大风险（over-fetch×3+10，上限 500）→ 以 partition-key 下推缓解；`AbstractRoutingDataSource` 每请求一次放置解析（本地缓存 placement，低频刷新）；Milvus cluster/多 collection 运维成本上升。
- **兼容性**：加列 + 默认租户回填可**向后兼容**上线（先双读单写/读旧写新，再收敛）；但唯一索引由全局改租户内，需先消除跨租户重复键，属**破坏性变更点**，安排在代码灰度之后。
- **安全**：G5/G6/G10 若不先修，加租户后越权面反而扩大；混合模型的 SILO 凭据管理、跨租户运维接口是新增高危面，需安全评审。
- **测试成本**：新增「跨租户隔离负向用例」为必测项（见 §8），全链路每个数据出口都要断言「A 租户取不到 B 租户数据」。
- **组织/协作**：需架构 + 安全双签；前端（admin/student/ai-exam 等）需配合传租户头或经子域解析；运维需具备多副本 + 对象存储 + Redis + 可能的 Milvus cluster 能力。

---

## 7. 分阶段实施路线图

> 建议按工作流分阶段、每阶段独立可回退、独立提交与冒烟，延续 `extraction-strategy-refactor-plan.md` 的 Phase 账本风格。

| 阶段 | 目标 | 关键交付 | 可独立发布 |
|---|---|---|---|
| **Phase 0（安全地基）** | 先修存量越权 | G10 身份只信令牌、G5 roles 接线、G6 列表加谓词、路径归一化 | ✅（不改租户语义） |
| **Phase 1（租户脊柱）** | 单租户语义打穿 | `sys_tenant`+默认租户、`TenantContext`、`MyBatisPlusConfig`+`TenantLineInnerInterceptor`、15 表加 `tenant_id`+回填、JWT 加 tenant claim、resolver filter | ✅（仅 default 租户运行） |
| **Phase 2（向量隔离）✅ 已交付（2026-09-30）** | Milvus 租户化（S-2 方案 b） | `EmbeddingStoreProvider` 骨架 + `metadata.tenant_id` 写入（Long）+ `IsEqualTo` JSON 谓词下推（`buildFilter` 永不 null）+ SILO 命名工具静态校验 + 跨租户负向 `@SpringBootTest` + CI `tenant-isolation` job；🚧 **partition-key 建集合 / SILO 独立集合生命周期 / 万级时延阈值 P95 / 运维 B 类正式签署 转 Phase 3** | ✅（POOL 单 store 先行） |
| **Phase 3（生命周期 + partition-key/SILO 引入）🚀 全量授权开工（2026-10-09，清单 v0.3）** | 多租户自助 + POOL 目标态落地 | [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) v0.3（18 项 B/S/T/C 全闭环）· 平台管理端 + provisioning 状态机 + suspend/archive/delete + SILO 建集合 + `io.milvus` SDK partition-key `knowledge_chunks_pk`（P3-T5 合并切换，S-2=方案 c）+ 逐库 Flyway（T-3=方案 A collection-only）+ 万级时延阈值 P95 复测 + CI 混跑门禁 + **企业 IdP OIDC 接入（P3-T7，C-3=方案 B）**；P3-T1~T4 已交付（`372994a`/`55a0872`/`73b0626`/`eece61a`），P3-T5/T6/T7 已解锁 | ✅ B-1/B-2/B-3 运维已书面签署（cluster 扩容 + 备份列为 P3-T5 前置）；P3-T1~T4 已交付 |
| **Phase 4（配置/缓存/LLM 租户化）** | 去全局化 | `sys_config`/`extraction_cache` 加租户维+分布式失效、per-tenant LLM 配置 | ✅ |
| **Phase 5（对象存储+无状态）** | 可水平扩展 | MinIO prefix、上传会话外置 Redis、去固定名 compose、副本化 | ✅（WL-D） |
| **Phase 6（配额计费）** | 商业化 | `tenant_usage` 采集、`QuotaGuard`、套餐限额、计费导出 | 可选后置 |

依赖：0→1→{2,4}→3→5→6；2/4 可与 3 并行准备。

---

## 8. 测试策略（红线 #11 必备）

- **跨租户隔离负向用例（核心）**：对每个数据出口（检索、出题、文章、错题、文档 CRUD、考试作答/评分、配图访问、sys_config 读写）编写「A 建 → B 查不可见/403」的断言，纳入 CI 门禁（参照 `testing-guideline.md` 负向用例风格）。
- **拦截器/路由**：`TenantLineInnerInterceptor` SQL 注入断言（生成的 SQL 必含 tenant 谓词）；`AbstractRoutingDataSource` 放置路由单测；`ignore` 白名单表不被误加条件。
- **上下文安全**：异步/虚拟线程/定时任务的租户快照传递测试（防止线程池串租户）；`finally` 清理 `ThreadLocal`。
- **Flyway**：主链 + SILO 逐库迁移冒烟（H2+MySQL 双方言）；回填与唯一索引切换的迁移前向/回滚脚本演练。
- **Milvus**：partition-key 召回正确性 + 跨 collection 不泄漏集成测试（可用 testcontainers 或本地 Milvus）。
- **生命周期**：provision 幂等/失败重放、archive 清理对账 dry-run。
- **配额**：超限 BLOCK/THROTTLE/ALERT 三策略行为断言。
- **性能**：over-fetch 下推后与基线对比（POOL 大租户检索 P95）。

---

## 9. 回退计划（红线 #11 必备）

- **总开关**：`knowledge.saas.enabled`（默认 false）。关闭时：TenantContext 恒 `default`、拦截器 ignore 全部、JWT 无 tenant claim 也能过——即退回现单租户行为，保证可回退。
- **分阶段回退**：每阶段独立可回滚——加列为 nullable 先行，回滚只需停止注入 tenant 条件；SILO 迁移保留 pool 数据副本直到验收；路由层可用配置把某租户从 SILO 切回 POOL。
- **数据回退**：迁移脚本提供配对 undo；唯一索引切换前保留旧索引定义以便回滚。
- **灰度**：先内部租户，再逐步放量；异常按租户粒度回退（暂停该租户）而非全局停机。

---

## 10. 风险与开放问题

- **R1（高）** `EmbeddingStore` 单例→provider 重构牵动全部向量读写，是最大技术不确定点，Phase 2 前需一次技术验证 spike。
  - **✅ P2-T0 spike 已完成（2026-09-30，报告见 [../spikes/milvus-partition-key/RESULTS.md](../spikes/milvus-partition-key/RESULTS.md)）**：在真实 Milvus 2.5.4 + bge-m3 1024 维实测。
    ① partition-key 隔离正确——`tenant_id` 作 Int64 partition-key，跨租户检索 `cross_tenant_leak_count=0`（三租户 4/3/3 行零串味）。
    ② LangChain4j `MilvusEmbeddingStore`（1.0.1-beta6）**无法**表达 partition-key：生产 `knowledge_chunks` schema 写死 4 列
    （`id`/`text`/**`metadata` 单 JSON**/`vector` dim=1024），`Builder` 只能改名这 4 列、不能增设顶层标量列，租户被塞进 JSON
    → `IsEqualTo("tenant_id", …)` 会译为 `metadata["tenant_id"]==X` JSON 谓词（隔离**正确**但**无物理分区剪枝**）。
    ③ 时延收益在 toy 规模（10 行）不可测（partition-key 与标量过滤 median 均 ~15ms），真实收益判定推迟到 P2-T5（≥万级向量）。
    ④ **顺带修正过时口径**：`MilvusVectorStoreService.buildFilter` 注释称"LangChain4j Filter 不支持复杂 OR/AND"其实有误——
    `MilvusMetadataFilterMapper` 支持 `And/Or/Not/In`，G3 的复杂 ACL 下推可重新评估。
  - **决策（R1 落定）**：POOL **目标态** = 用 `io.milvus`（已随 langchain4j-milvus 传递引入）自建 `tenant_id Int64 partitionKey=true` 集合，
    LangChain4j 仅保留 embedding 模型/query 向量化；**即时回退** = 沿用 LangChain4j JSON 谓词下推（已证隔离正确、零重构风险）；
    **SILO** = 独立集合 `kc_{tenantKey}`。三者由 `EmbeddingStoreProvider` 按 placement 路由，`saas.enabled=false` 仍走旧 store。
  - **落地节奏（Phase 2 开工授权 · 2026-09-30 项目负责人口头采纳 S-2 方案 b + S-1 方案 B）**：**Phase 2** 走"即时回退"路径——保留 LangChain4j 单例 + 现网 `knowledge_chunks` 集合，`metadata.tenant_id` 走 `IsEqualTo` JSON 谓词下推（spike 已证 0 leak），provider 骨架先落但不分片、SILO 命名工具与静态校验落但集合不建；**Phase 3** 引入 SILO 时同步上 partition-key 目标态（`io.milvus` SDK 自建 `knowledge_chunks_pk`）、SILO 集合生命周期、运维 B 类正式签署与万级时延阈值会审。R1 决策本身（partition-key 为 POOL 目标态）**不回退**，只是**落地节奏分两段**。详见 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) §1/§3/§7 与 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §4 P2-T1~P2-T5（v0.7 措辞已同步）。
  - **✅ Phase 2 已交付归档（2026-09-30）**：P2-T1~P2-T5 依次合入 `ddbcfb3`/`5c1982d`/`10fc424`/`908aac9`/`c702fce`，退出条件达成——`MilvusVectorStoreService` 全部经 `EmbeddingStoreProvider` 拿 store（不再直连 bean）；`metadata.tenant_id` 100% 写入非空（Long 型，`TenantIdResolver` 缓存 key→id）+ 检索必带 `IsEqualTo("tenant_id", ...)` 前置（`buildFilter` 永不返 null）；P2-T5 `MilvusTenantIsolationTest` 4 用例断言跨租户零泄漏（CI `tenant-isolation` job 首跑真绿由 GitHub Actions 承担，本机无 Docker 时 `disabledWithoutDocker=true` skipped）。全 reactor `./mvnw test` BUILD SUCCESS、单测新增 100 用例全绿。partition-key/SILO/万级时延阈值/运维 B 类正式签署留待 Phase 3 会审。措辞同步至执行计划 v0.8 + Phase 2 清单 v0.3 + AGENTS.md §1 分册地图。
  - **📝 Phase 3 开工门禁清单已起草（2026-09-30）**：[saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) v0.1（**待会审**，B 硬阻塞 3 / S 软阻塞 5 / T 技术细节 6 / C 交叉项 4 = 18 项），承接 Phase 2 消解/延后至 Phase 3 的 6 项（B-1/B-2/B-3/S-1/S-3/T-1）+ Phase 3 本体新引入的 12 项（P3-T0 partition-key 万级 spike / P3-T1 平台端点 + 鉴权分层 / P3-T2 状态机幂等键 / P3-T3 建库默认配置与 Phase 4 耦合 / P3-T4 Suspend / P3-T5 Archive 编排与 partition-key 切换 / P3-T6 SILO Flyway / SSO 决策 / CI 扩展 / 迁移演练 / 复杂 ACL 下推边界 / SILO placement 决策器）。**执行计划 v0.9 §5 Phase 3 任务表已加 P3-T0 行 + §11 S6 Sprint 条 + 关键路径 v0.9 更新**；待运维 B-1/B-2/B-3 书面签署 + 架构 S/T 结论落定后升 v0.2 授权开工，届时同步回写提案 §11 与本评审包 §6。R1 决策（partition-key 为 POOL 目标态）延续不回退，Phase 3 走目标态引入路径。
  - **⚠️ P3-T0 万级 spike 实测（2026-10-07）→ 触及 R1 性能前提，提请 Phase 3 会审复核（证据提交，非单方面改判）**：[../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md) 在 10k/3t、10k/50t、20k/100t（IVF_FLAT / Bounded / 本机 standalone）三组规模实测——**partition-key 检索 P95=13.4–20.9ms 全部慢于同谓词普通标量(8.3–10.1ms)与 LangChain4j JSON 谓词(10.3–12.2ms) 1.2–2.5 倍，无剪枝性能收益**；机理为小数据 IVF_FLAT 近全内存、扫描非瓶颈，分区路由固定开销主导。R1 当初把"partition-key 物理剪枝"作为 POOL 目标态收益之一，本 spike 证实在 POOL 现实规模该收益不存在（partition-key 正当理由应转为物理隔离/可运维/未来超大规模期权）。附带：`50k/100t` 触发本机 Milvus standalone(~3.8GB) OOM 崩溃 → 直接支撑清单 B-1 运维内存/cluster 预留必要性；目标态 schema（`tenant_id Int64 isPartitionKey` + `metadata` 兼容层）可建可查 + 与 LangChain4j 集合同库共存 0 泄漏（T-2 SDK 兼容已验证）。**处置**：结论回写清单 §3 S-2/S-3、§4 T-2 作为会审输入，建议会审评估"S-2 采纳方案 c（延后 partition-key、维持更快且零迁移的 LangChain4j JSON 谓词）"并复核 R1 目标态是否仍必要；在会审给出新结论前 R1 不回退，但由"性能收益"改标注为"隔离/运维性目标态（性能收益未经证实）"。
  - **✅ Phase 3 会审结论 + 全量授权（2026-10-09）**：承接上一条 P3-T0 spike 证据，Phase 3 会审定夺——**R1 复核结论**：partition-key 为 POOL 目标态的决策**延续不回退**，但"物理剪枝性能收益"经 spike 证伪，正式标注为**未经证实**，partition-key 正当性转为"物理隔离 / 可运维性 / 未来超大规模期权"。**S-2=方案 c**（partition-key 切换延后并入 P3-T5，P3-T1~T4 不依赖建集合；现网维持更快且零迁移的 LangChain4j JSON 谓词路径）· **S-3=定性共识**（门禁锚定 JSON/标量路径、partition-key 不设性能正收益断言、provisional P95≤50ms，绝对 X/Y 授权延后至 cluster 校准回填）· **T-2=方案 B**（`MilvusSdkAdapter` 骨架 + LangChain4j 单例保留作回退 bean）。**五方于 2026-10-09 书面齐签**（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星），运维 B-1/B-2/B-3 硬门槛承接，清单升 **v0.3 全量授权**、执行计划升 **v1.0**（新增 P3-T7 OIDC 任务行，C-3=方案 B），**解锁 P3-T5/T6/T7**。详见 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) §7 第 13 条。
- **R2（高）** 混合模型的双轨（POOL+SILO）长期维护复杂度；需明确 SILO 准入门槛与「是否只支持一种」的退让方案。**Phase 2 授权（2026-09-30）**：采纳方案 B 延后至 Phase 3——`sys_tenant_placement.placement` 恒 POOL，SILO 分支代码保留但运行时不放开；**Phase 3 会审（2026-10-09）**：S-1=方案 C（Phase 3 只支持手工 placement，SILO 决策器算法延后 Phase 3.5）、T-6=方案 C（Phase 3 只落 placement CRUD），准入门槛与商务模型留至产品化窗口（评审包 U2）
- **R3（中）** 无外键（G4）导致租户删除/级联只能应用层编排，误删风险高，需强对账。
- **R4（中）** JDK21 虚拟线程/上下文传播与 `ThreadLocal` 语义冲突，选型 `ScopedValue` vs 快照装饰需评审。
- **R5（中）** 前端需配合子域或 `X-Tenant-Id`；跨域/Cookie 登录态由 P1-T3 双解析支撑。**SSO（2026-10-09，C-3=方案 B）**：平台侧企业 IdP OIDC 纳入 Phase 3 = **P3-T7**（扩展 T-4 已交付 `PlatformTokenAuthFilter` 追加 IdP token 校验路径 + 回访 P3-T3 邀请式设密）；租户内前端 SSO 仍留后续。
- **O1（待定）** 计费是否本周期必须？（当前列为 Phase 6 可选后置）
- **O2（待定）** 数据驻留/合规（`data_region`）是否要求物理分区部署？
- **O3（部分已定）** 平台管理员与租户管理员的权限边界与审计留存周期。**2026-10-09**：T-4=方案 B（独立 `PlatformTokenAuthFilter` + 独立 JWT secret，P3-T1 `372994a` 已交付）划清平台/租户端点边界；S-4=方案 A（Archive 审计留存 30 天保留期 + 项目负责人临时 token 双人复核，审计落 `sys_audit_log`）。

---

## 11. 审批（依据 AGENTS.md §0 #11 / §5 例外与审批流程）

- [x] 架构负责人签署（同意混合模型 + 路线图 + 回退方案）—— 项目负责人 mouhinU 口头授权 2026-09-29；**架构负责人蒙斌 Phase 3 全量授权书面签署 2026-10-09**（S-1/S-2/S-3/S-5 + T-1~T-6 + C-3/C-4 架构侧）
- [x] 安全负责人签署（Phase 0 存量安全修复 + 租户隔离威胁模型 + 跨租户越权测试充分性）—— 项目负责人 mouhinU 口头授权 2026-09-29；**安全负责人莫道 Phase 3 全量授权书面签署 2026-10-09**（S-4 + T-4/T-5 + C-1/C-3）
- [x] 运维/基础设施确认（多副本 / 对象存储 / Redis / Milvus cluster 可承接）—— **✅ 运维/基础设施负责人无始于 2026-10-09 正式书面签署 B-1/B-2/B-3**：B-1 承接现网 Milvus 2.5.4 standalone `io.milvus` SDK 建/drop 集合权限 + list/create/alter 账号权限 + 监控接入 + 内存 segment/磁盘 snapshot 预留；B-2 Phase 3 前段维持 standalone，cluster 扩容 + 备份方案列为 **P3-T5 起前硬前置条件**（P3-T0 spike 已证 50k/100t 触发 standalone ~3.82 GiB OOM）；B-3 本 checkbox 闭环。此前 Phase 2 范围内触发条件已消解（2026-09-30 S-2 方案 b：不动集合/权限/拓扑；见 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) §2 B-1/B-2/B-3）。对象存储（MinIO，P5-T1）/ Redis 仍随 Phase 5 落地。
- [x] 通过后：本文件从 DRAFT 转为分册基线（已转）；`AGENTS.md` 分册地图补一行入口（待 X-T6 补）；例外（如某些表暂不加租户列）登记到 `docs/exceptions/`（EX-20260929-001 已登记）

> 审批说明：本轮为项目负责人「口头同意先行」（与 Phase 0 同模式），已解锁 Phase 1 地基（P1-T1/T2/T3），全程受 `knowledge.saas.enabled=false` 开关门控、运行时仅 `default` 租户、行为与主干等价可回退。正式三方书面签署与运维承接确认（评审包 §6 签署页、U1/U3）仍需在开启 Phase 2 前补齐；Phase 2 收口清单见 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md)。
>
> **Phase 2 开工授权（2026-09-30 项目负责人口头采纳）**：经 P2-T0 spike 结论 + Phase 2 收口清单 v0.2 分析，项目负责人口头采纳 **S-1 方案 B**（SILO 授权延后至 Phase 3，`sys_tenant_placement.placement` 恒 POOL）+ **S-2 方案 b**（现网 `knowledge_chunks` 走 LangChain4j JSON 谓词路径，`metadata.tenant_id` 走 `IsEqualTo` 下推、spike 已证 0 leak）+ **S-3 门禁降级**（partition-key 万级时延阈值不再是 Phase 2 CI gate 断言，改设正确性断言）。B-1/B-2/B-3 三项运维硬阻塞在 Phase 2 范围内触发条件已消解（方案 b 不动集合/权限/拓扑），Phase 2 沿用"项目负责人口头授权先行"模式开工；**正式运维三方签署随 partition-key 目标态 + SILO 生命周期引入时（Phase 3）补齐**，届时创建 `docs/saas-phase3-go-checklist.md` 参照本 Phase 2 清单格式，本 checkbox 转为 ☑ 签署。R1 决策本身（partition-key 为 POOL 目标态）不回退，落地节奏分两段（Phase 2 JSON 谓词、Phase 3 partition-key + SILO）。
>
> **Phase 3 全量授权（2026-10-09 五方书面齐签）**：经 P3-T0 万级 spike 证据 + Phase 3 会审，五方（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星）于 2026-10-09 全部书面齐签，[saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) 升 **v0.3 全量授权开工**。运维 B-1/B-2/B-3 三项硬门槛（本 §11 第三条 checkbox）已由无始正式签署承接，cluster 扩容 + 备份列为 P3-T5 起前硬前置。会审定夺：S-1=方案 C · S-2=方案 c（partition-key 切换并入 P3-T5）· S-3=定性共识 · S-4=方案 A · S-5=方案 A · T-1 延后 P3-T5 · T-2=方案 B · T-3=方案 A · T-4=方案 B（已交付）· T-5=方案 C · T-6=方案 C · C-1/C-2 已指派 · **C-3=方案 B（Phase 3 内接企业 IdP OIDC，落为新任务 P3-T7，scope 扩展）** · C-4=方案 A。R1 partition-key 目标态延续不回退，性能收益标注为未经证实。**解锁 P3-T5（Archive & Delete + partition-key 切换）/ P3-T6（SILO Flyway）/ P3-T7（OIDC）**；P3-T1~T4 已于 v0.2 部分授权期交付（`372994a`/`55a0872`/`73b0626`/`eece61a`）。全程维持双门控 `knowledge.saas.enabled=false` + `knowledge.platform.enabled=false`。执行计划同步升 v1.0、评审包升 v1.2、决策建议一页纸升 v0.2 定稿。
