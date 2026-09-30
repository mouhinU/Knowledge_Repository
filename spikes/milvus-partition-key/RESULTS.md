# P2-T0 · Milvus partition-key 技术 spike 报告

> 环境：Milvus **v2.5.4**（`localhost:19530`，RESTful v2）· Embedding **bge-m3 1024 维**（Ollama
> `localhost:11434`，COSINE 度量，IVF_FLAT `nlist=128`）· 语料 10 句 + 3 查询（均真实中文句，经 bge-m3 向量化）
> 复现：`python3 spike.py`（纯标准库，跑完自动 `drop spike_*`，绝不触碰生产 `knowledge_chunks`）；`--keep` 保留集合。

本 spike 只回答三件事：**partition-key 是否可用且安全**、**bge-m3 1024 维召回是否正确**、
**LangChain4j `MilvusEmbeddingStore` 能否表达 partition-key 下推**。结论直接写入提案 §10 R1。

## 1. partition-key 可用性与跨租户 0 泄漏（判据 a）

以 `tenant_id`（Int64，`isPartitionKey=true`）建 `spike_pk` 集合，REST `describe` 回读确认
`fields` 中 `tenant_id → partitionKey=True`。插入 default/acme/third 三租户（4/3/3 行）后：

| 租户 | partition-key 过滤 `tenant_id==X` 返回 | 该租户实际行数 | 跨租户泄漏命中 |
|---|---|---|---|
| 1 | 4 | 4 | 0 |
| 2 | 3 | 3 | 0 |
| 3 | 3 | 3 | 0 |

以租户 1 的向量在 `tenant_id == 1` 过滤下检索，返回 id ∉ {租户 2/3 的 id}：**`cross_tenant_leak_count = 0`**。
**结论**：partition-key 等值过滤在 Milvus 2.5.4 上隔离语义正确、可作为租户维硬过滤。

## 2. 下推时延（判据 a，规模受限，如实标注）

同一向量分别对 `spike_pk`（partition-key）与 `spike_nopk`（普通 Int64 标量、同谓词 `tenant_id==1`）各检索 40 次：

| 指标 | partition-key | 普通标量过滤 |
|---|---|---|
| median | 15.14 ms | 15.85 ms |
| p95 | 26.31 ms | 25.32 ms |

**toy 规模（10 行）下二者无统计意义差异**——partition 剪枝的收益要随分区数与数据量放大才显现，单集合 10 行
全部命中内存、剪枝省不下扫描。**本 spike 不能证明 perf 收益，也不能证伪**。P2-T5 需在**真实数据量**（≥万级向量、
`num_partitions` 数十~数百）重测，验收口径为与 P2-T0 基线对齐 ±20%。

## 3. LangChain4j `MilvusEmbeddingStore` 表达能力（判据 c，决定性约束）

对生产 `knowledge_chunks` 的 `describe` 与 `langchain4j-milvus:1.0.1-beta6` jar 静态分析交叉印证：

- 现有集合 `knowledge_chunks` 字段固定为 4 列：`id`(VarChar PK)、`text`(VarChar)、**`metadata`(单 JSON 列)**、
  `vector`(FloatVector, **dim=1024**)。租户/权限/分类**全部塞进 `metadata` JSON**（`MilvusVectorStoreService.storeChunks` 即如此）。
- jar 内 `FieldDefinition` 只有 `idFieldName/textFieldName/metadataFieldName/vectorFieldName` 四个可命名槽；
  `MilvusEmbeddingStore.Builder` 仅暴露这 4 个 `*FieldName(...)` + `dimension(...)`——**没有任何 API 增设顶层标量列或声明 partition-key**。
- `MilvusMetadataFilterMapper` 把 `Filter` 映射为对 `metadata` 的 JSON 路径谓词（`map/filter → formatKey(metadata,…)`，
  模板 `%s == %s` / `%s in %s` / `(%s or %s)` / `not(%s)` 等，支持 `And/Or/Not/In/IsEqualTo/Like/比较`）。

运行期验证：在复刻 LangChain4j 模型的 `spike_json`（`metadata` JSON 列）上用
`metadata["tenant_id"] == X` 检索，返回行数与 partition-key 完全一致（4/3/3）——**JSON 路径过滤同样 0 泄漏**。

**结论（R1 核心）**：
- ✅ 走 LangChain4j 现成 store：**能**用 `IsEqualTo("tenant_id", …)` 把租户条件下推为 JSON 谓词，隔离**正确**（已实测 0 泄漏），
  但**拿不到 partition 剪枝**（metadata 非物理分区列）。
- ❌ 走 LangChain4j 现成 store 想要 **partition-key 物理隔离**：**做不到**——schema 写死 4 列，`tenant_id` 无法成为顶层 Int64 partition-key。
- 附带修正一处过时文档口径：`MilvusVectorStoreService.buildFilter` 注释称"LangChain4j Filter 不支持复杂 OR/AND 组合"——
  实测 1.0.1-beta6 的 `MilvusMetadataFilterMapper` **支持 And/Or/Not/In**，G3 的 ACL 下推其实可行（Phase 2 可据实重新评估）。

## 4. 决策建议（写入提案 §10 R1）

**采纳"自建 partition-key 集合 + 裸 Milvus SDK 存储"为 POOL 目标态，保留"LangChain4j JSON 谓词下推"为即时可用回退。**

1. **P2-T1 `EmbeddingStoreProvider`**：POOL 租户的数据面**绕开 `MilvusEmbeddingStore`**，改用 `io.milvus`（milvus-sdk-java，
   已随 langchain4j-milvus 传递引入）自建集合——`tenant_id Int64 isPartitionKey=true`（建议 `num_partitions` 取租户基数上界的 2~4 倍），
   其余列沿用 id/text/vector(1024)/ACL 标量列。LangChain4j 仅保留 **embedding 模型 + query 向量化** 职责。
   `saas.enabled=false` 时 provider 直接返回旧的 `knowledge_chunks` store，行为等价主干（门控不变）。
2. **即时回退（成本最低、可先行落地）**：不建物理分区，沿用 LangChain4j `IsEqualTo("tenant_id", …)` 的 JSON 谓词下推——
   本 spike 已证隔离正确。数据量未起来前这是"够用且零重构风险"的过渡态。
3. **SILO 租户**：独立集合 `kc_{tenantKey}`（裸 SDK 创建），天然物理隔离，与 partition-key 二选一按 placement 路由。
4. **待真实规模复测**：P2-T5 用万级向量重测 partition-key 剪枝时延，若相对 JSON 谓词无 ≥15% 收益，则 Phase 2 维持"JSON 下推"
   即可，把自建 SDK 的改造成本留给真正需要它的租户量级。

## 5. 遗留 / 未覆盖

- 未测 `NUMPARTITION` 对建集合耗时与 compaction 的影响（留 P2-T2 schema 迁移脚本时一并量）。
- 未测混合 POOL+SILO 同库路由正确性（属 P2-T1/P2-T4 集成用例）。
- 时延仅 toy 规模，真实收益判定推迟到 P2-T5。
