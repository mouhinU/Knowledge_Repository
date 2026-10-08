# P3-T0 · Milvus partition-key 万级规模 spike 报告

> 环境：Milvus **v2.5.4 standalone**（Docker `knowledge-milvus`，内存上限 ~3.82 GiB，`localhost:19530`，RESTful v2）
> 索引：三张对照集合统一 **IVF_FLAT `nlist=128` / COSINE / dim=1024**（唯一变量是"是否分区剪枝"）
> 向量：固定种子（20260930）1024 维 L2 归一随机向量 —— **本 spike 测检索基础设施时延与剪枝效率，不测召回质量**；召回正确性与跨租户 0 泄漏已由 [../milvus-partition-key/RESULTS.md](../milvus-partition-key/RESULTS.md)（P2-T0，真实 bge-m3）证明
> 复现：`python3 spike_scale.py [--rows N --tenants T --partitions P --queries Q --keep --use-ollama]`；全程 `spikescale_*` 临时集合，跑完自动 drop，**绝不触碰生产 `knowledge_chunks`**（已复核仅剩 `knowledge_chunks`）
> 数据文件：[results.json](results.json)（合并 3 组 + 1 次崩溃事件）· [results_10k_3tenant.json](results_10k_3tenant.json) · [results_10k_50tenant.json](results_10k_50tenant.json) · [results_20k_100tenant.json](results_20k_100tenant.json)

本 spike 直接回答 Phase 3 清单三项决策（S-3 / S-2 / T-2）的经验问题。**核心结论是负向且重要：在 POOL 代表性规模（10k–20k 行、≤100 租户、IVF_FLAT）上，partition-key 不但没有时延收益，反而比同谓词的普通标量/JSON 过滤慢 1.2–2.5 倍（10k/50t 档 ~1.3×、10k/3t 与 20k/100t 档 ~2.4–2.5×）；其唯一被证实的优势是物理隔离语义——而这一点普通标量过滤同样 0 泄漏地提供了。**

## 1. 三组配置的检索时延（P50/P95/P99，单位 ms，limit=10）

| 配置（总行数 / 租户 / 分区） | 集合 | P50 | P95 | P99 | 跨租户泄漏 |
|---|---|---:|---:|---:|---:|
| **10k / 3 / 64** | partition-key (`spikescale_pk`) | 13.65 | **20.71** | 27.73 | 0 |
| | 普通标量 (`spikescale_nopk`) | 5.78 | **8.34** | 9.95 | 0 |
| | LangChain4j JSON (`spikescale_json`) | 6.77 | **10.34** | 14.43 | 0 |
| **10k / 50 / 128** | partition-key | 6.94 | **13.40** | 15.54 | 0 |
| | 普通标量 | 5.97 | **10.09** | 18.80 | 0 |
| | LangChain4j JSON | 7.08 | **11.28** | 13.31 | 0 |
| **20k / 100 / 256** | partition-key | 7.30 | **20.90** | 27.20 | 0 |
| | 普通标量 | 5.70 | **8.60** | 10.60 | 0 |
| | LangChain4j JSON | 7.80 | **12.20** | 14.80 | 0 |

partition-key 相对普通标量的 P95 收益比（正=更快）：`10k/3t = -148%`、`10k/50t = -33%`、`20k/100t = -143%`。三组全部为负——**partition-key 在这些规模下没有任何一次跑赢对照**。

## 2. 为什么剪枝没带来收益（机理）

预期模型是"partition-key 把扫描集从 N 降到 N/租户数"。实测否定了它在此规模的价值，原因：

1. **IVF_FLAT 在小数据上本就近乎全内存暴力**：10k–20k × 1024 维向量整体驻留内存，`tenant_id==X` 普通标量谓词在引擎里已是极廉价的后过滤（P95 ~8–10ms）。扫描从来不是瓶颈，剪掉的那部分扫描省不出可测收益。
2. **partition-key 路径引入固定路由开销**：声明 `isPartitionKey=true` + 数十~数百个物理分区后，每次查询要先做分区裁剪与 segment 调度，多分区元数据管理与跨 segment 合并反而叠加了每次请求的常数开销。当"省下的扫描 < 新增的路由开销"时，净效应为负。
3. **收益符号随规模不稳**：从 10k/3t（-148%）到 10k/50t（-33%）劣势一度收窄，但到 20k/100t（-143%）又恶化——说明在本量级内没有稳定收敛到"转正"的趋势；要指望剪枝转正，需要数据量大到普通标量后过滤本身成为瓶颈（远高于本地可复现的规模）。

**结论**：partition-key 的性能论证在 POOL 现实规模**不成立**。它的正当理由应是"物理隔离 / 可运维性 / 未来超大规模剪枝期权"，而不是当前检索时延。

## 3. 对照 S-3（万级时延阈值 P95）

- 若 Phase 3 把 P95 门禁定在"partition-key ≤ 某绝对阈值"，实测 partition-key P95 落在 **13–21ms**（本机、Bounded 一致性、limit=10），而普通标量/JSON 路径落在 **8–12ms**。
- **给决策的建议**：P95 阈值应锚定在"LangChain4j JSON 谓词路径或普通标量"这一更快的实现上（例如 P95 ≤ 50ms 留有充分裕度），**不应**以"partition-key 更快"为设计前提。partition-key 若被选，其门禁应设为"不劣于对照一定裕度"或干脆不设性能收益断言，仅设隔离正确性断言（P2-T5 已有）。
- **诚实边界**：以上为单机 standalone、IVF_FLAT、随机向量、Bounded 一致性的相对趋势，绝对值不可外推到生产 cluster / HNSW / Strong 一致性。正式阈值须在预生产或 CI 复跑校准（联动清单 B-1）。

## 4. 对照 S-2（现网集合迁移策略）

复刻现网 `knowledge_chunks` 的 `metadata` 单 JSON 列模型（`spikescale_json`）实测：

- **插入吞吐**（10k 行一批）：JSON 列路径 ~460 rows/s、partition-key ~376–463 rows/s、普通标量 ~306–460 rows/s，三者同量级，无显著成本差。
- **建集合开销**：partition-key 随分区数上升（64→256 分区，create 从 185ms→363ms），普通标量/JSON 恒 ~56–90ms。
- 对迁移三条路径的含义：
  - **方案 a 影子双写**：partition-key 与 LangChain4j JSON 两集合可在同库共存、互不冲突（本 spike 三集合同库建/查/0 泄漏即为共存证据），双写在技术上可行；代价是写放大 + 两套一致性。
  - **方案 b 停机重灌**：万级重灌吞吐 ~400 rows/s 量级，10k ≈ 25s、20k ≈ 43s（含批次串行与本机 IO），可作停机窗口下界参考；真实需另计索引 build / compaction。
  - **方案 c 延后 partition-key（本次证据最支持）**：鉴于 §2 实测 partition-key 在 POOL 现实规模**无性能收益**，且 LangChain4j JSON 谓词路径隔离已 0 泄漏（P2-T0/P2-T5 已证），"为性能而上 partition-key"的动机被削弱。延后 partition-key、维持 JSON 谓词路径是可被本 spike 数据支撑的低成本选项。

## 5. 对照 T-2（partition-key schema 与 io.milvus SDK 兼容）

- 用 REST v2（`io.milvus` Java SDK 是同 REST/gRPC 层的薄封装，schema 表达等价）成功建立目标态 schema：`id Int64 PK / vector FloatVector(1024) / tenant_id Int64 isPartitionKey=true / category VarChar / numPartitions 64–256`，并带 `enableDynamic=true` 以容纳 LangChain4j 风格 metadata 兼容层。**建集合、load、按 `tenant_id==X` 剪枝查询全部成功，0 泄漏** → 目标态 schema 在 Milvus 2.5.4 上可建可查，SDK 兼容层无障碍。
- 注意：`isPartitionKey` 字段与 `numPartitions` 需通过 schema 段传入；本 spike 用 enableDynamic 复刻 LangChain4j 的"额外键塞动态列"能力，说明自建 partition-key 集合仍可保留 metadata 兼容字段（对齐 R1 决策"partition-key + metadata JSON 双要素"）。

## 6. 崩溃事件（B-1 运维承接实证）

`50k / 100 租户 / 256 分区` 在插入阶段触发 **Broken pipe**，Docker 容器 `knowledge-milvus`（内存上限 ~3.82 GiB）**OOM 崩溃并约 20s 后自动重启**；生产 `knowledge_chunks` 未受影响（重启后仍在，已复核清理临时集合）。

含义：**运维承接 partition-key 时，单机/小内存 Milvus 在数十万 × 1024 维 ingest 下会顶不住**，Phase 3 提案 §11 B-1 要求的"内存/磁盘预留 + 备份"必须据此定量——本地 3.8GB 连 50k 都崩，生产按目标租户/文档量需显著上调并考虑 cluster 或 HNSW+磁盘向量存储。

## 7. 决策建议汇总（回写清单 S-2/S-3/T-2）

1. **S-3**：不要把 P95 阈值的设计前提建立在"partition-key 更快"上——实测更慢。阈值应锚定 JSON 谓词/普通标量路径，partition-key 仅作隔离能力、性能不设正收益断言。
2. **S-2**：三条迁移路径技术均无障碍；**方案 c（延后 partition-key，维持 LangChain4j JSON 谓词）被本 spike 的性能负结果最有力支撑**——现网隔离已 0 泄漏、更快、零 schema 迁移成本。若坚持 partition-key，则选方案 a 影子双写并预留写放大。
3. **T-2**：io.milvus SDK 目标态 schema（partition-key + metadata 兼容层）在 2.5.4 可建可查，兼容无障碍；此项从"待验证"转"已验证可行"。
4. **R1 复核触发点**：R1（partition-key 为 POOL 目标态）当初部分基于预期剪枝收益；本 spike 证实在 POOL 现实规模该收益不存在。**建议在 Phase 3 会审把"是否仍需 partition-key 作为 POOL 目标态"重新提为决策项**（不是由本 spike 单方面改架构，而是把负向实证摆到 S-2/T-2 门禁前供会审定夺）。

## 8. 遗留 / 未覆盖

- 未在 **HNSW / 生产 cluster / Strong 一致性** 下复测——剪枝收益可能随绝对规模（远大于本地可复现）与索引类型改变而转正，需预生产环境补测。
- 未测 `numPartitions` 上限对建集合耗时与 compaction 的长期影响（仅见 create 随分区数线性微增）。
- 混合 POOL+SILO 同库路由正确性、逐库 Flyway（属 P3-T1/T6，非本 spike 范围）。
- 查询用随机向量，绝对时延仅表趋势；如需贴近现网语义时延，可 `--use-ollama` 复跑（慢）。
