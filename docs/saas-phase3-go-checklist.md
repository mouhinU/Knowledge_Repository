<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §10 R1 · docs/saas-multi-tenant-proposal-review-pack.md §6 · docs/saas-multi-tenant-execution-plan.md §5 · docs/saas-phase2-go-checklist.md §2 B-1/B-2/B-3 · §3 S-1/S-3 · §4 T-1/T-3
-->

# SaaS 化改造 · Phase 3 开工确认清单（Phase 3 Go-Checklist）

> 用途：**Phase 3（生命周期 + SILO 引入，P3-T0~T6）开工前的最后一道闭环清单**。把 Phase 2 清单消解/延后至 Phase 3 的 B/S/T 项、评审包 §3 未决项 U2/U3/U4/U6、执行计划 §5 Phase 3 任务表中的开放设计问题落成可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策，v0.3）· 三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md)（签署栏，v1.1）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P3 任务表，v0.9——本次清单起草已同步加 P3-T0 spike 行 + §11 S6 Sprint 条 + 关键路径更新）· Phase 2 生效基线 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md)（v0.3，已交付归档）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> Spike 报告：[../spikes/milvus-partition-key/RESULTS.md](../spikes/milvus-partition-key/RESULTS.md)（P2-T0 toy 规模结论：partition-key 语义正确 + LangChain4j JSON 谓词等价）· [../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)（**P3-T0 万级规模复测，2026-10-07 已跑**：为 S-2/S-3/T-2 提供实证——核心负向结论：10k–20k/≤100 租户 IVF_FLAT 下 partition-key P95 13–21ms 反而慢于普通标量/JSON 谓词 8–12ms，无剪枝性能收益；schema 可建可查 T-2 已验证；触发 R1 复核建议）
> 版本：v0.1（2026-09-30 起草）· 作者：QoderWork · 状态：**待会审**——Phase 2 交付归档同日起草本清单，把 Phase 2 消解/延后至 Phase 3 的 6 项（B-1/B-2/B-3/S-1/S-3/T-1）与新引入的 12 项（P3-T0~T6 本体 + SSO/迁移演练/CI 扩展/配置耦合）合并成 18 项 B/S/T/C 四类可勾选表。**未签署前 Phase 3 不得开工**；项目负责人（架构+安全侧）口头授权或三方书面会签均可解锁开工。

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑**。任一 B 类未过则 Phase 3 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

**Phase 3 范围提示**：Phase 3 = 生命周期（P3-T1~T6）**+** partition-key/SILO 目标态落地（承接 Phase 2 S-1/S-3/T-1 延后项）**+** SILO Flyway 逐库 runner。**不**包含 Phase 4 sys_config 加租户维（依赖 P3-T3 但排 Phase 4）与 Phase 5 对象存储（P3-T5 依赖其 prefix 规约但 P5-T1 排 Phase 5）——两项在 §5 C 类中作为交叉项跟踪。

**与 Phase 2 清单的关系**：本清单是 Phase 2 清单 v0.3 的下游承接，Phase 2 清单保留为"生效基线"归档。本清单格式（§0/§1/§2/§3/§4/§5/§6/§7/§8 九节）与 Phase 2 清单 1:1 对齐，便于横向比较。

---

## 1. Go/No-Go 速览

| 类别 | 条目数 | 负责人 | 当前状态 | 时限建议 |
|---|---|---|---|---|
| B 硬阻塞 | 3 | 运维/基础设施 + 项目负责人 | **Phase 2 遗留 3 项**（B-1 Milvus 环境承接 / B-2 cluster 决策 / B-3 运维栏签署），Phase 3 起前需正式书面闭环；触发条件不再消解——Phase 3 建集合 + drop 集合是运维实质动作 | Phase 3 起前 2 周 |
| S 软阻塞 | 5 | 产品/商务 + 架构 + 运维 | S-1 SILO 准入门槛 · S-2 现网集合 → partition-key 迁移策略 · S-3 万级时延阈值 P95 · S-4 Archive 保留期与双人复核 · S-5 Provisioning 状态机幂等键 | Phase 3 起前 1 周给初值或延后至具体 P3-TX 前 |
| T 技术细节 | 6 | 架构 | T-1 provider 多 store 缓存策略 · T-2 partition-key 集合 schema 与 LangChain4j 兼容 · T-3 SiloCollectionAdmin 生命周期 API + 逐库 Flyway · T-4 平台端点鉴权分层 · T-5 复杂 ACL 下推边界 · T-6 SILO placement 决策器 | P3-T0 spike 起前给结论；P3-T1 起草 PR 前 T-4/T-6 必闭 |
| C 交叉项 | 4 | 运维 + 安全 + 项目负责人 | C-1 CI 门禁扩展（partition-key + SILO 混跑） · C-2 X-T4 迁移停机演练 · C-3 X-T5 SSO 决策 · C-4 Phase 4 与 Phase 3 P3-T3 耦合顺序 | 与 P3-T0/T5/T6 同期 |
| **合计** | **18** | — | — | — |

**一句话结论**：**Phase 3 是 SaaS 化改造从"门控影子态"进入"生产可用态"的临门一脚**——引入 `io.milvus` SDK 直连建 partition-key 集合（POOL 目标态）+ SILO 独立集合生命周期（`SiloCollectionAdmin`）+ `MilvusVectorStoreService` 从单 store 升级为 provider 多 store 缓存 + 平台管理员端点（`/api/platform/**`）+ 租户开通状态机 + Archive/Delete 编排 + SILO Flyway 逐库 runner。**B 类三项运维承接不再消解**（建集合/drop 集合/cluster 决策是硬门槛），Phase 2 遗留的 S-1 SILO 准入门槛与 S-3 万级时延阈值随本清单闭环。R1 决策（partition-key 为 POOL 目标态）延续不回退，Phase 2 的 JSON 谓词回退路径在 partition-key 切换后保留为兼容回退（S-2 决策项）。**待运维 B 类 ☑ 签署 + S/T 结论落定后方可起 P3-T0 spike**。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 3 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。三项 B 类由 Phase 2 清单 §2 B-1/B-2/B-3 原样承接，Phase 3 触发条件不再消解。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **B-1** | 运维书面确认现网 Milvus 2.5.4 环境可承接 Phase 3 全部改动：a) **允许应用侧调用 `io.milvus` SDK 直连建集合**（partition-key `knowledge_chunks_pk` + N×SILO `kc_{tenantKey}` 独立集合）；b) 现有 `knowledge_chunks` 集合的读写账号具备 `list/drop/create/alter` 权限；c) Milvus 备份/恢复流程覆盖 partition-key 集合与 SILO 独立集合的数量增长（每 SILO 租户 1 个集合）；d) **数据挂载点与内存/磁盘预留**：partition-key 集合与 SILO 集合数倍增时的内存 segment 开销、磁盘 snapshot 空间 | 运维/基础设施 | Phase 2 清单 §2 B-1 · 评审包 §2.3 O3 · 提案 §2.4/§10 R1 · 执行计划 §5 P3-T5/P3-T6 | ☐ 签署 ☐ 需补料（列明条目）☐ 否决 | Phase 3 起前 2 周 |
| **B-2** | 运维书面确认 Milvus **单机 vs cluster** 决策：现网单机版能否撑住 POOL partition-key + N×SILO 集合数上限；若否，cluster 模式接入时间窗与成本；partition-key/SILO 建集合的**运维流程**（DDL 审核、监控指标接入、慢查询看板、`list_collections` 巡检频率）由谁负责；partition-key 切换期间是否要维护窗口 | 运维/基础设施 | Phase 2 清单 §2 B-2 · 评审包 §3 U1 · 提案 §10 R1 决策段 · 执行计划 §5 Phase 3 退出条件 | ☐ 签署 ☐ 需补料 ☐ 否决 | Phase 3 起前 2 周 |
| **B-3** | 提案 §11 审批栏运维栏闭环：由 ☑ 需补料（U1 Milvus 环境 / U3 集群承接）→ ☑ 签署。本清单 §2 B-1/B-2 通过后，同步更新 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 签署页运维栏，并在提案 §11 第三条 checkbox 打勾；本清单 §6 签署页运维栏同步 ☑ | 项目负责人（协调） + 运维（签署） | Phase 2 清单 §2 B-3 · 提案 §11 · 评审包 §6 签署页 | ☐ 已闭环 ☐ 待运维书面签署 | Phase 3 起前 |

**B 类决策模板**（供签署后填）：三项 B 类是 Phase 3 唯一无法通过"项目负责人口头授权先行"绕开的硬门槛——partition-key 建集合与 SILO 生命周期是运维实质动作（DDL、备份、cluster 扩容、监控接入），必须由运维/基础设施方书面确认。若运维无法承接 cluster 与备份，Phase 3 需退回"只做生命周期 P3-T1~T4，partition-key/SILO 转 Phase 3.5 独立会审"降级方案（S-2 变体），届时重开本清单 v0.2。

---

## 3. S 类 · 软阻塞（Soft-Blocking）

> 未过不阻断开工，但会显著影响 P3-T0 spike 设计、P3-T1 placement 决策器规则或 P3-T5/T6 生命周期实现；开工前 1 周需给出结论或**明确授权延后至具体任务**。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **S-1** | **SILO 准入门槛**（评审包 U2 / Phase 2 清单 §3 S-1 承接）：合同规模/文档数量/QPS 阈值。方案 A：会前给初值（例如"单租户 `kb_document > 500` 或向量数 > 50 万 or QPS > 20 转 SILO"）；方案 B：延后至 P3-T1 placement 决策器规则草案时再定，本清单先记 ☑ 授权延后；方案 C：Phase 3 **只支持手工 placement**（运维/平台管理员在 `sys_tenant_placement.placement` 手动写 SILO），决策器算法留至 Phase 3.5 或产品化窗口 | 产品/商务 + 架构 | Phase 2 清单 §3 S-1 · 提案 §2.1/§10 R2 · 评审包 §3 U2 · 执行计划 §5 P3-T1 | ☐ 方案 A（给初值） ☐ 方案 B（延后至 P3-T1） ☐ 方案 C（手工 placement） | P3-T1 起前 1 周 |
| **S-2** | **现网 `knowledge_chunks` → partition-key `knowledge_chunks_pk` 迁移策略**（Phase 2 清单 §3 S-2 方案 b 的下一步）：Phase 2 走 LangChain4j JSON 谓词已在现网跑通，Phase 3 引入 partition-key 目标态时如何切换。选项 a)**影子集合双写**——新建 `knowledge_chunks_pk`（io.milvus SDK 建，`tenant_id Int64 partitionKey=true`），写入双路（LangChain4j 旧集合 + partition-key 新集合）、灰度切读（`saas.vector.partitionKey.enabled` 开关）、旧集合保留 30 天后 drop；b)**一次性停机重灌**——运维窗口停机批量 embedding 重算并写新集合（成本 = N × 单条 embed 时间 + 停机窗口）；c)**延后至 SILO 引入 P3-T5 一并做**——Phase 3 前段（P3-T1~T4 生命周期）只做 placement 与状态机，partition-key 切换与 SILO 生命周期同一 PR 落 | 架构 + 运维 | Phase 2 清单 §3 S-2 · spike RESULTS §3 · 执行计划 §5 P3-T0/T5 · 提案 §10 R1 决策段 | ☐ 方案 a（影子双写） ☐ 方案 b（停机重灌） ☐ 方案 c（延后至 P3-T5 一并做） | P3-T0 spike 起前 |
| **S-3** | **万级时延阈值 P95**（Phase 2 清单 §3 S-3 承接）：spike 只在 10 行 toy 规模跑（median ~15ms、p95 ~26ms），无法定分位数阈值。需约定基准数据集（例如从真实 `knowledge_chunks` 采 1–5 万条 bge-m3 1024 维向量 + 三租户均分），并写明"partition-key 检索 P95 时延 ≤ X ms 且 ≤ 标量过滤基线 × Y%"作为 P3-T0 spike 门禁断言；CI gate 加 `-Dgroups=tenant,perf` 分层跑（perf 只在 nightly / pre-prod 触发） | 架构 + 测试 | Phase 2 清单 §3 S-3 · spike RESULTS §5 · 执行计划 §5 P3-T0 | ☐ 已定阈值（填 X/Y） ☐ 延后至 P3-T0 spike 报告一起给 ☐ 由运维在 cluster 决策后重设 | P3-T0 spike 前 |
| **S-4** | **Archive & Delete 保留期与双人复核流程**（P3-T5）：软删保留期默认 **30 天**（proposal §4 归档章节）是否合理？双人复核 token 具体形态（临时 token + 二次确认密码？还是 OTP？审计日志落到 `sys_audit_log` 还是独立表？）。方案 A：沿用 30 天 + 项目负责人临时 token；方案 B：调整保留期（如 15/60/90 天）；方案 C：Phase 3 只落软删与归档状态，物理清理延后至合规窗口独立审批 | 项目负责人 + 运维 + 合规 | 提案 §4 归档章节 · 执行计划 §5 P3-T5 | ☐ 方案 A ☐ 方案 B（填天数）☐ 方案 C（延后物理清理） | P3-T5 起前 1 周 |
| **S-5** | **Provisioning 状态机幂等键与失败重放语义**（P3-T2）：`provision_request_id` 生成方式（UUID v4 / snowflake / `tenantKey + timestamp` 组合？），"各步骤幂等"的具体判定——Flyway 迁移天然幂等（版本号），建 Milvus 集合幂等（`hasCollection` 前置查），建默认 sys_config 需 `(tenant_id, config_key)` 唯一 upsert；FAILED 状态可查询原因（`sys_tenant_provision_step.error_detail` 字段）。方案 A：UUID v4 + `hasCollection` 前置查 + upsert；方案 B：`tenantKey + yyyyMMdd` 业务可读 id | 架构 + 数据库 | 执行计划 §5 P3-T2 · 提案 §5 provisioning 章节 | ☐ 方案 A ☐ 方案 B ☐ 延后至 P3-T2 起草 PR 时给结论 | P3-T2 起前 1 周 |

**S 类决策模板**：五项 S 中 S-1/S-2/S-3 是 partition-key + SILO 引入的核心决策，S-4/S-5 是生命周期细节。若 S-1 采纳方案 C（手工 placement）则 Phase 3 前段可先起 P3-T0 spike；若 S-2 采纳方案 c（延后至 P3-T5 一并做）则 P3-T1~T4 不依赖 partition-key 建集合，可先落生命周期骨架；S-3/S-4/S-5 三项均可"延后至具体 P3-TX 起草 PR 时给结论"，不阻断开工。

**S 类 · P3-T0 万级 spike 实证补充（2026-10-07，证据已就绪供会审，勾选位仍留待决策）**：详见 [../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)。

- **对 S-3**：实测 partition-key 检索 P95 在 10k/3t=20.7ms、10k/50t=13.4ms、20k/100t=20.9ms（本机 standalone / IVF_FLAT / Bounded / limit=10）；同谓词普通标量 P95=8.3/10.1/8.6ms、LangChain4j JSON 谓词 P95=10.3/11.3/12.2ms。**partition-key 三组全部慢于对照 1.2–2.5 倍，无剪枝正收益**。建议：P95 门禁阈值应锚定 JSON 谓词/普通标量路径（如 P95 ≤ 50ms 有裕度），partition-key 若选用则只设隔离正确性断言（P2-T5 已覆盖）、不设性能收益断言。绝对值须预生产/cluster 复跑校准（联动 B-1），不可由本机外推。
- **对 S-2**：三条迁移路径技术均无障碍——① 影子双写：partition-key 集合与 LangChain4j JSON 集合可在同库共存、互不冲突（本 spike 三集合同库建/查/0 泄漏即共存证据），代价是写放大 + 双一致性；② 停机重灌：本机 ingest ~400 rows/s，10k≈25s/20k≈43s（未计索引 build/compaction），作停机窗口下界；③ **方案 c（延后 partition-key、维持 JSON 谓词）被本次性能负结果最有力支撑**——现网隔离已 0 泄漏、更快、零 schema 迁移成本。
- **R1 复核触发点（新增，供会审）**：提案 §10 R1（partition-key 为 POOL 目标态）当初部分基于预期剪枝收益；本 spike 证实在 POOL 现实规模该收益不存在，partition-key 的正当前提应改为"物理隔离/可运维性/未来超大规模期权"而非当前时延。**建议会审把"POOL 是否仍需 partition-key 目标态"重新列为决策项**——本 spike 不单方面改架构，仅把负向实证摆到 S-2/T-2 门禁前供定夺。

---

## 4. T 类 · Phase 3 内部技术细节（Technical）

> 架构自决项，1 小时拉齐即可，但需在 P3-T0 spike 起草前给出书面结论避免中途返工。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **T-1** | **`MilvusVectorStoreService` 多 store 缓存策略**（Phase 2 清单 §4 T-1 承接）：provider 从 Phase 2 单 store 骨架升级为多 store 后的缓存形态。方案 A：`ConcurrentHashMap<tenantKey+placement, EmbeddingStore>` 无失效（进程重启生效）；方案 B：`Caffeine` LRU 上限 100 stores（对齐 SILO 租户数上限）+ placement 变更订阅失效；方案 C：不缓存，每次 `storeFor()` 都重建 store（简单但每请求建 Milvus client 开销大）。**注意**：Phase 3 若单副本则 A/B 均可，多副本（P5-T4）需引入 Redis pub/sub 广播失效（对齐 P4-T2 机制） | 架构 | Phase 2 清单 §4 T-1 · 执行计划 §5 P3-T1/T5 · 评审包 §2.3 O2 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 P3-T1 起草 PR 时 | P3-T1 起前 |
| **T-2** | **partition-key 集合 schema 与 LangChain4j 兼容**（S-2 方案 a/b 落点）：`io.milvus` SDK 建 `knowledge_chunks_pk` 集合的具体字段——`id VARCHAR pk` + `text VARCHAR max 65535` + `metadata JSON`（**保留**，向后兼容 LangChain4j 谓词与 Phase 2 现网 JSON 谓词回退）+ `tenant_id Int64 partitionKey=true` + `vector FloatVector dim=1024`；**LangChain4j `MilvusEmbeddingStore` 只走 rename 不新增顶层列**，Phase 3 若走 io.milvus SDK 自建则需自研 `EmbeddingStore` 实现或抽 `VectorStore` 端口脱离 LangChain4j 具体类。方案 A：抽 domain 端口 `VectorStorePort`，infra 提供 `LangChain4jAdapter` + `MilvusSdkAdapter` 两实现；方案 B：只做 `MilvusSdkAdapter`，Phase 2 `LangChain4j` 单例保留作回退 bean；方案 C：等 LangChain4j 上游支持 partition-key 再切（不确定何时） | 架构 | spike RESULTS §3 · 提案 §10 R1 决策段 · 执行计划 §5 P3-T0 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 P3-T0 spike 报告一起给 | P3-T0 spike 起前 |
| **T-3** | **`SiloCollectionAdmin` 生命周期 API + 逐库 Flyway**（Phase 2 清单 §4 T-3 承接）：`provision(tenantKey)` / `archive(tenantKey)` / `drop(tenantKey)` / `listSiloCollections()` 四方法；与 `SiloCollectionNaming` 静态工具（Phase 2 P2-T4 已落）的调用关系；SILO 独立库（`database-name` per SILO tenant）是否引入——若引入则 P3-T6 `TenantMigrationsRunner` 需按 SILO 租户列表遍历数据源跑 Flyway；若不做独立库则所有 SILO 集合共享 `default` database，`kc_{tenantKey}` 命名区分即可。方案 A：只建独立 collection 不建独立 database（简化 P3-T6）；方案 B：collection + database 双层隔离（合规更强但运维成本高） | 架构 + 数据库 | Phase 2 清单 §4 T-3 · 执行计划 §5 P3-T6 · 提案 §5 SILO 章节 | ☐ 方案 A（只 collection）☐ 方案 B（collection + database） | P3-T1 起前 |
| **T-4** | **平台端点鉴权分层**（P3-T1）：`/api/platform/**` 平台管理员端点的鉴权机制。方案 A：复用现有 `AdminTokenAuthFilter`（拦截 `/api/**` 与 `/actuator/**`）+ 在方法层加 `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`；方案 B：新建独立 `PlatformTokenAuthFilter` 只拦 `/api/platform/**`，用独立 JWT secret + 独立 token；方案 C：平台端点走内网 + IP 白名单，不加 token 层。同步需要：**`sys_role` 是否需要新增 `PLATFORM_ADMIN` 角色**？Phase 1 P1-T4 建 `sys_tenant` 时 `default` 租户管理员的 role 是否升级为平台管理员？ | 架构 + 安全 | 执行计划 §5 P3-T1 · 提案 §5 平台管理端 · 现有 `AdminTokenAuthFilter` | ☐ 方案 A ☐ 方案 B ☐ 方案 C · ☐ `PLATFORM_ADMIN` 角色走 `sys_role` 新增 ☐ 复用现有 ADMIN role + 独立权限表 | P3-T1 起前 |
| **T-5** | **复杂 ACL 下推边界**（Phase 2 清单 §4 T-2 后续）：Phase 2 只做 `tenant_id + category` 前置下推，`AuthorizedSearchSupport.filterAccessible` 应用层后置。Phase 3 若引入 `io.milvus` SDK 自建 `MilvusSdkAdapter`（T-2 方案 A/B）则可下推更复杂谓词——`PUBLIC or (INTERNAL and dept in [...]) or (RESTRICTED and role like ...)`。方案 A：Phase 3 只落 partition-key 切换，ACL 下推另立后续小任务；方案 B：Phase 3 一并做 ACL 下推（增加 P3-T2/P3-T3 交付量）；方案 C：ACL 保留应用层，只做 partition-key + tenant_id 前置 | 架构 + 安全 | Phase 2 清单 §4 T-2 · 执行计划附录 A G3 · spike RESULTS §3 末段 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 Phase 3 中评 | P3-T5 前 |
| **T-6** | **SILO placement 决策器**（P3-T1，与 S-1 联动）：`sys_tenant_placement` 表已 Phase 1 P1-T2 建（当前恒 POOL），Phase 3 引入决策器——按 S-1 阈值自动或半自动切 SILO。方案 A：决策器为**运行时查询**（每次 `storeFor()` 都查 `sys_tenant_placement` + 短 TTL 缓存 5min，与 T-1 缓存失效策略配合）；方案 B：决策器为**写入时快照**（provision 时一次决策，placement 变更需手动触发 reswitch 端点重灌向量）；方案 C：Phase 3 只落 CRUD（平台管理员手动切换 placement），决策器算法延后 | 架构 + 产品/商务 | Phase 2 清单 §3 S-1 · 执行计划 §5 P3-T1 · 提案 §5 placement 章节 | ☐ 方案 A ☐ 方案 B ☐ 方案 C（若 S-1 采纳方案 C 则本项自动 C） | P3-T1 起前 |

**T 类决策模板**：六项 T 中 T-1/T-2/T-3/T-6 与 partition-key/SILO 引入强绑定（若 S-1/S-2 采纳延后方案则 T-1/T-2/T-3/T-6 可随之后移至具体 P3-TX 起草时）；T-4 与 Phase 3 P3-T1 平台端点强绑定，P3-T1 起前必闭；T-5 与 Phase 3 本体解耦，可延后至中评。

**T 类 · P3-T0 万级 spike 实证补充（2026-10-07）**：

- **对 T-2**：目标态 schema 在 Milvus 2.5.4 **可建可查已验证**——用 REST v2（`io.milvus` Java SDK 为同层薄封装，schema 表达等价）成功建立 `id Int64 PK / vector FloatVector(1024) / tenant_id Int64 isPartitionKey=true / category VarChar / numPartitions 64–256` 并配 `enableDynamic=true` 容纳 metadata 兼容层，按 `tenant_id==X` 剪枝查询成功且 0 泄漏。`isPartitionKey` 与 `numPartitions` 须经 schema 段传入。T-2 的"SDK 兼容性"子问题从"待验证"转"已验证可行"。**但**附带发现 partition-key 无性能收益（见 S 类补充对 S-3/R1），因此 T-2 最终选方案 A/B/C 应与 S-2 会审结论合并定夺：若 S-2 采纳方案 c（延后 partition-key），则 T-2 可先只做方案 B（`MilvusSdkAdapter` 骨架 + LangChain4j 回退 bean 并存），不急于抽 `VectorStorePort`。

---

## 5. C 类 · 交叉项（Cross-cutting）

> 不属于 Phase 3 本体但与 Phase 3 退出条件（"平台管理员可通过 API 完成开通→使用→停用→归档→物理清理完整闭环；SILO 租户 schema 与 POOL 版本对齐"）挂钩；本类只需指派责任人与里程碑，不阻断开工。

| 编号 | 条目 | 负责人 | 依据锚点 | 状态 | 时限 |
|---|---|---|---|---|---|
| **C-1** | **X-T3 CI 门禁扩展**：Phase 2 P2-T5 已落 CI `tenant-isolation` job（`-Dgroups=tenant -DargLine="-Dapi.version=1.44"`）。Phase 3 引入 partition-key + SILO 后需扩展：a) 加 `perf` tag（S-3 万级时延阈值）到 nightly；b) 加 `silo` tag 覆盖 SILO 集合生命周期集成（provision → write → search → archive → drop）；c) POOL/SILO 混跑断言（同一 store 分别建 A=POOL、B=SILO，交叉查询零泄漏）。GitHub Actions matrix job 或独立 workflow | 项目负责人 + DevOps + 测试 | 执行计划 §9 X-T3 · Phase 2 清单 §5 C-1 · 现有 [.github/workflows/ci.yml](../.github/workflows/ci.yml) `tenant-isolation` job | ☐ 已扩展 ☐ 待 P3-T5/T6 起时同步 | 与 P3-T5/T6 同期 |
| **C-2** | **X-T4 迁移停机演练**：Phase 1 已跑通 V21→V26（15 表加 `tenant_id` + 默认租户 id=1 + 存量回填）。Phase 3 若 S-2 采纳方案 a（影子双写）则需演练"现网 LangChain4j 集合 → partition-key 新集合灰度切读 → 旧集合 30 天后 drop"运维窗口；若 S-2 采纳方案 b（停机重灌）则需演练"N 小时停机窗口批量重算 embedding"成本与回滚。演练脚本归档到 `.buckups/` 或 `scripts/migration-drills/`（对齐用户运维脚本归档偏好） | 运维 + 项目负责人 | 执行计划 §9 X-T4 · 提案 §7 迁移章节 · Phase 2 清单 §8 U6 | ☐ 已演练 ☐ 待 S-2 决策后指派 | 与 S-2 决策同期 |
| **C-3** | **X-T5 SSO 决策**（评审包 U4）：Phase 3 前是否要接企业 IdP（OIDC/SAML）？若开则影响 P3-T1 平台端点鉴权形态（T-4 需扩展）与 P3-T3 建库管理员引导 token 生成方式；若不开延后至 Phase 4/5。方案 A：延后至 Phase 4；方案 B：Phase 3 内接 OIDC；方案 C：延后至商务窗口独立评估 | 产品 + 安全 + 运维 | 评审包 §3 U4 · 执行计划 §10 X-T5 · Phase 2 清单 §8 U4 | ☐ 方案 A ☐ 方案 B ☐ 方案 C | 本清单会审前 1 周 |
| **C-4** | **Phase 4 P4-T1 `sys_config` 加租户维 与 Phase 3 P3-T3 建库默认配置耦合顺序**：P3-T3 provisioning 最后一步"拷贝平台默认 → 租户级 sys_config"依赖 P4-T1 完成 `sys_config` 表 `(tenant_id, config_key)` 唯一约束改造。方案 A：P4-T1 提前至 Phase 3 尾（作为 P3-T3 前置任务）；方案 B：P3-T3 只做"根部门 + 首个 admin"，租户级 config 延后至 Phase 4；方案 C：Phase 3 用平台默认 config 硬编码常量占位，Phase 4 迁移到表 | 架构 + 项目负责人 | 执行计划 §5 P3-T3 · §6 P4-T1 · 提案 §6 配置章节 | ☐ 方案 A ☐ 方案 B ☐ 方案 C | P3-T3 起前 2 周 |

---

## 6. 签署页

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。三类角色齐签后 Phase 3 转"开工中"。**v0.1 说明**：本清单待会审，五方签署位保持未打即视为未授权开工。运维侧 B-1/B-2/B-3 三项为 Phase 3 唯一硬门槛（不像 Phase 2 可通过 S-2 方案 b 消解），需书面签署或明确"cluster 承接不可行→降级方案 c 延后 partition-key"。

| 角色 | 关注条目 | 结论 | 签署人 | 日期 |
|---|---|---|---|---|
| 架构负责人 | B-2 · S-1 · S-2 · S-3 · S-5 · T-1 · T-2 · T-3 · T-5 · T-6 · C-3 · C-4 | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 安全负责人 | S-4 · T-4 · T-5 · C-1 · C-3 | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 运维/基础设施负责人 | **B-1 · B-2 · B-3** · S-2 · S-3 · S-4 · T-3 · C-1 · C-2 · C-3 | ☐ 签署 ☐ 需补料（列明条目） ☐ 否决 | ______ | ______ |
| 产品/商务（S-1/T-6/C-3 相关） | S-1 · T-6 · C-3 | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 项目负责人（协调与最终 Go/No-Go） | 全部 | ☐ 同意开工 ☐ 部分授权（列明可先动的 P3-TX） ☐ 暂缓 | ______ | ______ |

**一句话结论**（每方必填）：

- 架构：______
- 安全：______
- 运维/基础设施：______
- 产品/商务：______
- 项目负责人：______

---

## 7. 通过后的下一步

1. **回读同步（v0.1 起草已完成 + v0.1 → v0.2 授权落地时执行）**：
   - **v0.1（本清单起草，2026-09-30）**：同步执行计划 v0.8 → v0.9（§5 加 P3-T0 spike 行 + §11 S6 Sprint 条 + 关键路径更新）、提案 §8 路线图 Phase 3 行 + §10 R1 决策段追加"清单已起草"子项、评审包 §6 附带决议追加"Phase 3 清单已起草"bullet、AGENTS.md §1 分册地图新增 `saas-phase3-go-checklist.md` 一行 + §2 决策树 Phase 3 分支指向本清单。
   - **v0.2（授权落地时执行）**：清单 S/T/B 结论签署后回写：
     - [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §5 P3-T0/P3-T1/P3-T2/P3-T3/P3-T4/P3-T5/P3-T6 交付物与验收栏按 S-1/S-2/S-3/S-4/S-5 与 T-1~T-6 结论调整范围（若 S-2 采纳方案 c 则 P3-T1~T4 与 partition-key 切换分离）；§4 尾段"转 Phase 3 会审"标记改为"已由 Phase 3 清单 §X 承接"。
     - [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) 头部 v0.9 → v1.0，标注 Phase 3 开工授权已下。
     - [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 R1 决策段追加"Phase 3 partition-key 切换落地节奏"；§11 审批说明段补记运维 B-1/B-2/B-3 签署；§8 路线图 Phase 3 行状态由"清单起草"→"进行中"；提案版本戳 v0.3 → v0.4。
     - [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 附带决议将"清单已起草"改为"清单 v0.2 授权已下"；运维栏 ☑ 需补料 → ☑ 签署（若 B-1/B-2/B-3 通过）；U2/U3/U4/U6 各项按 §5 C 类与 §3 S 类闭环同步；评审包版本戳 v1.1 → v1.2。
     - [../AGENTS.md](../AGENTS.md) §1 分册地图 Phase 3 清单行版本戳由 v0.1 → v0.2、状态由"待会审"→"开工授权已下"；§2 决策树 Phase 3 分支同步。
     - 本清单 v0.1 → v0.2（授权状态），Phase 3 交付完成后升 v0.3（归档生效基线）。

2. **P3-T0 spike · 已执行（2026-10-07，`chore(saas): P3-T0 partition-key 集合万级 spike`）**：交付内容见 [../spikes/milvus-partition-key-scale/](../spikes/milvus-partition-key-scale/)（`spike_scale.py` + `README.md` + `RESULTS.md` + 合并 `results.json` + 三组分档 `results_*.json`）：
   - 万级规模复测三组：10k/3t、10k/50t、20k/100t（IVF_FLAT / 随机归一 1024 维 / Bounded / 本机 standalone），另 `50k/100t` 触发 Milvus standalone（~3.8GB）**OOM 崩溃**（作 B-1 运维内存预留实证，非数据档）。
   - **核心结论（回写本清单 §3 S-2/S-3、§4 T-2）**：partition-key 无剪枝性能收益（P95 13–21ms，慢于普通标量/JSON 谓词 8–12ms）；目标态 schema 可建可查 + 与 LangChain4j JSON 集合同库共存 0 泄漏（T-2 SDK 兼容已验证）；触发"POOL 是否仍需 partition-key 目标态"的 **R1 复核建议**。
   - **未改生产代码**：全程 `spikescale_*` 临时集合自动 drop，仅复核生产 `knowledge_chunks` 未受影响；结论作为证据摆到门禁前，**S-2/S-3/T-2 勾选位仍留待会审定夺**（本 spike 不单方面改架构）。

3. **P3-T1 起 PR**：以 `feat(tenant): P3-T1 PlatformTenantController 与平台端鉴权` 提交，交付内容：
   - 新 `web/controller/PlatformTenantController.java`（`/api/platform/**`）· `application/executor/platform/*`（`create/suspend/resume/archive/delete/get/list` 六 executor）· 平台管理员鉴权按 T-4 决策落 `@PreAuthorize` 或独立 `PlatformTokenAuthFilter`。
   - 若 T-4 采纳"复用 ADMIN role + `PLATFORM_ADMIN` 权限表"则新增 `sys_platform_admin` 表（Flyway V27）；否则复用 `sys_role.role_code='PLATFORM_ADMIN'`。
   - 端点默认关闭，`knowledge.platform.enabled=true` 才注册；写操作全审计落 `sys_audit_log` 或独立 `platform_audit_log`。
   - **不做**：状态机（P3-T2）、SILO placement 决策器实现（若 S-1 采纳 A/B 则另立 P3-T1.5）。

4. **P3-T2 起 PR**：以 `feat(tenant): P3-T2 租户开通状态机（Provisioning PENDING→PROVISIONING→ACTIVE/FAILED）` 提交，交付内容：
   - 新 `application/service/ProvisioningStateMachine.java` · 迁移 `V28__provision_state_machine.sql`（`sys_tenant.status` 加列 + 新表 `sys_tenant_provision_step`）。
   - 幂等键与失败重放语义按 S-5 决策落；集成测试覆盖"中断后重试不重复建表 / FAILED 可查询原因 / 步骤级幂等"。
   - **不做**：SILO 建集合（属 P3-T5/T6，视 S-2 决策可能合并）；默认 sys_config 拷贝（属 P3-T3）。

5. **P3-T3 起 PR**（依赖 C-4 决策）：以 `feat(tenant): P3-T3 建库默认配置与根部门引导` 提交，交付内容：
   - Provisioning 最后一步：按 C-4 方案落 `sys_config` 拷贝逻辑（若 A 提前 P4-T1 则走表，若 B/C 则走常量或延后）；建根 dept（`parent_id=null`）；建首个 admin 用户并发引导 token。
   - 集成：新租户首次登录即看到默认值。
   - **不做**：租户级 config 分布式失效（属 P4-T2）。

6. **P3-T4 起 PR**：以 `feat(tenant): P3-T4 Suspend/Resume 软拦` 提交，交付内容：
   - `sys_tenant.status=SUSPENDED` 时 `AdminTokenAuthFilter`（或 `PlatformTokenAuthFilter` 若 T-4 采纳方案 B）返 403 且带明确 `error_code=TENANT_SUSPENDED`。
   - 集成：suspend 后 5s 内该租户请求全拒；resume 立即恢复。
   - **不做**：Archive/Delete 编排（属 P3-T5）。

7. **P3-T5 起 PR**（关键任务，可能吸收 partition-key 切换 S-2 c 方案）：以 `feat(tenant): P3-T5 Archive & Delete 编排（保留期 + 对账 dry-run + 双人复核）` 提交，交付内容：
   - 新 `application/executor/tenant/ArchiveTenantCmdExe.java` · 后台对账 `TenantDeletionReconciler`（`@Scheduled` 每日跑）。
   - 顺序：软删 → 保留期（S-4 决策值，默认 30 天）→ 逐租户遍历 `kb_document` → Milvus 删向量（走 `SiloCollectionAdmin.drop` 或 POOL partition-key 删 `tenant_id=X` 记录）→ 对象存储删文件（**依赖 P5-T1 MinIO 已接入**——若 P5-T1 未完成则本 PR 只落 DB/Milvus，文件层延后）→ DB 行删。
   - 每次执行前 dry-run 预览；双人复核 token 流程按 S-4 落。
   - 集成：中断可续跑；文件/DB/Milvus 三处一致；保留期内可 cancel。
   - **若 S-2 采纳方案 c**：本 PR 一并落 partition-key 切换（`MilvusSdkAdapter` 落地 + `knowledge_chunks_pk` 建集合 + 双写或停机重灌）；否则 partition-key 另立 P3-T2.5。

8. **P3-T6 起 PR**：以 `feat(tenant): P3-T6 SILO Flyway 逐库 runner` 提交，交付内容：
   - 新 `infrastructure/migration/TenantMigrationsRunner.java`（`@Scheduled` + 手工触发端点 `/api/platform/schema-health`）· 按 T-3 决策遍历 SILO 数据源（若 A 只 collection 则遍历本库集合；若 B collection + database 则遍历每 SILO database）。
   - 集成：主链加 V27/V28 后 SILO 自动跟上；不一致告警；单库失败不影响其他库；提供单库重跑工具。
   - **不做**：跨机房 schema 一致性（属 Phase 5 U3 数据驻留）。

9. **C 类交叉项跟踪**：
   - C-1 CI 门禁扩展与 P3-T5/T6 同期上线，独立 PR 或合并到 P3-T5 均可。
   - C-2 迁移演练在 S-2 决策后 2 周内完成，脚本归档到 `.buckups/` 或 `scripts/migration-drills/`。
   - C-3 SSO 决策在本清单会审前 1 周给出（若延后至 Phase 4 则在 §7 归档记录注明）。
   - C-4 sys_config 与 P3-T3 耦合顺序在 P3-T3 起前 2 周闭环。

---

## 8. 未纳入本清单的相关事项（透明声明）

以下不属于 Phase 3 开工门禁，但常在讨论中被混淆，此处显式排除避免范围漂移：

| 事项 | 归属 | 备注 |
|---|---|---|
| Phase 4 全部（`sys_config` 加租户维 / 分布式失效 / `kb_extraction_cache` 加租户维 / per-tenant LLM profile） | Phase 4 | 本清单 C-4 只跟踪 P3-T3 与 P4-T1 耦合顺序，Phase 4 本体另立 Phase 4 开工门禁清单（如需） |
| Phase 5 全部（MinIO SDK / Redis 上传会话 / assetKey 校验 / compose 副本化 / 定时任务分片） | Phase 5 | P3-T5 Archive 编排依赖 P5-T1 MinIO 完成，若 P5-T1 未完成则 P3-T5 只落 DB/Milvus 两处，文件层延后并在 §7 步骤 7 注明 |
| Phase 6 计费配额（`tenant_usage` 表 / QuotaGuard / 套餐限额） | Phase 6 可选后置 | 与 Phase 3 生命周期解耦 |
| U3 数据驻留 `data_region` 物理分区部署 | Phase 5 布局 | 评审包 §3 U3；不影响 Phase 3 生命周期本体 |
| 复杂 ACL 深度下推（超出 T-5 范围的多属性 OR/NOT 组合） | Phase 3 中评或独立后续 | 本清单 T-5 只做边界决策；深度实现另立小任务 |
| 前端跨域/Cookie 策略调整 | X-T5（贯穿） | 评审包 §3 U5；已由 P1-T3 三源解析支撑，Phase 3 无前端改动 |

---

> 记录人：QoderWork · 性质：**Phase 3 开工前的门禁清单，与评审包 §6 签署页互补**——评审包对整个 SaaS 提案做三方决策，本清单专门收口 Phase 3 未闭环项（承接 Phase 2 清单消解/延后的 B/S/T 6 项 + Phase 3 本体新引入的 12 项）。
> 归档时机：五方齐签 + S/T/B 结论回写执行计划/提案/评审包后，本文件状态从"待会审"（v0.1）→"开工授权已下"（v0.2）→"Phase 3 已交付归档"（v0.3）；Phase 4 起前保留于 `docs/` 作为生效基线，届时参照格式决定是否创建 `docs/saas-phase4-go-checklist.md`。
