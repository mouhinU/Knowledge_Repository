<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-phase3-go-checklist.md（18 项门禁清单，权威勾选位）· docs/saas-multi-tenant-proposal.md §10 R1（架构决策权威）· ../spikes/milvus-partition-key-scale/RESULTS.md（P3-T0 spike 证据）
-->

# SaaS Phase 3 会审 · partition-key / SILO 决策建议一页纸（Review Brief）

> 用途：把 **P3-T0 万级 spike 的实证**浓缩成会审现场可直接定夺的**建议 + 依据 + 待确认项**
> ，服务于 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) 的 S-2 / S-3 / T-2 三项勾选与提案 §10 R1 复核。
> 定位：**这是"阐述方案、供确认"的决策辅助材料，本身不改判、不代权威文档打勾**。本文四条建议已于 **2026-10-09 会审全部被采纳
**（S-2=方案 c · S-3=门禁锚定现网 JSON/标量路径、partition-key 不设性能收益断言 · T-2=方案 B 骨架先行 ·
> R1=不回退并重定性为隔离/运维目标态），勾选位与结论已回写清单 §5/§6、提案 §10 R1、执行计划 §5、评审包 §6——**本 brief
仅作证据与建议留档，权威以清单为准**。
> 版本：v0.2（**定稿** · 2026-10-09 会审采纳四条建议后升版；原 v0.1 2026-10-08 起草，配套清单 v0.1）· 作者：QoderWork ·
> 证据来源：[../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)（2026-10-07
> 跑）
> 关联：门禁清单 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（**v0.3 全量授权开工**）·
> 变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §10 R1（**v0.4**）·
> 三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6（**v1.2**）·
> 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §5 P3-T0/P3-T5（**v1.0**）

---

## 0. 一句话结论（TL;DR）

**在 POOL 的现实规模（万级行、≤100 租户）下，partition-key 没有性能收益，反而比现有 LangChain4j JSON 谓词慢
1.2–2.5×；跨租户隔离正确性两者等价（均 0 泄漏）。** 因此建议会审：

- **S-2 → 方案 c**（延后 partition-key 切换，现网维持更快且零迁移的 LangChain4j JSON 谓词路径）；
- **S-3 → 阈值锚定 JSON/标量路径**，partition-key 若最终选用只设隔离正确性断言、**不设性能收益断言**；
- **T-2 → 先做方案 B 骨架**（`MilvusSdkAdapter` + LangChain4j 回退 bean 并存），暂不抽 `VectorStorePort`；
- **R1 → 不回退决策，但重新定性**：partition-key 的正当理由从"性能剪枝收益"改为"物理隔离 / 可运维 / 未来超大规模期权"
  ，并据此重新评估"POOL 是否仍需 partition-key 目标态"。

代价与影响：P3-T1~T4（生命周期骨架）**与 partition-key 切换解耦**，可先行开工；SILO（S-1 方案 B 延后项）与 partition-key
目标态一并顺延，压力集中到运维 B-1（内存/cluster 预留）。

---

## 1. 证据摘要（来自 P3-T0 spike，非复述原始数据）

环境：Milvus 2.5.4 standalone（本机，内存上限 ~3.82 GiB）/ IVF_FLAT `nlist=128` / COSINE / dim=1024 /
固定种子随机归一向量（测时延与剪枝效率，不测召回——召回正确性已由 P2-T0 用真实 bge-m3 证明）。三集合
`spikescale_pk / _nopk / _json` 同库共存、跑完自动 drop、生产 `knowledge_chunks` 未受影响。

检索时延 P95（ms，limit=10，200 query / 20 warmup）：

| 规模（行 / 租户 / 分区） | partition-key |  普通标量 | LangChain4j JSON 谓词 | 三组跨租户泄漏 |
|-----------------|--------------:|------:|--------------------:|--------:|
| 10k / 3 / 64    |     **20.71** |  8.34 |               10.34 |       0 |
| 10k / 50 / 128  |     **13.40** | 10.09 |               11.28 |       0 |
| 20k / 100 / 256 |     **20.90** |  8.60 |               12.20 |       0 |

三个可用事实：

1. **partition-key 无任何一次跑赢对照**（慢 1.2–2.5×）。机理：万级 ×1024
   维整体近乎驻留内存，标量谓词已是极廉价后过滤，扫描非瓶颈，剪枝省不出可测收益，反而叠加"分区路由"固定开销；建集合开销还随分区数上升（64→256
   分区 create 185→363ms）。
2. **目标态 schema 可建可查、与 LangChain4j 集合同库共存、0 泄漏** → T-2 的"SDK 兼容性"从"待验证"转"已验证可行"（REST
   v2，io.milvus SDK 同层薄封装等价）。
3. **`50k/100t` 触发 standalone OOM 崩溃**（自动重启）→ 这是运维 B-1"内存/cluster 预留"必要性的**直接实证**，也说明
   partition-key/SILO 集合数倍增对资源的需求必须运维承接后才可承诺。

局限（诚实披露，影响外推边界）：本机 standalone 非生产拓扑；未测 HNSW、未测 cluster、未测 Strong 一致性；随机向量不测召回质量。*
*绝对值不可由本机外推**，会审结论应落在"相对趋势 + 隔离等价 + 共存可行"这三条定性判断上。

---

## 2. 待决问题与建议（一一对齐清单勾选位）

### S-2 · 现网 `knowledge_chunks` → partition-key 迁移策略（清单 §3）

| 选项       | 含义                                                                          | spike 对它的支撑度                                                            | 建议       |
|----------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------|----------|
| 方案 a     | 影子集合双写 + 灰度切读 + 旧集合保留 30 天                                                  | 技术可行（三集合同库共存即证据），但代价是写放大 + 双一致性 + 30 天运维窗，且**换来的新集合更慢**                 | 不推荐      |
| 方案 b     | 一次性停机重灌 embedding 到新集合                                                      | ingest ~400 rows/s（10k≈25s / 20k≈43s，未计索引 build/compaction），停机成本可控但需停业务 | 不推荐      |
| **方案 c** | **延后 partition-key，前段 P3-T1~T4 只做生命周期；partition-key 切换与 SILO 同批（P3-T5）再评估** | **被负向性能结果最有力支撑**：现网隔离已 0 泄漏、更快、零 schema 迁移成本                            | ✅ **推荐** |

会审需确认：是否接受"partition-key 切换从 Phase 3 前段剥离、并入 P3-T5 SILO 议题延后评估"；若接受，则 P3-T1~T4 开工不再依赖
io.milvus 建集合，运维 B-1 的**建集合权限**部分可随之松绑（但 **cluster/备份**部分因 SILO 仍需而保留为硬门槛）。

### S-3 · 万级时延阈值 P95（清单 §3）

建议表述（供填写，不代填）：

- **门禁锚定现网实际路径（LangChain4j JSON 谓词 / 普通标量），而非 partition-key。** 本机相对趋势下 JSON 谓词 P95
  8–12ms，留裕度可设 **P95 ≤ 50ms（待预生产/cluster 校准后定绝对值，联动 B-1）**。
- **partition-key 若最终被选用**：只设"隔离正确性"断言（P2-T5 `MilvusTenantIsolationTest` 已覆盖跨租户 0 泄漏），**不设"
  partition-key 快于基线"这类性能收益断言**——因为该前提已被证伪。
- CI 分层：正确性进 `tenant-isolation` job（已就位）；性能阈值若要进 CI，须预生产 cluster 采真实 bge-m3 样本复跑，
  `-Dgroups=perf` 挂 nightly，不在本机 standalone 断言。

会审需确认：X（绝对阈值）与 Y（相对基线裕度）在 cluster 决策后由谁、何时回填；以及"partition-key 不设性能正收益断言"是否写为共识。

### T-2 · partition-key schema 与 LangChain4j 兼容（清单 §4）

- 兼容性子问题：**已验证可行**（见 §1 事实 2），可从门禁"待验证"转为"已验证"。
- 落地范围随 S-2：若采纳 **方案 c**，Phase 3 前段不切 partition-key，则 **T-2 建议先只做方案 B 骨架**——`MilvusSdkAdapter`
  接口 + LangChain4j 单 store 保留为回退 bean 并存，**暂不抽 domain 端口 `VectorStorePort`**（方案 A）以免在无收益的前提下增加抽象与工作量；待
  SILO/partition-key 真正排期时再决定是否升级到方案 A。

会审需确认：是否同意"T-2 的 SDK 兼容性判定 = 已验证；T-2 落地范围绑定 S-2 决策，先骨架后端口"。

### R1 · partition-key 作为 POOL 目标态（提案 §10，权威决策位）

- 现状：R1 当初把"物理剪枝带来性能收益"列为 POOL 采用 partition-key 的理由之一。spike 证实在 POOL 现实规模**该收益不存在
  **。
- 建议处置：**不回退 R1**（保持文档决策连续性、且物理隔离/可运维仍是合法动机），但把 R1 定性从"性能优化目标态"改标注为"*
  *隔离 / 运维性 / 未来超大规模（远超万级）期权的目标态；性能收益未经证实**"，并将"POOL 是否仍需 partition-key 目标态"
  重新列为会审开放项。
- 边界：本文仅提交证据与建议，**R1 的改判须由架构负责人在会审签署后落入提案 §10**，QoderWork 不单方面修改提案结论。

---

## 3. 连带影响（会审采纳建议后的排产变化）

1. **P3-T1~T4（平台端点 / Provisioning 状态机 / 建库默认 / Suspend·Resume）可与 partition-key 解耦先行**——它们不依赖
   io.milvus 建 partition-key 集合，只依赖 T-4（平台端鉴权分层）与 S-5（幂等键）结论。这降低 Phase 3 前段对运维 B-1 建集合权限的耦合。
2. **partition-key 切换与 SILO 合并到 P3-T5 议题**延后评估（S-2 方案 c 的直接结果）；SILO 引入仍依赖 S-1（准入门槛/placement）与
   T-3（`SiloCollectionAdmin` 生命周期 + 是否独立 database）结论。
3. **运维 B-1 权重上调**：spike 的 50k OOM 让"内存/磁盘预留 + cluster 承接"从"建议"变为"有实证支撑的硬门槛"；评审包 §6
   运维栏（U1/U3）会审时应据此复核，而非按原预期轻签。
4. **版本戳已统一升版（2026-10-09 会审后）**：本轮会审 B/S/T/C 结论落定 + 五方书面齐签后，按清单 §7 约定同一里程碑统一升版——清单
   v0.2→**v0.3（全量授权开工）**、执行计划 v0.9→**v1.0**、提案 v0.3→**v0.4**、评审包 v1.1→**v1.2**、本 brief v0.1→**v0.2（定稿）
   **。五处版本戳与交叉引用四处一致（索引表↔正文↔链接↔计数）。

---

## 4. 会审现场最小决议集（2026-10-09 会审已定）

- [x] **S-2 = 方案 c** ✅ 采纳建议——延后 partition-key 切换，现网维持更快且零迁移的 LangChain4j JSON 谓词路径；切换并入
  P3-T5 SILO 议题再评估。
- [x] **S-3 阈值表述确认** ✅ 采纳——门禁锚定现网实际路径（JSON 谓词 / 普通标量），partition-key 若选用只设隔离正确性断言、*
  *不设性能收益断言**（前提已被 spike 证伪）；绝对值 X（P95 ms）与裕度 Y 随 **cluster 决策（B-2）后由运维 + 架构校准回填**，联动
  B-1，不在本机 standalone 断言。
- [x] **T-2 SDK 兼容性判定 = 已验证** ✅；落地范围 = **方案 B 骨架先行**（`MilvusSdkAdapter` + LangChain4j 回退 bean 并存，暂不抽
  `VectorStorePort`），绑定 S-2=方案 c。
- [x] **R1 复核结论 = 维持且重定性** ✅——不回退 partition-key 目标态，定性由"性能剪枝收益"改标注为"**物理隔离 / 可运维 /
  未来超大规模期权**，性能收益未经证实"，已落入提案 §10 R1。
- [x] **S-1 placement 方案 = C（手工指派）** ✅——与 SILO 一并延后至 P3-T5；`sys_tenant_placement.placement` 恒 POOL 直至
  SILO 生命周期引入。
- [x] **B-1 / B-2 / B-3 运维书面签署** ✅——运维/基础设施负责人 **无始** 于 **2026-10-09** 承接签署（B-1 建集合/drop 权限 ·
  B-2 单机 vs cluster 决策列为 **P3-T5 前置** · B-3 内存/cluster 预留随 spike 50k OOM 实证加固）。
- [x] **批准 P3-T1~T4 与 partition-key 解耦先行开工** ✅——已交付（commits `372994a` / `55a0872` / `73b0626` / `eece61a`
  ），全程双门控 `knowledge.saas.enabled=false` + `knowledge.platform.enabled=false`。

上述各项签署/变更后，已回读清单 §7 更新流程与提案 §11、评审包 §6、执行计划 §5 保持零漂移。

---

## 5. 若会审否决本建议（对冲预案）

若架构/运维会审仍决定 Phase 3 前段即切 partition-key（采纳 S-2 方案 a/b）：本 spike **不阻断该决定**
，但需在会审记录中显式应答三条——(1) 在无性能收益的实证下，切 partition-key
的正当性依据是什么（物理隔离合规要求？超大规模预期？）；(2) 影子双写/停机重灌的写放大与停机成本是否已由运维承接（联动 B-1）；(3)
S-3 门禁是否接受"partition-key 不优于基线"为正常预期（否则门禁会因预期错误而误挂红灯）。此三问答完前，不建议在
`saas.enabled=false` 门控之外对生产 `knowledge_chunks` 做任何切换动作。
