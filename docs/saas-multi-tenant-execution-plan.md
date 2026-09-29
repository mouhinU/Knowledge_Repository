<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）、docs/saas-multi-tenant-proposal.md
-->

# SaaS 化改造 · 可执行计划（Execution Plan）

> 状态：**Planning（配合 `saas-multi-tenant-proposal.md` 使用，提案批准前不开工）**
> 版本：v0.1（2026-09-29） · 作者：QoderWork
> 关联：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策权威）·红线基线 [../AGENTS.md](../AGENTS.md)·迁移规范 [data-and-migration-guideline.md](data-and-migration-guideline.md)·测试规范 [testing-guideline.md](testing-guideline.md)

本文件把变更提案的 §7 路线图**拆到"一次 PR 一子任务"的颗粒度**，每项都有明确锚点文件、依赖、验收与回退。**任务编号规则**：`P<phase>-T<n>`（例：`P0-T1` = Phase 0 第 1 项）。

---

## 0. 使用说明

**颗粒度约束**（红线 #12）：单任务目标 ≤1 天完成 + ≤400 行 diff；超出的以子任务串起来。**复杂度分档**：S（≤0.5 天）/ M（0.5–1.5 天）/ L（1.5–3 天）/ XL（≥3 天，需在开工前再拆）。

**依赖记法**：`← P1-T3` 表示必须先合并 P1-T3 才能开始本任务。

**总开关**：所有 Phase ≥ 1 的任务受 `knowledge.saas.enabled`（默认 `false`）控制。关闭时行为与单租户等价，作为快速回退闸门（提案 §9）。

**PR 门禁**：每任务提交前须过 `mvn spotless:check` + `mvn test`；Phase ≥ 1 的每个"数据出口"任务必须同时新增跨租户负向用例（§8 测试策略）。

---

## 1. 里程碑与依赖图

```
Phase 0（安全地基，可先行）
   P0-T1 → P0-T2 → P0-T3 → P0-T4 → P0-T5
             │
Phase 1（租户脊柱）
   P1-T1 → P1-T2 → P1-T3 → P1-T4 → P1-T5 → P1-T6 → P1-T7 → P1-T8 → P1-T9 → P1-T10
             │                                       │
Phase 2（向量隔离，依赖 1-T7 上下文 & 1-T5 表列）
   P2-T1 → P2-T2 → P2-T3 → P2-T4 → P2-T5
             │
Phase 4（去全局化，与 P2 并行准备）
   P4-T1 → P4-T2 → P4-T3 → P4-T4
             │
Phase 3（生命周期，依赖 P1+P2）
   P3-T1 → P3-T2 → P3-T3 → P3-T4 → P3-T5 → P3-T6
             │
Phase 5（对象存储+无状态，依赖 P1 上下文）
   P5-T1 → P5-T2 → P5-T3 → P5-T4 → P5-T5
             │
Phase 6（配额计费，依赖 P1 全链 + 可选后置）
   P6-T1 → P6-T2 → P6-T3 → P6-T4

跨领域（贯穿始终）
   X-T1 提案审批 / X-T2 saas.enabled 开关骨架 / X-T3 CI 负向用例模板 /
   X-T4 迁移与回滚演练 / X-T5 前端租户解析改造 / X-T6 文档与 AGENTS 更新
```

**依赖要点**：0 独立；1 是脊柱；2/4 依赖 1 的表列与上下文；3 需 1+2；5 需 1；6 需 1（可选）。

---

## 2. Phase 0 · 安全地基（存量修复，不引入租户语义）

> 目标：修掉提案 §1.2 G5/G6/G10 三项存量漏洞，避免"加租户后越权面反而扩大"。可与审批并行开工，需口头同意。

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P0-T1 | SearchCmd 身份只信令牌回读 | `knowledge-application/executor/knowledge/KnowledgeSearchQryExe.java`（55-57 行附近）· 相应 Cmd/Query DTO | — | M | 移除请求体 `userId/departmentId/isAdmin` 字段；改由 `AdminPrincipalSupport` 从 JWT 属性取；补 `KnowledgeSearchQryExeTest` 断言请求体伪造字段被忽略 | 新增/修改单测通过；手工验证 token 未携带时 401 | DTO 字段暂保留但服务端忽略；后续版本删除 |
| P0-T2 | 角色链路接线（G5） | `knowledge-web/security/AdminPrincipalSupport.java`（36 行）· `AdminJwtGatewayImpl.issue/verify` | — | M | JWT claim 加 `roles: List<String>`；`toPermission()` 从 claim 读取真实 roles 而非恒 null；补 `AdminJwtGatewayImplTest` | 单元：签发含 roles 的 token，`toPermission()` 返回值断言；集成：非管理员访问 admin API 返回 403 | 保留旧接口以 `admin=true` 兜底 1 个版本 |
| P0-T3 | 考试列表加谓词（G6） | `knowledge-infrastructure/persistence/gateway/ExamHistoryGatewayImpl.java`（60-118 行 listPublished/listPage） | P0-T2 | M | 强制按 `owner_user_id` 或 `department_id` 过滤；`is_admin=true` 才允许全局；补 mapper XML | MyBatis SQL 打印断言含过滤条件；负向单测：用户 A 不能通过分页参数读到用户 B | 若性能问题，加缓存前保留谓词 |
| P0-T4 | 文件出口路径归一化审计 | `web/controller/ExamAssetController.java` · `AdminDocumentImageController.java` · `DocumentIngestionSupport.copyToStorage()` | — | S | 对 `assetKey`/`storage_path` 走白名单正则（`[A-Za-z0-9_\-]+`）+ `Path.normalize()` 前后校验；写清"不允许 `..`" | 负向单测：`../`/`..\\`/绝对路径均 400 | — |
| P0-T5 | 学生令牌多会话改造（G8 预备） | `application/executor/student/StudentLoginCmdExe.java`（53-57 行）· 迁移脚本新增 `V22__create_student_session.sql`·`StudentSessionDO` | — | L | 拆出 `kb_student_session` 表（`session_id, student_id, token_hash, expires_at, revoked_at`）；`session_token` 保留只读 1 个版本 | 单测：并发登录不互踢；吊销后旧 token 立即失效；Flyway smoke test 覆盖 H2+MySQL | 迁移 undo：删表 + 回退登录路径 |

**Phase 0 退出条件**：`grep -R "isAdmin.*request\|request.*isAdmin" knowledge-web knowledge-application` 零命中；`AdminPrincipalSupport.toPermission` 返回值随 JWT roles 变化；P0-T3/P0-T4 各有 ≥1 条负向用例；全量 `mvn test` 通过。

---

## 3. Phase 1 · 租户脊柱（单租户语义打穿）

> 目标：建"租户"这条主线（表列、上下文、拦截器、令牌、路由），但**运行时只有 `default` 租户**——保证向后兼容与灰度可控。

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P1-T1 | 平台元数据表与默认租户 | 新 `V23__create_sys_tenant.sql`（含 `sys_tenant`+`sys_tenant_placement`）· 迁移 undo `U23__...`（Flyway undo 或手工脚本）· `TenantDO/TenantPlacementDO` | P0-T5 | M | 建表 + 插入 `tenant_key='default'` 一条；H2/MySQL 双方言；`sys_tenant` 与 `sys_platform_admin` 加入拦截器 ignore 列表 | Flyway smoke 通过；启动日志显示默认租户已就绪 | 迁移 undo 或 truncate |
| P1-T2 | `TenantContext` 与开关骨架 | 新 `domain/context/TenantContext.java`（ThreadLocal + `get/set/clear` API；预留 `ScopedValue` 分支）· `application/support/TenantContextHolder.java` · `@ConfigurationProperties knowledge.saas.enabled` | P1-T1 | S | 上下文提供 `getTenantId() → 默认 default`；`saas.enabled=false` 时 `TenantResolver` 短路 | 单元：clear 后取到 `default`；异步任务显式传参覆盖 | 关闭 `saas.enabled` 即回原行为 |
| P1-T3 | `TenantResolverFilter` | 新 `web/filter/TenantResolverFilter.java` · 注册到 `WebFilterConfig`（放在 `AdminTokenAuthFilter` 之后、业务 filter 之前） | P1-T2 · X-T2 | M | 三级解析优先级：`X-Tenant-Id` 头 → 子域名 `{key}.example.com` → JWT claim `tenantId`；三者冲突拒绝；写入 `TenantContext`；`finally` clear | 单元：三路径 + 冲突 403；集成：FilterOrder 与 Security Filter 协同 | `saas.enabled=false` 时 filter 直接放行 |
| P1-T4 | 15 业务表 ALTER 加 `tenant_id`（先 nullable） | 新 `V24__add_tenant_id_columns.sql`（+H2/MySQL 双方言）· 相关 DO 类批量加字段（`DocumentDO/ExamHistoryDO/...`） | P1-T1 | L | 全表加 `tenant_id BIGINT NULL`；启动 `ApplicationReadyEvent` 回填 default；索引先建 `(tenant_id)` 单列 | 每 DO 有字段；回填脚本幂等；启动 smoke test 无异常 | nullable 阶段可 `UPDATE ... SET NULL` 快速回退 |
| P1-T5 | 唯一索引切换（全局→租户内） | 新 `V25__switch_unique_indexes.sql`（去旧加新，如 `UNIQUE(kb_document.document_key)` → `UNIQUE(tenant_id, document_key)`） | P1-T4 | M | `document_key`/`username`/`config_key` 等 4-6 个唯一键改造；迁移前预扫描跨租户重复 | H2/MySQL 双方言；`TenantUniqueKeyDuplicationTest` 断言同 key 不同租户可共存 | undo 保留旧索引定义，可回滚 |
| P1-T6 | `NOT NULL` + 复合索引收紧 | 新 `V26__make_tenant_id_not_null.sql`·业务索引重建 `(tenant_id, ...)` | P1-T5 | M | 全表 tenant_id 转 NOT NULL，默认 `default`；主键外索引重建 | EXPLAIN 断言常用查询走新索引；无 NULL 报错 | 保留旧索引 1 版本；改回 nullable |
| P1-T7 | `MyBatisPlusConfig` + `TenantLineInnerInterceptor` | 新 `infrastructure/config/MyBatisPlusConfig.java` | P1-T2 · P1-T6 | M | 装配 `MybatisPlusInterceptor`；`TenantLineHandler.ignoreTable` 登记 `sys_tenant`/`sys_platform_admin`/Flyway 表；分页插件按需要加入 | 单元：`select * from kb_document` 生成的 SQL 含 `WHERE tenant_id=?`；ignore 表不受影响；集成：不同租户互不可见 | `saas.enabled=false` 时 interceptor 直接放行 |
| P1-T8 | JWT 加 `tenantId` claim | 改 `AdminJwtGatewayImpl.issue/verify`（P0-T2 已加 roles）· `AdminLoginCmdExe` 签发时携带租户 · `AdminTokenAuthFilter` 传递到 request attr | P0-T2 · P1-T3 | S | claim 结构 `{sub, username, admin, tenantId, roles, iat, exp}`；老 token 无 claim 视为 `default`（向后兼容窗口） | 单测：老/新 token 都可 verify；filter 属性携带 tenantId | 保留 `default` 兜底 30 天 |
| P1-T9 | 异步/线程池租户快照 | 改 `ExamGenerationSupport.java` · `ArticleGenerationSupport.java` · 各 `CompletableFuture` 提交点 · 新 `TenantContextPropagatingExecutor.java`（装饰 `Runnable` 快照/回放 TenantContext） | P1-T2 | M | 所有 infra/app 层线程池改用装饰 executor；`ExamGradingScheduler` 显式遍历租户 | 集成：并发提交两租户任务不串；单元：Runnable 快照 clear 时机 | 保留旧 executor bean 名，切换需重启 |
| P1-T10 | 跨租户负向用例（脊柱骨架） | 新 `knowledge-web/src/test/java/.../TenantIsolationSmokeTest.java`（`@SpringBootTest`+`Testcontainers MySQL`） | P1-T7 · P1-T8 · P1-T9 | L | 5 类断言：文档/检索/出题/成绩/考试列表；每类"建 A→查 B 空/403" | 全绿；纳入 CI 门禁（X-T3） | 关闭 `saas.enabled` 时跳过 |

**Phase 1 退出条件**：`saas.enabled=true` 且只挂 `default` 租户跑一周稳定；`saas.enabled=false` 与旧版本行为等价（回归对照表）；P1-T10 全绿；`docs/architecture-decisions.md` 补一节《租户上下文与传播》。

---

## 4. Phase 2 · 向量隔离（Milvus）

> 目标：POOL 租户走 partition-key 下推；SILO 租户走独立 collection。这是提案 §10 R1（高风险）的落点，开工前建议先做一次技术 spike。

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P2-T0 | partition-key 技术 spike | 独立小样 `spikes/milvus-partition-key/` | P1-T2 | S | 用 Milvus 2.5.4 本地起样例集合，插入 `tenant_id` 作为 partition-key，验证：a) 等值过滤下推性能 b) bge-m3 1024 维召回正确性 c) LangChain4j `Filter` API 表达能力 | spike 报告（写回本文件 R1 决策）；给出"partition-key vs 多 collection"取舍结论 | — |
| P2-T1 | `EmbeddingStoreProvider` 注册表 | 新 `infrastructure/milvus/EmbeddingStoreProvider.java`·`EmbeddingStoreRouter.java`·改 `MilvusConfig.java`（拆掉单例 bean） | P2-T0 · P1-T2 | L | 按 `tenantId + placement` 返回目标 store；POOL 走共享 store（带 partition-key 表达式），SILO 走独立 collection；本地缓存 store 实例 | 单元：mock router 返回两 store；集成：写入落到目标 collection | provider 内部默认 store 走旧逻辑（`saas.enabled=false`） |
| P2-T2 | `tenant_id` metadata + 写入路径 | 改 `MilvusVectorStoreService.java`（193-200 行附近 `buildFilter` 返 null）· `DocumentIngestionSupport` 写入前填 `tenant_id` · `DocumentChunkDO` | P1-T4 · P2-T1 | M | metadata schema 加 `tenant_id`；写入断言非空；集合 schema 迁移脚本（对 SILO 建新集合，POOL 加字段） | 集成：写入的向量含 tenant_id；Milvus 端 grep 断言 | 迁移前保留旧集合副本；改回旧集合名 |
| P2-T3 | 检索 filter 下推 | 改 `MilvusVectorStoreService.search()` · `AuthorizedSearchSupport` · LangChain4j `EmbeddingSearchRequest.filter` 构造 | P1-T7 · P2-T2 | M | `IsEqualTo("tenant_id", ctx.getTenantId())` 作为**必带前置过滤**；复杂 ACL 仍在应用层；over-fetch 从"×3+10/上限 500"重估 | 单测：SQL/Filter 表达式包含 tenant；集成：A 建 B 查为空 | 应用层后置过滤保留作双保险 |
| P2-T4 | SILO 独立 collection 命名与生命周期 | 新 `infrastructure/milvus/SiloCollectionAdmin.java` | P2-T1 · P2-T2 · P3-T1 | M | 命名 `kc_{tenantKey}`；provision 时创建、archive 时删除；`MilvusConfig.database-name` 支持每 SILO 独立 database | 集成：provision 后 list_collections 含目标；archive 后清理 | 保留 collection 30 天再物理删 |
| P2-T5 | 向量跨租户负向用例 | 新 `MilvusTenantIsolationTest.java`（testcontainers） | P2-T3 · P2-T4 | M | 覆盖：A/B 两租户各插 100 向量，B 检索召回不含 A；SILO 与 POOL 混跑互不污染；partition-key 表达式命中 | CI 门禁；P95 检索时延与 P2-T0 spike 基线对齐（±20%） | `saas.enabled=false` 时跳过 |

**Phase 2 退出条件**：所有既有向量出口（`DocumentIngestionService`/`IndexProgressStore`/`KnowledgeSearchQryExe` 内部）走新 provider；`MilvusVectorStoreService` 无进程级单例；跨租户泄漏零命中。

---

## 5. Phase 3 · 生命周期（租户自助）

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P3-T1 | `PlatformTenantController` 与鉴权 | 新 `web/controller/PlatformTenantController.java`（`/api/platform/**`）· `application/executor/platform/*` · 平台管理员鉴权复用 `AdminTokenAuthFilter` | P1-T7 · P1-T8 | M | 端点：`create/suspend/resume/archive/delete/get/list`；写操作全审计（复用 `Slf4jBlackboardAuditLog` 或独立 `PlatformAuditLog`） | 平台管理员鉴权；租户管理员调用返回 403；审计入库 | 端点默认关闭，`knowledge.platform.enabled=true` 才注册 |
| P3-T2 | 租户开通状态机 | 新 `application/service/ProvisioningStateMachine.java` · `PENDING→PROVISIONING→ACTIVE/FAILED` · 幂等键 `provision_request_id` | P3-T1 | M | 状态持久化到 `sys_tenant.status` + `sys_tenant_provision_step`；失败可重放（各步骤幂等） | 集成：中断后重试不重复建表；FAILED 状态可查询原因 | 单步失败可人工修正 |
| P3-T3 | 建库默认配置与根部门 | 应用 `sys_config` 默认值 + 租户根部门 + 首个管理员引导 | P3-T2 · P4-T1 | S | provisioning 最后一步：拷贝平台默认 → 租户级 sys_config；建根 dept（`parent_id=null`）；建首个 admin 用户并发引导 token | 集成：新租户首次登录即看到默认值 | 默认配置有变更不影响已激活租户 |
| P3-T4 | Suspend/Resume（软拦） | 状态字段驱动，鉴权 filter 加校验 | P3-T1 · P3-T2 | S | `status=SUSPENDED` 时 `AdminTokenAuthFilter` 返 403 且带明确 `error_code=TENANT_SUSPENDED` | 集成：suspend 后 5s 内该租户请求全拒 | 只需 status=ACTIVE 恢复 |
| P3-T5 | Archive & Delete 编排（对账 dry-run） | 新 `application/executor/tenant/ArchiveTenantCmdExe.java` · 后台对账 `TenantDeletionReconciler` | P3-T1 · P2-T4 · P5-T1 | L | 顺序：软删 → 保留期（默认 30 天）→ 逐租户遍历 `kb_document` → Milvus 删向量 → 对象存储删文件 → DB 行删；每次执行前 dry-run 预览；双人复核（token + 二次确认） | 集成：中断可续跑；文件/DB/Milvus 三处一致 | 保留期内可 cancel |
| P3-T6 | SILO Flyway 逐库 runner | 新 `infrastructure/migration/TenantMigrationsRunner.java`（`@Scheduled` + 手工触发端点） | P2-T1 · P2-T4 | M | 遍历 SILO 租户的数据源跑同一套 Flyway；错峰；失败隔离；schema 版本一致性体检端点 `/api/platform/schema-health` | 集成：主链加 V26 后 SILO 库自动跟上；不一致告警 | 单库失败不影响其他库；提供单库重跑工具 |

**Phase 3 退出条件**：平台管理员可通过 API 完成"开通 → 使用 → 停用 → 归档 → 物理清理"完整闭环；SILO 租户 schema 与 POOL 版本对齐。

---

## 6. Phase 4 · 配置 / 缓存 / LLM 租户化

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P4-T1 | `sys_config` 加租户维 + 平台默认 | 迁移 `V27__tenant_scope_sys_config.sql` · `SystemConfigServiceImpl.java` · 新 `ConfigCacheKey.java` | P1-T4 · P1-T5 | M | 键结构 `UNIQUE(tenant_id, config_key)`；读取顺序：租户级 → 平台级 → 内置默认；缓存 key 带租户 | 单元：同 key 不同租户返回不同值；跨租户缓存不命中 | 缓存层回退到直接查表 |
| P4-T2 | `sys_config` 分布式失效 | 新 `infrastructure/cache/ConfigInvalidationBus.java`（Redis pub/sub）· 改 `SystemConfigServiceImpl` | P4-T1 · P5-T2 | M | 每次写入广播版本号，其他实例监听失效本地缓存；无 Redis 时降级到 TTL（默认 5min） | 集成：双实例配置 30s 内一致 | TTL 兜底可关掉 pub/sub |
| P4-T3 | `kb_extraction_cache` 加租户维 | 迁移 `V28__tenant_scope_extraction_cache.sql` · 相应 DO/Service | P1-T4 · P1-T5 | S | 唯一键改 `(tenant_id, checksum)`；跨租户不命中（修 G7 泄漏） | 单测：A 建缓存后 B 查为空；已有测试同步更新 | 提供清空缓存端点 |
| P4-T4 | per-tenant LLM/embedding 配置 | 新 `domain/model/valueobject/LlmProfile.java` · `infrastructure/llm/TenantLlmProfileResolver.java` · 迁移 `V29__tenant_llm_profile.sql`（`api_key` 加密） | P4-T1 · P1-T7 | L | 全局配置作默认，租户级覆盖 `{api_key, base_url, model, embedding_model, dimension}`；密钥用 `SecretCipher`（KMS 或本地主密钥）加密；调用点从单例 bean 改为 resolver | 集成：不同租户调用不同 API Key（可 mock）；单测：解密失败安全降级到平台默认 | 关闭 `saas.enabled` 走全局一套 |

**Phase 4 退出条件**：三处进程内缓存脑裂消除（P4-T2 pub/sub 或 TTL 兜底）；密钥不进 Prometheus 高基数标签；`config_key`/`checksum` 唯一约束已切租户内。

---

## 7. Phase 5 · 对象存储与水平扩展

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P5-T1 | MinIO 应用侧 SDK 接入 | 新 `infrastructure/storage/ObjectStorageGatewayImpl.java`（domain 端口 `ObjectStorageGateway`）· 改 `DocumentIngestionSupport.copyToStorage()` · `ExamAssetController` | P1-T2 | L | prefix 规约 `{tenantKey}/documents/{yyyy}/{mm}/{uuid}.{ext}`、`{tenantKey}/exam-assets/...`；本地磁盘与 MinIO 双驱动（配置切换）；写后校验 | 集成：两租户上传互不可见；路径穿越防护保留 P0-T4 | 配置切回本地磁盘模式 |
| P5-T2 | 上传会话外置 Redis | 新 `infrastructure/upload/RedisUploadSessionStore.java`（替换 `UploadSessionManager` 的 `ConcurrentHashMap`） | P5-T1 · X-T2 | M | key `tenant:{id}:upload:{sid}`，TTL 60min；断线重连保留 | 集成：两副本共享会话；重启副本不丢 | 无 Redis 时降级到进程内 Map（记 WARN） |
| P5-T3 | `/api/exam/assets/{assetKey}` 租户校验 | 改 `web/controller/ExamAssetController.java` | P5-T1 | S | assetKey 反查 `tenant_id`，与 `TenantContext` 比对；不匹配 404（不暴露存在性） | 负向：跨租户 assetKey 404；正向：本租户 200 | 加 flag `knowledge.asset.tenant_check.enabled` 默认 true |
| P5-T4 | compose 去固定化 + 副本化 | 改 `docker-compose.yml` · 新 `docker-compose.scale.yml` overlay | P5-T2 | S | 移除 `container_name`、端口暴露走 `.env`；`knowledge-app` 副本数 `deploy.replicas`；引入前置网关（Caddy/nginx） | 本机 `docker compose up --scale knowledge-app=2` 可跑；health check OK | 单副本配置保留 |
| P5-T5 | 定时任务分片遍历 | 改 `application/service/ExamGradingScheduler.java` · 引入 `TenantScanCursor` 游标 | P1-T9 · P3-T1 | M | 按租户分页轮询；单租户失败不阻塞下一个；支持分布式锁（Redis）避免多副本重复处理 | 集成：双副本不重复评分；单元：某租户失败仍继续 | 保留单租户模式（`saas.enabled=false`） |

**Phase 5 退出条件**：至少 2 副本无冲突；`grep "static final Map.*session\|ConcurrentHashMap.*upload"` 零命中；对象存储路径含 `{tenantKey}` 前缀。

---

## 8. Phase 6 · 配额与计费（可选后置）

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| P6-T1 | `tenant_usage` 计量表 + 采集 | 迁移 `V30__create_tenant_usage.sql` · 新 `application/support/TenantUsageRecorder.java` · 埋点：LLM 网关、向量写入、文件落库、出题执行器 | P1-T7 · P2-T3 · P4-T4 | M | 表结构 `(tenant_id, metric, period, value)`；`metric` 白名单（`chat_tokens, embedding_tokens, doc_count, storage_bytes, exam_count, article_count, mau_students`） | 集成：调用 LLM 一次后 usage 增量符合预期；不进 Prometheus 高基数 | 采集失败仅记 WARN 不阻塞业务 |
| P6-T2 | 套餐与限额表 `tenant_plan_limit` | 迁移 `V31__tenant_plan_limit.sql` · 平台端 CRUD | P6-T1 | S | `(tenant_id, metric, quota, policy)`；`policy=BLOCK/THROTTLE/ALERT`；平台管理员可覆盖 | 集成：CRUD 端点可用 | 无限额时视为无约束 |
| P6-T3 | `QuotaGuard` 前置 | 新 `application/support/QuotaGuard.java` · 接入所有写入类 CmdExe（文档上传、出题、文章生成） | P6-T1 · P6-T2 | M | 写入前检查当月用量；BLOCK 抛 402/403（走现有 `GlobalExceptionHandler`）；THROTTLE 排队；ALERT 记录不阻断 | 单元：三策略各一条断言；集成：BLOCK 阻断后不影响其他租户 | `knowledge.saas.quota.enabled=false` 关闭 |
| P6-T4 | 计费数据导出接口 | 新 `web/controller/PlatformBillingController.java` · 端点 `/api/platform/billing/export?from=...&to=...&tenant=...` | P6-T1 | S | 聚合 CSV/JSON；仅平台管理员可读；预留 webhook 位（Stripe/其他支付方集成属后续里程碑） | 集成：导出格式稳定；权限正确 | 只读接口，风险低 |

**Phase 6 退出条件**：三策略端到端可用；tenant 维度不进 Prometheus 标签；数据可导出。

---

## 9. 跨领域任务（贯穿始终）

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| X-T1 | 变更提案评审与签署 | `docs/saas-multi-tenant-proposal.md` §11 审批位 | — | S | 架构+安全+运维三方签署；例外登记 `docs/exceptions/saas-*.md` | 三 checkbox 完成 | 未通过则本计划不生效 |
| X-T2 | `knowledge.saas.enabled` 开关骨架 | 新 `infrastructure/config/SaasProperties.java` · `application.yml` | — | S | 默认 `false`；所有 T* 任务按此门控；actuator/info 暴露版本 | 关闭时与主干行为等价（回归对照表） | 关掉即回退 |
| X-T3 | CI 加负向用例门禁 | 改 `.github/workflows/*.yml`（或对应流水线）·新 `TenantIsolationSmokeTest` 归入 `@Tag("tenant")` | P1-T10 · P2-T5 | S | `mvn test -Dgroups=tenant` 单独跑，任何跨租户泄漏 fail build | 流水线红/绿可读 | 标签可跳过 |
| X-T4 | 迁移与回滚演练 | 新 `scripts/saas-migration-drill.sh` | P1-T4~T6 · P4-T1 · P4-T3 | M | 从 `main@HEAD-1` 拉快照→跑迁移→跑 undo→比对行数；MySQL 8 与 H2 双方言 | 演练日志与报告；回退时间目标 ≤15 min | — |
| X-T5 | 前端租户解析改造 | 改 `knowledge-web/src/main/resources/static/admin/assets/js/common.js`（fetch 拦截器）· `exam.html` 内联脚本 · 与 `HtmlCacheBusterFilter` 联动 | P1-T3 · P1-T8 | M | 登录后从 JWT 解析 `tenantId`；所有 fetch 请求带 `X-Tenant-Id`；子域模式则不用带；403 TENANT_SUSPENDED 提示页 | 手动跑一次两租户切换不串数据；`?v=` cache-busting 不受影响 | 无 `X-Tenant-Id` 时后端拒绝（saas.enabled=true） |
| X-T6 | 文档与 AGENTS 更新 | 改 `AGENTS.md` §1 分册地图（补一行 saas 入口）· 更新 `docs/architecture-decisions.md`（加"租户层"）· 更新 `docs/security-guideline.md`（跨租户越权面）· `docs/rag-domain-guideline.md`（partition-key 段落）· `PROJECT_SUMMARY.md` | 各 Phase 完成后 | M | 每 Phase 结束补一次；避免规范漂移 | 分册间相对链接可点通 | Git 版本控制天然回退 |

---

## 10. 风险登记（映射到具体任务）

| 编号 | 风险（提案 §10） | 缓解任务 | 残余风险 |
|---|---|---|---|
| R1 | EmbeddingStore 单例→provider 重构 | P2-T0 spike → P2-T1 | 若 partition-key 下推不达标则退化到多 collection 或应用层后过滤 |
| R2 | POOL+SILO 双轨复杂度 | P1-T1 placement 表 · P3-T6 逐库 Flyway | 长期看仍需明确"SILO 准入门槛"，建议单独立项 |
| R3 | 无外键导致级联删除风险 | P3-T5 对账 dry-run + 双人复核 | 建议同时启动独立小任务给核心表加 FK（不在本计划内） |
| R4 | ThreadLocal vs ScopedValue | P1-T2/T9 先走 ThreadLocal + 快照装饰；预留 ScopedValue 分支 | 若上虚拟线程需重跑 P1-T9 快照测试 |
| R5 | 前端跨域/Cookie/SSO | X-T5 · P1-T3 支持双解析 | SSO 集成不在本计划范围 |
| O1/O2/O3 | 计费节奏 / 数据驻留 / 权限边界 | P6-T4 · P1-T1 data_region 字段 · P3-T1 审计 | 均待审批会上敲定 |

---

## 11. Sprint 排布建议（1–2 人小团队视角，仅供参考）

- **S1（1–2 周）**：Phase 0 全 5 项 + X-T2 开关骨架 + X-T1 提案评审 → 存量安全修复上线，观察 1 周
- **S2（2–3 周）**：Phase 1 前 6 项（表列+索引，运行时仍默认租户）→ 灰度
- **S3（2–3 周）**：Phase 1 后 4 项（拦截器+JWT+异步+负向测试）+ X-T4 演练 → `saas.enabled=true` 内部试用
- **S4（1 周）**：P2-T0 spike → 决定 partition-key vs 多 collection → 更新提案
- **S5（2–3 周）**：Phase 2 全 5 项 → 向量层跨租户负向通过
- **S6（2 周）**：Phase 4 全 4 项 → 去全局化
- **S7（3 周）**：Phase 5 全 5 项 → 2 副本可用
- **S8（3–4 周）**：Phase 3 全 6 项 → 自助开通/归档闭环
- **S9（可选 2 周）**：Phase 6 全 4 项 → 配额计费
- **全程并行**：X-T3 CI 门禁、X-T5 前端配合、X-T6 文档更新

关键路径：**S4 spike 是最大不确定点**（R1），建议提前 2 周与运维协调 Milvus 环境；Phase 2 若退化到多 collection 方案，则 P3-T6 的逐库 Flyway 变复杂，需要在 S7 前重新评估。

---

## 12. Definition of Done（每任务通用）

1. 单测 + 相关集成/负向用例齐全，`mvn test` 通过；
2. `mvn spotless:check` 通过（红线 #12 命名/行长/注释）；
3. Flyway 迁移有 undo 或标"不可回退"（需架构签字）；
4. 若涉及鉴权/权限/SQL 拼接/文件路径 → 走一次 `docs/security-guideline.md` 检查；
5. 若涉及新增规范/例外 → PR 中 `Exception-Note`，登记 `docs/exceptions/`；
6. PR 描述含：任务 ID（如 P1-T7）·影响文件列表·跨租户负向用例截图或链接·回退步骤；
7. 关闭对应的 `knowledge.saas.enabled=true` 场景冒烟一次，确认回退路径可用。

---

## 13. 未决项清单（开工前需二次拍板）

1. **R1 技术方案**：P2-T0 spike 结果决定"partition-key"还是"多 collection"，影响后续 3 项。
2. **SILO 准入门槛**：合同/数据量/QPS 具体阈值（提案未定），P3-T1 平台端需据此设计 placement 决策器。
3. **SSO**：X-T5 是否要接企业 IdP（OIDC/SAML）；若是，Phase 3 前完成。
4. **数据驻留**：`sys_tenant.data_region` 是否要求物理分区部署（影响 compose/网络策略）。
5. **迁移窗口**：P1-T5 唯一索引切换需要停机窗口 or 双写？取决于最大租户的表规模，运维需给出预估。
6. **前端改造深度**：X-T5 是仅改 fetch 头还是引入租户切换 UI？由产品/教务拍板。

以上 6 点在提案 §11 审批会一并给出决策；本计划据此调整。

---

## 14. 附录 A：任务↔缺口覆盖矩阵

| 缺口 | 覆盖任务 |
|---|---|
| G1（15 表无租户列） | P1-T4 · P1-T6 |
| G2（无 MP 拦截器） | P1-T7 |
| G3（Milvus buildFilter null） | P2-T3 |
| G4（无外键） | 未覆盖，另立小任务（§10 R3） |
| G5（roles=null） | P0-T2 · P1-T8 |
| G6（列表全局可见） | P0-T3 |
| G7（sys_config/extraction_cache 全局） | P4-T1 · P4-T3 |
| G8（学生令牌单列） | P0-T5 |
| G9（Permission 无 tenantId） | P1-T7 隐含改造（值对象增字段并入 TenantContext 语义） |
| G10（SearchCmd 身份伪造） | P0-T1 |
| G11（单实例假设） | P5-T2 · P5-T4 · P5-T5 |
| G12（对象存储缺位） | P5-T1 |

---

> **开工前提**：本文件与 `saas-multi-tenant-proposal.md` 均需通过 §11 三方签署；例外走 `docs/exceptions/`。Phase 0 五项因低耦合可提前启动。
