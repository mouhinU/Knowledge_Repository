<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §11 · docs/saas-multi-tenant-proposal-review-pack.md §6 · docs/saas-multi-tenant-execution-plan.md §4
-->

# SaaS 化改造 · Phase 2 开工确认清单（Phase 2 Go-Checklist）

> 用途：**Phase 2（向量隔离，P2-T1~T5）开工前的最后一道闭环清单**。把提案 §10 R1 决策、评审包 §2.3/§3/§6、执行计划 §4/§13 中所有仍"待补/未拍板/交叉未接"的条目落成一页可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策）· 三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md)（签署栏）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P2 任务表）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> Spike 报告：[../spikes/milvus-partition-key/RESULTS.md](../spikes/milvus-partition-key/RESULTS.md)（P2-T0 结论）
> 版本：v0.1（2026-09-30） · 作者：QoderWork · 状态：**待签署**

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑**。任一 B 类未过则 Phase 2 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

---

## 1. Go/No-Go 速览

| 类别 | 条目数 | 负责人 | 当前状态 | 时限建议 |
|---|---|---|---|---|
| B 硬阻塞 | 3 | 运维/基础设施 + 项目负责人 | 未闭环（评审包 §6 运维栏 ☑ 需补料） | **开工前 2 周** |
| S 软阻塞 | 3 | 产品/商务 + 架构 | 未拍板 | **开工前 1 周** |
| T 技术细节 | 3 | 架构 + 项目负责人 | spike 已给方向，落地形态待定 | **P2-T1 起草 PR 前** |
| C 交叉项 | 3 | 运维 + 安全 + 架构 | 部分未接线 | **与 P2-T5 同期** |
| **合计** | **12** | — | — | — |

**一句话结论**：技术侧 R1 已定（P2-T0 spike 0 leak 已证），Phase 2 唯一硬阻塞是**运维对 Milvus 环境/cluster 承接的书面确认**；SILO 准入阈值与现网集合迁移兼容策略是**开工前需拍板**的两个软阻塞；CI 门禁/Prometheus 基数/多副本 store 缓存是**执行中同步处理**的交叉项。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 2 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **B-1** | 运维书面确认现网 Milvus 2.5.4 环境可承接 P2-T1~T5 全部改动：a) 允许应用侧调用 `io.milvus` SDK 直连建集合（partition-key + SILO 独立集合）；b) 现有 `knowledge_chunks` 集合的读写账号具备 `list/drop/create` 权限；c) Milvus 备份/恢复流程覆盖 partition-key 集合（含 SILO 独立集合的数量增长） | 运维/基础设施 | 评审包 §2.3 O3、提案 §2.4/§10 R1、执行计划 §4 P2-T4 | ☐ 可承接 ☐ 需补料（列条目） ☐ 否决 | 开工前 2 周 |
| **B-2** | 运维书面确认 Milvus 单机 vs cluster 决策：现网单机版能否撑住 POOL + N×SILO 集合数上限；若否，cluster 模式接入时间窗与成本；partition-key 建集合的运维流程（DDL 审核、监控指标接入、慢查询看板）由谁负责 | 运维/基础设施 | 评审包 §3 U1、提案 §10 R1 决策段、执行计划 §4 Phase 2 退出条件 | ☐ 单机可撑 ☐ 需 cluster（附时间窗） ☐ 需补料 ☐ 否决 | 开工前 2 周 |
| **B-3** | 提案 §11 审批栏运维栏闭环：由 ☑ 需补料（U1 Milvus 环境、U3 集群承接）→ ☐ 签署；本清单 §2 B-1/B-2 通过后，同步更新 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 签署页运维栏，并在提案 §11 第三条 checkbox 打勾 | 项目负责人（协调） + 运维（签署） | 提案 §11、评审包 §6 签署页 | ☐ 已闭环 ☐ 未闭环 | B-1/B-2 通过后 3 天内 |

---

## 3. S 类 · 软阻塞（Soft-Blocking）

> 未过不阻断开工，但会显著影响 P2-T1 路由规则或 P2-T4 生命周期设计；开工前 1 周需给出结论或**明确授权延后**。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **S-1** | **SILO 准入门槛**（评审包 U2）：合同规模/文档数量/QPS 阈值。方案 A：会前给初值（例如"单租户 `kb_document > X` 或向量数 > Y 万 or QPS > Z 转 SILO"）；方案 B：**授权延后至 Phase 3**——Phase 2 只做 POOL，SILO 代码分支保留但运行时不放开（`sys_tenant_placement.placement` 恒 POOL），P3-T1 placement 决策器补上 | 产品/商务 + 架构 | 提案 §2.1/§10 R2、评审包 §3 U2、执行计划 §4 P2-T1/P2-T4 | ☐ 方案 A（附阈值） ☐ 方案 B（授权延后） ☐ 需补料 | 开工前 1 周 |
| **S-2** | **现网 `knowledge_chunks` 集合迁移兼容策略**（P2-T2）：spike 只走"新建集合"路径，未覆盖存量向量迁移。选项 a)**影子集合**——新建 `knowledge_chunks_pk` 带 partition-key，写入双路（旧 + 新）、灰度切读、旧集合保留 30 天后 drop；b)**原地加 JSON 字段**——保留 LangChain4j 4 列 schema，`metadata.tenant_id` 走 JSON 谓词（已证 0 leak、无分区剪枝），partition-key 目标态留待后续；c)**一次性重灌**——停机窗口批量 embedding 重算并写新集合（成本 = N × 单条 embed 时间，需 Milvus 侧预留） | 架构 + 运维 | 执行计划 §4 P2-T2、spike RESULTS §3、提案 §10 R1 决策段 | ☐ a ☐ b ☐ c ☐ 需补料 | 开工前 1 周 |
| **S-3** | **P2-T5 万级基准数据集与时延阈值**：spike 只在 10 行 toy 规模跑（median ~15ms、p95 ~26ms），无法定分位数阈值。需约定基准数据集（例如从真实 `knowledge_chunks` 采 1–5 万条 bge-m3 1024 维向量 + 三租户均分），并写明"partition-key 检索 P95 时延 ≤ X ms 且 ≤ 标量过滤基线 × Y%"作为门禁断言，避免 CI gate 无阈值可校 | 架构 + 测试 | 执行计划 §4 P2-T5、spike RESULTS §5 | ☐ 已定（附数据集来源与阈值） ☐ 需补料 | 开工前 1 周 |

---

## 4. T 类 · Phase 2 内部技术细节（Technical）

> 架构自决项，30 分钟拉齐即可，但需在 P2-T1 起草 PR 前给出书面结论避免中途返工。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **T-1** | **`EmbeddingStoreProvider` 缓存失效形态**（P2-T1）：本地 `ConcurrentMap<tenantId+placement, EmbeddingStore>` 的更新语义——placement 从 POOL 改 SILO 时（P3-T1 平台端 CRUD 才会改）如何热切换。方案：**Phase 2 先降级到"进程重启生效"**（provider 侧不订阅 placement 变更事件，写入路径每次从 `sys_tenant_placement` 读表 + 短 TTL 缓存 5min）；P5-T4 副本化时同步引入 Redis pub/sub 广播失效（对齐 P4-T2 机制） | 架构 | 执行计划 §4 P2-T1、评审包 §2.3 O2 | ☐ 已定 ☐ 需补料 | P2-T1 PR 起草前 |
| **T-2** | **P2-T3 filter 下推边界与 G3 关系**：spike 顺带修正 `MilvusVectorStoreService.buildFilter` 陈旧注释（`MilvusMetadataFilterMapper` 实支持 And/Or/Not/In/ContainsString），意味着复杂 ACL（"PUBLIC or (INTERNAL and dept=X) or (RESTRICTED and role like Y)"）理论上可下推到 Milvus。Phase 2 决策：**P2-T3 只做 `tenant_id` 前置过滤下推**（`IsEqualTo("tenant_id", ctx.getTenantId())`），复杂 ACL 仍留应用层后置过滤（保留 `AuthorizedSearchSupport.filterAccessible` 双保险），ACL 下推另立后续小任务 | 架构 | 执行计划 §4 P2-T3、附录 A G3、spike RESULTS §3 末段 | ☐ 已定 ☐ 需补料 | P2-T3 起前 |
| **T-3** | **SILO 独立集合命名规范**（P2-T4）：`kc_{tenantKey}` 中 `tenantKey` 字符集约束（Milvus collection name 规则：字母/下划线开头 + 字母/数字/下划线，1–255 长度，禁保留字）。`tenantKey` 侧需**入库时强制正则** `^[a-z][a-z0-9_]{1,62}$`（小写蛇形），P3-T1 平台端 `create` 接口预校验；已入库的 `default`/`acme` 兼容。命名前缀是否要带 `knowledge_` 保留域？—— **建议**：`kc_{tenantKey}`（简洁、`kc_` 前缀已足以与 LangChain4j 默认集合 `knowledge_chunks` 区分） | 架构 + 数据库 | 执行计划 §4 P2-T4、P1-T1 `sys_tenant.tenant_key` 字段 | ☐ 已定 ☐ 需补料 | P2-T4 起前 |

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

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。三类角色齐签后 Phase 2 转"开工中"。

| 角色 | 关注条目 | 结论 | 签署人 | 日期 |
|---|---|---|---|---|
| 架构负责人 | B-2 · S-1 · S-2 · S-3 · T-1 · T-2 · T-3 · C-2 · C-3 | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 安全负责人 | S-2（迁移窗口暴露） · C-1（CI gate） · C-2（监控基数） | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 运维/基础设施负责人 | **B-1 · B-2 · B-3** · S-2 · S-3 · C-3 | ☐ 签署 ☐ 需补料 ☐ 否决 | ______ | ______ |
| 产品/商务（S-1 相关） | S-1 | ☐ 签署 ☐ 需补料 ☐ 否决 ☐ 不适用（授权延后） | ______ | ______ |
| 项目负责人（协调与最终 Go/No-Go） | 全部 | ☐ 同意开工 ☐ 暂缓（列原因） | ______ | ______ |

**一句话结论**（每方必填）：

- 架构：______
- 安全：______
- 运维：______
- 产品/商务：______
- 项目负责人：______

---

## 7. 通过后的下一步

1. **回读同步**：本清单三类角色齐签后，同步更新：
   - [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §11 第三条 checkbox 打勾，"口头同意先行"备注改为"Phase 2 开工前已补齐运维书面确认"。
   - [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 签署页运维栏由 ☑ 需补料 → ☑ 签署。
   - [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) 头部状态行由"运维 U1/U3 待补"改为"三方齐签，Phase 2 开工"，版本 v0.6 → v0.7。
   - 本清单从"待签署"→"已归档（生效基线）"。

2. **S/T 结论回写**：S-1/S-2/S-3/T-1/T-2/T-3 的具体决策文字，落到对应执行计划条目行的"交付物"或"验收"栏，保持"索引表↔正文↔交叉链接↔计数"四处一致零漂移。

3. **P2-T1 起 PR**：以 `chore(saas): P2-T1 EmbeddingStoreProvider 注册表` 提交，引用本清单编号（例："落地 S-2 方案 a、T-1 Phase 2 降级方案、C-2 无 tenant tag 自检"）。

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
