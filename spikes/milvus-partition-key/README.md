# spikes/milvus-partition-key — P2-T0 技术 spike

SaaS 向量隔离（提案 §10 R1）的**一次性技术验证**，不属于生产构建，不参与 Maven reactor。

- `spike.py` — 纯标准库脚本。连本机 Milvus 2.5.4（REST v2 `:19530`）+ Ollama bge-m3（`:11434`），
  建 `spike_pk`（`tenant_id` partition-key）/`spike_nopk`（普通标量，时延对照）/`spike_json`（复刻
  LangChain4j 单 JSON metadata 模型）三张临时集合，跑隔离正确性 + 召回 + 时延，结束自动 `drop`。
  **绝不触碰生产 `knowledge_chunks`。**
- `results.json` — 最近一次运行输出（脚本自动覆写）。
- `RESULTS.md` — 结论文档；决策已写回 `docs/saas-multi-tenant-proposal.md` §10 R1 与
  `docs/saas-multi-tenant-execution-plan.md` P2-T0。

复跑：

```bash
cd spikes/milvus-partition-key
python3 spike.py          # 跑完清理
python3 spike.py --keep   # 保留 spike_* 集合供人工用 Attu/REST 复查
```

前置：Milvus 2.5.4 与 Ollama(`bge-m3`) 需已在本机就绪。
