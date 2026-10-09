<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· docs/saas-multi-tenant-proposal.md §11 · docs/saas-multi-tenant-execution-plan.md §6 · docs/saas-phase3-go-checklist.md §2 B-2 · §5 C-1/C-3 · docs/saas-multi-tenant-proposal-review-pack.md §6
-->

# SaaS 化改造 · Phase 4 开工确认清单（Phase 4 Go-Checklist）

> 用途：**Phase 4（配置 / 缓存 / LLM 租户化，P4-T1~T4）开工前的最后一道闭环清单**。把 Phase 3 消解/延后至 Phase 4 的 B/S/T 项、执行计划 §6 Phase 4 任务表中的开放设计问题、Phase 4 退出条件"三处进程内缓存脑裂消除 / 密钥不进 Prometheus 高基数标签 / `config_key`/`checksum` 唯一约束已切租户内"落成可勾选的 Go/No-Go 表。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策，v0.4）· 三方评审包 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md)（签署栏，v1.2）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（P4 任务表，v1.0）· Phase 3 生效基线 [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md)（v0.3，全量授权开工中；Phase 4 起前保留为"生效基线"）· Phase 2 归档基线 [saas-phase2-go-checklist.md](saas-phase2-go-checklist.md)（v0.3）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> 版本：**v0.1（2026-10-09 起草）** · 作者：QoderWork · 状态：**待会审**——Phase 3 已交付 P3-T1~T4 + P3-T5 Archive & Delete 子切片 + P3-T7 OIDC 子切片 + P3-T7 邀请式设密回访子切片；partition-key/SILO 分支 + P3-T6 逐库 runner 仍受运维 B-2 硬前置门控。Phase 4 起草与 Phase 3 尾分支解耦（P4-T1 迁移已由 C-4=方案 A 提前并入 P3-T3；P4-T2 Redis 依赖 Phase 5 P5-T2 需前置或延后；P4-T3/P4-T4 独立）。**本清单起草不代表 Phase 4 已授权开工**——需 B 类三项运维/安全书面承接 + S/T 类全项闭环 + C 类已指派责任人与里程碑后，方按 §7 阶梯升 v0.2/v0.3。

---

## 0. 使用说明与结论判定

**颗粒度**：每项一行，含"编号 · 条目 · 负责人 · 依据锚点 · 判断位 · 时限"。

**通过条件**：**B 类全部 ☑ 签署** + **S 类全部 ☑ 签署 或 ☑ 已授权延后** + **T 类全部 ☑ 已给出书面结论** + **C 类已指派责任人与里程碑**。任一 B 类未过则 Phase 4 不得开工。

**编号前缀**：B=Blocking 硬阻塞、S=Soft 软阻塞、T=Technical 技术细节、C=Cross-cutting 交叉项。

**Phase 4 范围提示**：Phase 4 = 配置/缓存/LLM 三项租户化（P4-T1 `sys_config` 读取顺序/缓存 key + P4-T2 分布式失效 + P4-T3 `kb_extraction_cache` 加租户维 + P4-T4 per-tenant LLM profile），**不**包含 Phase 3 尾分支 partition-key/SILO 切换（P3-T5 partition-key + P3-T6 SILO 逐库 runner，仍在 Phase 3 §7 第 15 条门控）、Phase 5 对象存储 Redis 会话与 compose 副本化（P4-T2 依赖 P5-T2 Redis 但排 Phase 5 起前，Phase 4 需要独立讨论 Redis 引入方式）、Phase 6 计费配额（P6-T1 `tenant_usage` 依赖 P4-T4）。

**与 Phase 3 清单的关系**：本清单是 Phase 3 清单 v0.3 的下游承接。Phase 3 清单 §5 C-3=方案 B 的 IdP RBAC 角色映射/租户归属绑定延后至 Phase 4 视需要，本清单 §5 C 类跟踪。Phase 3 §5 C-4=方案 A 已把 P4-T1 迁移提前并入 P3-T3（`V28__tenant_scope_sys_config_and_invite.sql`），Phase 4 P4-T1 剩余仅覆盖 `SystemConfigServiceImpl` 读取顺序与 `ConfigCacheKey` 抽象。格式（§0/§1/§2/§3/§4/§5/§6/§7/§8 九节）与 Phase 2/Phase 3 清单 1:1 对齐。

---

## 1. Go/No-Go 速览

| 类别 | 条目数 | 负责人 | 当前状态 | 时限建议 |
|---|---|---|---|---|
| B 硬阻塞 | 3 | 运维/基础设施 + 安全 | ☐ 待签署：B-1 Redis 引入方案（P4-T2 pub/sub）· B-2 密钥保管方案（KMS vs 本地主密钥 vs Vault，P4-T4）· B-3 提案 §11 Phase 4 栏闭环 | Phase 4 起前 2 周 |
| S 软阻塞 | 5 | 架构 + 安全 + 产品/商务 | ☐ 待签署：S-1 P4-T1 缓存 key 结构 · S-2 P4-T2 Redis 缺席降级 · S-3 P4-T3 checksum 唯一约束切换冲突扫描 · S-4 P4-T4 密钥轮换与解密失败安全降级 · S-5 P4-T4 未配 API Key 的租户是否禁用 LLM | Phase 4 起前 1 周 |
| T 技术细节 | 5 | 架构 + 安全 | ☐ 待签署：T-1 `TenantLlmProfileResolver` 缓存 · T-2 `ConfigCacheKey` 分层落点 · T-3 `SecretCipher` API 边界 · T-4 Prometheus 租户维度基数控制 · T-5 `kb_extraction_cache` checksum 是否 per-tenant 加盐 | Phase 4 起前 |
| C 交叉项 | 4 | 运维 + 安全 + 项目负责人 + 产品/商务 | ☐ 待签署：C-1 CI 门禁扩展（Phase 4 profile）· C-2 与 Phase 5 P5-T1 MinIO/P5-T2 Redis 时序 · C-3 Phase 3 P3-T7 IdP RBAC/租户绑定延后承接 · C-4 Phase 4 P4-T4 上线后 per-tenant LLM 配额计量与 Phase 6 P6-T1 排产 | Phase 4 会审前 1 周 |
| **合计** | **17** | — | — | — |

**一句话结论**：**Phase 4 是 SaaS 化改造从"生命周期完整"走向"多租户真正隔离"的第二步**——把 Phase 1/2 遗留的三处进程内全局缓存（`sys_config` / `kb_extraction_cache` / LLM 客户端单例）从"租户可见但共享"升级为"租户级独立 + 平台默认兜底 + 分布式失效"，同时引入 `SecretCipher` 密钥保管边界，让 per-tenant LLM/embedding profile 得以在不牺牲密钥安全的前提下落地。B 类三项运维/安全承接不可消解：Redis 引入方案（P4-T2 pub/sub 与 P5-T2 Redis 会话共用或分离）、密钥保管（KMS vs 本地主密钥）、提案 §11 Phase 4 栏闭环。**Phase 3 partition-key/SILO 尾分支与 P3-T6 逐库 runner 不阻塞 Phase 4 起草，但阻塞 Phase 4 与 Phase 3 同期上线**——见 §5 C-2。

---

## 2. B 类 · 硬阻塞（Blocking）

> 未过则 Phase 4 不得开工，任一 ☑ 签署位保持未打即视为 No-Go。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **B-1** | **Redis 引入方案**（P4-T2 `sys_config` 分布式失效 + P5-T2 上传会话 + Phase 6 配额限流潜在需求）：a) Redis 部署形态——**单机** vs **Sentinel** vs **Cluster**，与 P5-T2 上传会话 Redis 是否共用实例；b) 生产环境 Redis 实例的申请路径、SLA、监控接入；c) P4-T2 若 Redis 不可用降级 TTL 兜底——降级窗口内多实例配置一致性 SLA 是多少秒；d) Redis 是否承载未来 P6-T1 `tenant_usage` 计数（若是，需要预留容量与持久化策略）。方案 A：Phase 4 起前 Redis 单机到位；方案 B：Phase 4 只落 TTL 兜底，Redis pub/sub 与 P5-T2 同期；方案 C：延后 P4-T2 到 Phase 5 | 运维/基础设施 + 架构 | 执行计划 §6 P4-T2 · §7 P5-T2 · 提案 §6 配置章节 · Phase 3 清单 §5 C-4（P4-T1 已提前） · Phase 5 未创建 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 需补料 ☐ 否决 | Phase 4 起前 2 周 |
| **B-2** | **密钥保管方案**（P4-T4 `LlmProfile.api_key` 加密 + 未来 Phase 6 计费凭据潜在需求）：a) `SecretCipher` 后端——**云 KMS**（AWS/阿里云/Azure）vs **本地主密钥文件**（`keystore.p12`）vs **HashiCorp Vault** vs **不加密仅走权限隔离**；b) 密钥轮换周期与流程（泄露应急）；c) 密钥文件不落 Git（红线 #4），`.gitignore` 与密钥注入路径（env / Vault token / KMS AssumeRole）；d) 若走云 KMS，需评估供应商绑定风险；若走本地主密钥，需评估备份与灾难恢复；e) Prometheus 指标严禁带 `api_key` / `tenant_id + key_fingerprint` 类高基数标签（Phase 4 退出条件明确"密钥不进 Prometheus 高基数标签"）。方案 A：KMS；方案 B：本地主密钥 + 备份；方案 C：Vault 自建 | 安全 + 运维/基础设施 | 执行计划 §6 P4-T4 · 提案 §5 密钥边界 · 规范基线 [security-guideline.md](security-guideline.md) §5/§6 日志脱敏 · [coding-guideline.md](coding-guideline.md) §九 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 需补料 ☐ 否决 | Phase 4 起前 2 周 |
| **B-3** | **提案 §11 审批栏 Phase 4 栏闭环**：Phase 3 v0.3 全量授权后，[saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §11 第四条（Phase 4 相关：配置/缓存/LLM 租户化）checkbox 由 ☐ 待审 → ☑ 签署；同步回写 [saas-multi-tenant-proposal-review-pack.md](saas-multi-tenant-proposal-review-pack.md) §6 签署页第五栏（若存在），或新增"Phase 4 承接"栏目。**若 B-1/B-2 采纳延后方案（B-1 方案 C / B-2 需补料），则 Phase 4 起整体后移至 Phase 5 同期，本条随迁** | 项目负责人（协调）+ 安全 + 运维 | Phase 3 清单 §2 B-3 · 提案 §11 · 评审包 §6 | ☐ 已闭环 ☐ 待 B-1/B-2 通过后签署 ☐ 需补料 ☐ 否决 | Phase 4 起前 |

---

## 3. S 类 · 软阻塞（Soft）

> 未闭环则相应子任务不得开工；允许授权延后至具体 P4-TX 起草时定。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **S-1** | **`sys_config` 缓存 key 结构**（P4-T1 余量）：Phase 3 P3-T3 已落 `V28__tenant_scope_sys_config_and_invite.sql`（`sys_config` 加 `tenant_id` + `(tenant_id, config_key)` 复合唯一），Phase 4 P4-T1 只需覆盖 `SystemConfigServiceImpl` 读取顺序（租户级 → 平台级 → 内置默认）与 `ConfigCacheKey` 抽象。方案 A：`ConfigCacheKey = (tenantId, configKey)`，两级（租户/平台）由 `tenantId IS NULL` 或 `tenantId=0` 表示；方案 B：`ConfigCacheKey = (tenantId, scope, configKey)`，`scope ∈ {TENANT, PLATFORM, BUILTIN}` 显式；方案 C：不落 `ConfigCacheKey` 值对象，直接 `Map<Long tenantId, Map<String key, String value>>` 双层缓存。考虑：可读性、跨层失效、P4-T2 pub/sub payload 编码 | 架构 | 执行计划 §6 P4-T1 · Phase 3 清单 §5 C-4 · Phase 3 P3-T3 交付记录 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 P4-T1 起草 | P4-T1 起前 |
| **S-2** | **Redis 缺席降级策略**（P4-T2）：Redis pub/sub 不可用时（例如未部署、连接失败、租户灰度期），`SystemConfigServiceImpl` 的兜底策略。方案 A：TTL 硬编码 5min；方案 B：TTL 可配置 `knowledge.config.cache.ttl-seconds`，默认 300；方案 C：启动期一次性预加载 + 手工触发失效端点 `/api/admin/config/invalidate`；方案 D：关闭 `sys_config` 写入端点直到 Redis 恢复。考虑：配置漂移窗口、租户管理员写入延迟感知、审计 | 架构 + 运维 | 执行计划 §6 P4-T2 · B-1 · 提案 §6 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 方案 D ☐ 延后至 B-1 定后 | P4-T2 起前 |
| **S-3** | **`kb_extraction_cache` 唯一约束切换冲突扫描**（P4-T3）：Phase 1 P1-T4/T5 已给 15 表加 `tenant_id`，但 `kb_extraction_cache` 属"Phase 4 延后表"（执行计划 §5 P1-T4 备注），当前唯一键仍为全局 `checksum`。P4-T3 迁移到 `UNIQUE(tenant_id, checksum)` 前需：a) 现网是否存在跨租户同 checksum 存量（默认租户 `tenant_id=1` 之外的租户是否已产生 extraction cache 行）；b) 若存在，迁移脚本是否需要先跑 `DELETE` 去重；c) 迁移窗口是否需要停机；d) 迁移后 checksum 值是否需要 per-tenant 加盐（防侧信道推断）。方案 A：无跨租户冲突，直接加约束；方案 B：先扫描，冲突行按 `tenant_id + checksum` 保留最早一条其余清理；方案 C：checksum 加盐 per-tenant（详见 T-5） | 数据库 + 架构 + 运维 | 执行计划 §6 P4-T3 · Phase 3 清单 §5 C-2（迁移演练） · Phase 1 交付记录 P1-T4 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 需先跑扫描脚本 | P4-T3 起前 1 周 |
| **S-4** | **P4-T4 `LlmProfile` 解密失败安全降级**：租户配置了 `api_key` 但 `SecretCipher` 解密失败（密钥丢失、密文损坏、KMS 不可达）时的策略。方案 A：立即回退到平台默认 `api_key`（风险：跨租户调用错乱、成本归属错乱）；方案 B：抛业务异常拒绝本次调用并写入审计；方案 C：拒绝调用 + 触发租户管理员告警 + 保留租户已知的其他非 LLM 功能；方案 D：整个租户降级为"只读"（停用 LLM + 停用出题 + 停用文章生成）。考虑：红线 #5 参数注入 / 红线 #6 DO 不越 infra / 安全侧默认拒绝 | 架构 + 安全 | 执行计划 §6 P4-T4 · 提案 §5 密钥边界 · [security-guideline.md](security-guideline.md) §5 认证令牌 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 方案 D | P4-T4 起前 |
| **S-5** | **未配 `api_key` 的租户 LLM 是否禁用**（P4-T4）：Phase 4 上线时新租户可能只填了 `embedding_model` 但没填 `api_key`，或整个 `LlmProfile` 为空。方案 A：全部走平台默认（沿用当前 `saas.enabled=false` 行为）；方案 B：强制"每个租户必须有独立 `api_key`"，无配置则 LLM 相关端点直接拒绝；方案 C：分模块——`embedding` 缺失走平台默认，`chat` 缺失禁用出题/文章；方案 D：产品决策位（按租户套餐/是否付费分层）。考虑：Phase 6 计费口径、成本归属 | 产品/商务 + 架构 | 执行计划 §6 P4-T4 · Phase 6 计费配额 · 提案 §5 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 方案 D ☐ 延后至 Phase 6 一起定 | P4-T4 起前 |

---

## 4. T 类 · 技术细节（Technical）

> 未给出书面结论则相应 P4-TX 起草时无法收敛；允许授权延后至具体任务起草时。

| 编号 | 条目 | 负责人 | 依据锚点 | 判断 | 时限 |
|---|---|---|---|---|---|
| **T-1** | **`TenantLlmProfileResolver` 缓存策略与失效钩子**（P4-T4）：a) 缓存 key 与形态——`ConcurrentHashMap<tenantId, LlmProfile>` 无失效 vs Caffeine LRU 上限 vs Redis 共享；b) 失效钩子——`api_key` / `base_url` / `model` 任一变更时立即 evict，与 P4-T2 pub/sub 对齐；c) 解密失败结果是否缓存（避免每次都跑 KMS）；d) 冷启动加载策略（懒加载 vs 启动期预加载活跃租户集）；e) 与 P4-T1 `ConfigCacheKey` 是同一份缓存层还是各自独立 | 架构 | 执行计划 §6 P4-T4 · S-1/S-2 · 提案 §5 | ☐ 方案（列明 A/B/C/D/E 各选择） ☐ 延后至 P4-T4 起草 | P4-T4 起前 |
| **T-2** | **`ConfigCacheKey` 分层落点**（P4-T1 余量）：值对象是 `domain/model/valueobject/ConfigCacheKey.java` 还是 `infrastructure/cache/ConfigCacheKey.java`。方案 A：domain（若 S-1 选方案 B 显式 scope 且未来 `LlmProfile` 也需要相同形态则有复用价值）；方案 B：infra（仅内部实现细节，domain 层只暴露 `getEffective(tenantId, key)` 端口，符合红线 #7 domain 纯净）。考虑：与 T-1 `TenantLlmProfileResolver` 是否共享 key 抽象 | 架构 | 执行计划 §6 P4-T1 · [architecture-decisions.md](architecture-decisions.md) §2/§5 | ☐ 方案 A ☐ 方案 B | P4-T1 起前 |
| **T-3** | **`SecretCipher` API 边界与 KMS 集成方式**（P4-T4 依赖 B-2）：a) 端口定义——`domain/gateway/SecretCipher.java`（`encrypt/decrypt/rotateKey`）or `infrastructure/security/SecretCipher.java`；b) Spring `@ConditionalOnProperty` 门控：`knowledge.secret.cipher.mode={kms,local,vault,none}`；c) `none` 模式（Phase 4 起草时若 B-2 未定）允许明文入库但 yml 强 warn，红线 #4 严禁日志；d) 密文列长度——AES-GCM 输出（IV + 密文 + Tag）通常 base64 后 ~256 字符，`api_key` 列 `VARCHAR(512)` 是否够；e) `KeyRotationCmdExe` 是否作为独立用例（写审计 + 双人复核） | 架构 + 安全 | 执行计划 §6 P4-T4 · B-2 · [security-guideline.md](security-guideline.md) §5/§6 · [architecture-decisions.md](architecture-decisions.md) §5 | ☐ 方案（列明 A/B/C/D/E 各选择） ☐ 延后至 B-2 定后 | P4-T4 起前 |
| **T-4** | **Prometheus 指标租户维度基数控制**（Phase 4 退出条件明确"密钥不进 Prometheus 高基数标签"）：a) 指标命名——`knowledge_llm_calls_total{tenant_id, model, status}` 还是 `knowledge_llm_calls_total{tenant_bucket="top10", other_bucket="rest", model, status}`；b) `tenant_id` 作为 label 是否引入高基数爆炸（若 SILO 租户数 >1000 需评估）；c) 密钥指纹（`key_hash`）严禁作为 label，只允许日志中脱敏引用；d) Actuator `@Timed` 注解在 Executor 层的粒度；e) Grafana 看板 per-tenant 曲线限制 top-N + rest。方案 A：`tenant_id` 直进 label；方案 B：bucket 化；方案 C：不暴露 per-tenant 指标，只做平台级聚合 | 运维/基础设施 + 架构 | 执行计划 §6 Phase 4 退出条件 · [deployment-ci-cd.md](deployment-ci-cd.md) · 现有 `docs/pr-notes/exceptions/20260923-observability-prometheus-registry.md` | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 Phase 4 中评 | P4-T4 起前 |
| **T-5** | **`kb_extraction_cache.checksum` 是否 per-tenant 加盐**（P4-T3 安全侧）：全局 checksum 意味着 A 租户上传 PDF-X 后 B 租户上传同一 PDF-X 会直接命中缓存（G7 泄漏风险，Phase 1 附录 A 已登记）。方案 A：加盐 `SHA-256(tenant_id + ":" + checksum)`，跨租户永不命中；方案 B：不加盐，仅在 `WHERE` 加 `tenant_id`，唯一键 `(tenant_id, checksum)`（等价 S-3 方案 B）；方案 C：加盐 + 唯一键，且**加盐密钥本身**用 `SecretCipher` 加密。考虑：性能（同租户重复上传命中）vs 侧信道（时间差推断他人是否上传过同文件） | 安全 + 架构 | 执行计划 §6 P4-T3 · 附录 A G7 · S-3 · [security-guideline.md](security-guideline.md) §5 | ☐ 方案 A ☐ 方案 B ☐ 方案 C | P4-T3 起前 |

**T 类决策模板**：五项 T 中 T-2/T-4/T-5 与具体 P4-TX 起草时对齐即可；T-1/T-3 与 B-1/B-2/S-1/S-2/S-4 有强依赖，若 B-1 走方案 C（Redis 延后）则 T-1 也需重估；若 B-2 需补料则 T-3 悬空，Phase 4 起草时先落 `SecretCipher` `none` 模式作占位。

---

## 5. C 类 · 交叉项（Cross-cutting）

> 不属于 Phase 4 本体但与 Phase 4 退出条件（"三处进程内缓存脑裂消除；密钥不进 Prometheus 高基数标签；`config_key`/`checksum` 唯一约束已切租户内"）挂钩；本类只需指派责任人与里程碑，不阻断开工。

| 编号 | 条目 | 负责人 | 依据锚点 | 状态 | 时限 |
|---|---|---|---|---|---|
| **C-1** | **X-T3 CI 门禁扩展（Phase 4 profile）**：Phase 3 尾 CI 已有 `tenant-isolation` job + `perf`/`silo` tag 待扩展（Phase 3 清单 §5 C-1）。Phase 4 新增：a) `config-isolation` tag 覆盖 P4-T1 跨租户配置读写负向；b) `llm-profile-isolation` tag 覆盖 P4-T4 per-tenant API Key 隔离；c) `cache-invalidation` tag 覆盖 P4-T2 双实例 30s 一致集成（依赖 B-1 Redis 到位）；d) GitHub Actions matrix 或独立 workflow | 项目负责人 + DevOps + 测试 | 执行计划 §9 X-T3 · Phase 3 清单 §5 C-1 · 现有 [.github/workflows/ci.yml](../.github/workflows/ci.yml) `tenant-isolation` job | ☐ 已扩展 ☐ 待 P4-T1~T4 起时同步（起草 2026-10-09） | 与 P4-T1~T4 同期 |
| **C-2** | **Phase 3 尾分支时序耦合**：P3-T5 partition-key 分支 + `MilvusSdkAdapter` 骨架 + `SiloCollectionAdmin`（T-3=A）+ P3-T6 SILO 逐库 runner 均受 **Phase 3 B-2 cluster 扩容 + 备份** 硬前置门控。Phase 4 P4-T2 需要 Redis（B-1），Phase 5 P5-T2 上传会话也需要 Redis。**若 Phase 3 B-2 与 Phase 4 B-1 由运维同期落定**，Phase 3 尾分支 + Phase 4 可并行推进；否则按 Phase 3 完成 → Phase 4 起 顺序排。责任人指派需含 Phase 3 B-2 状态跟踪 | 项目负责人 + 运维 | Phase 3 清单 §2 B-2 · §7 第 14/15 条 · 执行计划 §5 Phase 3 退出条件 · 本清单 §2 B-1 | ☐ 已指派 ☐ 待项目负责人确认时序 | Phase 4 会审前 |
| **C-3** | **Phase 3 P3-T7 IdP RBAC / 租户归属绑定延后承接**：P3-T7 `93dcbb9` 交付时明确"IdP token 命中后未做 RBAC 角色映射 / 租户归属绑定"，仅归一为 `PlatformTokenPayload` 供平台端审计。**Phase 4 是否补齐**：若企业 IdP 首次接入需要平台管理员 RBAC，则新增 P4-T5 任务（或并入 P4-T4）；若延后至 Phase 5/6 视客户接入节奏。方案 A：并入 P4-T4；方案 B：独立 P4-T5；方案 C：延后至客户 IdP 首次接入时排产 | 架构 + 安全 + 产品/商务 | Phase 3 清单 §7 第 15 条 · 执行计划 §5 P3-T7 行 · Phase 3 清单 §5 C-3 | ☐ 方案 A ☐ 方案 B ☐ 方案 C | Phase 4 会审前 |
| **C-4** | **per-tenant LLM 配额计量与 Phase 6 排产**：P4-T4 `TenantLlmProfileResolver` 上线后，per-tenant 调用会带独立 `api_key`，Phase 6 P6-T1 `tenant_usage` 采集需要感知 profile 归属（哪次调用是哪个租户、成本落在哪家云账号）。方案 A：Phase 4 只落 resolver，Phase 6 起前再补采集钩子；方案 B：Phase 4 P4-T4 交付时同步埋点 `TenantUsageRecorder`；方案 C：Phase 4 与 Phase 6 合并同期上线（若 SaaS GA 需要计费）| 产品/商务 + 架构 | 执行计划 §6 P4-T4 · §8 P6-T1 · 提案 §8 配额 | ☐ 方案 A ☐ 方案 B ☐ 方案 C ☐ 延后至 Phase 6 会审 | Phase 4 会审前 |

---

## 6. 签署页

> 结论选项：**☐ 签署（同意）/ ☐ 需补材料（列明条目号）/ ☐ 否决（列明理由）**。五类角色齐签后 Phase 4 转"全量授权开工"。**v0.1 状态（2026-10-09）**：五方均未签署，Phase 4 待会审。B-1/B-2 由运维/安全承接为硬门槛（Redis + 密钥），B-3 由项目负责人协调闭环。S-1~S-5 / T-1~T-5 / C-1~C-4 均待相应负责人书面定夺或授权延后。

| 角色 | 关注条目 | 结论 | 签署人 | 日期 |
|---|---|---|---|---|
| 架构负责人 | S-1 · S-2 · S-3 · T-1 · T-2 · T-3 · T-5 · C-2 · C-3 · C-4 | ☐ 签署 ☐ 需补料 ☐ 否决 | — | — |
| 安全负责人 | B-2 · S-4 · S-5 · T-3 · T-4 · T-5 · C-3 | ☐ 签署 ☐ 需补料 ☐ 否决 | — | — |
| 运维/基础设施负责人 | B-1 · B-2 · S-2 · S-3 · T-4 · C-1 · C-2 | ☐ 签署 ☐ 需补料 ☐ 否决 | — | — |
| 产品/商务负责人 | S-5 · C-3 · C-4 | ☐ 签署 ☐ 需补料 ☐ 否决 | — | — |
| 项目负责人（协调） | B-3 · C-1 · C-2 | ☐ 签署 ☐ 需补料 ☐ 否决 | — | — |

**签署要点提示**（v0.1 起草说明）：

- 架构（蒙斌）：Phase 4 五项 T 中 T-2/T-4/T-5 与 P4-TX 起草对齐即可；T-1/T-3 与 B-1/B-2 强依赖，可在 B-1/B-2 定后再签。C-2/C-3/C-4 需要架构牵头做时序决策。
- 安全（莫道）：B-2 密钥保管是 Phase 4 唯一硬门槛之一，KMS/Vault/本地主密钥三选一（或"需补料"要求运维给出容量与合规意见）；T-4 Prometheus 指标是 Phase 4 退出条件的直接体现，务必给出方案 A/B/C 或明确延后。
- 运维/基础设施（无始）：B-1 Redis 与 Phase 3 B-2 cluster 扩容同期决策最经济；T-4 bucket 化 vs 高基数 label 需给出监控栈侧容量意见。
- 产品/商务（七月）：S-5 决定"是否强制每租户独立 API Key"影响 Phase 6 计费口径，与 C-4 联动。
- 项目负责人（流星）：B-3 提案 §11 Phase 4 栏闭环需在其他 B 项通过后统一回写；C-2 Phase 3 尾分支时序需给出"是否并行"结论。

---

## 7. 起草与升版路径

**版本阶梯（v0.1 → v0.4）**：

1. **v0.1（当前，2026-10-09）**：Phase 3 v0.3 全量授权开工后起草 Phase 4 门禁清单 v0.1，把 Phase 3 §5 C 类延后至 Phase 4 的 4 项（C-3 IdP RBAC / Phase 5 Redis 依赖 / Phase 6 配额埋点 / Phase 3 尾分支时序）+ Phase 4 本体 4 任务的开放决策项合并成 17 项 B/S/T/C 四类可勾选表。本清单起草不代表 Phase 4 已授权开工，仅供会审。
2. **v0.2（部分授权 provisional）**：项目负责人口头采纳"Phase 4 与 Phase 3 尾分支解耦先行"建议时，可先授权 P4-T1 余量（`SystemConfigServiceImpl` 读取顺序 + `ConfigCacheKey` 抽象，依赖 S-1/T-2）与 P4-T3（`kb_extraction_cache` 加租户维，依赖 S-3/T-5）；B-1 Redis 与 B-2 密钥两项硬门槛未闭时 P4-T2/P4-T4 保持阻塞。回写：§1 速览 B-1/B-2 保持 ☐ 未签 · S-1/S-3/T-2/T-5 打钩 · 执行计划 §6 P4-T1/P4-T3 行标注"已授权开工"。
3. **v0.3（全量授权开工）**：五方书面齐签后，Phase 4 全量开工。回写：AGENTS.md §1 分册地图 Phase 4 清单行标 v0.3、§2 决策树同步；执行计划升 v1.1；提案 §11 Phase 4 栏 ☑；评审包新增 Phase 4 承接栏。
4. **v0.4（Phase 4 已交付归档）**：P4-T1~T4 全部落地 + S/T 结论回写执行计划/提案后升版。Phase 5 起前保留于 `docs/` 作为生效基线，届时参照格式决定是否创建 `docs/saas-phase5-go-checklist.md`。

**执行清单**（v0.1 起草 → v0.2 部分授权）：

1. **会审前准备**：架构 + 安全 + 运维三方各出 1 名代表过一遍 B/S/T 全部 13 项，标记需补料项。
2. **B 类决策**：B-1 Redis 与 Phase 5 P5-T2 时序合并给出；B-2 密钥保管走安全侧独立评审；B-3 待前两项闭环后项目负责人协调统一回写提案 §11。
3. **S/T 类可延后**：若某项在 P4-TX 起草前无法定，可 ☑ 授权延后至具体 PR 起草（与 Phase 3 清单 S/T 处理方式一致）。
4. **C 类已指派**：C-1 CI 扩展责任人 + C-2 时序决策责任人 + C-3 RBAC 延后承接责任人 + C-4 配额埋点责任人，四项均需明确到具体人与里程碑。

**Phase 4 与 Phase 3 尾分支的排产关系**：见 §5 C-2。若 Phase 3 B-2 与 Phase 4 B-1 由运维同期落定，Phase 3 partition-key/SILO/`SiloCollectionAdmin`/P3-T6 与 Phase 4 P4-T1~T4 可并行推进；否则严格串行。

---

## 8. 未纳入本清单的相关事项（透明声明）

以下不属于 Phase 4 开工门禁，但常在讨论中被混淆，此处显式排除避免范围漂移：

| 事项 | 归属 | 备注 |
|---|---|---|
| Phase 3 尾分支 P3-T5 partition-key 切换 / `MilvusSdkAdapter` / `SiloCollectionAdmin` / `knowledge_chunks_pk` 建集合 | Phase 3 | 由 Phase 3 清单 §7 第 14 条门控，等待运维 B-2 cluster 扩容 + 备份方案书面落定 |
| Phase 3 尾分支 P3-T6 SILO Flyway 逐库 runner | Phase 3 | 由 Phase 3 清单 §7 第 15 条门控，依赖 `SiloCollectionAdmin` + 首批 SILO placement 出现 |
| Phase 3 尾分支 P3-T7 IdP 邀请式设密回访 · **落地页预检查 + 平台运维重签 + 前端四态引导子切片已交付**；**IdP 首登绑定 RBAC/租户归属映射**仍未做 | Phase 3（IdP 首登绑定为可选后续子切片） | 邀请式设密回访预检查 + 重签两段已随 P3-T7 子切片交付（详见 Phase 3 清单 §7 第 16 条）；IdP 首登绑定 RBAC/租户归属映射随客户 IdP 首次接入排产，不阻塞 Phase 4（本清单 C-3 承接） |
| Phase 5 全部（MinIO SDK / Redis 上传会话 / assetKey 校验 / compose 副本化 / 定时任务分片） | Phase 5 | 本清单 B-1 Redis 决策与 Phase 5 P5-T2 上传会话 Redis 部署形态同期评估，Phase 5 本体另立 Phase 5 开工门禁清单 |
| Phase 6 计费配额（`tenant_usage` 表 / QuotaGuard / 套餐限额） | Phase 6 可选后置 | 与 Phase 4 退出条件解耦，本清单 C-4 只跟踪 P4-T4 埋点时序 |
| U3 数据驻留 `data_region` 物理分区部署 | Phase 5 布局 | 评审包 §3 U3；不影响 Phase 4 配置/缓存/LLM 本体 |
| 前端跨域 / Cookie / IdP 首登跳转流程 | X-T5（贯穿） | 评审包 §3 U5；P3-T3 邀请式设密已就位，IdP 首登流程留待客户接入时 |
| `LlmProfile` 与 `EmbeddingModel` `dimension` 一致性 | Phase 4 P4-T4 内部约束 | 若租户覆盖 `embedding_model` 但 Milvus 集合已建 `dim=1024`，需拒绝切换或走重新灌向量流程；此约束在 P4-T4 交付 PR 中体现，不进入开工门禁 |

---

## 附：与本清单相关的既有决策记录（避免重新讨论）

- Phase 3 全量授权 v0.3 已于 2026-10-09 达成，Phase 3 尾分支不受本清单起草阻塞。
- Phase 2 交付归档 S-2=方案 b + S-1=方案 B 已在 Phase 2 清单 §8 归档记录留痕。
- P4-T1 迁移已提前并入 P3-T3（`V28__tenant_scope_sys_config_and_invite.sql`，C-4=方案 A），Phase 4 起草时只需处理读取顺序与缓存 key 抽象。
- P3-T7 `93dcbb9` 已交付（OIDC 分支），P3-T7 邀请式设密回访子切片 `9aa358b` 已交付（落地页预检查 + 平台重签 + 前端四态引导）；剩余 **IdP 首登绑定 RBAC/租户归属映射**延后至本清单 C-3 承接。

---

**清单版本 v0.1 · 2026-10-09 起草 · 待会审 · 不代表 Phase 4 已授权开工**
