# P3-T0 · Milvus partition-key 万级规模 spike

承接 [../milvus-partition-key/](../milvus-partition-key/)（P2-T0 toy spike，10 行）遗留的
**"万级规模 partition 剪枝时延复测"**，为 Phase 3 开工门禁清单
[../../docs/saas-phase3-go-checklist.md](../../docs/saas-phase3-go-checklist.md) 三项决策提供实证：

| 清单项                                             | 本 spike 提供的证据                                                                                                                                      |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| **S-3** 万级时延阈值 P95                              | partition-key vs 普通标量全扫 vs LangChain4j JSON 谓词三条路径的 P50/P95/P99 时延分布                                                                               |
| **S-2** 现网集合迁移策略                                | 复刻 `knowledge_chunks` 的 `metadata` 单 JSON 列模型，评估双写 / 停机重灌 / 延后的插入成本                                                                                |
| **T-2** partition-key schema 与 io.milvus SDK 兼容 | 用 SDK 原生 schema（`tenant_id Int64 isPartitionKey=true` + `metadata JSON` + `category VarChar` + `FloatVector 1024`）建集合，验证与 LangChain4j 现网集合同库共存可建可查 |

## 运行

```bash
# 前置：本机 Milvus standalone 在 localhost:19530（或 --uri 指向 Testcontainers 映射端口）
python3 spike_scale.py                 # 默认 10000 行 / 3 租户 / 64 分区 / 200 查询
python3 spike_scale.py --rows 30000 --queries 300   # 更大规模
python3 spike_scale.py --keep          # 保留 spikescale_* 集合供人工复查
python3 spike_scale.py --use-ollama    # 查询向量走真实 bge-m3（慢，贴近现网语义）
```

## 三张对照集合（同库共存，结束自动 drop，绝不触碰生产 knowledge_chunks）

- `spikescale_pk` —— `tenant_id` 作 partition-key（POOL 目标态，Phase 3 待建 `knowledge_chunks_pk`）
- `spikescale_nopk` —— `tenant_id` 普通 Int64 标量（全扫对照，剪枝收益基线）
- `spikescale_json` —— `metadata` 单 JSON 列（复刻现网 LangChain4j `knowledge_chunks`）

## 设计说明与局限

- **向量来源**：默认固定随机种子生成 1024 维 L2 归一向量。本 spike 测的是**检索基础设施时延与
  partition 剪枝效率**，不是召回质量——召回正确性与跨租户 0 泄漏已在 P2-T0 用真实 bge-m3 证明。
  `--use-ollama` 可切真实向量，但万级 batch 调 Ollama 很慢，一般只在查询探针上用。
- **索引**：三张集合统一 `IVF_FLAT nlist=128 / COSINE`，保证时延对照只由"是否分区剪枝"单一变量决定。
- **num_partitions**：默认 64。租户数远小于分区数时，多数分区为空，剪枝收益更明显；Phase 3 提案建议
  `num_partitions` 取租户基数上界的 2–4 倍。
- **一致性**：用 `Bounded` + flush + 预热 20 条消除冷启动；生产如需 `Strong` 需另计额外时延。
- **诚实披露**：本机 Milvus standalone 内存/磁盘与生产可能不同，P95 绝对值仅供**相对趋势与阈值选型**，
  正式门禁阈值应在 CI 或预生产环境复跑校准（对齐清单 B-1 运维承接）。
