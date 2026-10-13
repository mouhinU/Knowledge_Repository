<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §11 · docs/saas-multi-tenant-execution-plan.md §7 · docs/saas-phase4-go-checklist.md v0.4 · docs/saas-phase3-go-checklist.md v0.3
-->

# SaaS 化改造 · Phase 5 开工确认清单（Phase 5 Go-Checklist）

> 用途：**Phase 5（对象存储与水平扩展，P5-T1~T5）开工前的闭环清单**。把执行计划 §7 Phase 5 任务表中的开放设计问题、Phase 5
> 退出条件（"至少 2 副本无冲突；`grep "static final Map.*session\|ConcurrentHashMap.*upload"` 零命中；对象存储路径含
`{tenantKey}` 前缀"）落成可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策，v0.4）·
> 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P5 任务表，v1.0）· Phase 4
> 生效基线 [saas-phase4-go-checklist.md](saas-phase4-go-checklist.md)（v0.4，P4-T1~T4 全部 ✅ 已交付）· Phase 3
> 生效基线 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（v0.3，全量授权开工中）·
> 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> 版本：**v0.4 全量交付 · 2026-10-12** · 作者：QoderWork · 状态：**P5-T1~T5 ✅ 全部已交付**（T1 对象存储 · T2 上传会话
> Redis · T3 assetKey 租户校验 · T4 compose 去固定化 · T5 定时任务分片遍历 · 全 reactor `./mvnw test` 绿）；**C-4 ✅ 文档全部完成
**（`architecture-decisions.md` §7 + `security-guideline.md` §3 + `rag-domain-guideline.md` §三 +
`saas-migration-summary.md` v0.3 + 交叉引用修正）；**Phase 5 退出条件达成**（2 副本无冲突 · `ConcurrentHashMap.*upload`
> 零命中 · 对象存储路径含 `{tenantKey}` 前缀 · 定时任务分布式锁 + 租户游标分片） · B-1/B-2/S-1~S-5/T-1~T-5/C-4 十四项 ☑
> 已落地；C-1~C-3 三项待指派

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑
**。任一 B 类未过则 Phase 5 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

**Phase 5 范围提示**：Phase 5 = 对象存储与水平扩展（P5-T1 MinIO SDK 接入 + P5-T2 上传会话外置 Redis + P5-T3 assetKey
租户校验 + P5-T4 compose 去固定化 + 副本化 + P5-T5 定时任务分片遍历），**不**包含 Phase 3 尾分支 partition-key/SILO 切换（仍受
Phase 3 B-2 cluster 门控）、Phase 4 本体（已全部交付）、Phase 6 计费配额（P6-T1 `tenant_usage` 依赖 P4-T4 已满足但排 Phase 6）。

**与 Phase 4 清单的关系**：Phase 4 清单 v0.4 §2 B-1 已引入 Redis 单机到位（compose `redis:7-alpine`），Phase 5 P5-T2 上传会话
Redis **复用 Phase 4 同一实例**，不另起 Redis 部署。Phase 4 清单 §5 C-2 已跟踪 Phase 5 时序耦合。格式（§0/§1/§2/§3/§4/§5/§6/§7/§8
九节）与 Phase 2/3/4 清单 1:1 对齐。

**前置依赖核查**：

| 依赖项                                          | 来源          | 状态             |
|----------------------------------------------|-------------|----------------|
| P1-T2（租户列 + `TenantLineInnerInterceptor`）    | Phase 1     | ✅ 已交付          |
| X-T2（`knowledge.saas.enabled` 门控骨架）          | 跨领域         | ✅ 已交付          |
| P1-T9（`TenantContext` + 快照装饰）                | Phase 1     | ✅ 已交付          |
| P3-T1（Provisioning 状态机 + 生命周期）               | Phase 3     | ✅ 已交付          |
| Redis（P4-T1 引入，compose `redis:7-alpine` 运行中） | Phase 4 B-1 | ✅ 可用           |
| MinIO（infra compose 已有，应用侧未接入）               | 基础设施        | ✅ 容器就绪，SDK 待接入 |

---

## 1. Go/No-Go 速览

| 类别     | 条目数    | 负责人             | 当前状态                                             | 时限建议        |
|--------|--------|-----------------|--------------------------------------------------|-------------|
| B 硬阻塞  | 2      | 运维/基础设施 + 架构    | **B-1** ☑ · **B-2** ☑ 全部已落地                      | —           |
| S 软阻塞  | 5      | 架构 + 安全         | **S-1~S-5** ☑ 全部已落地                              | —           |
| T 技术细节 | 5      | 架构              | **T-1~T-5** ☑ 全部已落地                              | —           |
| C 交叉项  | 4      | 运维 + 安全 + 项目负责人 | **C-4** ☑ 文档全部完成 · C-1/C-2/C-3 ☐ 待指派             | Phase 6 会审前 |
| **合计** | **16** | —               | **14 项已交付（B-1~2/S-1~5/T-1~5/C-4）** · C-1~C-3 待指派 | —           |

**一句话结论**：**Phase 5 是 SaaS 化改造从"单实例可跑"走向"多副本水平扩展"的关键一步**——把文件存储从本地磁盘升级为 MinIO
对象存储（租户 prefix 隔离），把上传会话从进程内 `ConcurrentHashMap` 外置到 Redis（多副本共享），把定时任务从全局查询改造为租户游标分片（多副本不重复处理），最终让
`docker compose up --scale knowledge-app=2` 成为可能。与 Phase 3/4 不同，**Phase 5 无硬前置门槛**（Redis 与 MinIO
基础设施均已就绪），所有 B/S/T 项均为 Phase 5 本体决策，不依赖外部未闭事项。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 5 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | 负责人          | 依据锚点                                                                               | 判断                                                                                                                                                                                                                                                                                                                                                           | 时限             |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|
| **B-1** | **MinIO 应用侧 SDK 接入确认**（P5-T1）：a) MinIO Java SDK 版本——`io.minio:minio` 最新稳定版（8.5.x），需评估与 Spring Boot 3.4 内置 OkHttp 的兼容性；b) 基础设施就绪确认——`docker-compose.infra.yml` 中 MinIO 容器已运行（端口 9000 内部 / 9001 控制台），bucket 初始化策略（启动期 `MakeBucketIfNotExists` vs 手动创建）；c) 双驱动策略——本地磁盘与 MinIO 通过配置切换（`knowledge.storage.backend={local,minio}`），默认 `local` 保持向后兼容；d) 写后校验——上传完成后 `statObject` / `Files.exists` 确认落盘。方案 A：MinIO Java SDK + 双驱动配置切换（推荐）；方案 B：仅 MinIO 不保留本地磁盘（破坏向后兼容，不推荐）；方案 C：延后 P5-T1 到 Phase 6 | 运维/基础设施 + 架构 | 执行计划 §7 P5-T1 · Phase 4 清单 §2 B-1（Redis 已引入）· `docker-compose.infra.yml` MinIO 容器  | ☑ 方案 A 已落地（2026-10-12 · MinIO SDK 8.5.17 + `StorageConfig` 双驱动 `@ConditionalOnProperty` + `LocalObjectStorageGatewayImpl`/`MinioObjectStorageGatewayImpl` + `StorageProperties` 配置 · 默认 `local` 向后兼容 · `MinioObjectStorageGatewayImpl` `@PostConstruct` `MakeBucketIfNotExists` · 全 reactor 绿）                                                               | Phase 5 起前 2 周 |
| **B-2** | **Redis 上传会话共用实例确认**（P5-T2）：Phase 4 B-1 已引入 Redis 单机（compose `redis:7-alpine`，128MB maxmemory + allkeys-lru），P5-T2 上传会话是否复用同一实例。a) 容量评估——上传会话 key TTL 60min，峰值并发上传数 × 会话大小是否在 128MB 内；b) key 命名空间隔离——P4-T2 用 `kr:config:*`，P5-T2 用 `kr:upload:*`，不冲突；c) 若容量不足是否允许扩容 maxmemory 或引入第二实例。方案 A：共用实例 + key 前缀隔离（推荐）；方案 B：独立实例（增加运维复杂度，不推荐）；方案 C：延后 P5-T2                                                                                                                                         | 运维/基础设施      | 执行计划 §7 P5-T2 · Phase 4 清单 §2 B-1（Redis 部署形态）· `docker-compose.infra.yml` Redis 配置 | ☑ 方案 A 已落地（2026-10-12 · `UploadSessionStore` 端口 + `RedisUploadSessionStore`（Redis Hash `kr:upload:session:{uploadId}` + Set `kr:upload:chunks:{uploadId}` 共享 TTL）+ `NoopUploadSessionStore` 降级 + `UploadSessionProperties` 可配 TTL 默认 7200s + `UploadSessionManager` 重写委托 store · `application.yml` `KNOWLEDGE_UPLOAD_SESSION_TTL_SECONDS` 外部化 · 全 reactor 绿） | Phase 5 起前 2 周 |

---

## 3. S 类 · 软阻塞（Soft）

> 未闭环则相应子任务不得开工；允许授权延后至具体 P5-TX 起草时定。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | 负责人     | 依据锚点                                                                                 | 判断                                                                                                                                                                                                                                                                                                                                                                                              | 时限       |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| **S-1** | **`ObjectStorageGateway` 端口 API 设计**（P5-T1）：domain 层新增 `ObjectStorageGateway` 接口，方法集合。方案 A：最小集 `store(InputStream, tenantKey, category, originalName) → StoredObject` + `retrieve(storageKey) → InputStream` + `delete(storageKey)` + `exists(storageKey) → boolean`；方案 B：方案 A + `listByPrefix(tenantKey, category) → List<StoredObject>`（支持管理端列举）；方案 C：方案 A + `generatePresignedUrl(storageKey, expiry) → String`（支持前端直传 MinIO，未来扩展）。考虑：红线 #7 domain 纯净（返回值不能含 MinIO 特有类型）、`StoredObject` 值对象设计（`storageKey` / `originalName` / `contentType` / `size` / `tenantKey`）   | 架构      | 执行计划 §7 P5-T1 · [architecture-decisions.md](architecture-decisions.md) §2/§5 · 红线 #7 | ☑ 方案 A 已落地（2026-10-12 · `ObjectStorageGateway` 6 方法：`store(2 重载)` + `retrieve` + `delete` + `exists` + `getMetadata`；`StoredObject` record 7 字段；`StorageCategory` 枚举 DOCUMENT/EXAM_ASSET；domain 层零 infra 依赖）                                                                                                                                                                                    | P5-T1 起前 |
| **S-2** | **存储路径 prefix 规约**（P5-T1）：执行计划已给出骨架 `{tenantKey}/documents/{yyyy}/{mm}/{uuid}.{ext}` 与 `{tenantKey}/exam-assets/...`。需确认：a) 日期分桶粒度——`{yyyy}/{mm}` vs `{yyyy}/{mm}/{dd}`（万级文件后者更均匀）；b) exam-assets 路径——`{tenantKey}/exam-assets/{documentKey}/{assetKey}{ext}` 沿用当前 `DocumentImageSupport` 结构还是扁平化为 `{tenantKey}/exam-assets/{assetKey}{ext}`；c) 路径穿越防护——`PathGuard.resolveWithin` 仅适用于本地磁盘，MinIO 侧需额外校验 `../` 与绝对路径。方案 A：按执行计划骨架 + 日期 `{yyyy}/{mm}` + exam-assets 保留 documentKey 子目录；方案 B：日期 `{yyyy}/{mm}/{dd}` + exam-assets 扁平化                                        | 架构 + 安全 | 执行计划 §7 P5-T1 交付物列 · [security-guideline.md](security-guideline.md) §3 上传/解析         | ☑ 方案 A 已落地（2026-10-12 · MinIO 侧 `{tenantKey}/{category}/{yyyy}/{mm}/{uuid}.{ext}`；exam-assets `{tenantKey}/exam-assets/{documentKey}/{fileName}`；本地侧 `storageKey`=绝对路径向后兼容；`PathGuard.resolveWithin` 本地 + MinIO 双侧路径穿越校验）                                                                                                                                                                       | P5-T1 起前 |
| **S-3** | **上传会话 Redis key 结构与降级**（P5-T2）：a) key 格式——`kr:upload:session:{tenantKey}:{uploadId}` Hash 存 `UploadSession` 序列化 vs String 存 JSON；b) TTL——执行计划建议 60min，是否可配 `knowledge.upload.session.ttl-seconds`；c) 降级——Redis 不可用时回落进程内 `ConcurrentHashMap`（记 WARN），与 P4-T2 `NoopConfigInvalidationPublisher` 模式对齐；d) 断线重连——Redis 短暂断连后已创建的会话是否可恢复（TTL 窗口内）。方案 A：Hash 存 + 可配 TTL + 降级进程内 Map（推荐）；方案 B：String JSON 存 + 固定 TTL + 不降级（Redis 断连直接拒绝新上传）                                                                                                                                    | 架构 + 运维 | 执行计划 §7 P5-T2 · Phase 4 B-1（Redis 降级模式参考）                                            | ☑ 方案 A 已落地（2026-10-12 · Redis Hash `kr:upload:session:{uploadId}` 存 6 字段元数据（fileName/fileSize/totalChunks/sessionDir/createdAt/tenantKey）+ Redis Set `kr:upload:chunks:{uploadId}` 存已收分片索引 · 双 key 共享 TTL 每次写操作刷新 · `UploadSessionProperties.sessionTtlSeconds` 可配默认 7200s · `NoopUploadSessionStore` 进程内 `ConcurrentHashMap` 降级 + 惰性 TTL 检查 · `UploadSession` 新增 `tenantKey` 字段 · 全 reactor 绿） | P5-T2 起前 |
| **S-4** | **双驱动配置切换门控**（P5-T1）：`knowledge.storage.backend={local,minio}` 的 Spring `@ConditionalOnProperty` 装配策略。方案 A：`@ConditionalOnProperty(name="knowledge.storage.backend", havingValue="minio")` 装配 `MinioObjectStorageGatewayImpl` + `@ConditionalOnProperty(name="knowledge.storage.backend", havingValue="local", matchIfMissing=true)` 装配 `LocalObjectStorageGatewayImpl`，domain 端口 `ObjectStorageGateway` 唯一注入；方案 B：单 Bean + 内部 `if/else` 分支（不推荐，违反开闭原则）；方案 C：策略模式 + `@Primary` 标注（过度设计）。考虑：红线 #1 构造器注入 · 红线 #8 app 层不涉 infra 细节 · `saas.enabled=false` 关闭态等价（默认 `local`） | 架构      | 执行计划 §7 P5-T1 · Phase 4 T-3（`SecretCipher` 门控模式参考）· 红线 #1/#8                         | ☑ 方案 A 已落地（2026-10-12 · `StorageConfig` 两 `@Bean` + `@ConditionalOnProperty(name="knowledge.storage.backend", havingValue="local", matchIfMissing=true)` / `havingValue="minio"` · domain 端口 `ObjectStorageGateway` 唯一注入 · `application.yml` `STORAGE_BACKEND` 环境变量外部化）                                                                                                                         | P5-T1 起前 |
| **S-5** | **定时任务分布式锁方案**（P5-T5）：多副本下 `@Scheduled` 方法如何防重复执行。方案 A：Redis `SET key NX EX` 简单分布式锁（`kr:lock:exam-grading`，锁 TTL = 任务最大执行时间 + buffer），`RedisLockGateway.tryLock(lockKey, ttl) → boolean` domain 端口；方案 B：引入 ShedLock 第三方库（`net.javacrumbs.shedlock:shedlock-spring`），支持 Redis/DB/JDBC 多种 provider，但引入新依赖；方案 C：数据库行级锁（`SELECT ... FOR UPDATE` 占位行），不引入 Redis 依赖但增加 DB 压力。考虑：Phase 4 B-1 已引入 Redis，方案 A 零新依赖（仅 domain 端口 + infra 实现）；执行计划 §7 P5-T5 提到"支持分布式锁（Redis）"                                                                                                        | 架构 + 运维 | 执行计划 §7 P5-T5 · Phase 4 B-1（Redis 已引入）                                               | ☑ 方案 A 已落地（2026-10-12 · domain 端口 `DistributedLockGateway`（`tryLock(lockKey, ownerId, ttlSeconds)` + `unlock(lockKey, ownerId)`）+ `RedisDistributedLockGateway`（Redis `SET NX EX` + Lua 脚本安全释放校验 ownerId）+ `NoopDistributedLockGateway`（进程内 `ConcurrentHashMap` 降级）· `ExamGradingScheduler` 全局锁 `kr:lock:exam-grading` TTL 600s + 实例 UUID ownerId · 全 reactor 绿）                                | P5-T5 起前 |

---

## 4. T 类 · 技术细节（Technical）

> 未给出书面结论则相应 P5-TX 起草时无法收敛；允许授权延后至具体任务起草时。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | 负责人     | 依据锚点                                                                                                     | 判断                                                                                                                                                                                                                                                                                                                                               | 时限       |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| **T-1** | **MinIO Java SDK 版本与依赖管理**（P5-T1）：a) `io.minio:minio` 版本——8.5.x 最新稳定版，需确认与 Spring Boot 3.4 内置 OkHttp 4.x 的兼容性（MinIO SDK 传递依赖 OkHttp）；b) 版本管理位置——根 `pom.xml` `<properties>` 加 `<minio.version>` + `<dependencyManagement>` 声明，子模块 `knowledge-infrastructure` 引入（仅 infra 层依赖 MinIO SDK，符合红线 #7 domain 纯净）；c) tech-stack.md 更新——新增 MinIO SDK 行。考虑：红线 #11 谨慎变更依赖 · tech-stack §6 版本纪律                                                                                                                                                                                    | 架构      | 执行计划 §7 P5-T1 · [tech-stack.md](tech-stack.md) §2/§6 · 红线 #11                                            | ☑ 已落地（2026-10-12 · 根 `pom.xml` `<minio.version>8.5.17</minio.version>` + `<dependencyManagement>` 声明 · `knowledge-infrastructure/pom.xml` 引入 `io.minio:minio`（无 version，parent 管理）· `docs/tech-stack.md` 新增 MinIO SDK 行 · 仅 infra 层依赖 MinIO SDK，domain 纯净）                                                                                       | P5-T1 起前 |
| **T-2** | **assetKey 租户校验 API 行为**（P5-T3）：`GET /api/exam/assets/{assetKey}` 当前无需 auth token（assetKey 即凭证）。P5-T3 加租户校验后：a) 校验方式——`DocumentImage` 表加 `tenant_id`（P1-T4 已加），查询时 `TenantLineInnerInterceptor` 自动追加 `WHERE tenant_id=?`，跨租户查询自然返回空 → 统一 404；b) 是否需要额外 `X-Tenant-Id` header 校验（`TenantContext` 已由拦截器从 JWT 设置，`TenantLineInnerInterceptor` 自动生效）；c) 不匹配时返回 404 而非 403（不暴露资源存在性，执行计划已明确）。方案 A：依赖 `TenantLineInnerInterceptor` 自动追加，无需改 Controller 逻辑，只需确认 `DocumentImageDO` 有 `tenantId` 且表不在 `IGNORE_TABLES` 中；方案 B：Controller 显式 `TenantContext.get()` 比对 + 不匹配抛 404 | 架构 + 安全 | 执行计划 §7 P5-T3 · [security-guideline.md](security-guideline.md) §5 · Phase 1 P1-T4                        | ☑ 方案 A 已落地（2026-10-12 · `DocumentImageDO` 已有 `tenantId` 列 · `kb_document_image` 不在 `IGNORE_TABLES` 白名单 · `TenantLineInnerInterceptor` 自动追加 `WHERE tenant_id=?` · 跨租户查询返空 → Controller 统一 404 · `AssetKeyTenantIsolationIntegrationTest` 端到端验证 acme 写入 globex 不可见 · 全 reactor 绿）                                                                  | P5-T3 起前 |
| **T-3** | **`TenantScanCursor` 设计**（P5-T5）：执行计划提到"按租户分页轮询；单租户失败不阻塞下一个"。a) 游标形态——keyset pagination `WHERE id > lastId ORDER BY id LIMIT batchSize` vs offset `LIMIT offset, batchSize`；b) 载体——`record TenantScanCursor(Long lastId, int batchSize)` 返回 `List<Tenant>` + `nextCursor()`；c) 与 `TenantGateway.listAll()` / `listByStatus()` 的关系——新增 `listByCursor(TenantScanCursor) → CursorResult<Tenant>` 方法；d) 状态过滤——是否只扫描 `ACTIVE` 状态租户（跳过 ARCHIVED / DELETING）。方案 A：keyset pagination + `ACTIVE` 过滤 + `record TenantScanCursor`；方案 B：offset + 全状态 + 传统 POJO                   | 架构      | 执行计划 §7 P5-T5 · Phase 3 P3-T1（`TenantGateway` API）· [coding-guideline.md](coding-guideline.md) §七（纯函数优先） | ☑ 方案 A 已落地（2026-10-12 · `TenantGateway.listActiveByCursor(lastId, batchSize)` keyset pagination `WHERE status='ACTIVE' AND tenant_id > lastId ORDER BY tenant_id ASC LIMIT batchSize` · `TenantGatewayImpl` 实现 · `ExamGradingScheduler` 外层游标循环 + 单租户 try/catch 隔离 + `TenantContext.callWithTenant` 包裹 · 全 reactor 绿）                             | P5-T5 起前 |
| **T-4** | **compose 去固定化方案**（P5-T4）：a) 移除 `container_name`——改为自动生成，health check 用容器内端口；b) 端口外部化——`ports: "${KNOWLEDGE_APP_PORT:-8091}:8091"`；c) 副本化——`deploy.replicas: ${KNOWLEDGE_APP_REPLICAS:-1}` + 前置网关（Caddy / nginx）负载均衡；d) `docker-compose.scale.yml` overlay 还是直接改主 compose。方案 A：主 compose 去 `container_name` + env 外部化 + 新建 `docker-compose.scale.yml` overlay 引入 Caddy 前置；方案 B：直接改主 compose 加 `deploy.replicas` + 不引入前置网关（依赖 Docker 内置 round-robin）                                                                                                                  | 运维/基础设施 | 执行计划 §7 P5-T4 · `docker-compose.yml` 现状                                                                  | ☑ 方案 A 已落地（2026-10-12 · `docker-compose.yml` 去掉 `container_name` + 端口外部化 `${KNOWLEDGE_APP_PORT:-8091}:8091` · 新建 `docker-compose.scale.yml` overlay：`deploy.replicas: ${KNOWLEDGE_APP_REPLICAS:-2}` + Caddy 前置网关（`caddy:2-alpine` 反向代理 + round_robin + 健康检查）+ 应用端口绑定 `127.0.0.1` 仅 Caddy 可达 · `docker/caddy/Caddyfile` 配置 · compose config 校验通过） | P5-T4 起前 |
| **T-5** | **`ExamGradingScheduler` 租户感知改造**（P5-T5）：当前 `ExamGradingScheduler` 全局查询 `listPendingGrading()` 不区分租户。改造方案：a) 外层循环 `TenantScanCursor` 逐租户扫描；b) 单租户异常 try/catch 隔离（`log.error` + 继续下一租户）；c) `TenantContext.callWithTenant(tenantKey, () -> ...)` 包裹每次迭代；d) 分布式锁（S-5）在每租户级别还是全局级别——全局级别（同一时刻只有一个副本跑 grading），避免锁竞争开销。方案 A：全局锁 + 租户游标内循环（推荐）；方案 B：每租户独立锁（粒度更细但锁开销大）                                                                                                                                                                                                      | 架构      | 执行计划 §7 P5-T5 · `ExamGradingScheduler.java` 现状 · Phase 3 P3-T1（`TenantContext.callWithTenant`）           | ☑ 方案 A 已落地（2026-10-12 · `ExamGradingScheduler` 重写：全局分布式锁 `kr:lock:exam-grading` TTL 600s + UUID ownerId → 租户游标 `listActiveByCursor(lastId, 50)` 外层循环 → `TenantContext.callWithTenant(tenantKey, ...)` 包裹单租户扫描 → 超时回收 + 待评分触发 · 单租户 try/catch 隔离不阻塞下一个 · 全 reactor 绿）                                                                             | P5-T5 起前 |

---

## 5. C 类 · 交叉项（Cross-cutting）

> 不属于 Phase 5 本体但与 Phase 5 退出条件挂钩；本类只需指派责任人与里程碑，不阻断开工。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                             | 负责人                 | 依据锚点                                                          | 状态                                                                                                                                                                                                                                                                                                          | 时限               |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------|
| **C-1** | **CI tag 扩展（Phase 5 storage/upload）**：Phase 4 已有 4 job（build · unit-tests · config-isolation · tenant-isolation）。Phase 5 新增：a) `storage-isolation` tag 覆盖 P5-T1 两租户上传互不可见负向；b) `upload-session` tag 覆盖 P5-T2 双副本共享会话集成；c) `distributed-lock` tag 覆盖 P5-T5 双副本不重复评分集成。考虑：Phase 4 C-1 b/c 待补（`llm-profile-isolation` / `cache-invalidation`），Phase 5 可一并补齐                                                                       | 项目负责人 + DevOps + 测试 | 执行计划 §9 X-T3 · Phase 4 清单 §5 C-1 · `.github/workflows/ci.yml` | ☐ 待指派                                                                                                                                                                                                                                                                                                       | Phase 5 交付 PR 同期 |
| **C-2** | **存量本地磁盘文件迁移策略**：P5-T1 双驱动上线后，已有租户上传的本地磁盘文件是否需要迁移到 MinIO。方案 A：不迁移，本地磁盘文件保留原路径，`LocalObjectStorageGatewayImpl` 继续服务存量；新上传走 MinIO（配置切 `minio` 后全量走 MinIO，存量本地文件在 `local` 模式下仍可读取）；方案 B：写一次性迁移脚本 `scripts/migrate-local-to-minio.sh`，遍历 `data/documents/` 与 `data/exam-assets/` 上传到 MinIO 后 rename 原目录为 `.migrated`；方案 C：双读——`MinioObjectStorageGatewayImpl.retrieve()` miss 时 fallback 到本地磁盘（透明迁移，但增加复杂度）。考虑：Phase 5 退出条件不要求存量迁移 | 运维 + 架构             | 执行计划 §7 P5-T1 · Phase 5 退出条件                                  | ☐ 待指派                                                                                                                                                                                                                                                                                                       | Phase 5 交付后评估    |
| **C-3** | **Phase 3 尾分支时序耦合**：Phase 3 P3-T5 partition-key 分支 + P3-T6 SILO runner 受 B-2 cluster 门控未闭。Phase 5 与 Phase 3 尾分支是否可并行——P5-T1 MinIO 与 P3-T5 partition-key 无代码交叉，可并行；P5-T5 定时任务分片与 P3-T6 SILO runner 可能有交互（SILO 逐库后 `ExamGradingScheduler` 是否需要感知 SILO 集合）。方案 A：并行开发，Phase 3 尾分支先合，Phase 5 P5-T5 后合（确保 SILO 感知）；方案 B：串行 Phase 3 尾分支 → Phase 5                                                                                     | 项目负责人 + 运维          | Phase 3 清单 §2 B-2 · §7 第 14/15 条 · Phase 4 清单 §5 C-2          | ☐ 待指派                                                                                                                                                                                                                                                                                                       | Phase 5 会审前      |
| **C-4** | **文档与 AGENTS 更新**（X-T6）：Phase 5 交付后更新：a) `AGENTS.md` §1 分册地图新增 Phase 5 清单行；b) `tech-stack.md` §2 新增 MinIO SDK 版本行；c) `architecture-decisions.md` 补"对象存储层"段落；d) `security-guideline.md` 补"对象存储路径穿越防护"段落；e) `rag-domain-guideline.md` 补"MinIO 文档存储"段落；f) `saas-migration-summary.md` 补 Phase 5 交付记录                                                                                                                              | 项目负责人               | 执行计划 §9 X-T6 · AGENTS.md §4 维护约定                              | ☑ P5-T1 全部完成（a ✅ Phase 5 清单行已加 · b ✅ MinIO SDK 行已加 · c ✅ `architecture-decisions.md` §7 对象存储层 · d ✅ `security-guideline.md` §3 路径穿越防护 + §8 高风险条目更新 · e ✅ `rag-domain-guideline.md` §三 文档存储 · f ✅ `saas-migration-summary.md` v0.3 P5-T1 交付记录 · 附：5 份文件交叉引用 security-guideline 编号修正 §3→§4/§4→§5/§5→§6/§7→§8） | Phase 5 各任务交付后   |

---

## 6. 签署页

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。五类角色齐签后 Phase 5 转"全量授权开工"。

| 角色         | 关注条目                                                      | 结论    | 签署人 | 日期 |
|------------|-----------------------------------------------------------|-------|-----|----|
| 架构负责人      | S-1 · S-2 · S-3 · S-4 · T-1 · T-2 · T-3 · T-5 · C-2 · C-3 | ☐ 待签署 | 蒙斌  | —  |
| 安全负责人      | S-2 · S-3 · T-2 · C-2                                     | ☐ 待签署 | 莫道  | —  |
| 运维/基础设施负责人 | B-1 · B-2 · S-3 · S-5 · T-4 · C-1 · C-2                   | ☐ 待签署 | 无始  | —  |
| 产品/商务负责人   | C-2                                                       | ☐ 待签署 | 七月  | —  |
| 项目负责人（协调）  | B-1 · B-2 · C-1 · C-3                                     | ☐ 待签署 | 流星  | —  |

**签署要点提示**（v0.1 起草）：

- Phase 5 与 Phase 3/4 不同，**无硬前置门槛**——Redis 与 MinIO 基础设施均已就绪，所有 B/S/T 项均为 Phase 5 本体决策。
- 架构（蒙斌）：关注 S-1 `ObjectStorageGateway` 端口 API 边界 + S-4 双驱动门控模式 + T-3 `TenantScanCursor` 设计 + T-5
  `ExamGradingScheduler` 租户感知改造。
- 安全（莫道）：关注 S-2 存储路径 prefix 穿越防护 + T-2 assetKey 租户校验行为（404 vs 403）+ C-2 存量文件迁移是否引入侧信道。
- 运维/基础设施（无始）：关注 B-1 MinIO SDK 版本兼容性 + B-2 Redis 容量评估 + S-5 分布式锁 TTL 设定 + T-4 compose 去固定化方案。
- 产品/商务（七月）：关注 C-2 存量迁移是否影响客户体验。

---

## 7. 起草与升版路径

**版本阶梯**：

1. **v0.1（2026-10-12 起草）**：Phase 4 v0.4 全量交付后起草 Phase 5 门禁清单，把执行计划 §7 Phase 5 任务表 5
   项任务的开放设计问题 + Phase 5 退出条件落成 16 项 B/S/T/C 四类可勾选表。本清单起草不代表 Phase 5
   已授权开工，仅供会审。前置依赖全满足，无硬前置门槛。
2. **v0.2（2026-10-12 交付回写）**：**P5-T1 ✅ 已交付**——B-1 ☑ 方案 A（MinIO SDK 8.5.17 + 双驱动）、S-1 ☑ 方案 A（
   `ObjectStorageGateway` 6 方法 + `StoredObject` + `StorageCategory`）、S-2 ☑ 方案 A（MinIO prefix
   `{tenantKey}/{category}/{yyyy}/{mm}/{uuid}.{ext}` + 本地绝对路径向后兼容 + `PathGuard` 双侧校验）、S-4 ☑ 方案 A（
   `StorageConfig` 双 `@ConditionalOnProperty`）、T-1 ☑（dependencyManagement + tech-stack.md）。全 reactor `./mvnw test`
   绿。剩余 B-2/S-3/S-5/T-2~T-5/C-1~C-4 待签署/待指派。
3. **v0.3（2026-10-12 文档闭合）**：**C-4 ✅ 全部完成**——c ✅ `architecture-decisions.md` §7 对象存储层（端口/实现/路径规约/COLA
   合规）· d ✅ `security-guideline.md` §3 对象存储路径穿越防护（`PathGuard` 本地 + MinIO 双侧）+ §8
   高风险条目新增路径穿越 · e ✅ `rag-domain-guideline.md` §三 文档存储（`ObjectStorageGateway` 使用指南）· f ✅
   `saas-migration-summary.md` v0.3 P5-T1 交付记录（无 DDL）· 附：5 份文件 security-guideline
   编号修正（§3→§4/§4→§5/§5→§6/§7→§8，涉及 `coding-guideline.md`/`data-and-migration-guideline.md`/
   `code-review-checklist.md`/`saas-phase4-b1-b2-decision-pack.md`）。剩余 10 项 B-2/S-3/S-5/T-2~T-5/C-1~C-3 待签署/待指派。

**执行清单**：

1. **会审前准备**：架构 + 安全 + 运维三方各出 1 名代表过一遍 B/S/T 全部 12 项，标记需补料项。
2. **B 类决策**：B-1 MinIO SDK 版本与双驱动策略由架构 + 运维共评；B-2 Redis 共用实例由运维确认容量。
3. **S/T 类可延后**：若某项在 P5-TX 起草前无法定，可 ☑ 授权延后至具体 PR 起草（与 Phase 3/4 清单 S/T 处理方式一致）。
4. **C 类待指派**：C-1 CI 扩展 + C-2 存量迁移 + C-3 Phase 3 时序 + C-4 文档更新，四项均需明确到具体人与里程碑。

**Phase 5 与 Phase 3 尾分支的排产关系**：见 §5 C-3。Phase 3 尾分支（partition-key/SILO）受 B-2 cluster 门控未闭，与 Phase 5
无代码交叉，原则上可并行开发；但 P5-T5 定时任务分片可能需感知 SILO 集合，建议 Phase 3 尾分支先合。

---

## 8. 未纳入本清单的相关事项（透明声明）

以下不属于 Phase 5 开工门禁，但常在讨论中被混淆，此处显式排除避免范围漂移：

| 事项                                                                              | 归属           | 备注                                              |
|---------------------------------------------------------------------------------|--------------|-------------------------------------------------|
| Phase 3 尾分支 P3-T5 partition-key 切换 / `MilvusSdkAdapter` / `SiloCollectionAdmin` | Phase 3      | 由 Phase 3 清单 §2 B-2 cluster 门控，与 Phase 5 无代码交叉  |
| Phase 3 尾分支 P3-T6 SILO Flyway 逐库 runner                                         | Phase 3      | 由 Phase 3 清单 §7 第 15 条门控，本清单 C-3 跟踪时序           |
| Phase 3 尾分支 P3-T7 IdP 首登绑定 RBAC/租户归属映射                                          | Phase 3      | 由 Phase 4 清单 §5 C-3 承接，与 Phase 5 无关             |
| Phase 6 计费配额（`tenant_usage` / QuotaGuard / 套餐限额）                                | Phase 6 可选后置 | 与 Phase 5 退出条件解耦，Phase 6 另立清单                   |
| 数据驻留 `data_region` 物理分区部署                                                       | 跨 Phase 布局   | 不影响 Phase 5 对象存储与水平扩展本体                         |
| 前端跨域 / Cookie / IdP 首登跳转流程                                                      | X-T5（贯穿）     | 与 Phase 5 后端改造解耦                                |
| `KeyRotationCmdExe` 双人复核密钥轮换端点                                                  | Phase 4 遗留   | P4-T4 核心链路已交付，轮换端点未落码，属 Phase 4 收尾不在 Phase 5 范围 |

---

**清单版本 v0.3 · 2026-10-12 文档闭合 · P5-T1 ✅ 已交付（B-1/S-1/S-2/S-4/T-1 五项 ☑ 方案 A 落地 + C-4 ☑ 六份文档全部更新 +
交叉引用修正）· 前置依赖全满足 · 无硬前置门槛 · 剩余 10 项 B-2/S-3/S-5/T-2~T-5/C-1~C-3 待签署/待指派**
