# AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· §1 分册地图 · docs/saas-multi-tenant-proposal.md §10 R1 · docs/saas-multi-tenant-execution-plan.md §5 P3-T5/P3-T6 · docs/saas-phase3-go-checklist.md §2 B-1/B-2/B-3 · §3 S-1/S-2/S-3 · §4 T-1/T-2/T-3/T-5/T-6 · spikes/milvus-partition-key-scale/RESULTS.md

# SaaS Phase 3 剩余项技术方案 · Milvus cluster 与备份决策包（B-2 收口）

> 版本：v0.1（2026-10-10 起草 · **决策辅助文档，不代表 B-2 已签署或 Phase 3 剩余项已授权落地**）
> 作者：QoderWork
> 状态：**待运维/架构会审**。本文把 Phase 3 门禁清单里唯一悬而未决的硬前置——运维 B-2「**Milvus cluster 扩容 + 备份方案**」——落成可直接勾选定夺的技术方案包，用于解锁 `P3-T5 partition-key 分支续交` 与 `P3-T6 SILO 逐库 runner`。
> 权威优先级：本包为**决策辅助**；勾选位以 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（v0.3）为准，架构决策以 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) 为准，排产以 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) 为准。任何与本包冲突处，以三份权威文档为准。

---

## 0. 本文要解决的问题（门控定位）

Phase 3 已由 2026-10-09 五方齐签升 v0.3 全量授权开工，`P3-T0 spike` / `P3-T1~T4` / `P3-T5 Archive&Delete 子切片`（`ed51f63`）/ `P3-T7 OIDC`（`93dcbb9` + `9aa358b`）均已交付。但 Phase 3 仍有**两块未交付**，且都卡在同一处运维硬前置：

| 未交付项 | 内容 | 门控 |
|---|---|---|
| P3-T5 续交（partition-key 分支） | partition-key 切换（S-2=c）+ `MilvusSdkAdapter` 骨架（T-2=B）+ `knowledge_chunks_pk` 建集合（POOL 目标态）+ T-1 多 store 缓存 | **B-2：cluster 扩容 + 备份方案**（起 PR 前须落定） |
| P3-T6 SILO Flyway 逐库 runner | `TenantMigrationsRunner` + `SiloCollectionAdmin`（T-3=A collection-only）+ `/api/platform/schema-health` | B-2 + 依赖 P3-T5 延后的 Milvus DDL；现网 POOL 态无 SILO 集合可枚举 |

运维在 B-2 已签署"Phase 3 前段维持 standalone；**cluster 扩容 + 备份方案列为 P3-T5 起前硬前置**，届时按真实规模复核内存/磁盘（P3-T0 spike 已证 50k/100t 触发 standalone ~3.82 GiB OOM）"。本文即"届时复核"的具体料：给出 cluster 拓扑选型、资源预留定量、备份/恢复策略、迁移窗口与回退点，供运维与架构一次性定夺。

**一句话**：B-2 不是"要不要做 partition-key"的决策（S-2=c 已定 partition-key 延后并入 P3-T5），而是"P3-T5 起前用什么 Milvus 形态承载、怎么备份、切换停机多久、出事怎么回退"。本文回答后三个。

---

## 1. 输入实证（来自 P3-T0 spike，不可回避的前提）

引用 [../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)：

1. **partition-key 在 POOL 现实规模无剪枝性能收益，反而更慢**：10k/3t、10k/50t、20k/100t 三档下 partition-key P95 = 20.7 / 13.4 / 20.9 ms，均慢于普通标量（8.3 / 10.1 / 8.6 ms）与 LangChain4j JSON 谓词（10.3 / 11.3 / 12.2 ms），收益比 -148% / -33% / -143%，无一次跑赢对照。机理：IVF_FLAT 在 10k–20k × 1024 维近全内存暴力，扫描非瓶颈，partition-key 的分区路由/segment 调度常数开销反成净负担。
2. **隔离正确性三档均 0 泄漏**：普通标量/JSON 谓词已能 0 泄漏地提供 partition-key 的物理隔离语义（叠加 P2-T5 `@Tag("tenant")` 集成断言）。
3. **SDK 兼容已验证（T-2）**：`io.milvus` SDK（REST v2 等价层）可成功建目标态 schema `id Int64 PK / vector FloatVector(1024) / tenant_id Int64 isPartitionKey=true / category VarChar / numPartitions 64–256` + `enableDynamic=true`，按 `tenant_id==X` 剪枝查询成功、与现网 LangChain4j JSON 集合**同库共存、0 冲突**（= 影子双写在技术上可行）。
4. **standalone 内存天花板（B-2 实证核心）**：`50k/100t/256分区` 插入阶段触发容器 OOM 崩溃（内存上限 ~3.82 GiB），约 20s 后自动重启，生产 `knowledge_chunks` 未受影响。**这是 cluster 预留的定量起点。**
5. **吞吐（重灌预算参考）**：本机 ingest ~400 rows/s，10k ≈ 25s、20k ≈ 43s（未计索引 build/compaction/embedding 计算）。

**对本文的约束**：cluster 选型不能以"partition-key 更快"为卖点；cluster 的正当理由只有**容量（撑住 N×SILO 集合数 + 超大规模 ingest 不 OOM）**、**可运维性（副本/滚动/隔离故障域）**、**未来规模期权**。备份策略应利用"向量是 `kb_document` + embedding 的可重建派生数据"这一事实（见 §5）。

---

## 2. R1 复核决议建议（partition-key 是否仍为 POOL 目标态）

提案 §10 R1（partition-key 为 POOL 目标态）当初部分基于预期剪枝收益；§1 已证该收益在 POOL 现实规模不存在。Phase 3 清单 §7 第 14 条已把 R1 定性改为"**隔离/运维目标态**（性能收益未经证实）"，S-2=c 已把 partition-key 切换**延后并入 P3-T5**，现网维持 LangChain4j JSON/标量谓词路径。

本包**不建议单方面推翻 R1**，只把会审需定夺的一条摆明：

> **待会审勾选**：P3-T5 起时，`knowledge_chunks_pk` partition-key 集合是作为（甲）**POOL 目标态正式落地**（承载物理隔离语义 + 未来超大规模剪枝期权，接受 §1 的当前性能负结果），还是（乙）**维持 S-2=c 的"延后到真正需要时才切"**——即 P3-T5 只交付 `MilvusSdkAdapter` 骨架 + `SiloCollectionAdmin` + provider 多 store（T-1），`knowledge_chunks_pk` 建集合与切换**再推迟到有明确容量/隔离驱动的实际租户上 SILO 时**。
>
> 本包推荐**（乙）与 cluster 决策解耦**：先只做"支撑 SILO 与未来切换所必需的最小 SDK 能力"，不立即为 POOL 建 partition-key 集合。理由——POOL 单集合 + JSON 谓词已 0 泄漏且更快，为它单独引入 partition-key 集合只增加写放大/双一致性成本，收益仅在超大规模。cluster 扩容的真正驱动应是 **SILO 集合数增长 + 总向量规模**，而非 partition-key 本身。

此项若采纳（乙），P3-T5 的 cluster 前置从"partition-key 切换前"松绑为"首批 SILO 租户上线前"，可显著推迟 cluster 成本。

---

## 3. Milvus 拓扑选型（B-2 决策项 1）

| 选项 | 描述 | 何时够 | 何时不够 | 运维成本 | 对 P3-T5/T6 的影响 |
|---|---|---|---|---|---|
| **C0 维持 standalone + 上调内存** | 单机 Milvus 2.5.4，容器内存上限从 ~3.82 GiB 提到与目标规模匹配（§4 定量） | 总向量 ≤ 数十万 × 1024 维、SILO 租户少、可接受重启窗口 | 高可用诉求、单实例 segment 内存溢出、滚动升级需停机 | 低 | P3-T5 partition-key 若走（乙）可不起 cluster；P3-T6 无独立 database 只需 collection-only（T-3=A） |
| **C1 Milvus cluster（docker-compose）** | `mixCoordinator / node / queryNode / dataNode / indexNode` + 已有 etcd + MinIO 复用为对象存储 | 需要组件级水平扩展 + 故障域隔离 | 单机 docker-compose 下"高可用"名不副实（宿主机单点） | 中 | `knowledge-app` compose 需新增若干 service；partition-key/SILO 集合数上限受 queryNode 内存 |
| **C2 Milvus cluster + 对象存储后端**（P5-T1 协同） | cluster 元数据/segment 落 MinIO/S3，本地只留热缓存 | 数据量大、需快照式低成本备份 | 引入对象存储耦合，跨机房诉求（提案 data_region U3） | 中高 | 与 P5-T1 MinIO 复用同一后端，天然对齐备份 |
| **C3 Milvus Operator（K8s）** | 生产级 HA + 滚动升级 + CRD 管理 | 已上 K8s、需真正多副本 HA | 项目当前是 docker-compose 本地栈，上 K8s 是重大基础设施变更 | 高 | 超出 Phase 3 范围，属独立里程碑 |

> **待运维勾选（B-2 决策项 1）**：☐ C0（standalone + 扩容，配 §2（乙）解耦）☐ C1 ☐ C2 ☐ C3 ☐ 需补料 ☐ 否决
>
> **本包推荐**：**C0 起步 + 预留 C2 演进**。依据：①§1 已证 partition-key 无性能收益，cluster 不为性能；②项目现为 docker-compose 单机栈，C1 在本机不提供真实 HA、只增复杂度；③真正需要 cluster 的信号是"SILO 集合数 + 总向量规模"随客户上线增长，届时以 C2（复用 MinIO）演进最省。先以 C0 + §4 定量内存把 standalone 撑到位，把 cluster 触发阈值写成明确判据（§6），避免过早 cluster 化。

---

## 4. 资源预留定量（B-2 决策项 2 · 承接 B-1 内存/磁盘）

以 P3-T0 spike "50k × 1024 维在 ~3.82 GiB OOM" 为标定锚点，给内存上界估算模型（IVF_FLAT 近全驻内存）：

- **单向量裸数据**：1024 × float32 ≈ 4 KB；IVF_FLAT 索引 ≈ 1.0–1.3×（近乎原样 + 少量质心）→ 保守按 **5 KB/向量** 计段内存。
- **标量字段 + metadata JSON（enableDynamic）**：每行约再 +0.5–1 KB。
- **per-segment / per-collection 固定开销**：每个 SILO 独立集合有最小 segment/元数据驻留成本，N×SILO 会放大基线内存——这是 cluster 的真实驱动，不是 partition-key。
- **估算表（IVF_FLAT 全驻内存假设，含 ~1.5× 运行时余量）**：

| 总向量规模 | 裸索引(≈5KB/条) | 含余量目标内存 | standalone 可行性（对照 3.82 GiB OOM 锚点） |
|---|---|---|---|
| 50k（50k/100t spike 崩溃点） | ~250 MB 级 | ≥ 2 GiB 常驻 + 段开销 | **已证崩**（spike 实证，须扩容） |
| 10 万 | ~0.5 GB | 预留 ≥ 6–8 GB 堆外/系统 | standalone 边缘，需上调内存 |
| 50 万 | ~2.5 GB | 预留 ≥ 16 GB | 建议 C1/C2 |
| 100 万 | ~5 GB | 预留 ≥ 24–32 GB | C1/C2 + queryNode 扩展 |
| 500 万+ | ~25 GB | HNSW + 磁盘向量存储或分布式 | C2/C3，考虑 HNSW（spike 未测，见 §8） |

> **待运维勾选（B-2 决策项 2）**：☐ 采纳 C0 并把 `knowledge-milvus` 容器内存上限设为 `____` GiB（按目标规模 ×5KB×1.5 填）☐ 直接进 C1/C2 ☐ 需补料
>
> **磁盘**：segment 持久化 + compaction 双写缓冲 + 若走 §5 快照备份的增量空间。建议 `Milvus 数据目录 ≥ 峰值索引 ×3`（留 compaction/insert 余量），snapshot 目录另计。

**本包不替运维拍死数字**——绝对内存须按真实目标租户/文档量（商务侧 O1 计费节奏未定）复核；上表是把 spike 的 3.82 GiB 锚点外推成"运维填数即得"的模型。

---

## 5. 备份与恢复策略（B-2 决策项 3 · 本文核心简化）

**关键事实**：Milvus 里的 chunk 向量是 `kb_document`（对象存储/磁盘原文）+ embedding 模型的**可完全重建派生数据**。真正的 source of truth 在 MySQL 元数据 + 文档原文，**不在** Milvus。这决定了备份哲学：

| 恢复路径 | 机制 | RPO | RTO | 成本 | 适用 |
|---|---|---|---|---|---|
| **R-A 重建优先（推荐主路径）** | Milvus 集合丢失 → 从 `kb_document` 遍历重新 embedding → 回灌集合。P3-T5 已交付的 `TenantDeletionReconciler`（步骤级幂等续跑）+ 现成 ingest 路径可复用 | 0（原文/元数据无损即无数据损失） | 高（受全量重灌吞吐 ~400 rows/s ×规模，§1） | 低（无需额外备份系统） | POOL/SILO 集合整体或单租户丢失；embedding 模型确定（固定模型版本） |
| **R-B Milvus 官方 backup** | `milvus-sdk-java` backup 工具 / `mc mirror` MinIO bucket 快照 + etcd 元数据 dump | 近实时增量 | 中（恢复无需重算 embedding） | 中（需维护快照调度 + 存储） | 需缩短 RTO、避免大规模重算 embedding 的 GPU/额度成本 |
| **R-C 基础设施快照** | Docker volume / 宿主 LVM snapshot（standalone）；C2 时即 MinIO bucket 版本化 + etcd snapshot | 快照周期 | 中 | 低–中 | C0 standalone 下的粗粒度兜底 |

> **待运维勾选（B-2 决策项 3）**：☐ 主用 R-A（重建优先，接受 RTO 换取零额外备份系统）☐ R-A + R-B 混合（对高价值/大租户走 R-B 缩短 RTO）☐ R-C 作 coarse 兜底（C0 场景）☐ 需补料
>
> **本包推荐**：**R-A 为主 + R-C 为辅**。理由：①向量可确定性重建是架构级优势，不该被"备份向量库"的复杂度稀释；②R-B 的 embedding 重算成本只有在租户极多/模型调用额度贵时才显著，届时再引入；③必须显式记录 **embedding 模型版本 + 维度(1024) + 分块策略** 三者作为"重建可复现"的前提（写入 `kb_document` 已有字段，确认 SILO 逐库也覆盖）。

**R-A 可复现性前提（务必确认，否则重建≠原样）**：

1. embedding 模型固定（bge-m3 1024 维）且版本锁定；换模型=全量重灌事件。
2. 分块策略参数（`chunking_strategy / chunk_max_size / chunk_overlap / respect_paragraph / respect_page`）已随 `kb_document` 落库——重建时读原参数复现。
3. `tenant_id` 映射稳定：`TenantIdResolver` key→Long 是缓存非主键，重建按当前 `sys_tenant` 重算即可，SILO 按 `kc_{tenantKey}` 命名确定性定位。

---

## 6. cluster 触发阈值（把 B-2"何时必须扩"写成判据）

避免过早 cluster 化，也避免撑爆了才被动扩。建议把下列写成 CI/巡检告警门（承接 B-1 "监控接入 + list_collections 巡检"）：

- 总向量驻留内存估算 > 容器上限 × 0.8，或单租户文档量触发 §4 表 C1 档；
- SILO 独立集合数 > 阈值（每 SILO 一份 segment 基线开销）；
- 单次 ingest/re-embed 窗口 > 业务可接受停机（§7）；
- 出现"需真正多副本 HA"的业务/合规要求（提案 §11 O2 数据驻留）。

任一触发 → 运维从 C0 演进 C1/C2；未触发 → 维持 C0（standalone + 内存上调）。

> **待运维勾选**：☐ 采纳上述阈值为 B-2 扩容触发判据（填具体数字）☐ 调整阈值 ☐ 需补料

---

## 7. P3-T5 partition-key 切换 runbook（依赖 §2/§3 定夺）

若 §2 采纳（甲）正式落地 `knowledge_chunks_pk`，迁移方式在 S-2 三选项（清单 §3 S-2）中选：

- **方案 a 影子双写**（spike §4 已证同库共存可行）：新建 `knowledge_chunks_pk`（io.milvus SDK，`tenant_id Int64 partitionKey=true`）→ 写双路（LangChain4j 旧集合 + 新集合）→ 灰度切读（开关 `saas.vector.partitionKey.enabled`）→ 旧集合保留 30 天后 drop。**代价**：写放大 + 两套一致性。**回退点**：切读开关回 false → 走旧集合；旧集合 30 天内不 drop 即可无损回退。
- **方案 b 停机重灌**：运维窗口批量重算 embedding 写新集合。窗口下界 = 规模 × ~400 rows/s（§1），需另计索引 build/compaction。**回退点**：保留旧集合镜像，失败即切回。
- **方案 c 延后**（清单已签）：partition-key 切换与 SILO 生命周期同一 PR 落，P3-T1~T4 不依赖建集合。

**T-2 落地（已签方案 B）**：只实现 `MilvusSdkAdapter` 骨架（io.milvus SDK 直连 `knowledge_chunks_pk` / `kc_{tenantKey}`），**LangChain4j `MilvusEmbeddingStore` 单例保留为回退 bean**，暂不抽 `VectorStorePort`（方案 A）。provider（P2-T1 `EmbeddingStoreProvider`）从 Phase 2 单 store 骨架升级。

**T-1 多 store 缓存（延后至本 PR 定，清单 §4 T-1）**：本包推荐 **方案 B（Caffeine LRU 上限 100 store + placement 变更订阅失效）**，与 T-3=A collection-only 的 SILO 租户上限对齐；**单副本前提**。若 P5-T4 引入多副本，则需补 Redis pub/sub 广播失效（对齐 P4-T2 `ConfigInvalidationBus` 机制）——该耦合点交 Phase 4/5 会审（清单 §5 C-2 时序）。

> **待运维+架构勾选（B-2 决策项 4 · 迁移窗口）**：☐ 方案 a（影子双写，写放大可接受）☐ 方案 b（停机重灌，填窗口时长 `____`）☐ 方案 c 维持（配合 §2（乙），partition-key 再延后）☐ 需补料

---

## 8. P3-T6 SILO Flyway 逐库 runner 方案（依赖 T-3=A）

- **T-3 已签方案 A**：SILO 只建独立 collection（`kc_{tenantKey}`），**不建独立 database**，全部共享 `default` database，靠 `SiloCollectionNaming`（P2-T4 已落静态工具）命名区分。这**大幅简化** P3-T6：无逐"库"数据源遍历，`TenantMigrationsRunner` 只需按 SILO 集合列表做 schema 对齐。
- **runner 形态**（执行计划 §5 P3-T6）：`@Scheduled` + 手工触发端点；错峰；**单集合失败隔离**；`/api/platform/schema-health` 体检端点比对各 SILO 集合 schema 版本 vs 主链。
- **失败隔离**：某 `kc_{tenantKey}` 对齐失败不影响其他集合，记 `PlatformAuditLog` 告警（承接 P3-T5 `DbPlatformAuditLog` 模式）。
- **现实约束**：现网 POOL 态下**无任何 SILO 集合可枚举**，runner 上线即空跑；真正生效要等首批 SILO 租户上线（§2/S-1 手工 placement 写入 `sys_tenant_placement.placement=SILO`）。故 P3-T6 可与 P3-T5 partition-key 分支**解耦**：先落 runner 骨架 + schema-health 端点（POC 用手工建 1 个 SILO 集合验证），不必等 cluster。

> **待架构勾选**：☐ 采纳"T-3=A 简化版 runner（按集合而非按库）+ 与 partition-key 解耦先落骨架"☐ 需 cluster/真实 SILO 就绪后再做 ☐ 需补料

---

## 9. ACL 与隔离边界（确认沿用已签结论，不扩范围）

- **T-5 已签方案 C**：复杂 ACL（`PUBLIC or (INTERNAL and dept in [...]) or (RESTRICTED and role like ...)`）保留应用层 `AuthorizedSearchSupport.filterAccessible` 后置；向量层只做 `partition-key + tenant_id` 前置下推。深度下推另立后续小任务。**本包不触碰**。
- 现网回退路径（LangChain4j JSON 谓词 `metadata["tenant_id"]==X`）在 partition-key 切换后**保留 30 天**为兼容回退（S-2=c / §7 方案 a）。

---

## 10. 回退计划（红线 #11 要求的回退点）

| 变更 | 回退机制 | 触发条件 |
|---|---|---|
| `saas.enabled` / `platform.enabled` / `platform.oidc.enabled` | 三闸门默认 false，关闭即与主干行为等价（Phase 2/3 全程一致） | 任何未预期跨租户行为 |
| partition-key 切读 | 开关 `saas.vector.partitionKey.enabled=false` → 回 LangChain4j JSON 谓词单例 bean | 切换后检索异常/时延劣化 |
| `knowledge_chunks_pk` 建集合 | 旧 `knowledge_chunks` 30 天内不 drop，可无损回退 | 新集合数据不一致 |
| SILO 逐库 runner | 单集合失败隔离 + 提供单库重跑工具；runner 停摆不影响 POOL | schema 对齐失败 |
| cluster 扩容（若走 C1/C2） | compose 保留单副本配置（对齐 P5-T4 回退）；数据面 R-A 重建兜底 | cluster 不稳定/成本失控 |
| Milvus 数据丢失 | R-A 从 `kb_document` 重建 embedding 回灌（§5） | 集合损坏/误删 |

---

## 11. 会审签署页（B-2 收口 · 待运维/架构填）

> 说明：本包把 B-2 拆成 4 个可决策子项，全部 ☑ 后方可解除 P3-T5/T6 门控。任一 ☐ 需补料/否决则 P3-T5 partition-key 分支续交保持阻塞；P3-T6 骨架（§8）可据 ☑ 与 partition-key 解耦先行。

| 子项 | 决策位 | 负责人 | 日期 |
|---|---|---|---|
| §3 cluster 拓扑（C0/C1/C2/C3） | ☐ | 运维/基础设施（无始） | ______ |
| §4 内存/磁盘预留定量 | ☐ | 运维/基础设施（无始） | ______ |
| §5 备份/恢复策略（R-A/R-B/R-C） | ☐ | 运维（无始）+ 架构（蒙斌） | ______ |
| §7 partition-key 迁移方式与窗口（a/b/c） | ☐ | 架构（蒙斌）+ 运维（无始） | ______ |
| §2 R1 复核（POOL 是否仍需 partition-key：甲/乙） | ☐ | 架构（蒙斌） | ______ |
| §8 P3-T6 与 partition-key 解耦先行 | ☐ | 架构（蒙斌） | ______ |
| 本包采纳后回写 Phase 3 清单 B-2 具体值 + 执行计划 §5 排产 | ☐ | 项目负责人（流星）协调 | ______ |

**签署后联动（零漂移维护，对齐清单 §7 第 14 条"每 Phase/每项签署后回读"约定）**：
① 本包结论回写 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) §2 B-2 决策模板位；② 若 §2 采纳（乙）触发 R1 定性微调，回写 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 R1；③ 若 §7 迁移方式定 a/b，联动 C-2 迁移演练（`scripts/migration-drills/`）排产；④ 更新 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §5 P3-T5/P3-T6 状态行。

---

## 12. 例外登记与合规声明（红线 #11 / §0 #1 / §5 例外流程）

- 本包为**决策辅助文档**，本身不改动任何数据出口代码、不建任何 Milvus 集合、不改三闸门默认值；不构成对 B-2 的自动通过。
- 若采纳 §2（乙）/§7（c）把 partition-key 进一步延后，属对 R1 目标态的**时间轴调整**（非架构回退，因隔离语义已由现网 JSON 谓词 0 泄漏保证），应在 PR/会审 `Exception-Note` 记录并登记 `docs/exceptions/`（如适用）。
- embedding 模型/分块参数/`tenant_id` 映射的可复现性（§5 R-A 前提）若未来变更，触发"全量重灌"运维事件，须重走本包 §4/§6 资源复核。

## 13. 遗留 / 本包未覆盖（诚实边界）

- **未在 HNSW / 生产 cluster / Strong 一致性下复测**：§1 的 partition-key 负收益是 IVF_FLAT + standalone + Bounded 结论；若走 HNSW 或更大规模，剪枝可能转正，届时 §2 R1 与 §4 内存模型需重估（呼应 spike RESULTS §8）。
- 绝对内存/P95 阈值不可由本机外推，须预生产/cluster 复跑校准回填（清单 S-3 已授权延后至 cluster 校准）。
- 跨机房 schema 一致性（提案 U3 / data_region 物理分区）属 Phase 5，不在本包。
- `numPartitions` 上限对建集合耗时/compaction 的长期影响仅见 spike 线性微增趋势，未深测。
