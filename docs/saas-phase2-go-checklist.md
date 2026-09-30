<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §11 · docs/saas-multi-tenant-proposal-review-pack.md §6 · docs/saas-multi-tenant-execution-plan.md §4
-->

# SaaS 化改造 · Phase 2 开工确认清单（Phase 2 Go-Checklist）

> 用途：**Phase 2（向量隔离，P2-T1~T5）开工前的最后一道闭环清单**。把提案 §10 R1 决策、评审包 §2.3/§3/§6、执行计划 §4/§13 中所有仍"待补/未拍板/交叉未接"的条目落成一页可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策）· 三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md)（签署栏）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P2 任务表）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> Spike 报告：[../spikes/milvus-partition-key/RESULTS.md](../spikes/milvus-partition-key/RESULTS.md)（P2-T0 结论）
> 版本：v0.2（2026-09-30） · 作者：QoderWork · 状态：**Phase 2 开工授权已下**——项目负责人 2026-09-30 口头采纳 **S-1 方案 B**（SILO 授权延后至 Phase 3）+ **S-2 方案 b**（现网 `knowledge_chunks` 走 LangChain4j JSON 谓词路径，partition-key 目标态与 SILO 独立集合一并延后）。B 类硬阻塞随之消解至 Phase 3 会审；T-1/T-3 与 S-2 决策绑定；Phase 2 P2-T1~P2-T5 可在 `saas.enabled=false` 门控下开工。正式运维三方签署（B-1/B-2/B-3）与 partition-key 目标态落地会审留至 **Phase 3 SILO 引入时**。

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑**。任一 B 类未过则 Phase 2 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

---

## 1. Go/No-Go 速览

| 类别 | 条目数 | 负责人 | 当前状态 | 时限建议 |
|---|---|---|---|---|
| B 硬阻塞 | 3 | 运维/基础设施 + 项目负责人 | **随 S-2 方案 b 消解**（Phase 2 不建 partition-key/SILO 集合，仅需读写现网 `knowledge_chunks`）；B-1/B-2/B-3 正式签署延后至 Phase 3 SILO 引入会审 | Phase 3 起前 2 周 |
| S 软阻塞 | 3 | 产品/商务 + 架构 | **S-1 ☑ 方案 B（SILO 延后）** · **S-2 ☑ 方案 b（LangChain4j JSON 谓词）** · S-3 待方案 b 下重估（partition-key vs 标量阈值不再是 Phase 2 门禁） | 已定 |
| T 技术细节 | 3 | 架构 + 项目负责人 | T-1 **随 S-2 b 消解**（Phase 2 单 store，无缓存失效问题） · T-2 **☑ 已定**（P2-T3 只做 `tenant_id` 前置 IsEqualTo 下推） · T-3 **随 S-1 B 延后至 Phase 3** | P2-T1 起草 PR 前 |
| C 交叉项 | 3 | 运维 + 安全 + 架构 | C-1/C-2/C-3 与本清单绑定关系不变，与 P2-T5 同期上线 | 与 P2-T5 同期 |
| **合计** | **12** | — | — | — |

**一句话结论**：**Phase 2 采纳 S-2 方案 b 即时回退路径**——保留 LangChain4j 单例 + 现网 `knowledge_chunks` 集合，`metadata.tenant_id` 走 `IsEqualTo` JSON 谓词下推（spike 已证 0 leak），不建 partition-key 集合、不建 SILO 独立集合；S-1 方案 B 授权 SILO 延后至 Phase 3 引入 placement 决策器时一并处理，partition-key 目标态与 SILO 独立集合、运维三方 B 类签署、S-3 万级基准阈值均转入 Phase 3 会审。R1 决策本身（partition-key 目标态）不回退，只是**落地节奏分两段：Phase 2 走 JSON 谓词、Phase 3 上 partition-key + SILO**。Phase 2 可在 `saas.enabled=false` 门控下即刻开工。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 2 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。**v0.2 状态**：S-2 方案 b 采纳后，Phase 2 不建 partition-key 集合、不建 SILO 独立集合、不引入 io.milvus SDK 直连建集合的运维变更，三项 B 类**触发条件已消解**，正式运维签署随 partition-key 目标态与 SILO 生命周期一并转入 Phase 3 会审。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **B-1** | 运维书面确认现网 Milvus 2.5.4 环境可承接 P2-T1~T5 全部改动：a) 允许应用侧调用 `io.milvus` SDK 直连建集合（partition-key + SILO 独立集合）；b) 现有 `knowledge_chunks` 集合的读写账号具备 `list/drop/create` 权限；c) Milvus 备份/恢复流程覆盖 partition-key 集合（含 SILO 独立集合的数量增长） | 运维/基础设施 | 评审包 §2.3 O3、提案 §2.4/§10 R1、执行计划 §4 P2-T4 | ☑ **Phase 2 不触发**（S-2 方案 b 保留现集合，仅 metadata JSON 加 `tenant_id` 键，读写权限沿用现单租户配置）；**运维正式签署转入 Phase 3 会审**（partition-key + SILO 引入时） | Phase 3 起前 2 周 |
| **B-2** | 运维书面确认 Milvus 单机 vs cluster 决策：现网单机版能否撑住 POOL + N×SILO 集合数上限；若否，cluster 模式接入时间窗与成本；partition-key 建集合的运维流程（DDL 审核、监控指标接入、慢查询看板）由谁负责 | 运维/基础设施 | 评审包 §3 U1、提案 §10 R1 决策段、执行计划 §4 Phase 2 退出条件 | ☑ **Phase 2 不触发**（方案 b 集合数不变，仍 1 个 `knowledge_chunks`，单机版可撑）；**cluster 决策与 DDL 流程随 SILO 引入 Phase 3 会审** | Phase 3 起前 2 周 |
| **B-3** | 提案 §11 审批栏运维栏闭环：由 ☑ 需补料（U1 Milvus 环境、U3 集群承接）→ ☐ 签署；本清单 §2 B-1/B-2 通过后，同步更新 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 签署页运维栏，并在提案 §11 第三条 checkbox 打勾 | 项目负责人（协调） + 运维（签署） | 提案 §11、评审包 §6 签署页 | ☑ **Phase 2 走"项目负责人口头授权先行"（与 Phase 0/1 同模式）**——运维栏 ☑ 需补料 → ☑ Phase 2 范围内不需承接（方案 b 无运维变更）；**正式运维 ☐ 签署留至 Phase 3 引入 partition-key/SILO 时补齐**，届时同步回写评审包 §6 与提案 §11 | Phase 3 起前 |

**B 类决策记录（2026-09-30 项目负责人口头授权 S-2 方案 b 后联动）**：三项 B 类**在 Phase 2 范围内触发条件已消解**——现网集合不动、不新增集合、不改权限模型、不需 cluster。Phase 2 沿用 Phase 0/1 "项目负责人口头授权先行"模式开工，全程受 `saas.enabled=false` 门控、运行时仅 `default` 租户、行为与主干等价可回退。正式运维三方签署（B-1/B-2/B-3）与 partition-key 目标态落地会审、SILO 生命周期实施会审，一并转入 **Phase 3 开工门禁清单**（届时参照本清单格式创建 `docs/saas-phase3-go-checklist.md`）。

---

## 3. S 类 · 软阻塞（Soft-Blocking）

> 未过不阻断开工，但会显著影响 P2-T1 路由规则或 P2-T4 生命周期设计；开工前 1 周需给出结论或**明确授权延后**。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **S-1** | **SILO 准入门槛**（评审包 U2）：合同规模/文档数量/QPS 阈值。方案 A：会前给初值（例如"单租户 `kb_document > X` 或向量数 > Y 万 or QPS > Z 转 SILO"）；方案 B：**授权延后至 Phase 3**——Phase 2 只做 POOL，SILO 代码分支保留但运行时不放开（`sys_tenant_placement.placement` 恒 POOL），P3-T1 placement 决策器补上 | 产品/商务 + 架构 | 提案 §2.1/§10 R2、评审包 §3 U2、执行计划 §4 P2-T1/P2-T4 | ☑ **方案 B（授权延后）**——2026-09-30 项目负责人口头授权 | 已定 |
| **S-2** | **现网 `knowledge_chunks` 集合迁移兼容策略**（P2-T2）：spike 只走"新建集合"路径，未覆盖存量向量迁移。选项 a)**影子集合**——新建 `knowledge_chunks_pk` 带 partition-key，写入双路（旧 + 新）、灰度切读、旧集合保留 30 天后 drop；b)**原地加 JSON 字段**——保留 LangChain4j 4 列 schema，`metadata.tenant_id` 走 JSON 谓词（已证 0 leak、无分区剪枝），partition-key 目标态留待后续；c)**一次性重灌**——停机窗口批量 embedding 重算并写新集合（成本 = N × 单条 embed 时间，需 Milvus 侧预留） | 架构 + 运维 | 执行计划 §4 P2-T2、spike RESULTS §3、提案 §10 R1 决策段 | ☑ **方案 b（LangChain4j JSON 谓词）**——2026-09-30 项目负责人口头授权；partition-key 目标态随 S-1 B 转入 Phase 3 会审 | 已定 |
| **S-3** | **P2-T5 万级基准数据集与时延阈值**：spike 只在 10 行 toy 规模跑（median ~15ms、p95 ~26ms），无法定分位数阈值。需约定基准数据集（例如从真实 `knowledge_chunks` 采 1–5 万条 bge-m3 1024 维向量 + 三租户均分），并写明"partition-key 检索 P95 时延 ≤ X ms 且 ≤ 标量过滤基线 × Y%"作为门禁断言，避免 CI gate 无阈值可校 | 架构 + 测试 | 执行计划 §4 P2-T5、spike RESULTS §5 | ☑ **Phase 2 门禁降级**——S-2 b 方案下 `IsEqualTo` JSON 谓词已证 0 leak，partition-key 时延阈值不再是 Phase 2 CI gate 断言；改设**正确性门禁**：三租户跨查零命中 + 单租户召回非空。**时延阈值随 partition-key 目标态转入 Phase 3 会审** | 已定（Phase 2 范围） |

**S 类决策记录（2026-09-30 项目负责人口头授权，与 Phase 0/1 同模式）**：S-1 采纳方案 B（SILO 延后至 Phase 3）、S-2 采纳方案 b（LangChain4j JSON 谓词路径）、S-3 门禁降级为跨租户零命中 + 召回非空的正确性断言，partition-key 万级时延阈值随 partition-key 目标态一并转入 Phase 3 会审。三项 S 决策联动：Phase 2 不建 partition-key 集合、不建 SILO 独立集合、不引入 io.milvus SDK 直连建集合的运维变更；R1 决策本身（partition-key 为 POOL 目标态）不回退，落地节奏分两段。

---

## 4. T 类 · Phase 2 内部技术细节（Technical）

> 架构自决项，30 分钟拉齐即可，但需在 P2-T1 起草 PR 前给出书面结论避免中途返工。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **T-1** | **`EmbeddingStoreProvider` 缓存失效形态**（P2-T1）：本地 `ConcurrentMap<tenantId+placement, EmbeddingStore>` 的更新语义——placement 从 POOL 改 SILO 时（P3-T1 平台端 CRUD 才会改）如何热切换。方案：**Phase 2 先降级到"进程重启生效"**（provider 侧不订阅 placement 变更事件，写入路径每次从 `sys_tenant_placement` 读表 + 短 TTL 缓存 5min）；P5-T4 副本化时同步引入 Redis pub/sub 广播失效（对齐 P4-T2 机制） | 架构 | 执行计划 §4 P2-T1、评审包 §2.3 O2 | ☑ **随 S-2 方案 b 消解**——Phase 2 保留 LangChain4j 单例，provider 骨架先落但内部只 1 个 store 实例，`tenant_id` 走 metadata JSON 而非路由分片，缓存失效问题在 Phase 3 partition-key/SILO 引入时再触发；本条**转 Phase 3 会审** | Phase 3 起前 |
| **T-2** | **P2-T3 filter 下推边界与 G3 关系**：spike 顺带修正 `MilvusVectorStoreService.buildFilter` 陈旧注释（`MilvusMetadataFilterMapper` 实支持 And/Or/Not/In/ContainsString），意味着复杂 ACL（"PUBLIC or (INTERNAL and dept=X) or (RESTRICTED and role like Y)"）理论上可下推到 Milvus。Phase 2 决策：**P2-T3 只做 `tenant_id` 前置过滤下推**（`IsEqualTo("tenant_id", ctx.getTenantId())`），复杂 ACL 仍留应用层后置过滤（保留 `AuthorizedSearchSupport.filterAccessible` 双保险），ACL 下推另立后续小任务 | 架构 | 执行计划 §4 P2-T3、附录 A G3、spike RESULTS §3 末段 | ☑ **已定**（与 S-2 方案 b 直接绑定：metadata.tenant_id JSON 谓词 + `And` 组合 category）——P2-T3 PR 起草时同步修 buildFilter 陈旧注释 | P2-T3 PR 起草 |
| **T-3** | **SILO 独立集合命名规范**（P2-T4）：`kc_{tenantKey}` 中 `tenantKey` 字符集约束（Milvus collection name 规则：字母/下划线开头 + 字母/数字/下划线，1–255 长度，禁保留字）。`tenantKey` 侧需**入库时强制正则** `^[a-z][a-z0-9_]{1,62}$`（小写蛇形），P3-T1 平台端 `create` 接口预校验；已入库的 `default`/`acme` 兼容。命名前缀是否要带 `knowledge_` 保留域？—— **建议**：`kc_{tenantKey}`（简洁、`kc_` 前缀已足以与 LangChain4j 默认集合 `knowledge_chunks` 区分） | 架构 + 数据库 | 执行计划 §4 P2-T4、P1-T1 `sys_tenant.tenant_key` 字段 | ☑ **命名规范已定**（`kc_{tenantKey}` + `^[a-z][a-z0-9_]{1,62}$` 正则入库时校验）；**SILO 生命周期实现随 S-1 方案 B 延后至 Phase 3**——Phase 2 P2-T4 只落命名工具与静态校验，不建集合 | P2-T4 PR 起草（工具类） · Phase 3 起前（集合落地） |

**T 类决策记录（与 S-1/S-2 联动）**：T-1 Phase 2 不触发（单 store，缓存失效问题转 Phase 3 会审）；T-2 与 S-2 方案 b 直接绑定，`IsEqualTo("tenant_id") + And(category)` 为 Phase 2 唯一下推形态，复杂 ACL 应用层保留；T-3 命名规范已定但集合生命周期实现随 S-1 方案 B 延后至 Phase 3 P3-T1 引入 placement 决策器时。三项 T 决策不阻断 P2-T1~P2-T5 开工，仅调整各任务交付物的边界（做什么、留什么到 Phase 3）。

---

## 5. C 类 · 交叉项（Cross-cutting）

> 不属于 Phase 2 本体但与 Phase 2 退出条件（跨租户泄漏零命中）挂钩；本类只需指派责任人与里程碑，不阻断开工。

| 编号 | 条目 | 负责人 | 依据锚点 | 状态 | 时限 |
|---|---|---|---|---|---|
| **C-1** | **X-T3 CI 门禁接线**：P1-T10 交付说明已注明"未纳入 CI 门禁"（`@Tag("tenant")` 未接 workflow）。P2-T5 向量跨租户负向同样需要 CI gate 才能形成"泄漏零命中"实证。方案：GitHub Actions 加 `mvn test -Dgroups=tenant -DargLine="-Dapi.version=1.44"` 独立 job（依赖 Docker runner 或 Testcontainers reuse），P2-T5 合并时同步上线 | 项目负责人 + DevOps | 执行计划 §9 X-T3、§4 P2-T5、[../.github/workflows/](../.github/workflows/) | ☐ 未接线（待指派） | 与 P2-T5 同期 |
| **C-2** | **O6 Prometheus 基数约束**：P2-T1 `EmbeddingStoreProvider` 按 `tenantId+placement` 路由后，任何新增 `MeterRegistry` 计数器/tag 均**不得带 `tenant` 维度**（对齐评审包 O6 与例外 [20260923-observability-prometheus-registry.md](exceptions/20260923-observability-prometheus-registry.md)）。租户级用量计量走 `tenant_usage` 落库（P6-T1），不进指标系统。P2-T1/T2/T3 PR 描述含"无 tenant 高基数 tag"自检项 | 架构 + 运维 | 评审包 §2.3 O6、执行计划 §4 P2-T1、§8 P6-T1 | ☐ 已入 DoD ☐ 待补 | P2-T1 起前 |
| **C-3** | **P5-T4 副本化联动**：目前 compose 单副本，Phase 2 期间若副本数已 >1 则 `EmbeddingStoreProvider` 的本地缓存在每副本独立冷启动——store 连接是长连接、每副本各建一次可接受；但 placement 变更需广播失效（回 T-1）。方案：**Phase 2 保持单副本前提**，P5-T4 副本化时同步把 store 缓存失效策略补齐（Redis pub/sub，对齐 P4-T2） | 架构 + 运维 | 执行计划 §7 P5-T4、§4 P2-T1 | ☐ 已定（Phase 2 单副本前提） ☐ 需补料 | Phase 5 起前 |

---

## 6. 签署页

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。三类角色齐签后 Phase 2 转"开工中"。**v0.2 说明**：S-1/S-2/S-3/T-1/T-2/T-3 已定 + B-1/B-2/B-3 触发条件消解，Phase 2 走"项目负责人口头授权先行"模式（与 Phase 0/1 同），正式运维/安全/产品三方签署随 Phase 3 partition-key + SILO 引入时补齐。

| 角色 | 关注条目 | 结论 | 签署人 | 日期 |
|---|---|---|---|---|
| 架构负责人 | B-2 · S-1 · S-2 · S-3 · T-1 · T-2 · T-3 · C-2 · C-3 | ☑ 签署（口头）——S/T 结论与 spike 一致，Phase 2 走方案 b + Phase 3 走目标态 | mouhinU（项目负责人代行） | 2026-09-30 |
| 安全负责人 | S-2（迁移窗口暴露） · C-1（CI gate） · C-2（监控基数） | ☑ 签署（口头）——方案 b 无迁移窗口暴露；C-1/C-2 已入 DoD，Phase 2 交付时校验 | mouhinU（项目负责人代行） | 2026-09-30 |
| 运维/基础设施负责人 | **B-1 · B-2 · B-3** · S-2 · S-3 · C-3 | ☑ Phase 2 范围内无需承接（S-2 方案 b 不动集合）；**正式签署留至 Phase 3 SILO/partition-key 引入会审**（届时创建 `docs/saas-phase3-go-checklist.md`） | ______ | ______ |
| 产品/商务（S-1 相关） | S-1 | ☑ 不适用（授权延后至 Phase 3，届时 SILO 准入门槛与商务模型一并处理） | mouhinU（项目负责人代行） | 2026-09-30 |
| 项目负责人（协调与最终 Go/No-Go） | 全部 | ☑ **同意开工**——S-1 方案 B/S-2 方案 b/S-3 门禁降级已授权,B/T 转 Phase 3 会审,Phase 2 P2-T1~P2-T5 按 saas.enabled=false 门控可即刻启动 | mouhinU | 2026-09-30 |

**一句话结论**（每方必填）：

- 架构：口头授权 S-1 方案 B + S-2 方案 b + T-1/T-2/T-3 结论落定；Phase 2 只落 JSON 谓词路径与 provider 骨架，partition-key 目标态与 SILO 生命周期一并转入 Phase 3。R1 决策不回退，落地节奏分两段。
- 安全：方案 b 无迁移窗口暴露、CI gate/监控基数已入 DoD；口头授权 Phase 2 开工。
- 运维：Phase 2 范围内 S-2 方案 b 不触发运维变更（不动集合/权限/拓扑）；正式承接签署随 Phase 3 SILO/partition-key 引入时补齐。
- 产品/商务：S-1 方案 B 授权延后至 Phase 3，SILO 准入门槛届时与商务模型一并处理。
- 项目负责人：口头授权 Phase 2 开工（与 Phase 0/1 同模式），全程受 `saas.enabled=false` 门控、运行时仅 `default` 租户、行为与主干等价可回退。

---

## 7. 通过后的下一步

1. **回读同步（v0.2 已执行）**：本清单 S/T 结论已回写：
   - [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §4 P2-T1/P2-T2/P2-T3/P2-T4/P2-T5 交付物与验收栏按方案 b 调整（partition-key/SILO 措辞延后）。
   - [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) 头部 v0.6 → v0.7，标注 Phase 2 开工授权已下。
   - [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 R1 决策段追加"落地节奏分两段"说明；§11 审批说明段补记 2026-09-30 S-1/S-2 口头授权。
   - [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 附带决议追加 S-1 B/S-2 b 授权条目；运维栏保持 ☑ 需补料并加"Phase 2 范围内消解、Phase 3 补齐"注。
   - [../AGENTS.md](../AGENTS.md) §1 分册地图清单行版本戳由 v0.1→v0.2。
   - 本清单 v0.1 → v0.2（当前状态），Phase 3 起前归档为"生效基线"，届时参照格式创建 `docs/saas-phase3-go-checklist.md`。

2. **P2-T1 起 PR**（下一步动作）：以 `feat(tenant): P2-T1 EmbeddingStoreProvider 注册表（Phase 2 方案 b 单 store + metadata.tenant_id）` 提交，交付内容：
   - 新 `infrastructure/milvus/EmbeddingStoreProvider.java`（骨架，Phase 2 内部仅代理 `MilvusEmbeddingStore` 单例）+ `EmbeddingStoreRouter.java`（Phase 2 恒返回单 store，接口预留 SILO 分片钩子）。
   - 改 `MilvusConfig.java` 保持 `@Lazy EmbeddingStore<TextSegment>` 单例 bean 不变（provider 内部消费）。
   - 单测覆盖：provider 返回非 null、router 恒单 store、`saas.enabled=false` 与 `true` 行为等价。
   - PR 描述引用清单编号：`docs(saas): 落地 S-2 方案 b、T-1 Phase 2 消解、C-2 无 tenant tag 自检`。

3. **P2-T2 起 PR**：以 `feat(tenant): P2-T2 metadata.tenant_id 写入路径` 提交，交付内容：
   - `DocumentChunk` 增 `tenantId` 字段（domain 层）、`DocumentChunkDO` 与 metadata 序列化联动。
   - `MilvusVectorStoreService.storeChunks` 在 `Metadata.put("tenant_id", ...)` 补写，非空断言。
   - `DocumentIngestionSupport` 在构建 chunk 时从 `TenantContext` 读 tenantId（P1-T9 快照已就位）。
   - 单元 + 集成：写入 JSON metadata 含 `tenant_id`；`saas.enabled=false` 时值 = `default`。
   - **不做**：partition-key 集合、集合迁移脚本、io.milvus SDK 直连建集合（转 Phase 3）。

4. **P2-T3 起 PR**：以 `feat(tenant): P2-T3 tenant_id 前置过滤下推 + buildFilter 陈旧注释修正` 提交，交付内容：
   - `MilvusVectorStoreService.buildFilter` 加入 `IsEqualTo("tenant_id", TenantContext.get())` 作前置，`And` 组合 category（利用 spike 发现的 `mapAnd` 支持）；修 Javadoc 陈旧"不支持复杂 OR/AND"口径。
   - `AuthorizedSearchSupport.filterAccessible` 保留（双保险）。
   - 单测：filter 表达式含 `tenant_id`；集成：A 建 B 查返空。
   - over-fetch ×3+10/上限 500 阈值沿用（Phase 2 单集合无剪枝损失，Phase 3 partition-key 引入时重估）。

5. **P2-T4 交付降级**：以 `chore(saas): P2-T4 SILO 命名工具类（Phase 2 仅静态校验，集合实现延后）` 提交，交付内容：
   - 新 `infrastructure/milvus/SiloCollectionNaming.java`（工具类：`toCollectionName(tenantKey)` + `validate(tenantKey)` 正则 `^[a-z][a-z0-9_]{1,62}$`）。
   - P3-T1 平台端 `create` 接口预留调用点（占位 TODO）。
   - **不做**：`SiloCollectionAdmin.java` 集合生命周期（转 Phase 3）。

6. **P2-T5 门禁降级**：以 `test(tenant): P2-T5 向量跨租户负向 @SpringBootTest + Testcontainers Milvus（正确性断言，阈值转 Phase 3）` 提交，交付内容：
   - 新 `knowledge-web/src/test/java/.../tenant/MilvusTenantIsolationTest.java`（Testcontainers Milvus standalone 或 disabledWithoutDocker），断言：A 建 100 向量 + B 检索 0 命中；单租户召回非空；metadata.tenant_id 谓词表达式在 search 请求 filter 中可见。
   - `@Tag("tenant")`，与 P1-T10 同标签。
   - C-1 CI gate 独立 job（`-Dgroups=tenant -DargLine="-Dapi.version=1.44"`）与 P2-T5 同期合入。
   - **不做**：partition-key 万级时延阈值（转 Phase 3 会审）。

---

## 8. 未纳入本清单的相关事项（透明声明）

以下不属于 Phase 2 开工门禁，但常在讨论中被混淆，此处显式排除避免范围漂移：

| 事项 | 归属 | 备注 |
|---|---|---|
| U3 数据驻留 `data_region` 物理分区 | Phase 5 布局 | 提案 §10 O2、评审包 §3 U3；不影响 Phase 2 向量隔离本体 |
| U4 SSO / 企业 IdP（OIDC/SAML） | Phase 3 前 | 评审包 §3 U4；与向量层无关 |
| U5 前端跨域 / Cookie / 子域解析 | X-T5（贯穿） | 评审包 §3 U5；已由 P1-T3 三源解析支撑，Phase 2 无前端改动 |
| U6 唯一索引切换停机窗口 | X-T4 · S2 灰度 | 评审包 §3 U6；Phase 1 尾巴（P1-T5 已完成 V25/V26），Phase 2 不再涉及 DB 迁移 |
| O1 多副本 compose 去固定化 | Phase 5 P5-T4 | 本清单 C-3 只约束"Phase 2 保持单副本前提" |
| O4 MinIO 应用侧 SDK 接入 | Phase 5 P5-T1 | 与向量层无耦合 |

---

> 记录人：QoderWork · 性质：**Phase 2 开工前的门禁清单，与评审包 §6 签署页互补**——评审包对整个 SaaS 提案做三方决策，本清单专门收口 Phase 2 未闭环项。
> 归档时机：三类角色齐签 + S/T 结论回写执行计划后，本文件状态从"待签署"→"已归档（生效基线）"，保留于 `docs/` 供 Phase 3 开工清单参照格式。
