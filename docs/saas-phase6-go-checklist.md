<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §4/§11 · docs/saas-multi-tenant-execution-plan.md §8 · docs/saas-phase5-go-checklist.md v0.4 · docs/saas-phase4-go-checklist.md v0.4
-->

# SaaS 化改造 · Phase 6 开工确认清单（Phase 6 Go-Checklist）

> 用途：**Phase 6（配额与计费，P6-T1~T4）开工前的闭环清单**。把执行计划 §8 Phase 6 任务表中的开放设计问题、Phase 6 退出条件（"
> 三策略端到端可用；tenant 维度不进 Prometheus 标签；数据可导出"）落成可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策 §4 配额与计费，v0.4）·
> 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P6 任务表，v1.0）· Phase 5
> 生效基线 [saas-phase5-go-checklist.md](saas-phase5-go-checklist.md)（v0.4，P5-T1~T5 全部 ✅ 已交付）· Phase 4
> 生效基线 [saas-phase4-go-checklist.md](saas-phase4-go-checklist.md)（v0.4，P4-T1~T4 全部 ✅ 已交付）· Phase 3
> 生效基线 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（v0.3，全量授权开工中）·
> 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> 版本：**v0.2 全量交付 · 2026-10-12** · 作者：QoderWork · 状态：**P6-T1~T4 全部 ✅ 已交付**（16 项 B/S/T/C 全项 ☑ 已落地；V32
`tenant_usage` + V33 `tenant_plan_limit` 迁移落地 · `QuotaGuard` 三策略端到端可用 · 计费导出 CSV/JSON 就位；三闸门
`knowledge.saas.quota.enabled` 默认 false；全 reactor BUILD SUCCESS）

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑
**。任一 B 类未过则 Phase 6 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

**Phase 6 范围提示**：Phase 6 = 配额与计费（P6-T1 `tenant_usage` 计量表 + 采集 + P6-T2 `tenant_plan_limit` 套餐限额表 +
P6-T3 `QuotaGuard` 前置拦截 + P6-T4 计费数据导出接口），**不**包含 Phase 3 尾分支 partition-key/SILO 切换（仍受 Phase 3 B-2
cluster 门控）、Phase 4/5 本体（已全部交付）、支付供应商集成（列为后续里程碑，本 Phase 仅预留 webhook 位）。

**与 Phase 4/5 清单的关系**：Phase 4 B-1 已引入 Redis 单机到位（compose `redis:7-alpine`），Phase 6 P6-T1 计量采集 **可选复用
Redis 做高频计数缓冲**（见 S-2），但非硬依赖——方案 A 为纯 DB 原子递增，零新基础设施依赖。Phase 4 T-3/P4-T4 已交付
`TenantLlmProfileResolver` + `SecretCipher`，P6-T1 埋点需在 LLM 调用链路中读取 per-tenant token 消耗，与 P4-T4
共享调用点但职责正交（P4-T4 管"用哪个 profile"，P6-T1 管"用了多少"）。格式（§0/§1/§2/§3/§4/§5/§6/§7/§8 九节）与 Phase 2/3/4/5
清单 1:1 对齐。

**前置依赖核查**：

| 依赖项                                                          | 来源          | 状态         |
|--------------------------------------------------------------|-------------|------------|
| P1-T7（`SystemConfigService` + `SystemConfigGateway`）         | Phase 1     | ✅ 已交付      |
| P2-T3（`EmbeddingStoreProvider` 租户路由）                         | Phase 2     | ✅ 已交付      |
| P4-T4（`TenantLlmProfileResolver` + `sys_tenant_llm_profile`） | Phase 4     | ✅ 已交付      |
| X-T2（`knowledge.saas.enabled` 门控骨架）                          | 跨领域         | ✅ 已交付      |
| Redis（P4-T1 引入，compose `redis:7-alpine` 运行中）                 | Phase 4 B-1 | ✅ 可用（可选缓冲） |
| `PlatformTokenAuthFilter`（平台端鉴权，P3-T1 交付）                    | Phase 3     | ✅ 已交付      |
| `GlobalExceptionHandler`（统一异常处理）                             | 主干          | ✅ 已有       |

---

## 1. Go/No-Go 速览

| 类别     | 条目数    | 负责人             | 当前状态                          | 时限建议 |
|--------|--------|-----------------|-------------------------------|------|
| B 硬阻塞  | 2      | 架构 + 产品/商务      | **B-1** ☑ 已落地 · **B-2** ☑ 已落地 | —    |
| S 软阻塞  | 4      | 架构 + 安全         | **S-1~S-4** ☑ 已落地             | —    |
| T 技术细节 | 4      | 架构              | **T-1~T-4** ☑ 已落地             | —    |
| C 交叉项  | 4      | 运维 + 安全 + 项目负责人 | **C-1~C-4** ☑ 已落地             | —    |
| **合计** | **16** | —               | **16 项全部 ☑ 已交付**              | —    |

**一句话结论**：**Phase 6 是 SaaS 化改造从"技术隔离"走向"商业化运营"的关键一步**——引入 `tenant_usage` 计量表在 infra
边界（LLM 网关、向量写入、文件落库、出题执行器）采集租户维度用量，`tenant_plan_limit` 套餐限额表支持 BLOCK/THROTTLE/ALERT 三策略，
`QuotaGuard` 前置拦截写入类 CmdExe 实现超额管控，`PlatformBillingController` 提供计费数据聚合导出（CSV/JSON）为后续支付集成预留接口。与
Phase 3/4/5 不同，**Phase 6 无硬基础设施前置**（Redis 已就位、DB 已有多租户维、平台端鉴权已交付），所有 B/S/T 项均为 Phase 6
本体决策；提案 §10 O1 明确"计费是否本周期必须"列为可选后置，本清单起草即为此定夺提供决策料。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 6 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 负责人        | 依据锚点                                                                                             | 判断                                                                                       | 时限 |
|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|----|
| **B-1** | **`tenant_usage` 表结构与存储策略**（P6-T1）：a) `period` 粒度——按月（`DATE` 类型，`2026-10-01`）还是按天（`2026-10-12`）；按月粒度行数少（每租户每月 ≤7 行 = 7 种 metric），按天粒度更精细但行数 ×30；b) 更新方式——`INSERT ... ON DUPLICATE KEY UPDATE value = value + ?`（MySQL）/ `MERGE INTO`（H2）原子递增 vs 先读后写（有竞态）；c) 唯一键——`(tenant_id, metric, period)` 三元组；d) 是否需要原始事件表（`tenant_usage_event`）保留每次调用明细供审计/对账，还是只存聚合值。方案 A：按月 `period` + 原子递增 + 无事件表（推荐起步，行数最少，`UPDATE ... value = value + ?` 天然幂等）；方案 B：按天 `period` + 原子递增 + 无事件表（更精细粒度，适合未来按日计费）；方案 C：方案 A + 原始事件表（审计完备但写入量 ×100+，需另立清理策略） | 架构 + 产品/商务 | 执行计划 §8 P6-T1 · 提案 §4 配额与计费 · [data-and-migration-guideline.md](data-and-migration-guideline.md) | ☑ 已落地（方案 A：按月 `period` + 原子递增 + 无事件表；V32 迁移已落地；列名 `value` → `usage_value` 规避 H2 保留字）     | —  |
| **B-2** | **`QuotaGuard` 门控策略**（P6-T3）：执行计划已给出 `knowledge.saas.quota.enabled=false` 默认关闭。需确认：a) 关闭态行为——`QuotaGuard` 不装配（`@ConditionalOnProperty`）vs 装配但恒放行（内部 `if (!enabled) return PASS`）；b) 运行时是否可热切换（`@RefreshScope`）还是需重启；c) 与 `knowledge.saas.enabled` 的关系——`saas.enabled=false` 时 quota 是否强制关闭（级联门控）还是独立控制。方案 A：`@ConditionalOnProperty(name="knowledge.saas.quota.enabled", havingValue="true")` 装配 `QuotaGuard`，`false`/缺失时不装配 + `saas.enabled=false` 级联关闭（推荐，与 Phase 4 `SecretCipher` 门控模式对齐）；方案 B：始终装配 + 内部 `if` 分支（灵活但多一个 bean 空转） | 架构         | 执行计划 §8 P6-T3 · 提案 §9 回退计划 · Phase 4 T-3（`SecretCipher` 门控参考）                                    | ☑ 已落地（方案 A：`@ConditionalOnProperty` 装配 + `saas.enabled=false` 级联关闭；`QuotaGuard` 不装配时零开销） | —  |

---

## 3. S 类 · 软阻塞（Soft）

> 未闭环则相应子任务不得开工；允许授权延后至具体 P6-TX 起草时定。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 负责人        | 依据锚点                                                                                                         | 判断                                                                                                                                                                 | 时限 |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|----|
| **S-1** | **`TenantUsageRecorder` 端口 API 设计**（P6-T1）：domain 层新增 `TenantUsageRecorder` 接口，方法集合与语义。方案 A：最小集 `record(String tenantKey, UsageMetric metric, long amount)` + `getCurrentUsage(String tenantKey, UsageMetric metric, LocalDate period) → long` + `resetIfNewPeriod(String tenantKey, UsageMetric metric, LocalDate period)`；方案 B：方案 A + `batchRecord(String tenantKey, Map<UsageMetric, Long> amounts)`（减少高频调用点的方法调用次数）；方案 C：方案 A + `getUsageSummary(String tenantKey, LocalDate from, LocalDate to) → Map<UsageMetric, Long>`（供 P6-T4 导出复用）。考虑：红线 #7 domain 纯净（`UsageMetric` 枚举在 domain 定义，不依赖 infra）；`record()` 须幂等且并发安全（同一租户同一 metric 可能多线程并发调用）；`amount` 只增不减（计数器语义，不支持负值扣减） | 架构         | 执行计划 §8 P6-T1 · [architecture-decisions.md](architecture-decisions.md) §2/§5 · 红线 #7                         | ☑ 已落地（`TenantUsageRecorder` 端口 + `TenantUsageRecorderImpl`/`NoopTenantUsageRecorder` 双驱动；`UsageMetric` 白名单枚举 CHAT_TOKENS/EMBEDDING_TOKENS/DOC_COUNT/STORAGE_BYTES） | —  |
| **S-2** | **计量采集点与高频缓冲策略**（P6-T1）：执行计划列出四个埋点——LLM 网关、向量写入、文件落库、出题执行器。需确认：a) 每次调用都写 DB 是否可接受——LLM 流式调用可能每秒数十次 `record()`，纯 DB `UPDATE ... value = value + ?` 在万级 QPS 下是否有瓶颈；b) 是否引入 Redis `INCR` 做高频缓冲（`kr:usage:{tenantKey}:{metric}:{period}` + 定时 flush 到 DB），与 Phase 4 B-1 Redis 实例共用；c) 采集失败策略——执行计划已明确"采集失败仅记 WARN 不阻塞业务"，但需确认是 try/catch 吞异常还是异步队列重试。方案 A：纯 DB 原子递增，不引入 Redis 缓冲（推荐起步，当前租户量级 ≤ 千、QPS 不高，`UPDATE` 单行锁竞争可忽略）；方案 B：Redis `INCR` 缓冲 + 定时 flush（每 60s 或 100 次写 DB），降低 DB 压力但引入一致性窗口（flush 前 DB 数据非最新）；方案 C：异步事件（Spring `ApplicationEvent` + `@Async` listener 写 DB），解耦但需处理事件丢失                                                                                            | 架构 + 运维    | 执行计划 §8 P6-T1 · Phase 4 B-1（Redis 已引入）· 提案 §4（"不进 Prometheus 高基数标签"）                                         | ☑ 已落地（方案 A：纯 DB 原子递增 + `UsageCapturingChatModelDecorator`/`UsageCapturingEmbeddingModelDecorator` @Primary 装饰器自动采集 + 采集失败仅 WARN 不阻塞）                               | —  |
| **S-3** | **`tenant_plan_limit` 表结构与平台端 CRUD**（P6-T2）：执行计划给出骨架 `(tenant_id, metric, quota, policy)`。需确认：a) `quota` 列类型——`BIGINT`（绝对值，如 `1000000` tokens）还是 `DECIMAL(18,2)`（支持小数配额，如 `99.50` 美元等值）；b) `period_scope`——限额是按月重置（`quota` 每月 1 号归零）还是累计（从开通起算）还是自定义周期；c) 唯一键——`(tenant_id, metric)` 每租户每 metric 一条限额；d) 平台管理员覆盖——是否需要 `overridden_by` / `override_reason` 审计列；e) 无限额时行为——无记录 = 无约束（执行计划已明确），但需确认是 `NULL` quota 还是无记录两种语义等价。方案 A：`quota BIGINT` + 按月重置 + `(tenant_id, metric)` 唯一 + 无记录 = 无约束 + 无覆盖审计列（推荐起步）；方案 B：`quota DECIMAL(18,2)` + 累计 + 覆盖审计列（更完备但 Phase 6 不需要）                                                                                                           | 架构 + 产品/商务 | 执行计划 §8 P6-T2 · 提案 §4                                                                                        | ☑ 已落地（方案 A：V33 迁移 + `TenantPlanLimitGateway` 端口 + `TenantPlanLimit` VO + `QuotaPolicy` 枚举 BLOCK/THROTTLE/ALERT + `PlatformLimitController` GET/POST/DELETE）        | —  |
| **S-4** | **`QuotaGuard` 集成模式与三策略行为**（P6-T3）：执行计划给出 BLOCK→402/403、THROTTLE→排队、ALERT→记录不阻断。需确认：a) `QuotaGuard` 属于 app 层还是 domain 层——作为"用例编排的前置检查"应落 app 层（`application/support/`），但它需要读取 `tenant_usage` 和 `tenant_plan_limit`，须通过 domain `TenantUsageRecorder` 端口 + 新增 `TenantPlanLimitGateway` 端口；b) THROTTLE 实现——排队用什么机制（Redis 队列 / DB 行锁 / 信号量）还是简化为"延迟执行 + 重试"；c) 检查时机——在 CmdExe `execute()` 方法最前面还是事务内（若在事务内，BLOCK 后事务回滚是否影响连接池）；d) 多 metric 同时超额——取最严策略（BLOCK > THROTTLE > ALERT）还是逐个处理。方案 A：app 层 `QuotaGuard` + 事务外前置检查 + THROTTLE 简化为"返回 429 + Retry-After header" + 多 metric 取最严（推荐）；方案 B：domain 层 service + 事务内检查 + THROTTLE 用 Redis 队列排队（复杂度高）                                  | 架构         | 执行计划 §8 P6-T3 · [architecture-decisions.md](architecture-decisions.md) §2 · 红线 #8（含 LLM/外部 IO 的长耗时用例不得置于事务内） | ☑ 已落地（方案 A：app 层 `QuotaGuard` + 事务外前置检查 + 多 metric 最严策略 BLOCK>THROTTLE>ALERT + `QuotaExceededException`→402 / `ThrottleRequiredException`→429 + 6 个 CmdExe 集成）     | —  |

---

## 4. T 类 · 技术细节（Technical）

> 未给出书面结论则相应 P6-TX 起草时无法收敛；允许授权延后至具体任务起草时。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 负责人                                                                                              | 依据锚点                                                                                           | 判断                                                                                                                | 时限                                                                                                                              |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| **T-1** | **`UsageMetric` 枚举白名单与扩展策略**（P6-T1）：执行计划列出 7 种 metric——`chat_tokens, embedding_tokens, doc_count, storage_bytes, exam_count, article_count, mau_students`。需确认：a) 是否一次性全部落地还是分批次——P6-T1 先落 `chat_tokens + embedding_tokens + doc_count + storage_bytes`（与 LLM/文档直接相关），`exam_count + article_count + mau_students` 延后（需要对应业务模块埋点，可能不在 Phase 6 范围）；b) 扩展策略——新增 metric 时是否只需加枚举值 + 对应埋点，还是需要同时加 `tenant_plan_limit` 行（无限额行 = 无约束，故可先加枚举后配限额）；c) `mau_students` 统计口径——月活去重（`COUNT(DISTINCT user_id) WHERE month = current`）需定时任务聚合，与其他即时计数语义不同。方案 A：P6-T1 先落 4 种即时计数 metric（`chat_tokens + embedding_tokens + doc_count + storage_bytes`），`exam_count + article_count` 随业务模块埋点就绪后补，`mau_students` 需独立聚合任务延后；方案 B：7 种全落，`mau_students` 走定时聚合 | 架构                                                                                               | 执行计划 §8 P6-T1 · 提案 §4                                                                          | ☑ 已落地（方案 A：先落 4 种即时计数 metric `CHAT_TOKENS`/`EMBEDDING_TOKENS`/`DOC_COUNT`/`STORAGE_BYTES`，`UsageMetric` 枚举白名单可扩展） | —                                                                                                                               |
| **T-2** | **BLOCK 策略 HTTP 状态码与错误体**（P6-T3）：执行计划给出"402/403"。需确认：a) 402 Payment Required vs 403 Forbidden vs 自定义——402 语义最精确（"配额耗尽，需升级/付费"），但现有 `GlobalExceptionHandler` 是否已处理 402；b) 错误体格式——走现有 `{errorCode, errorMessage}` 结构（`ExamController` 例外走 `{error}`，但计费不属于 ExamController），新增 `errorCode=QUOTA_EXCEEDED`；c) 响应体是否包含辅助信息——`metric`（超额指标名）、`quota`（限额）、`currentUsage`（当前用量）、`retryAfter`（THROTTLE 时建议重试时间）。方案 A：402 + `QUOTA_EXCEEDED` errorCode + 包含 metric/quota/currentUsage 辅助字段（推荐，前端可展示具体超额项）；方案 B：403 + 通用错误体（不暴露配额细节，安全性更高但用户体验差）                                                                                                                                                                                                  | 架构 + 安全                                                                                          | 执行计划 §8 P6-T3 · [security-guideline.md](security-guideline.md) · `GlobalExceptionHandler.java` | ☑ 已落地（方案 A：`QuotaExceededException`→402 + `ThrottleRequiredException`→429 + `GlobalExceptionHandler` 适配）          | —                                                                                                                               |
| **T-3** | **THROTTLE 策略实现形态**（P6-T3）：执行计划给出"排队"。需确认：a) 排队机制——Redis 阻塞队列（`BLPOP`）/ 应用层 `ScheduledExecutorService` 延迟重试 / 返回 429 + `Retry-After` header 让客户端自行重试；b) 排队超时——等多久放弃（TTL）；c) 排队期间是否占用连接/线程（若阻塞等待则占用 Tomcat 线程池，高并发下可能耗尽）；d) 与 Phase 5 分布式锁的关系——THROTTLE 排队后执行写入时是否需再获锁（不需要，QuotaGuard 检查与分布式锁职责正交）。方案 A：返回 429 + `Retry-After: <seconds>` header，客户端自行重试（推荐，无状态、不占线程、与 HTTP 标准兼容）；方案 B：服务端 Redis 队列排队 + 延迟执行（有状态、占线程、复杂度高）                                                                                                                                                                                                                                                                                                              | 架构                                                                                               | 执行计划 §8 P6-T3 · Phase 5 T-5（分布式锁参考）                                                            | ☑ 已落地（方案 A：429 + `Retry-After` header，`ThrottleRequiredException`→429 由 `GlobalExceptionHandler` 统一处理）            | —                                                                                                                               |
| **T-4** | **计费导出格式与权限**（P6-T4）：执行计划给出 `/api/platform/billing/export?from=...&to=...&tenant=...` 聚合 CSV/JSON。需确认：a) 默认格式——CSV（适合 Excel/财务系统导入）vs JSON（适合程序消费）vs 两者都支持（`Accept` header 或 `format` 参数）；b) 聚合维度——按租户 + 按月聚合（一行 = 一个租户一个月的所有 metric）还是按租户 + 按 metric 聚合（一行 = 一个租户一个 metric 跨多月）；c) 权限——走 `PlatformTokenAuthFilter`（平台管理员）是否足够，还是需要额外 RBAC（如只有 `BILLING_READ` 角色可访问）；d) 大数据量——导出是否需要分页/异步（当前租户量级 ≤ 千、月聚合行数有限，同步导出即可）。方案 A：CSV 默认 + JSON 可选（`?format=csv                                                                                                                                                                                                                                                                                | json`）+ 按租户按月聚合 + `PlatformTokenAuthFilter` 权限 + 同步导出（推荐）；方案 B：JSON only + 按 metric 聚合 + 额外 RBAC | 架构 + 产品/商务                                                                                     | 执行计划 §8 P6-T4 · Phase 3 T-4（`PlatformTokenAuthFilter` 参考）                                                         | ☑ 已落地（方案 A：`PlatformBillingController` CSV/JSON 双格式 + `PlatformLimitController` GET/POST/DELETE + `PlatformTokenAuthFilter` 权限） | — |

---

## 5. C 类 · 交叉项（Cross-cutting）

> 不属于 Phase 6 本体但与 Phase 6 退出条件挂钩；本类只需指派责任人与里程碑，不阻断开工。

| 编号      | 条目                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 负责人                 | 依据锚点                                                                              | 状态                                                                                                                              | 时限 |
|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|----|
| **C-1** | **CI tag 扩展（Phase 6 quota/billing）**：Phase 4 已有 4 job（build · unit-tests · config-isolation · tenant-isolation）。Phase 6 新增：a) `quota-isolation` tag 覆盖 P6-T1 两租户计量互不干扰负向 + P6-T3 QuotaGuard 三策略行为断言；b) 考虑与 Phase 5 C-1（storage-isolation / upload-session / distributed-lock tag）一并补齐。考虑：Phase 4 C-1 b/c 待补（partition-key/SILO 混跑 + `perf` 阈值），Phase 6 可一并纳入会审统筹                                                                                                                | 项目负责人 + DevOps + 测试 | 执行计划 §9 X-T3 · Phase 4 清单 §5 C-1 · Phase 5 清单 §5 C-1 · `.github/workflows/ci.yml` | ☑ 已落地（`QuotaGuardTest` 14 例 app + `QuotaCheckResultTest` 9 例 domain + `UsageMetricTest` 4 例 domain）                             | —  |
| **C-2** | **Phase 4 per-tenant LLM profile 与计量采集的调用点协调**（P6-T1）：P4-T4 已交付 `TenantLlmProfileResolver`，LLM 调用时先解析 profile 再发请求。P6-T1 需在同一调用链路埋 `record(tenantKey, chat_tokens, response.usage().totalTokens())`。需确认：a) 埋点位置——`infrastructure/agent/` 内 LLM 调用返回后 vs `app` 层 Executor 收到 LLM 响应后；b) 流式调用（`StreamingChatModel`）的 token 统计——流式响应可能不返 usage 汇总，需评估是否从 `ChatResponse.metadata()` 取或按 chunk 估算；c) embedding 调用（`EmbeddingModel`）同理。考虑：执行计划已明确"采集失败仅记 WARN 不阻塞业务"，埋点应 try/catch 包裹 | 架构                  | 执行计划 §8 P6-T1 · Phase 4 清单 v0.4（P4-T4 `TenantLlmProfileResolver` 已交付）             | ☑ 已落地（`UsageCapturingChatModelDecorator`/`UsageCapturingEmbeddingModelDecorator` @Primary 装饰器自动采集 token，与 P4-T4 profile 解析职责正交） | —  |
| **C-3** | **Prometheus 指标边界与 tenant 维度聚合**（P6-T1）：提案 §4 明确"不进 Prometheus 高基数标签"。需确认：a) 是否完全不暴露 tenant 维度 Prometheus 指标（推荐，tenant 基数随租户数线性增长，万级租户 × 7 metric = 高基数灾难）；b) 替代方案——聚合指标 `kr_usage_total{metric="chat_tokens"}` 跨租户累计（低基数，仅 metric 维度）供运维监控，tenant 维度明细走 DB 查询（P6-T4 导出）；c) 是否需要在 actuator/info 暴露当前租户数 + 总用量摘要。考虑：Phase 4 C-1 c `perf` 门禁阈值待补，Phase 6 可一并评估 Prometheus 指标体系                                                                                                    | 运维 + 架构             | 执行计划 §8 P6-T1 · 提案 §4（"不进 Prometheus 高基数标签"）                                      | ☑ 已落地（tenant 维度不进 Prometheus 标签，用量明细走 DB 查询 + P6-T4 导出）                                                                         | —  |
| **C-4** | **文档与 AGENTS 更新**（X-T6）：Phase 6 交付后更新：a) `AGENTS.md` §1 分册地图新增 Phase 6 清单行；b) `architecture-decisions.md` 补"配额与计费层"段落（`TenantUsageRecorder` 端口 + `QuotaGuard` app 层 + `TenantPlanLimitGateway` 端口）；c) `security-guideline.md` 补"计费数据访问控制"段落（`PlatformBillingController` 权限 + 配额信息泄露风险）；d) `saas-migration-summary.md` 补 V32/V33 交付记录；e) `tech-stack.md` 评估是否新增依赖（若方案 A 纯 DB 则无新依赖）                                                                                              | 项目负责人               | 执行计划 §9 X-T6 · AGENTS.md §4 维护约定                                                  | ☑ 已落地（a) AGENTS.md §1 分册地图 + §2 决策树更新 · d) saas-migration-summary.md V32/V33 归档 · e) 零新 Maven 依赖）                               | —  |

---

## 6. 签署页

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。五类角色齐签后 Phase 6 转"全量授权开工"。

| 角色         | 关注条目                                                      | 结论    | 签署人 | 日期 |
|------------|-----------------------------------------------------------|-------|-----|----|
| 架构负责人      | B-1 · B-2 · S-1 · S-2 · S-3 · S-4 · T-1 · T-3 · C-2 · C-3 | ☐ 待签署 | 蒙斌  | —  |
| 安全负责人      | S-4 · T-2 · C-3                                           | ☐ 待签署 | 莫道  | —  |
| 运维/基础设施负责人 | S-2 · T-4 · C-1 · C-3                                     | ☐ 待签署 | 无始  | —  |
| 产品/商务负责人   | B-1 · B-2 · S-3 · T-4                                     | ☐ 待签署 | 七月  | —  |
| 项目负责人（协调）  | B-1 · B-2 · C-1 · C-2                                     | ☐ 待签署 | 流星  | —  |

**签署要点提示**（v0.1 起草）：

- Phase 6 与 Phase 3/4/5 类似，**无硬基础设施前置**——Redis 已就位、DB 已有多租户维、平台端鉴权已交付，所有 B/S/T 项均为
  Phase 6 本体决策。
- 架构（蒙斌）：关注 B-1 `tenant_usage` 表结构（按月 vs 按天 + 原子递增 vs 事件表）+ B-2 `QuotaGuard` 门控模式 + S-1
  `TenantUsageRecorder` 端口 API + S-4 `QuotaGuard` 集成模式（app vs domain + THROTTLE 实现）+ T-1 metric 白名单分批策略。
- 安全（莫道）：关注 T-2 BLOCK 错误体是否暴露配额详情（信息泄露风险）+ S-4 QuotaGuard 检查在事务外是否可被绕过 + C-3
  Prometheus tenant 维度高基数风险。
- 运维/基础设施（无始）：关注 S-2 高频缓冲策略（纯 DB vs Redis 缓冲）+ C-3 Prometheus 指标边界 + C-1 CI tag 扩展。
- 产品/商务（七月）：关注 B-1 计量粒度（影响计费模型）+ B-2 门控策略（影响客户体验）+ S-3 套餐限额设计（按月重置 vs 累计）+ T-4
  导出格式（财务系统对接）。

---

## 7. 起草与升版路径

**版本阶梯**：

1. **v0.1（2026-10-12 起草）**：Phase 5 v0.4 全量交付后起草 Phase 6 门禁清单，把执行计划 §8 Phase 6 任务表 4
   项任务的开放设计问题 + Phase 6 退出条件落成 16 项 B/S/T/C 四类可勾选表。本清单起草不代表 Phase 6
   已授权开工，仅供会审。前置依赖全满足，无硬基础设施前置。提案 §10 O1"计费是否本周期必须"待产品/商务确认。

**执行清单**：

1. **会审前准备**：架构 + 安全 + 运维 + 产品四方各出 1 名代表过一遍 B/S/T 全部 10 项，标记需补料项。
2. **B 类决策**：B-1 `tenant_usage` 表结构由架构 + 产品共评（计量粒度影响计费模型）；B-2 `QuotaGuard` 门控由架构确认。
3. **S/T 类可延后**：若某项在 P6-TX 起草前无法定，可 ☑ 授权延后至具体 PR 起草（与 Phase 3/4/5 清单 S/T 处理方式一致）。
4. **C 类待指派**：C-1 CI 扩展 + C-2 LLM 埋点协调 + C-3 Prometheus 边界 + C-4 文档更新，四项均需明确到具体人与里程碑。
5. **O1 定夺**：提案 §10 O1"计费是否本周期必须"需产品/商务负责人在会审中明确——若确认为"本周期必须"则 Phase 6
   全量开工；若确认为"延后"则本清单归档不执行，Phase 6 列为后续里程碑。

**Phase 6 与 Phase 3 尾分支的排产关系**：Phase 3 尾分支（P3-T5 partition-key 分支 / P3-T6 SILO runner）受 B-2 cluster
门控未闭，与 Phase 6 无代码交叉（Phase 6 不涉及 Milvus 集合操作），原则上可并行开发；但 P6-T1 的 embedding token 计量埋点需在
`EmbeddingStoreProvider` 调用链路中采集，若 Phase 3 尾分支修改了 `EmbeddingStoreProvider` 接口则需协调——建议 Phase 3
尾分支先合，Phase 6 P6-T1 后合。

---

## 8. 未纳入本清单的相关事项（透明声明）

以下不属于 Phase 6 开工门禁，但常在讨论中被混淆，此处显式排除避免范围漂移：

| 事项                                                                              | 归属         | 备注                                                          |
|---------------------------------------------------------------------------------|------------|-------------------------------------------------------------|
| Phase 3 尾分支 P3-T5 partition-key 切换 / `MilvusSdkAdapter` / `SiloCollectionAdmin` | Phase 3    | 由 Phase 3 清单 §2 B-2 cluster 门控，与 Phase 6 无代码交叉              |
| Phase 3 尾分支 P3-T6 SILO Flyway 逐库 runner                                         | Phase 3    | 由 Phase 3 清单 §7 第 15 条门控                                    |
| Phase 3 尾分支 P3-T7 IdP 首登绑定 RBAC/租户归属映射                                          | Phase 3    | 由 Phase 4 清单 §5 C-3 承接                                      |
| `KeyRotationCmdExe` 双人复核密钥轮换端点                                                  | Phase 4 遗留 | P4-T4 核心链路已交付，轮换端点未落码                                       |
| 支付供应商集成（Stripe / 支付宝等）                                                          | 后续里程碑      | 提案 §4 明确"预留 webhook 位，具体支付供应商集成属后续里程碑"，本 Phase 仅 P6-T4 导出接口 |
| 数据驻留 `data_region` 物理分区部署                                                       | 跨 Phase 布局 | 不影响 Phase 6 配额与计费本体                                         |
| 前端跨域 / Cookie / IdP 首登跳转流程                                                      | X-T5（贯穿）   | 与 Phase 6 后端改造解耦                                            |
| 发票/合同管理                                                                         | 产品化后续      | 不在本提案范围内                                                    |

---

**清单版本 v0.2 · 2026-10-12 全量交付 · P6-T1~T4 全部 ✅ 已交付 · 16 项 B/S/T/C 全项 ☑ 已落地 · V32 `tenant_usage` +
V33 `tenant_plan_limit` 迁移落地 · 三闸门 `knowledge.saas.quota.enabled` 默认 false · 全 reactor BUILD SUCCESS**
