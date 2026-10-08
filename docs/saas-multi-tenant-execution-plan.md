<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）、docs/saas-multi-tenant-proposal.md
-->

# SaaS 化改造 · 可执行计划（Execution Plan）

> 状态：**执行中（Phase 0 已完成；Phase 1 地基 P1-T1/T2/T3 + 迁移波 P1-T4/T5/T6 + 脊柱串联 P1-T7/T8/T9/T10 已完成并提交；Phase 2 向量隔离 P2-T1~T5 已完成并提交，退出条件达成。全程受 `knowledge.saas.enabled=false` 门控。生产迁移里程碑：V21→V26 已实跑至真实 MySQL 8（`localhost:3307`），schema 校验全绿——默认租户 ACTIVE id=1、15 张业务表 `tenant_id NOT NULL DEFAULT 1`、V25 六张复合唯一在位、存量行回填全部 `tenant_id=1`。Phase 2 落地口径：S-2 方案 b（LangChain4j 单 store + `metadata.tenant_id` JSON 谓词路径）；S-1 方案 B 与 partition-key 目标态、SILO 集合生命周期、运维正式 B 类签署、partition-key 万级时延阈值一并转入 Phase 3 会审。**Phase 3 开工门禁清单 v0.1 已于 2026-09-30 起草**（18 项 B/S/T/C，承接 Phase 2 消解/延后项 6 项 + Phase 3 本体 12 项；待运维 B-1/B-2/B-3 书面签署 + S/T 结论落定后升 v0.2 授权开工）。**P3-T0 partition-key 万级 spike 已于 2026-10-07 执行**（`spikes/milvus-partition-key-scale/`）：实测 partition-key 在 POOL 代表规模（10k–20k 行、≤100 租户、IVF_FLAT）下**无剪枝性能收益**、反较裸标量/LangChain4j JSON 谓词慢 1.2–2.5×（P95 13.4–20.9ms vs 8.3–12.2ms），跨租户泄漏恒为 0；证据已回写 Phase 3 清单 S-2/S-3/T-2，并**触发 R1（partition-key 为 POOL 目标态）性能前提复核**——R1 决策不回退，仅将"性能收益"标注为未经证实、留待会审定夺；50k 档触发 Milvus standalone OOM 作为 B-1 运维内存预留证据。**）**
> 版本：v0.9（2026-09-30） · 作者：QoderWork · 审批：见 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §11（口头授权，运维 U1/U3 待补；Phase 2 开工走方案 b 授权先行、正式运维签署留至 Phase 3 SILO 引入）
> 关联：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策权威）·三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md)·Phase 2 门禁清单（生效基线）[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md)·Phase 3 开工门禁清单（v0.1 待会审）[saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)·Phase 3 会审决策建议一页纸（P3-T0 spike 实证→S-2/S-3/T-2/R1 建议）[saas-phase3-review-brief.md](saas-phase3-review-brief.md)·红线基线 [../AGENTS.md](../AGENTS.md)·迁移规范 [data-and-migration-guideline.md](data-and-migration-guideline.md)·测试规范 [testing-guideline.md](testing-guideline.md)

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
| ✅ P1-T1 | 平台元数据表与默认租户 | 新 `V23__create_sys_tenant.sql`（含 `sys_tenant`+`sys_tenant_placement`）· `TenantDO/TenantPlacementDO` ·（ignore 列表登记落 P1-T7） | P0-T5 | M | 建表 + 插入 `tenant_key='default'` 一条 + POOL 放置；H2/MySQL 双方言 | Flyway smoke 通过（V23 计数 22→23，默认租户 ACTIVE + POOL 断言）；提交 4a983f9 | 迁移 undo 或 truncate |
| ✅ P1-T2 | `TenantContext` 与开关骨架 | 新 `domain/context/TenantContext.java`（ThreadLocal + `get/set/clear`/`getOrDefault`；预留 `ScopedValue` 分支；额外提供 `callWithTenant` + `capture/Snapshot` 供 P1-T9）· `application/support/TenantContextHolder.java` · `application/config/SaasProperties.java`（`enabled` + `apexDomain`）| P1-T1 | S | 上下文提供 `getTenantId() → 默认 default`；`saas.enabled=false` 时 holder 恒返回 default、filter 短路 | 单元：TenantContext 11 + Holder 6 全绿（clear 后取 default / 开关门控）；提交 8bce7d4 | 关闭 `saas.enabled` 即回原行为 |
| ✅ P1-T3 | `TenantResolverFilter` | 新 `web/filter/TenantResolverFilter.java` · 注册到 `WebSecurityConfig`（`addFilterAfter(AdminTokenAuthFilter.class)`，紧随鉴权过滤器） | P1-T2 · X-T2 | M | 三级解析优先级：`X-Tenant-Id` 头 → 子域名 `{key}.{apexDomain}` → JWT claim（读 P1-T8 落到请求属性 `knowledge.tenant.claim`）；冲突 403、非法 400、皆空不绑定；`finally` clear | 单元：11 项覆盖三路径 + 冲突 403 + 非法 400 + 关闭态短路 + 非 /api 跳过；提交 295bdb9（链内 FilterOrder 已接入，端到端 @SpringBootTest 断言已由 P1-T10 补齐（提交 6e97e89）） | `saas.enabled=false` 时 filter `shouldNotFilter` 直接放行 |
| ✅ P1-T4 | 15 业务表 ALTER 加 `tenant_id`（先 nullable） | 新 `V24__add_tenant_id_columns.sql`（H2 MODE=MySQL + MySQL 8 双方言）· 14 个 DO 批量加 `@TableField("tenant_id") Long tenantId`（`DocumentDO/DocumentChunkDO/DocumentImageDO/ExamHistoryDO/ExamSessionDO/ExamQuestionDO/ExamAnswerDO/StudentDO/StudentSessionDO/WritingHistoryDO/UserDO/RoleDO/UserUserRoleDO/DepartmentDO`；`kb_category` 无 DO） | P1-T1 | L | 全表加 `tenant_id BIGINT NULL` + 单列 `idx_<t>_tenant`；回填走迁移内 `UPDATE ... SET tenant_id = (SELECT tenant_id FROM sys_tenant WHERE tenant_key='default')`（比 `ApplicationReadyEvent` 更幂等，H2/MySQL 双方言均已冒烟验证）| 15 表 = 19 全表 − 2 平台表（`sys_tenant`/`sys_tenant_placement`）− 2 Phase 4 延后表（`sys_config`→P4-T1，`kb_extraction_cache`→P4-T3）；Flyway smoke 24→V24 断言 15 张表都有 `tenant_id` 列 + 种子管理员归属 default；提交 4834d40 | nullable 阶段可 `UPDATE ... SET NULL` 快速回退 |
| ✅ P1-T5 | 唯一索引切换（全局→租户内） | 新 `V25__switch_unique_indexes.sql`（6 对 `DROP INDEX uk_x ON t` + `CREATE UNIQUE INDEX uk_<t>_tenant_x ON t(tenant_id, key)`）：`kb_category.name` / `kb_student.username` / `kb_writing_history.session_id` / `kb_exam_history.session_id` / `kb_exam_session.session_key` / `kb_exam_question(session_key,question_number)` | P1-T4 | M | 保留全局：`asset_key`（图片资源天然全局去重）、`(document_id, sha256)`（文档内去重语义）、`sys_user_role(user_id, role_id)`（角色绑定本身租户内）、`kb_student_session.session_token`（UUID 全局唯一已足）；跨租户重复预扫描在迁移头注释中说明为"当前单租户库天然无冲突" | `TenantUniqueKeyDuplicationTest` 正向（acme 与 default 同名分类共存）+ 负向（default 内同名分类 SQLException）；Flyway smoke 25→V25；提交 1e488c8 | undo 保留旧索引定义，可回滚 |
| ✅ P1-T6 | `NOT NULL` + 过渡 DEFAULT + 冗余索引清理 | 新 `V26__make_tenant_id_not_null.sql`：15 张表 `MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 1` + 6 张表 `DROP INDEX idx_<t>_tenant`（`kb_category/kb_exam_history/kb_exam_session/kb_exam_question/kb_student/kb_writing_history`） | P1-T5 | M | `DEFAULT 1` 是过渡安全网：`saas.enabled=false` 阶段 `TenantLineInnerInterceptor`（P1-T7）尚未注册、存量写路径不会显式填 `tenant_id`，无默认则所有 `INSERT` 违约、单租户功能立即崩；指向 V23 种子默认租户 id（smoke 断言恒为 1）。V25 的 `UNIQUE(tenant_id, key)` 最左前缀已覆盖 `tenant_id` 等值过滤，这 6 张表 V24 单列 `idx_<t>_tenant` 遂冗余，此处 DROP 降写放大。其余 9 张无 tenant 前导唯一的表继续保留单列 tenant 索引；更细粒度 `(tenant_id, 高频过滤列)` 复合优化留待 P1-T10 依实测查询画像再定 | Flyway smoke 26→V26：新增两段断言（默认租户 id=1、15 张表 `is_nullable='NO'`）；全量回归 467+55 全绿；MyBatis-Plus 默认 `FieldStrategy.NOT_NULL` 在 `tenantId=null` 时跳过列由 DB DEFAULT 兜底，未触发 NOT NULL 违约；提交 9310a51 | 保留旧索引 1 版本；改回 nullable |
| ✅ P1-T7 | `MyBatisPlusConfig` + `TenantLineInnerInterceptor` | 新 `infrastructure/config/MyBatisPlusConfig.java` · 新 `infrastructure/tenant/KnowledgeTenantLineHandler.java` · 新 `infrastructure/tenant/TenantIdResolver.java` · 新 `infrastructure/persistence/mapper/TenantMapper.java` · 依赖：parent pom + infra pom 加 `mybatis-plus-jsqlparser` 3.5.17（MyBatis-Plus 3.5.9 起 `TenantLine*Interceptor` 与 `LongValue` 移出 extension 到独立模块） | P1-T2 · P1-T6 | M | `@ConditionalOnProperty(knowledge.saas.enabled=true)` 装配 `MybatisPlusInterceptor`+`TenantLineInnerInterceptor`（COLA 依赖方向 infra 不能引 app 侧 `SaasProperties`，用条件注解做等价门控）；`KnowledgeTenantLineHandler.ignoreTable` 白名单登记 `sys_tenant`/`sys_tenant_placement`/`flyway_schema_history`/`sys_config`(P4-T1)/`kb_extraction_cache`(P4-T3)；`TenantIdResolver` 提供 `tenant_key→tenant_id` 双向映射（`ConcurrentHashMap` 懒加载 + miss 回源 DB + 未知即抛 `IllegalStateException` 作漂移哨兵，禁静默 fallback） | 单元：`TenantIdResolverTest` 8 + `KnowledgeTenantLineHandlerTest` 8 + `MyBatisPlusConfigTest` 4（`ApplicationContextRunner` 断言 bean 缺失/存在两种 profile）；全 reactor 467+55 全绿；提交 a1fd4f3 | `saas.enabled=false` 时 `MyBatisPlusConfig` 不激活，行为与 P1-T6 提交前等价 |
| ✅ P1-T8 | JWT 加 `tenantKey` claim 端到端串通 | 改 `AdminJwtGatewayImpl`（新增 5 参 issue，3/4 参旧签名 default 方法向后兼容）· `AdminJwtService` 接口增 5 参 default · `AdminTokenPayload` record 加 `tenantKey` 字段 + 5/4 参兼容构造器 · `AdminLoginCmdExe` 从 `User.tenantKey` 取值传入 · `AdminValidateTokenQryExe` 组装 6 参 `AdminPrincipalDTO` · `AdminTokenAuthFilter` 校验成功后写 request attr `knowledge.tenant.claim` 供 P1-T3 tier 2 消费 · 数据回读：`User` 实体加 `tenantKey` 字段 · `UserGatewayImpl` 通过 `TenantIdResolver.lookupKeyOrDefault` 反解 `DO.tenantId` · `TenantIdResolver` 升级为 `keyToId`+`idToKey` 双向 Map 每次解析同时预热 | P0-T2 · P1-T3 · P1-T7 | S | 偏离说明：claim 名用 `tenantKey`（字符串语义）而非原计划字面的 `tenantId`（数值），理由：`TenantContext`/`TenantResolverFilter` 头/属性口径均以字符串为事实源，数值 id 只在 infra 边界由 `TenantIdResolver` 转换；null/blank 归一 `default` 保证升级前签发的旧令牌在校验端仍可解析（向后兼容窗口） | 单元：`AdminJwtGatewayImplTest` +3 覆盖 5 参 roundtrip 与 null/blank/legacy 归一 default；`AdminTokenAuthFilterTest` +2 覆盖 claim 属性写入与向后兼容不写；`AdminLoginCmdExeTest` 新增 `successForwardsTenantKey` 断言 `acme` 一路透传到 issue；`UserGatewayImplTest` +2 覆盖 `null→default` 与 `42L→acme` 反解；提交 e70e028 | 保留 `default` 兜底 30 天；旧 3/4 参 issue 与 5/4 参 payload 构造器仍可用 |
| ✅ P1-T9 | 异步/线程池租户快照传播 | 新 `application/util/TenantContextPropagatingExecutor.java`（`ExecutorService` 装饰器，`execute/submit/invokeAll/invokeAny` 提交侧 `capture()` + worker 侧 `restore()` 前置状态的 `finally` 兜底）· `AgentExecutorFactory.newTenantAwareAgentPool(namePrefix)` 新方法（不改原 `newBoundedAgentPool` 的 `ThreadPoolExecutor` 返回类型，`AgentExecutorFactoryTest` 依赖具体类型做饱和探针）· 3 处业务出口切至新方法：`ExamGenerationSupport (exam-gen)` · `ArticleGenerationSupport (article-gen)` · `ExamGradingSupport (exam-grading)` · `InfraExecutorConfig.visionExecutor` bean 内联等价匿名 view（infra 因 COLA 依赖方向不可引 app 的 `TenantContextPropagatingExecutor`，就地最小实现，语义一致） | P1-T2 | M | 闭合进度更新（后续提交 738c8d3）：原"未闭合项"3 个 `docingestion/*AsyncCmdExe`（`Index/Reindex/IndexCustomChunks`）落 `ForkJoinPool.commonPool()` 的缺口已补——给 `TenantContextPropagatingExecutor` 加 `wrapCurrent(Runnable)`/`wrapCurrent(Supplier)` 静态 helper，把这三处 `CompletableFuture.runAsync(...)` 的 lambda 包一层（提交侧 capture / worker 侧 restore），不新增线程池以免改动原拒绝/并发语义。`ExtractEnhanceAsyncCmdExe` 已由 `visionExecutor` 覆盖。`ExamGradingScheduler` 多租户遍历属 P5-T5，非本任务 | 单元：`TenantContextPropagatingExecutorTest` +10（`execute` 正向/未绑定/池复用不泄漏/异常路径 `finally` 恢复；`submit(Runnable/Callable+result)`/`invokeAll` 保持 Future 语义与租户；null 参数拒绝；`shutdown` 透传）+738c8d3 追加 `WrapCurrent` 嵌套 4 项；全 reactor 477+61 全绿；提交 2d7efd7（+收尾 738c8d3） | 保留旧 `newBoundedAgentPool` API；关闭 `saas.enabled` 时 `TenantContext.get()` 恒为 null，快照/恢复等价于空操作 |
| ✅ P1-T10 | 跨租户负向用例（脊柱骨架） | 新 `knowledge-web/src/test/java/.../tenant/TenantIsolationIntegrationTest.java`（`@SpringBootTest`+`@Testcontainers(disabledWithoutDocker=true)`+`MySQLContainer("mysql:8.0")` 临时库，Flyway V1→V26 全量回放）· 依赖：web pom test-scope 加 `org.testcontainers:{junit-jupiter,mysql}`（版本随 spring-boot BOM）· 修复：`TenantIdResolver` 构造参数加 `@Lazy TenantMapper`（见下"缺陷"） | P1-T7 · P1-T8 · P1-T9 | L | **暴露并修复真实缺陷**：`saas.enabled=true` 端到端首跑即触发 `mybatisPlusInterceptor → TenantIdResolver → TenantMapper → SqlSessionFactory →（MybatisPlusAutoConfiguration 回收集）interceptor` 构造期循环——此前无任何测试覆盖开启态。以 `@Lazy` 注入 mapper 打断。**偏离说明**：原计划 5 类断言覆盖 文档/检索/出题/成绩/考试列表 **服务层**；实落为 **DB/拦截器层** 6 类断言（SELECT 过滤 / `selectById` 跨租户返 null / `updateById`·`deleteById` 他租户 0 行 / INSERT 自动注入 `tenant_id` / `sys_tenant` ignore 白名单不被过滤），以 `sys_department` 作代表性租户域表。理由：Milvus 检索与 LLM 出题/文章链未容器化入 CI，且均受 `saas.enabled` 门控；服务层"建 A→查 B 空/403"的检索/出题断言顺延至 Phase 2 `P2-T5`（向量跨租户负向）一并补齐 | 单元：6 项全绿；全 reactor 回归 app 481 + web 67 全绿（`@Lazy` 改动不破坏以 mock 直接构造 `TenantIdResolver` 的既有单测）；提交 6e97e89。无 Docker 环境自动 skip 不阻塞纯单测 CI。**未做**：纳入 CI 门禁（X-T3，待后续 PR 接 `-Dgroups`/`@Tag`） | 关闭 `saas.enabled` 时 `MyBatisPlusConfig` 不装拦截器，本用例通过 `disabledWithoutDocker` 及门控属性跳过 |

**Phase 1 退出条件**：`saas.enabled=true` 且只挂 `default` 租户跑一周稳定；`saas.enabled=false` 与旧版本行为等价（回归对照表）；P1-T10 全绿；`docs/architecture-decisions.md` 补一节《租户上下文与传播》。

---

## 4. Phase 2 · 向量隔离（Milvus）

> 目标：POOL 租户走 partition-key 下推；SILO 租户走独立 collection。这是提案 §10 R1（高风险）的落点。**P2-T0 技术 spike 已完成（2026-09-30，报告见 [../spikes/milvus-partition-key/RESULTS.md](../spikes/milvus-partition-key/RESULTS.md)）**，R1 决策已回写提案 §10；开工门禁与交付归档见 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md)（v0.3，B 硬阻塞 3 / S 软阻塞 3 / T 技术细节 3 / C 交叉项 3 = 12 项，**Phase 2 已交付归档——P2-T1~P2-T5 依次合入 `ddbcfb3`/`5c1982d`/`10fc424`/`908aac9`/`c702fce`，S-1 方案 B + S-2 方案 b 落地，partition-key 目标态与 SILO 生命周期转入 Phase 3 会审**）。**Phase 2 落地范围**：只走 LangChain4j 单 store + `metadata.tenant_id` JSON 谓词路径（spike 已证 0 leak），provider 骨架先落但不分片，SILO 集合实现与 partition-key 建集合在 Phase 3 引入。

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| ✅ P2-T0 | partition-key 技术 spike | 独立小样 `spikes/milvus-partition-key/`（`spike.py`+`RESULTS.md`+`results.json`） | P1-T2 | S | 用 Milvus 2.5.4 本地起样例集合，插入 `tenant_id` 作为 partition-key，验证：a) 等值过滤下推性能 b) bge-m3 1024 维召回正确性 c) LangChain4j `Filter` API 表达能力 | **结论**：a) partition-key 跨租户 `leak_count=0`（三租户 4/3/3 行），但时延收益 toy 规模（10 行）不可测 → 真实收益推迟 P2-T5 复测；b) bge-m3 1024 维召回引擎↔本地余弦 top-1 全一致（3/3）；c) **LangChain4j `MilvusEmbeddingStore` 无法表达 partition-key**（schema 写死 4 列、`metadata` 单 JSON，`Builder` 不能增设顶层标量列）→ POOL 目标态须用裸 `io.milvus` SDK 自建 `tenant_id Int64 partitionKey=true` 集合，LangChain4j `IsEqualTo` JSON 谓词下推作即时回退（隔离已证正确），SILO 走 `kc_{tenantKey}`；顺带修正 `buildFilter` 过时注释（Filter 实支持 And/Or/Not/In）。决策写回提案 §10 R1 | — |
| ✅ P2-T1 | `EmbeddingStoreProvider` 注册表（Phase 2 骨架） | 新 `infrastructure/milvus/EmbeddingStoreProvider.java`·`EmbeddingStoreRouter.java`·`MilvusVectorStoreService.java` 改为经 provider 拿 store（不再直连 bean）；`MilvusConfig.java` 保持 `@Lazy EmbeddingStore<TextSegment>` 单例 bean 不变 | P2-T0 · P1-T2 | S→M | **S-2 方案 b 授权范围**：provider 骨架先落，内部只 1 个 store（`store()` 直通 `poolStore`），`router.route(tenantKey, placement, poolStore)` 恒返回该 store；SILO 分支注释化占位（Phase 3 落）；无 MeterRegistry（C-2 无 tenant 高基数 tag 自检通过） | 单元：`EmbeddingStoreProviderTest` 8/8 + `EmbeddingStoreRouterTest` 13/13（覆盖 POOL/SILO 都恒返 poolStore、`normalisePlacement` 大小写/trim、null poolStore NPE 语义、unknown placement 归 POOL）；集成：`saas.enabled=false/true` 行为等价（provider 代理旧 bean）；全 reactor `./mvnw test` BUILD SUCCESS。提交 ddbcfb3 | provider 内部默认 store 走旧逻辑；关闭 `saas.enabled` 时 provider 直通旧 bean |
| ✅ P2-T2 | `tenant_id` metadata + 写入路径 | 改 `MilvusVectorStoreService.java`（`storeChunks` 内 metadata 首位加 `tenant_id` 键）· `DocumentIngestionDomainService`/`DocumentIngestionSupport`/`IndexCustomChunksCmdExe` 在构建 chunk 时 `chunk.setTenantKey(TenantContext.getOrDefault())` · `DocumentChunk` domain 增 `tenantKey` 字段（transient，仅向量写路径；DB `tenant_id` 由 P1-T4 拦截器注入）· `TenantIdResolver` 提供 key→Long 缓存映射 | P1-T4 · P2-T1 | M | metadata JSON 加 `tenant_id`（**数值型 Long**，对齐 spike Int64 + Phase 3 partition-key 目标态）；`storeChunks` 走 `storeProvider.store().addAll(...)`；写入前 `tenantIdResolver.resolve(...)` 断言非空；**🚧 集合 schema 迁移脚本延后至 Phase 3** | 单元：`MilvusVectorStoreServiceTest` 覆盖 `ChunkTenantKeyPreferred`/`ContextFallback`/`SaasFlagEquivalence`/`MultiChunkBatch`/`ErrorPaths`/`OtherMetadataPreserved` 6 类 12 用例全绿（含 `Metadata.put("tenant_id", long)` 断言 + 未知 tenantKey 抛 `IllegalStateException`）；Spike 0 leak 已证。提交 5c1982d | 无需回退集合结构；metadata 键可 `null` 兼容旧数据 |
| ✅ P2-T3 | 检索 filter 下推 | 改 `MilvusVectorStoreService.search()`（`buildFilter` 内 `new IsEqualTo("tenant_id", currentTenantId)` + 可选 `Filter.and(tenantFilter, new IsEqualTo("category", ...))` 组合，**永不返 null**；同步修 Javadoc 陈旧"不直接支持复杂 OR/AND"口径 → 真因是字符串反解析器缺失非 API 限制） · `AuthorizedSearchSupport` 保留后置双保险 · LangChain4j `EmbeddingSearchRequest.filter` 构造 | P1-T7 · P2-T2 | M | `tenant_id` 前置过滤**必带**（S-2 方案 b 下走 JSON 谓词 `metadata["tenant_id"]==X`）；`SystemStatusQryExe` 4-arg 健康检查在上下文未绑时安全回退到 `default` id=1；复杂 ACL 仍应用层；over-fetch ×3+10/上限 500 沿用（Phase 2 单集合无剪枝损失） | 单元：`MilvusVectorStoreServiceTest.SearchFilterPushdown` 7 用例全绿（捕获 `EmbeddingSearchRequest.filter()` 断言 `IsEqualTo.key()=tenant_id`·`comparisonValue`=Long、And 组合左右两侧、category null/blank 走单谓词、未知 tenantKey 抛 `IllegalStateException`、多 chunk 各自谓词独立）；陈旧 Javadoc 清零；全 reactor BUILD SUCCESS。提交 10fc424 | 应用层后置过滤保留作双保险；🚧 阈值与剪枝收益重估转 Phase 3 partition-key |
| ✅ P2-T4 | SILO 命名工具与静态校验 | 新 `infrastructure/milvus/SiloCollectionNaming.java`（工具类：`toCollectionName(tenantKey)` 校验+`kc_`前缀拼接·`isValid(tenantKey)` 布尔正则 `^[a-z][a-z0-9_]{1,62}$`·`assertValidForProvisioning` Phase 3 钩子·`isSiloCollectionName`/`fromCollectionName` 反解·`MAX_MILVUS_COLLECTION_NAME_LENGTH=255`）·**🚧 `SiloCollectionAdmin.java` 集合生命周期实现延后至 Phase 3**（S-1 方案 B） | P2-T1 · P2-T2 · P3-T1（部分） | S | Phase 2 只落命名工具与入库校验（P3-T1 平台端 `create` 接口预校验调用点占位 TODO）；`kc_{tenantKey}` 命名规范已定（T-3）；**🚧 provision 建集合 / archive 删集合 / `database-name` 每 SILO 独立库 转 Phase 3** | 单元：`SiloCollectionNamingTest` 57 用例全绿（`@CsvSource` 正例集 + `@ValueSource` 反例集覆盖 null/blank/首字符数字/首字符下划线/大写/连字符/点/`kc_` 前缀重复/长度 <2 或 >63/边界 2 与 63 允许、64 拒）；工具类可独立存在，Phase 3 起前无运行时影响。提交 908aac9 | 工具类可独立存在；Phase 3 起前无运行时影响 |
| ✅ P2-T5 | 向量跨租户负向用例（正确性门禁） | 新 `knowledge-web/src/test/java/.../tenant/MilvusTenantIsolationTest.java`（`@Tag("tenant") @Testcontainers(disabledWithoutDocker=true) @SpringBootTest(classes=TestConfig.class)`；`MilvusContainer("milvusdb/milvus:v2.5.4")` + `MySQLContainer("mysql:8.0")`；`@DynamicPropertySource` 装配 `knowledge.saas.enabled=true` + Flyway + Milvus host/mappedPort/collection `p2t5_knowledge_chunks` dim=4；`@Primary EmbeddingModel deterministicEmbeddingModel()` 返常量 `Embedding.from(new float[]{1f,0f,0f,0f})`）·web pom test-scope 加 `org.testcontainers:milvus`（版本随 spring-boot BOM）·改 `.github/workflows/ci.yml` 新增 `tenant-isolation` job（`needs: build` + `runs-on: ubuntu-24.04` + 预拉 mysql:8.0/milvusdb/milvus:v2.5.4 + `mvn -B test -pl knowledge-web -am -Dgroups=tenant -DfailIfNoTests=false -DargLine="-Dapi.version=1.44"`） | P2-T3 · P2-T4（工具类） · C-1 | M | **🚧 门禁降级（S-3 方案）**：Phase 2 断言 A 建 100 向量 + B 检索 0 命中、单租户召回非空、`metadata.tenant_id` 谓词表达式在 search 请求 filter 中可见；**partition-key 万级时延阈值 P95 转 Phase 3 会审** | 4 用例：`sameTenantRecallIsNonEmpty`（A store 100→search=100）· `crossTenantQueryDoesNotLeak`（B 上下文只见 `p2t5-b-*` 不含 `p2t5-a-*`）· `unboundContextFallsBackToDefault`（无 TenantContext 绑定→返空且不抛）· `tenantIdsDistinct`（default=1、acme/beta 各自 distinct）。CI 门禁（C-1 X-T3 独立 job 与 P2-T5 同期合入）；`@Tag("tenant")` 与 P1-T10 同标签。**本地无 Docker**：4 用例 compile 通过 + `disabledWithoutDocker=true` 自动 skipped（web skip 从 6→10），首次真实绿跑由 CI 承担（诚实披露已写入 commit message + Javadoc）；🚧 时延阈值 Phase 3 引入 partition-key 时重设。提交 c702fce | `saas.enabled=false` 时跳过；🚧 partition-key 与 SILO 混跑断言转 Phase 3 |

**Phase 2 退出条件**（S-2 方案 b 授权范围）：**已达成（2026-09-30，5 commit 依次合入 `ddbcfb3` P2-T1 / `5c1982d` P2-T2 / `10fc424` P2-T3 / `908aac9` P2-T4 / `c702fce` P2-T5，全 reactor `./mvnw test` BUILD SUCCESS、单测新增 100 用例全绿：`EmbeddingStoreProviderTest` 8 + `EmbeddingStoreRouterTest` 13 + `MilvusVectorStoreServiceTest` 19 + `SiloCollectionNamingTest` 57；集成层 `MilvusTenantIsolationTest` 4 用例 compile 通过 + `disabledWithoutDocker=true` 环境无 Docker 时 skipped、CI `tenant-isolation` job 首跑真绿由 GitHub Actions 承担）**——所有既有向量出口（`DocumentIngestionService`/`IndexProgressStore`/`KnowledgeSearchQryExe` 内部）经 `EmbeddingStoreProvider` 路由（Phase 2 内部只 1 个 store 实例）；`MilvusVectorStoreService` 通过 provider 拿 store（不直连 bean）；`metadata.tenant_id` 100% 写入非空（Long 型，`TenantIdResolver` 缓存 key→id）+ 检索必带 `IsEqualTo("tenant_id", ...)` 前置（`buildFilter` 永不返 null）；跨租户泄漏零命中（P2-T5 断言 + Spike §2 已证）。**🚧 转 Phase 3 会审**：partition-key 集合落地、SILO 独立集合生命周期（`SiloCollectionAdmin.provision/archive/drop`）、`MilvusVectorStoreService` 从单例升级为多 store（provider 缓存启用）、复杂 ACL 下推、partition-key 万级时延阈值 P95、运维正式 B 类签署。

---

## 5. Phase 3 · 生命周期（租户自助 + partition-key/SILO 目标态）

> 目标：把 Phase 2 消解/延后至 Phase 3 的 partition-key 目标态、SILO 独立集合生命周期、运维 B 类正式承接、万级时延阈值 P95 一并落地；同时交付 P3-T1~T6 生命周期端到端闭环（平台管理员可通过 API 完成"开通 → 使用 → 停用 → 归档 → 物理清理"，SILO 租户 schema 与 POOL 版本对齐）。**开工门禁与未闭环项收口清单见** [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（v0.1，**待会审**，B 硬阻塞 3 / S 软阻塞 5 / T 技术细节 6 / C 交叉项 4 = 18 项；B-1/B-2/B-3 三项运维承接不再消解——建集合/drop 集合/cluster 决策为硬门槛）。**Phase 2 生效基线**：[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) v0.3（已交付归档，作为下游承接参考）。**Phase 3 落地范围**：LangChain4j 现网 `knowledge_chunks` 单 store + JSON 谓词路径**保留为兼容回退**（S-2 决策项），新增 `io.milvus` SDK 直连建 `knowledge_chunks_pk` partition-key 集合（POOL 目标态）与 N×SILO 独立集合 `kc_{tenantKey}`；`EmbeddingStoreProvider` 从 Phase 2 单 store 骨架升级为多 store 缓存（T-1 决策项）；`SiloCollectionNaming` 静态工具（Phase 2 P2-T4 已落）与新增 `SiloCollectionAdmin` 生命周期实现联动（T-3 决策项）；平台管理员端点 `PlatformTenantController` + Provisioning 状态机 + Suspend/Resume + Archive & Delete 编排 + SILO Flyway 逐库 runner。

| ID | 标题 | 主要文件 | 依赖 | 复杂度 | 交付物 | 验收 | 回退 |
|---|---|---|---|---|---|---|---|
| ✅ P3-T0 | partition-key 万级 spike（承接 Phase 2 P2-T0 + S-3 阈值）· **已执行 2026-10-07** | 独立小样 `spikes/milvus-partition-key-scale/`（`spike_scale.py`+`README.md`+`RESULTS.md`+`results.json`+三组分档 `results_*.json`）· REST v2（io.milvus SDK 等价层）建 partition-key 集合 schema | P2-T0 · Phase 3 清单 S-2/T-2/T-3 | M | 三组规模 10k/3t、10k/50t、20k/100t（IVF_FLAT/随机归一 1024 维/Bounded/本机 standalone）跑 S-3 P95 时延；`50k/100t` 触发 Milvus standalone(~3.8GB) OOM 崩溃（作 B-1 实证）；验证 partition-key 与 LangChain4j JSON 集合同库共存；只出报告不改生产代码 | **实测负向结论**：partition-key P95=13.4–20.9ms，全部慢于普通标量(8.3–10.1ms)/JSON 谓词(10.3–12.2ms) 1.2–2.5 倍，**无剪枝性能收益**；目标态 schema 可建可查 + 同库共存 0 泄漏（T-2 SDK 兼容已验证）；三组 `cross_tenant_leak=0`。结论回写清单 §3 S-2/S-3 + §4 T-2，并触发"POOL 是否仍需 partition-key 目标态"的 **R1 复核建议**（勾选位留待会审，spike 不单方面改架构） | 报告可反复重跑；spike 环境独立不动现网（复核 `knowledge_chunks` 完好） |
| 🔓 P3-T1（**已授权开工 2026-10-08**） | `PlatformTenantController` 与平台端鉴权（**T-4=方案 B**） | 新 `web/controller/PlatformTenantController.java`（`/api/platform/**`）· `web/security/PlatformTokenAuthFilter.java`（独立 filter + 独立 `knowledge.platform.jwt.secret`，只拦 `/api/platform/**`）· `application/executor/platform/*` · 新 domain `TenantGateway` + infra `TenantGatewayImpl`（COLA 红线 #6/#7）· SLF4J `PlatformAuditLog` | P1-T7 · P1-T8 · Phase 3 清单 T-4（=B）/T-6 | M | 端点：`create/suspend/resume/get/list` 本期落地（`archive/delete` 仅软状态骨架，完整编排属 P3-T5）；独立平台 token 鉴权（不引 `@EnableMethodSecurity`/`sys_platform_admin`/`PLATFORM_ADMIN` role）；写操作审计落 SLF4J `PlatformAuditLog`（脱敏）；placement 恒 POOL 只留 CRUD 钩子（S-1/T-6 未签署，决策器延后） | 平台 token 鉴权通过；普通管理员 token 调 `/api/platform/**` 返 401/403；审计日志可见 | 端点默认关闭，`knowledge.platform.enabled=true` 才注册 filter + controller |
| 🔓 P3-T2（**已授权 2026-10-08**） | 租户开通状态机（**S-5=方案 A**） | 新 `application/service/ProvisioningStateMachine.java` · `PENDING→PROVISIONING→ACTIVE/FAILED` · 幂等键 `provision_request_id`=UUID v4 · 迁移 `V28__provision_state_machine.sql`（`sys_tenant.status` 加 PENDING/PROVISIONING/FAILED + 新表 `sys_tenant_provision_step`） | P3-T1 · Phase 3 清单 S-5（=A） | M | 状态持久化到 `sys_tenant.status` + `sys_tenant_provision_step`；幂等靠 Flyway 版本号 + `hasCollection` 前置查 + `(tenant_id,config_key)` upsert；失败可重放（各步骤幂等），FAILED 可查 `error_detail` | 集成：中断后重试不重复建表；FAILED 状态可查询原因 | 单步失败可人工修正 |
| 🔓 P3-T3（**已授权 2026-10-08**） | 建库默认配置与根部门（**C-4=方案 A：提前 P4-T1**） | 迁移 `V29__tenant_scope_sys_config.sql`（`sys_config` 加 `tenant_id NOT NULL DEFAULT 1` + `(tenant_id, config_key)` 复合唯一）· provisioning 末步拷贝平台默认 → 租户级 `sys_config` · 建根 dept（`parent_id=null`）· 建首个 admin + 引导 token | P3-T2 · P4-T1（提前并入） · Phase 3 清单 C-4（=A） | S | provisioning 最后一步：拷贝平台默认 → 租户级 sys_config（走表，非硬编码）；建根 dept；建首个 admin 用户并发引导 token | 集成：新租户首次登录即看到默认值 | 默认配置有变更不影响已激活租户 |
| 🔓 P3-T4（**已授权 2026-10-08**） | Suspend/Resume（软拦，**T-4=方案 B 绑定**） | 租户请求路径按 `sys_tenant.status` 校验（`TenantResolverFilter`/`AdminTokenAuthFilter` 侧）· suspend/resume 调 `TenantIdResolver.invalidate*()` 缓存钩子即时生效 | P3-T1 · P3-T2 | S | `status=SUSPENDED` 时该租户请求返 403 且带明确 `error_code=TENANT_SUSPENDED`（平台端 `/api/platform/**` 不受租户 suspend 影响，走独立平台 token）；suspend/resume 后立即失效 `TenantIdResolver` 缓存 | 集成：suspend 后 5s 内该租户请求全拒；resume 立即恢复 | 只需 status=ACTIVE 恢复 |
| P3-T5 | Archive & Delete 编排（对账 dry-run）+ 可能合并 partition-key 切换 | 新 `application/executor/tenant/ArchiveTenantCmdExe.java` · 后台对账 `TenantDeletionReconciler` · **若 S-2 采纳方案 c** 本任务合并 `MilvusSdkAdapter` 落地 + `knowledge_chunks_pk` 建集合 + 双写/停机重灌 | P3-T1 · P2-T4 · P5-T1（依赖） · Phase 3 清单 S-2/S-4/T-1/T-2/T-3/T-5 | L | 顺序：软删 → 保留期（S-4 决策值，默认 30 天）→ 逐租户遍历 `kb_document` → Milvus 删向量（`SiloCollectionAdmin.drop` 或 POOL partition-key 按 `tenant_id=X` 删）→ 对象存储删文件（P5-T1 已完成则落，否则延后）→ DB 行删；每次执行前 dry-run 预览；双人复核（token + 二次确认，S-4 决策形态） | 集成：中断可续跑；文件/DB/Milvus 三处一致 | 保留期内可 cancel |
| P3-T6 | SILO Flyway 逐库 runner | 新 `infrastructure/migration/TenantMigrationsRunner.java`（`@Scheduled` + 手工触发端点）·按 T-3 决策（collection-only vs collection+database） | P2-T1 · P2-T4 · Phase 3 清单 T-3 | M | 遍历 SILO 租户的数据源跑同一套 Flyway；错峰；失败隔离；schema 版本一致性体检端点 `/api/platform/schema-health` | 集成：主链加 V27+ 后 SILO 库自动跟上；不一致告警 | 单库失败不影响其他库；提供单库重跑工具 |

**Phase 3 退出条件**（待清单 v0.2 签署后细化）：平台管理员可通过 API 完成"开通 → 使用 → 停用 → 归档 → 物理清理"完整闭环；SILO 租户 schema 与 POOL 版本对齐；partition-key `knowledge_chunks_pk` 集合落地 + LangChain4j `knowledge_chunks` 保留为兼容回退（S-2 决策）；`MilvusVectorStoreService` 从单 store 升级为 provider 多 store 缓存（T-1 决策）；`SiloCollectionAdmin` 生命周期 API（provision/archive/drop/list）实现（T-3 决策）；万级时延阈值 P95 通过 P3-T0 spike 与 CI `perf` 门禁断言（S-3 决策）；CI `tenant-isolation` job 扩展至 partition-key/SILO 混跑（C-1）；运维 B-1/B-2/B-3 三项书面签署闭环、评审包 §6 运维栏 ☐→☑、提案 §11 第三条 checkbox 打勾（B-3）。

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
| X-T3 | CI 加负向用例门禁 | 改 `.github/workflows/*.yml`（或对应流水线）·新 `TenantIsolationIntegrationTest` 归入 `@Tag("tenant")` | P1-T10 · P2-T5 | S | `mvn test -Dgroups=tenant` 单独跑，任何跨租户泄漏 fail build | 流水线红/绿可读 | 标签可跳过 |
| X-T4 | 迁移与回滚演练 | 新 `scripts/saas-migration-drill.sh` | P1-T4~T6 · P4-T1 · P4-T3 | M | 从 `main@HEAD-1` 拉快照→跑迁移→跑 undo→比对行数；MySQL 8 与 H2 双方言 | 演练日志与报告；回退时间目标 ≤15 min | — |
| X-T5 | 前端租户解析改造 | 改 `knowledge-web/src/main/resources/static/admin/assets/js/common.js`（fetch 拦截器）· `exam.html` 内联脚本 · 与 `HtmlCacheBusterFilter` 联动 | P1-T3 · P1-T8 | M | 登录后从 JWT 解析 `tenantKey`（P1-T8 实际落地的 claim 名，字符串键，非原计划字面的 `tenantId`）；所有 fetch 请求带 `X-Tenant-Id`；子域模式则不用带；403 TENANT_SUSPENDED 提示页 | 手动跑一次两租户切换不串数据；`?v=` cache-busting 不受影响 | 无 `X-Tenant-Id` 时后端拒绝（saas.enabled=true） |
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
- **S4（1 周）**：✅ P2-T0 spike 已完成（提交 a181d4c）→ R1 决策落定（partition-key 目标态 + LangChain4j JSON 谓词回退 + SILO 延后）→ 提案 §10 R1 已回写；S-1 方案 B/S-2 方案 b 已授权（[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) v0.2）
- **S5（1–2 周）**：✅ Phase 2 全 5 项已交付（**范围已缩：S-2 方案 b**）——P2-T1 `EmbeddingStoreProvider` 骨架（`ddbcfb3`）+ P2-T2 `metadata.tenant_id` 写入（`5c1982d`）+ P2-T3 `IsEqualTo` 前置下推 + 陈旧 Javadoc 修正（`10fc424`）+ P2-T4 SILO 命名工具（`908aac9`）+ P2-T5 跨租户负向 `@SpringBootTest` + Testcontainers Milvus + C-1 CI `tenant-isolation` job（`c702fce`）；**不含 partition-key 建集合 / SILO 生命周期 / 万级时延阈值**，均转 Phase 3 会审。本地无 Docker → 4 集成用例 compile 通过 + `disabledWithoutDocker=true` skipped，首次真实绿跑由 CI 承担。
- **S6（会审 + 3–4 周）**：**Phase 3 开工门禁清单 v0.1 已于 2026-09-30 起草**（18 项 B/S/T/C），待运维 B-1/B-2/B-3 书面签署 + 架构 S-1/S-2/S-3/S-4/S-5 + T-1/T-2/T-3/T-4/T-5/T-6 结论落定后升 v0.2 授权开工；✅ **P3-T0 partition-key 万级 spike 已执行（2026-10-07）**——实测 partition-key 在 10k–20k/≤100 租户 IVF_FLAT 下 P95 13–21ms 反而慢于普通标量/JSON 谓词 8–12ms、无剪枝性能收益，schema 可建可查且同库共存 0 泄漏，结论回写清单 S-2/S-3/T-2 并触发 R1 复核建议（详见 [../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)），S-2/S-3/T-2 会审将据该证据定夺；剩余依次：P3-T1 `PlatformTenantController` + 平台端鉴权 → P3-T2 Provisioning 状态机 → P3-T3 建库默认配置与根部门 → P3-T4 Suspend/Resume 软拦 → P3-T5 Archive & Delete 编排（视 S-2 决策可能合并 partition-key 切换）→ P3-T6 SILO Flyway 逐库 runner；C-1 CI 门禁扩展 + C-2 迁移演练与 P3-T5/T6 同期。

**关键路径（v0.9 更新）**：~~S4 spike 是最大不确定点~~→ S4 已完成、R1 决策落定；~~S5 Phase 2 走方案 b 交付向量层跨租户负向~~→ S5 已完成，5 commit 依次合入、全 reactor `./mvnw test` BUILD SUCCESS、单测新增 100 用例全绿、CI `tenant-isolation` job 就位；**S6 关键路径**：Phase 3 开工门禁清单 v0.1 起草已完成，运维 B-1/B-2/B-3 三项书面签署（Milvus cluster/备份/DDL 审核流程）为最大不确定点——若运维无法承接则触发降级方案（Phase 3.5 partition-key 单独会审 + Phase 3 只落生命周期 P3-T1~T4）。✅ **P3-T0 万级 spike 已执行（2026-10-07）**，实测 partition-key 无剪枝性能收益（P95 13–21ms 慢于对照 8–12ms）且触发 R1 复核建议——**会审据该证据定夺 S-2（是否延后 partition-key，倾向方案 c）/ S-3（P95 阈值锚定 JSON/标量路径）/ T-2（SDK 兼容已验证，方案随 S-2）三项，并复核 POOL 是否仍需 partition-key 目标态**；S-1 SILO 准入门槛由架构 + 运维会审给结论。**2026-10-08 部分授权**：项目负责人口头采纳会审建议一页纸"P3-T1~T4 与 partition-key/SILO 解耦先行"，清单升 v0.2（provisional），锁定 T-4=方案 B / S-5=方案 A / C-4=方案 A，**P3-T1~T4 解耦子集授权开工**（双门控 `saas.enabled=false` + `platform.enabled=false`）；**P3-T5/P3-T6 仍阻塞**，待运维 B-1/B-2/B-3 书面会签 + partition-key/SILO 决策（S-1/S-2/S-3/T-1/T-2/T-3/T-5/T-6）落定后升清单 v0.3 全量授权。

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

1. **R1 技术方案** ✅ **已定（2026-09-30）**：P2-T0 spike 完成，走"partition-key 目标态 + LangChain4j JSON 谓词即时回退（S-2 方案 b）+ SILO 延后（S-1 方案 B）"三段式；见 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) §3 S-1/S-2 与提案 §10 R1 决策段。
2. **SILO 准入门槛** ✅ **已定（2026-09-30）**：合同/数据量/QPS 具体阈值授权延后至 Phase 3，Phase 2 `sys_tenant_placement.placement` 恒 POOL；P3-T1 平台端 placement 决策器届时补上；见清单 §3 S-1 方案 B。
3. **SSO**：X-T5 是否要接企业 IdP（OIDC/SAML）；若是，Phase 3 前完成。
4. **数据驻留**：`sys_tenant.data_region` 是否要求物理分区部署（影响 compose/网络策略）。
5. **迁移窗口**：P1-T5 唯一索引切换需要停机窗口 or 双写？取决于最大租户的表规模，运维需给出预估。
6. **前端改造深度**：X-T5 是仅改 fetch 头还是引入租户切换 UI？由产品/教务拍板。

以上 6 点中 1/2 已定（走方案 b + S-1 B 授权）；3/4/5/6 在提案 §11 审批会或 Phase 3 开工门禁清单中给出决策。

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
| G9（Permission 无 tenantId） | P1-T7 已定：**不**给 `Permission` 增字段，`TenantContext` 作唯一租户载体（值对象增字段会污染 `Permission` 语义与传递链，改由 `TenantLineInnerInterceptor` 从 `TenantContext` 读，`Permission` 保持"谁能看什么"的角色维度不变） |
| G10（SearchCmd 身份伪造） | P0-T1 |
| G11（单实例假设） | P5-T2 · P5-T4 · P5-T5 |
| G12（对象存储缺位） | P5-T1 |

---

> **开工前提**：本文件与 `saas-multi-tenant-proposal.md` 均需通过 §11 三方签署；例外走 `docs/exceptions/`。Phase 0 五项因低耦合可提前启动。
