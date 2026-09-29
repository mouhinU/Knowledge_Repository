# Spike：Milvus partition-key 技术验证（对应执行计划 [P2-T0](saas-multi-tenant-execution-plan.md)）

> 状态：**待执行** ｜ 时间盒：3 个工作日 ｜ 风险登记：提案 [§10 R1](saas-multi-tenant-proposal.md)（高）
> 产出：本文件 §5 脚本跑完后，在 §6 决策表上打钩并把结论写回提案 R1 与执行计划 P2-T0/T1/T4。

---

## 1. 背景与决策问题

提案 [§4 向量隔离](saas-multi-tenant-proposal.md) 要求：POOL 租户走"共享 collection + `tenant_id` partition-key 下推"，SILO 租户走"每租户独立 collection `kc_{tenantKey}`"。这是 Phase 2（P2-T1 `EmbeddingStoreProvider` 重构）的地基，**开工前必须用数据回答两个问题**：

- **Q1**：Milvus 2.5.4 是否支持 partition-key isolation 与等值过滤下推？在 bge-m3 1024 维（`application.yml` 已配 `knowledge.milvus.dimension: 1024`，模型 `bge-m3` via Ollama）下，**召回质量与延迟**相对现状如何？
- **Q2**：POOL 走"共享 collection + partition-key"（方案 B）还是"每租户独立 collection"（方案 C）？以本项目的租户规模（预估 10–200 租户）与单人运维能力为准绳。

**现状痛点**（spike 的对照组即当前生产行为）：

| 问题 | 代码锚点 | 表现 |
| --- | --- | --- |
| 进程级单例 store，collection 名静态注入 | [MilvusConfig.java](../../knowledge-infrastructure/src/main/java/com/mouhin/knowledge/repository/infrastructure/config/MilvusConfig.java)（`@Lazy @Bean EmbeddingStore`，`collection-name` 走 `@Value`） | 无法按租户路由，P2-T1 重构的前置阻塞 |
| ACL 过滤表达式被直接丢弃 | [MilvusVectorStoreService.buildFilter](../../knowledge-infrastructure/src/main/java/com/mouhin/knowledge/repository/infrastructure/milvus/MilvusVectorStoreService.java)（193–200 行，非 category 时 `return null`） | 仅 category 下推；租户/ACL 全靠应用层后过滤 |
| over-fetch 后过滤 | [AuthorizedSearchSupport](../../knowledge-application/src/main/java/com/mouhin/knowledge/repository/application/support/AuthorizedSearchSupport.java)（`×3+10`，硬上限 500） | 数据量越大、受限文档占比越高，top-N 名额浪费越严重，P95 随租户数据量放大 |
| 复杂 ACL 无法表达为 LangChain4j Filter | [PermissionDomainService.buildFilterExpression](../../knowledge-domain/src/main/java/com/mouhin/knowledge/repository/domain/service/PermissionDomainService.java)（生成 `or/and/like` 原生串） | 本 spike 只验证 **`tenant_id` 等值下推**（单条件 `IsEqualTo`），ACL 下推不在范围内 |

## 2. 技术环境准备

- **Milvus 2.5.4 standalone**：项目 [docker-compose.infra.yml](../../docker-compose.infra.yml) 已编排 etcd + minio + milvus（`milvusdb/milvus:v2.5.4`），spike 直接复用，不改编排。
- **LangChain4j 1.0.1**（[docs/tech-stack.md](tech-stack.md) 基线），milvus-sdk-java 以其传递依赖版本为准，**spike 期间禁止升级**（升级触发红线 #11）。
- **bge-m3 embedding**：Ollama 本地已跑通，1024 维。
- **参考文件（执行 Day0 预研时先读，列出可用 API）**：
  - [MilvusConfig.java](../../knowledge-infrastructure/src/main/java/com/mouhin/knowledge/repository/infrastructure/config/MilvusConfig.java) — store 构建入口、`dimension/database-name` 参数面
  - [MilvusVectorStoreService.java](../../knowledge-infrastructure/src/main/java/com/mouhin/knowledge/repository/infrastructure/milvus/MilvusVectorStoreService.java) — `search()` 的 `EmbeddingSearchRequest.filter` 构造、`storeChunks()` metadata schema
  - [DocumentIngestionSupport.java](../../knowledge-application/src/main/java/com/mouhin/knowledge/repository/application/executor/docingestion/DocumentIngestionSupport.java)（128 行 `vectorStoreService.storeChunks`）— 写入路径，未来 P2-T2 填 `tenant_id` 的落点
  - [AuthorizedSearchSupport.java](../../knowledge-application/src/main/java/com/mouhin/knowledge/repository/application/support/AuthorizedSearchSupport.java) — over-fetch 与后置过滤基线语义
  - [PermissionDomainService.java](../../knowledge-domain/src/main/java/com/mouhin/knowledge/repository/domain/service/PermissionDomainService.java) — ACL 表达式形态（确认 LangChain4j Filter 表达不了的边界）

## 3. 实验设计（4 组对照）

**数据规模**：
- 主组：5 租户 × 1000 chunk/租户 ≈ 5000 向量（模拟小型多租户），查询集 100 条（每租户 20 条，其中 30% 设计为"答案在别的租户也有近邻"的干扰项）。
- 极端组：20 租户 × 5000 chunk ≈ 10 万向量，验证规模放大后的趋势。
- Ground truth：每查询标注目标 `chunk_id` 集合（构造数据时已知归属），四方案共用同一份。
- 向量来源：优先直接调本地 bge-m3 对合成中文文本做真 embedding（保证分布真实）；预算不够时退化为随机向量 + 少量扰动（结论仅用于延迟，召回数据作废并标注）。

| 方案 | 拓扑 | 检索过滤 |
| --- | --- | --- |
| **A（基线）** | 单 collection，无 partition-key | 纯应用层后过滤（现生产行为：over-fetch ×3+10 后按 `document_key→tenant` 映射剔除） |
| **B（partition-key）** | 单 collection，`tenant_id`（Int64/VarChar）设 partition-key | `IsEqualTo("tenant_id", ...)` 下推 + 原 ACL 后过滤 |
| **C（多 collection）** | 每租户独立 collection `kc_{tenantKey}` | 直接切 store，无 tenant 过滤 |
| **D（混合）** | POOL 用 B，SILO 用 C | 按租户 placement 路由 |

**指标**（每项都要落 CSV，见 §5）：
- 召回：P@10 / P@50（同一 ground truth）
- 延迟：1000 次连续查询的 P50 / P95 / P99（单并发 + 10 并发各一轮）
- 冷启动：进程重启后首次查询延迟（含 store 建连 + collection load）
- Milvus 侧：`docker stats` 内存峰值；stand-alone 下 stress test（locust 或 wrk 打 HTTP 网关 `/search`）找 QPS 拐点
- 元数据开销：方案 C 建 50 / 100 / 200 个 collection 时，`list_collections` / load / 查询路由耗时；etcd 体积变化

## 4. LangChain4j 1.0.1 API 兼容性验证（Day1 半天预研，先于跑数）

逐项验证并记录"支持 / 不支持 / workaround"：

1. **`MilvusEmbeddingStore` 是否原生暴露 partition-key 定义入口**：读 1.0.1 源码 `MilvusEmbeddingStore.builder()` 参数面与 `indexParams`/`metadataFields` 能力。若无 `partitionKey(isPartitionKey=true)` 入口 → 用 milvus-sdk-java（`io.milvus:v2-sdk`，LangChain4j 传递依赖）直连建 collection 声明 partition-key，store 以 `MilvusClient` 构造参数复用该 collection。
2. **`EmbeddingSearchRequest.filter` 表达式构造**：确认 `IsEqualTo("tenant_id", v)` 能编译为 Milvus 标量过滤并**真正下推**（观察 Milvus proxy 日志或 `search` 请求 body），验证 `and` 组合（tenant + category）即 `Filter.and(a, b)`。`GroupFilter` 不在本项目范围（无分组检索需求）。
3. **metadata schema 迁移**：验证对**已存在** collection 能否 `addField(tenant_id)`（Milvus 2.5.4 不支持给已有 collection 加 partition-key 字段 → 预期结论是**必须重建/新集**，直接影响 P2-T2 迁移脚本设计：POOL 走"新建带 partition-key 的 collection + 双写/回灌"，而非 ALTER）。
4. **退化路径**：若第 1 或第 2 项阻塞且 milvus-sdk 直连 workaround 成本 > 半天 → spike 即判定"SDK 支持差"，走 §6 规则选 C。

## 5. 执行步骤与脚本清单

目录（**由执行人手工 `mkdir`，本文档不创建**）：

```
spikes/milvus-partition-key/
├── README.md                    # 复现步骤：先起 docker compose infra，再按序跑脚本
├── spike-app/                   # 独立最小 Spring Boot 应用（不依赖 knowledge-* 模块，仅引 LangChain4j + milvus-sdk）
│   └── src/main/java/.../       #   ScenarioA/B/C runner + 指标采集
├── data/
│   ├── gen_corpus.py            # 合成 5×1000 / 20×5000 中文 chunk + ground_truth.json
│   └── gen_queries.py           # 100 条查询 + 干扰项标注
├── run/
│   ├── bench.py                 # 调 spike-app REST，串行 1000 次 + 10 并发，输出 latency_{A,B,C,D}.csv
│   ├── recall.py                # 用 ground_truth 计算 P@10/P@50 → recall_{A,B,C,D}.csv
│   └── meta_cost.py             # 方案 C：50/100/200 collection 的 list/load/search 开销 → meta_cost.csv
└── report/
    └── REPORT_TEMPLATE.md       # 指标表 + §6 决策勾选 + 原始 CSV 附件路径
```

CSV 统一列：`scenario, tenant_count, chunks_per_tenant, concurrency, metric, value, note`。

执行顺序：Day1 上午预研（§4）→ Day1 下午环境+数据 → Day2 A/B/C 各跑（含极端组）→ Day3 D 组合验证（若 B/C 至少一成立）+ 汇总决策。

## 6. 验收与决策规则（看完即可判）

以**主组（5×1000）**为准，极端组只看趋势；P@10 差异在 ±1% 内视为"持平"：

| # | 条件 | 决策 |
| --- | --- | --- |
| 1 | B 相对 A：P95 延迟下降 **≥30%** 且 召回持平 且 §4 预研确认 SDK 支持（含 milvus-sdk workaround） | **选 B**：POOL 用 partition-key |
| 2 | B 相对 A 延迟无显著改善（<10%）**或** §4 出现阻塞级不支持 | **选 C**：多 collection；同时必须评估 200+ collection 的运维成本（meta_cost.csv：load 总时长、etcd 体积、`list` 延迟），超阈值则收紧租户上限 |
| 3 | 条件 1 或 2 任一成立（即 B、C 至少一个可行） | **采纳 D**（提案原意）：POOL 取胜者，SILO 恒用 C |
| 4 | B、C 均不达标（极端组下 P99 恶化 >3 倍等） | 回到提案重议：考虑独立 database 或应用层分区，Phase 2 暂停 |

补充判定：
- 冷启动：方案 C 单租户首查延迟若 > 3s（collection lazy load），需在 P2-T1 里做 load 预热，记录为已知代价而非否决项。
- QPS：standalone 拐点数据仅作参考，集群行为差异见 §8。
- **结果写回**：更新提案 [§10 R1](saas-multi-tenant-proposal.md)（解除/维持高风险标记）与执行计划 P2-T0 验收列、P2-T1（store 路由策略）、P2-T4（SILO 命名确认）。

## 7. 时间盒（3 个工作日）

| 日程 | 内容 |
| --- | --- |
| Day1 | 上午：§4 LangChain4j 支持性预研（0.5 天硬停）；下午：环境 + 数据生成 |
| Day2 | 方案 A / B / C 各完整跑一轮（主组 + 极端组），1 天 |
| Day3 | 方案 D 组合验证 + 汇总报告 + 决策写回，1 天（含 0.5 天缓冲） |

**Day1 检查点**：若预研发现 partition-key 存在阻塞（§4 第 1/2 项均不支持且 workaround 不可行），立即砍掉方案 B，Day2 只深挖 C（重点转向 meta_cost 与 load 预热），Day3 照常出决策。

## 8. 风险与回退

- **LangChain4j 版本兼容**：若必须升级 LangChain4j 才能原生支持 partition-key → **触发红线 #11（框架版本变更）**，spike 不做升级，仅在报告中记录"目标版本 + 变更清单"，另立小提案走审批。
- **standalone ≠ 集群**：spike 只覆盖 Milvus 2.5.4 standalone；生产如为 3 节点集群，partition-key 的负载均衡行为、collection 数量上限、QPS 均有差异 → 决策选 B/D 后，需在类生产集群补一轮验证（不阻塞 Phase 2 开工，列为 P2-T5 后回归项）。
- **回退**：本 spike 全部在独立 `spikes/` 目录与独立 collection 上进行，不触碰生产 collection `knowledge_chunks`；随时可整体删除目录，无代码回滚成本。
