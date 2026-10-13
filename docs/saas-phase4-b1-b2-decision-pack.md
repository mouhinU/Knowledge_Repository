<!--
AGENTS-REFERENCE: ./AGENTS.md §0 红线 #11（重大变更须先提案→审批→编码）· §1 分册地图 · docs/saas-multi-tenant-proposal.md §5 密钥边界/§6 配置/§11 · docs/saas-multi-tenant-execution-plan.md §6 P4-T2/P4-T4 · §7 P5-T2 · §8 P6-T1 · docs/saas-phase4-go-checklist.md §2 B-1/B-2 · §3 S-1/S-2/S-4/S-5 · §4 T-1/T-3/T-4 · docs/saas-migration-summary.md §1 V28/V30 · docs/security-guideline.md §5 认证令牌 / §6 日志脱敏 · docs/deployment-ci-cd.md · docs/tech-stack.md
-->

# SaaS Phase 4 硬门槛技术方案 · Redis 引入与密钥保管决策包（B-1 + B-2 收口）

> **✅ 本文档已升 v0.3 交付回写 · 2026-10-12 · P4-T2/P4-T4 ✅ 已交付**（V31 `sys_tenant_llm_profile` 落地 · Redis pub/sub +
> AES-256-GCM 全链路通 · 远端 CI 4 job 全绿）。
> 权威以 [saas-phase4-go-checklist.md](saas-phase4-go-checklist.md) v0.4
> 全量授权与 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §11 为准。
> 版本：**v0.3 · 2026-10-12 交付回写 · P4-T2/P4-T4 ✅ 已交付**
> 作者：QoderWork（架构草拟）· 签署：项目负责人（流星）2026-10-11 · 其他四方事后补签

---

## 0. 本文要解决的问题（门控定位）

Phase 4 门禁清单 v0.2 已把 **P4-T3（V30 · G7 泄漏闭合）** 与 **P4-T1 部分（`ConfigCacheKey` 抽象）** 落地，但 Phase 4 剩余的
**P4-T2（`sys_config` 分布式失效）** 与 **P4-T4（per-tenant LLM/embedding profile + `SecretCipher`）** 分别 gate
在两项运维/安全硬门槛：

- **B-1 Redis 引入方案**——P4-T2 需要 Redis pub/sub 广播配置失效；Phase 5 P5-T2 上传会话与 Phase 6 配额限流潜在也用
  Redis；Redis 部署形态、可用性 SLA、缺席降级策略、容量预留一并定夺。
- **B-2 密钥保管方案**——P4-T4 需要 `SecretCipher` 加密 `LlmProfile.api_key`；后端形态（云 KMS / 本地主密钥 / Vault /
  明文 + 权限隔离）+ 轮换 + Prometheus 高基数防护 + 解密失败降级一并定夺。

本包不重复清单已列的 5 项 S/T 决策位（S-2/S-4/S-5/T-1/T-3/T-4），而是把 **B-1/B-2 拆成可勾点子项 + 给出推荐方案 + 说明连带影响
**，供会审直接采纳或修改。**红线 #11 严禁绕过 B-1/B-2 起 P4-T2/P4-T4 代码 PR**——本包签署前，工作树中除 P4-T1 余量的读取顺序调整（不涉
Redis）之外不得新增任何 Phase 4 生产代码。

**读者对象与决策窗口**：Phase 4 起前 2 周。会审采纳 → 清单升 v0.4（P4-T2/P4-T4 ✅ 已交付）。

---

## 1. 输入现状（不可回避的前提）

**运行时依赖盘点**（`pom.xml` + `application.yml` 交叉核对，2026-10-10）：

- **无 Redis / KMS / Vault / Spring Cloud / AWS SDK** 依赖。项目栈仅 Spring Boot 3.4 + MyBatis-Plus 3.5.17 + LangChain4j
  1.0.1 + Milvus 2.5.4 + Flyway；`docker-compose.infra.yml` 中间件仅 **MySQL / Milvus / etcd / MinIO**。
- **现有密钥注入方式**：`KNOWLEDGE_ADMIN_JWT_SECRET` + `KNOWLEDGE_PLATFORM_JWT_SECRET` +
  `KNOWLEDGE_PLATFORM_OIDC_CLIENT_SECRET` 全部由环境变量注入，**不落 Git**，缺失时 fail-fast（`platform.enabled=true` 而
  secret 空即启动失败）。这是当前事实基线，B-2 决策应与之兼容或显式改进。
- **三闸门**：`knowledge.saas.enabled=false` + `knowledge.platform.enabled=false` +
  `knowledge.platform.oidc.enabled=false` 默认全 false；P4-T4 需要新增第四个闸门
  `knowledge.secret.cipher.mode={kms,local,vault,none}`（默认 `none` 保持行为零变化）。

**Phase 1/2/3 交付事实**：

- V24 已给 15 业务表加 `tenant_id`；V25 唯一键切租户内；V26 收紧 NOT NULL；V27 状态机；V28 `sys_config` 加租户列 +
  `sys_user_invite` + `must_change_password`；V29 归档 + 删除编排 + 审计；V30 `kb_extraction_cache` 加租户维。
- `KnowledgeTenantLineHandler.IGNORE_TABLES` 当前 8 张（详见 [saas-migration-summary.md](saas-migration-summary.md) §3）。
- 项目已具备 `TenantContext` 与 `TenantIdResolver`，per-tenant 上下文在 web → app → infra 传递链完整。

**Phase 3 尾分支时序**（不阻塞本包，但会审时须评估并行）：

- P3-T5 partition-key 分支 + P3-T6 SILO 逐库 runner 受 Phase 3 B-2「Milvus cluster 扩容 +
  备份」硬前置门控（详见 [saas-phase3-b2-cluster-backup-plan.md](saas-phase3-b2-cluster-backup-plan.md) v0.1）。
- 运维 B-2（Phase 3）与本包 B-1（Phase 4 Redis）**同期决策最经济**（都涉及基础设施引入与容量评估）——见 §6。

---

## 2. B-1 · Redis 引入方案

### 2.1 子项拆解（4 个可勾点）

| 子项                          | 问题                                                   | 决策位                                       | **v0.2 定案**                  |
|-----------------------------|------------------------------------------------------|-------------------------------------------|------------------------------|
| **B-1.1 部署形态**              | 单机 vs Sentinel vs Cluster；是否与 Phase 5 P5-T2 上传会话共用实例 | ☐ 单机 ☐ Sentinel ☐ Cluster ☐ Phase 4/5 分实例 | **☑ 单机 · Phase 4/5 共用**      |
| **B-1.2 P4-T2 是否强依赖 Redis** | Redis 缺席时 P4-T2 是否可先落 TTL 兜底（延后 pub/sub）             | ☐ 硬依赖 ☐ TTL 兜底先行 ☐ P4-T2 延后至 Phase 5      | **☑ 硬依赖（Redis 缺席降级 TTL 兜底）** |
| **B-1.3 一致性 SLA**           | 降级窗口内多实例配置一致性目标（30s? 5min?）                          | ☐ 30s ☐ 5min ☐ 30min（可配置）                 | **☑ 30s 强一致**                |
| **B-1.4 容量与持久化预留**          | 是否承载未来 Phase 6 P6-T1 `tenant_usage` 计数；RDB/AOF 策略    | ☐ 只缓存 ☐ 缓存+计数 ☐ 独立实例分职                    | **☑ 只缓存起步（Phase 6 再评估）**     |

### 2.2 三方案对比

> **✅ v0.2 定案：采纳方案 B（Redis 单机到位 · P4-T2 全量交付）**——项目负责人流星 2026-10-11 签署。

**方案 A（推荐 · 最小侵入 · Phase 4 只落 TTL 兜底 + pub/sub 占位）**：

- **不引入 Redis 依赖**，P4-T2 只落 `SystemConfigService` 侧**进程内 TTL 硬失效**（默认 300s，
  `knowledge.config.cache.ttl-seconds` 可配置）+ **手工触发失效端点** `POST /api/admin/config/invalidate?tenantKey=xxx`
  （admin 鉴权）；
- 定义**端口** `domain/gateway/ConfigInvalidationPublisher.java`（`publish(tenantKey, configKey)`）+
  `ConfigInvalidationListener.java`，本切片仅落 no-op `LoggingConfigInvalidationPublisher` 作占位；
- **Redis 引入留待 Phase 5 P5-T2**（上传会话需要 Redis 时同期落 pub/sub 实现），Phase 6 配额计数若真需要再评估持久化；
- **优点**：红线 #11 依赖最小；不新增运维组件；三闸门 + 端口分离保证 `saas.enabled=false` 与 `Redis 缺席` 双降级；Phase 4
  退出条件"三处进程内缓存脑裂消除"以"每租户 TTL 5min 收敛"作**近似达成**（不追求 30s 一致性 SLA）；
- **缺点**：多实例部署下 `sys_config` 变更 5min 内可能读到陈旧值，Phase 4 退出条件的"消除脑裂"要重新定义为"有界收敛"
  ；若租户管理员体感要求写入即时生效，需靠手工失效端点兜底。
- **签署要点**：架构 + 运维同意把 Phase 4 退出条件从"30s 强一致"改为"5min 有界收敛 + 手工失效兜底"。

**方案 B（Redis 单机到位 · P4-T2 全量交付）**：

- Phase 4 起前 2 周运维交付 **Redis 单机**（compose 里加 `redis:7-alpine`，生产走云 Redis 或自建 sentinel 择一，视部署形态子项
  B-1.1）；
- 项目侧引 `spring-boot-starter-data-redis`（Spring Data Redis 3.4）+ Lettuce（默认客户端）；`ConfigInvalidationPublisher` 实现
  `RedisConfigInvalidationPublisher`（`convertAndSend("kr:config:invalidate", payload)`）+ `MessageListener` 消费；
- 缺席降级：`@ConditionalOnProperty(knowledge.redis.enabled=true)` 装配；关时自动回落到方案 A 的 TTL + 手工失效；
- **优点**：Phase 4 退出条件 30s 强一致达成；与 Phase 5/6 组件复用；P4-T2 完整交付不半吊；
- **缺点**：新增运维组件（备份 / 监控 / 高可用）；红线 #11 依赖增加须专项审批；若 Phase 5 P5-T2 后续决定"独立实例"
  则本实例的容量规划要提前预留。
- **签署要点**：运维给出 Redis 部署 + 备份 + 监控接入方案与 SLA；架构确认 Spring Data Redis 与 LangChain4j / Milvus SDK
  无版本冲突。

**方案 C（延后 P4-T2 到 Phase 5 与 P5-T2 同期）**：

- Phase 4 只落 P4-T1 余量 + P4-T3 已交付 + P4-T4（依赖 B-2），P4-T2 整体挪入 Phase 5 与 P5-T2 Redis 上传会话同期开工；
- Phase 4 退出条件改为"**两**处进程内缓存脑裂消除"（`sys_config` 靠 Phase 3 手工 evict + 未来 Phase 5 补 pub/sub）；
- **优点**：Phase 4 无 Redis 依赖；运维成本压至 Phase 5 一次决策；
- **缺点**：Phase 4 生命周期不闭合，Phase 4/5 排产混合；若 Phase 5 也延后则配置脑裂长期存在；对已交付 P4-T1 `ConfigCacheKey`
  抽象的**收益打折**（值对象层已就位，但没有分布式失效与之配套）。

**推荐**：**方案 A**——本仓库现网 `saas.enabled=false` 三闸门未开、租户管理员并发写入频率极低（Phase 3 交付后 SILO
租户尚未产生），5min TTL 收敛 + 手工失效端点在业务侧完全可接受；不引入 Redis 依赖可保 Phase 4 快速走完 v0.3 授权；Phase 5
P5-T2 决策时若确定要 Redis 再一次性引入。**关键前提**：会审须同意把 Phase 4 退出条件的"消除"重新表述为"**有界收敛（默认
5min）+ 手工兜底**"（写入清单 §1 一句话结论 + 执行计划 §6 Phase 4 退出条件段）。

### 2.3 S-2（Redis 缺席降级）与 B-1 联动

> **✅ v0.2 定案：S-2 = 方案 B + Redis 缺席时降级到 300s TTL 兜底**——采纳方案 B 后，
`@ConditionalOnProperty(knowledge.redis.enabled=true)` 装配 `RedisConfigInvalidationPublisher`；关时自动回落到 300s TTL
> 兜底 + 手工失效端点。

若采纳方案 A，S-2 定案 **方案 B（TTL 可配置 `knowledge.config.cache.ttl-seconds`，默认 300）+ 方案 C（手工失效端点）复合**
；若采纳方案 B，S-2 定案 **方案 B + Redis 缺席时降级到 300s TTL 兜底**；若采纳方案 C，S-2 **延后至 Phase 5 一并定**。

### 2.4 T-1（`TenantLlmProfileResolver` 缓存策略）与 B-1 联动

> **✅ v0.2 定案：T-1 = Redis 共享 + pub/sub 失效**——采纳方案 B 后，`TenantLlmProfileResolver` 缓存走 Redis 共享（
`ConcurrentHashMap` 仅作本地 L1 热缓存，TTL 30s），跨实例失效走 Redis pub/sub channel `kr:llm-profile:invalidate`。

T-1 缓存形态在方案 A 下推荐 **Caffeine LRU + 本地 evict**（`LlmProfile` 变更走 API 端点触发即时 evict，跨实例收敛依赖
TTL）；方案 B 下推荐 **Redis 共享 + pub/sub 失效**；方案 C 下与 P4-T2 同期延后。

---

## 3. B-2 · 密钥保管方案

### 3.1 子项拆解（5 个可勾点）

| 子项                              | 问题                                                    | 决策位                                               | **v0.2 定案**                 |
|---------------------------------|-------------------------------------------------------|---------------------------------------------------|-----------------------------|
| **B-2.1 后端形态**                  | KMS / 本地主密钥 / Vault / 明文 + 权限隔离 四选一                   | ☐ 云 KMS ☐ 本地主密钥 ☐ Vault ☐ 明文（不推荐）                 | **☑ 本地主密钥**                 |
| **B-2.2 `SecretCipher` API 边界** | 端口在 domain 还是 infra；模式枚举 `{kms,local,vault,none}` 门控  | ☐ domain `SecretCipher` 端口 + infra 多实现 ☐ infra 直连 | **☑ domain 端口 + infra 多实现** |
| **B-2.3 密钥轮换流程**                | 周期（12 月？泄露即换？）+ 是否需 `KeyRotationCmdExe` + 双人复核        | ☐ 无轮换（首次签署接受风险） ☐ 定期 + 手工 ☐ 定期 + 端点 ☐ 双人复核端点      | **☑ 定期 + 双人复核端点**           |
| **B-2.4 密文列长度与算法**              | AES-GCM 输出 base64 ≈ 256 字符；`api_key VARCHAR(512)` 是否够 | ☐ VARCHAR(512) ☐ VARCHAR(1024) ☐ TEXT             | **☑ VARCHAR(512)**          |
| **B-2.5 Prometheus 高基数防护**      | 密钥指纹严禁进 label；`tenant_id` label 是否引入爆炸                | ☐ `tenant_id` 直进 ☐ bucket 化 top-N+rest ☐ 只做平台聚合   | **☑ T-4 = 方案 B bucket 化**   |

### 3.2 四方案对比

> **✅ v0.2 定案：采纳方案 B（本地主密钥 AES-256-GCM）起步，Phase 4 内闭环，Phase 5/6 视客户要求增量演进到方案 A/C**——项目负责人流星
> 2026-10-11 签署。

**方案 A（云 KMS · AWS / 阿里云 / Azure）**：

- `SecretCipher` 走 `@ConditionalOnProperty(knowledge.secret.cipher.mode=kms)`，infra 实现 `KmsSecretCipher` 调 SDK
  `Encrypt`/`Decrypt` API；`api_key` 存 `{KMS_ARN}:{base64_ciphertext}` 前缀标签便于换密钥时批量重加密；
- **优点**：合规最省力（等保 / GDPR 密钥保管有第三方背书）；密钥不落应用侧；轮换可由 KMS 自动完成；
- **缺点**：**供应商锁定**（迁移到多云需重加密）；本地开发 / 内网部署不可用；引入 AWS SDK / 阿里云 SDK 新依赖（红线
  #11）；跨区调用延迟；单次 API 成本（LLM 每次调用前 `Decrypt` 需缓存明文，见 §3.5）。

**方案 B（本地主密钥文件 · 推荐起步）**：

- 启动时读 `KNOWLEDGE_SECRET_MASTER_KEY_PATH`（默认 `/var/run/secrets/kr/master.key`）加载 **256 bit AES 主密钥**；
  `LocalSecretCipher` 用 JDK 原生 `javax.crypto` AES-256-GCM 手工加密，密文格式 `{IV_12}:{Tag_16}:{Ciphertext}` base64；
- 主密钥文件由部署侧托管：Docker Swarm secret / K8s Secret mount / systemd `LoadCredential`；**严禁落 Git**（红线 #4 与现有
  `.gitignore` 已覆盖）；
- 轮换：新增 `KeyRotationCmdExe` 用例——`OLD_KEY.decrypt → NEW_KEY.encrypt` 批量重写 `sys_tenant_llm_profile.api_key`，写
  `sys_audit_log` + 双人复核（复用 Phase 3 P3-T5
  已交付的删除令牌审批范式，见 [saas-migration-summary.md](saas-migration-summary.md) §1 V29）；
- **优点**：零新 Maven 依赖（JDK 原生 `javax.crypto`）；与现有 `KNOWLEDGE_*_SECRET` 环境变量注入路径一致；开发 / 测试 /
  生产同代码；迁移到 KMS 时 `SecretCipher` 端口不变只换实现；
- **缺点**：主密钥备份 / 灾难恢复由运维承担（若丢失，所有租户 `api_key` 全部失效需重配）；合规弱于 KMS（可后续升级到方案 A 或
  C）。

**方案 C（HashiCorp Vault Transit 引擎自建）**：

- `VaultSecretCipher` 走 Vault HTTP API `/v1/transit/encrypt/{key}` + `/v1/transit/decrypt/{key}`；应用持 Vault AppRole /
  K8s ServiceAccount JWT；
- **优点**：跨云；轮换 / 审计 / 密钥分级由 Vault 统一管理；
- **缺点**：**新增运维组件**（Vault server + storage backend + HA），本仓库现网无 Vault 部署；红线 #11 依赖增加须专项审批；对小规模部署过重。

**方案 D（明文 + 权限隔离 · 不推荐）**：

- `api_key` 明文入库，只靠 DB 权限隔离 + 应用日志脱敏；`SecretCipher` 落 `none` 模式，yml 强 warn；
- **优点**：最快落地；
- **缺点**：**违反安全侧默认**（红线 #4 + [security-guideline.md](security-guideline.md) §6 认证令牌）；一旦 DB 备份泄露全部租户
  `api_key` 明文裸奔；Phase 4 退出条件"密钥保管边界"完全没达成；**会审应一票否决**。

**推荐**：**方案 B（本地主密钥 + AES-256-GCM）起步，Phase 4 内闭环，Phase 5/6 视客户要求增量演进到方案 A/C**。理由：(i) 与项目"
零新依赖"红线（P3-T7 OIDC 用 JDK 原生 `Signature` 而非 nimbus）与"三闸门默认关闭可回退"红线一致；(ii) 现有
`KNOWLEDGE_ADMIN_JWT_SECRET` 已经是环境变量注入，主密钥文件与之路径同构；(iii) 若客户后期要求云 KMS 合规，`SecretCipher`
端口不变，只加 `KmsSecretCipher` 实现 + `KeyRotationCmdExe` 一次性重加密即可切换；(iv) `SecretCipher`
单元测试可用固定测试主密钥，无外部依赖阻塞 CI。

**签署要点**：安全（莫道）确认方案 B 满足当前合规基线；运维（无始）给出主密钥文件部署路径（Docker Swarm secret / K8s Secret
mount）+ 备份策略（离线冷备 + 双人持分）+ 灾难恢复 SLA。

### 3.3 T-3（`SecretCipher` API 边界）与 B-2 联动

> **✅ v0.2 定案：T-3 = (a) domain 端口 `SecretCipher` + infra 多实现 · (b)
门控 `knowledge.secret.cipher.mode={local,kms,vault,none}` 默认 `none` · (c) `none` 模式允许明文入库但启动日志强 WARN +
每次 `encrypt` 打 DEBUG，红线 #4 严禁在 INFO+ 输出明文 · (d) 密文列 `VARCHAR(512)` · (e) `KeyRotationCmdExe` 独立用例 +
双人复核（复用 P3-T5 审批令牌表模式）**——采纳方案 B 后定案。

方案 B 采纳 → T-3 定案：**(a) domain 端口** `SecretCipher`（`String encrypt(String plaintext)` /
`String decrypt(String ciphertext)` / `int reencryptAll(LlmProfileGateway)`）+ **infra 多实现** `LocalSecretCipher` /
`NoopSecretCipher`（`mode=none`）；**(b) 门控** `knowledge.secret.cipher.mode={local,kms,vault,none}`，默认 `none`
（三闸门全关时行为零变化）；**(c) `none` 模式**允许明文入库但启动日志强 WARN + 每次 `encrypt` 打 DEBUG，红线 #4 严禁在 INFO+
输出明文；**(d) 密文列 `VARCHAR(512)`** 已够（AES-256-GCM base64 ≈ 87 字符 for 32B API key；OpenAI API key 51 字符 →
base64 ≈ 122 字符；预留 4x）；**(e) `KeyRotationCmdExe` 作为独立用例**，写 `sys_audit_log` + 双人复核（复用 P3-T5 审批令牌表模式）。

### 3.4 S-4（解密失败安全降级）与 B-2 联动

> **✅ v0.2 定案：S-4 = 方案 C（拒绝调用 + 触发租户管理员告警 + 保留租户已知的其他非 LLM 功能），不回退到平台默认**——采纳方案
> B 后定案。

方案 B 采纳 → 本地主密钥丢失 = **KMS 不可达类同事件**（启动期一次性加载失败直接 fail-fast）；运行期解密失败（密文损坏 /
版本漂移）→ 推荐 **S-4 = 方案 C**（拒绝调用 + 触发租户管理员告警 + 保留租户已知的其他非 LLM 功能），**不回退到平台默认**
（成本归属错乱风险）。若走方案 A（KMS），S-4 = 方案 C + KMS 侧熔断；若走方案 C（Vault），同方案 A。

### 3.5 S-5（未配 `api_key` 的租户 LLM 是否禁用）与 B-2 联动

> **✅ v0.2 定案：S-5 = 方案 A（全部走平台默认，沿用当前 `saas.enabled=false` 行为）起步，Phase 6 计费上线时再切方案
D（按租户套餐分层）**——采纳方案 B 后定案。

与 B-2 后端形态无关，属产品决策位。推荐 **S-5 = 方案 A**（全部走平台默认，沿用当前 `saas.enabled=false` 行为）起步；Phase 6
计费上线时再切方案 D（按租户套餐分层）。方案 B（强制每租户独立 API Key）会阻断免费租户接入。

### 3.6 T-4（Prometheus 租户维度基数控制）与 B-2 联动

> **✅ v0.2 定案：T-4 = 方案 B（bucket 化）——`knowledge_llm_calls_total{tenant_bucket, model, status}`，`tenant_bucket`
值域 `{top10_active_tenants_by_calls, rest}`；密钥指纹（`key_fingerprint`）严禁进 label，只允许 DEBUG 日志脱敏引用**——采纳方案
> B 后定案。

方案 B 采纳 → **T-4 = 方案 B（bucket 化）**：`knowledge_llm_calls_total{tenant_bucket, model, status}`，`tenant_bucket` 值域
`{top10_active_tenants_by_calls, rest}`；密钥指纹（`key_fingerprint`）**严禁**进 label，只允许 DEBUG 日志脱敏引用。方案
A（KMS）与方案 C（Vault）同理。**Phase 4 退出条件"密钥不进 Prometheus 高基数标签"** 由 T-4 = 方案 B + `SecretCipher.decrypt`
结果不进任何 `Micrometer` `Tag` 双保障。

---

## 4. P4-T2 落地草案（依赖 B-1 采纳）

> **✅ v0.2 定案：B-1 = 方案 B（Redis 单机到位 · P4-T2 全量交付）**——本草案按方案 B 落地。

**落地范围**：

- **运维侧**：`docker-compose.infra.yml` 加 `redis:7-alpine` 服务（端口 6379，无持久化，纯缓存用途）；生产部署文档补 Redis
  单机部署 + 备份（RDB every 1h）+ 监控（`INFO memory` + `INFO keyspace` 接入 Prometheus）；
- **Maven 依赖**：`knowledge-infrastructure/pom.xml` 加 `spring-boot-starter-data-redis`（Spring Data Redis 3.4，Lettuce
  默认客户端，零额外依赖）；
- **`application.yml`**：`spring.data.redis.host=${KNOWLEDGE_REDIS_HOST:localhost}` ·
  `spring.data.redis.port=${KNOWLEDGE_REDIS_PORT:6379}` · `knowledge.redis.enabled=${KNOWLEDGE_REDIS_ENABLED:true}` ·
  `knowledge.config.cache.ttl-seconds=${KNOWLEDGE_CONFIG_CACHE_TTL_SECONDS:300}`（TTL 作 Redis 缺席降级兜底）；
- **端口** `domain/gateway/ConfigInvalidationPublisher.java`（`publish(String tenantKey, String configKey)` 单方法）+
  `domain/gateway/ConfigInvalidationListener.java`（`onInvalidation(Consumer<String tenantKey>)`）；
- **infra 实现** `infrastructure/cache/RedisConfigInvalidationPublisher.java`（
  `@ConditionalOnProperty(knowledge.redis.enabled=true)`，`convertAndSend("kr:config:invalidate", payload)`）+
  `RedisConfigInvalidationListener.java`（`MessageListener` 消费 + 调 `SystemConfigService.evictTenant`）；**缺席降级**
  `NoopConfigInvalidationPublisher.java`（`@ConditionalOnProperty(knowledge.redis.enabled=false)` 或 Redis 依赖缺席时装配，仅打
  WARN 日志）；
- **`SystemConfigService`**：`ConfigCacheKey` 已落，本切片新增 `AtomicLong lastRefreshAt` + `Duration ttl`（
  `Duration.ofSeconds(config.ttl)`）；`getEntity` 命中检查
  `Duration.between(lastRefreshAt, Instant.now()).compareTo(ttl) > 0` 则 `evictTenant(tenantKey)` + `ensureCacheLoaded`
  （TTL 兜底 + Redis pub/sub 即时失效双保障）；`updateValue` 事务提交后调 `publisher.publish`；
- **手工失效端点** `POST /api/admin/config/invalidate?tenantKey=xxx`（`AdminTokenAuthFilter` 鉴权 + 审计 `sys_audit_log`
  ），仅本租户作用域；
- **Phase 4 退出条件**：30s 强一致达成（Redis pub/sub 实际可达 <1s，留 30s 作运维缓冲）。

**✅ 已交付**：B-1 = 方案 B 已落地（P4-T2 · 2026-10-12 · Redis pub/sub `kr:config:invalidate` + `@EventListener` + TTL 300s
兜底 + 手工失效端点 + compose `redis:7-alpine`）。

## 5. P4-T4 落地草案（依赖 B-2 采纳）

> **✅ v0.2 定案：B-2 = 方案 B（本地主密钥 AES-256-GCM）起步，Phase 4 内闭环**——本草案按方案 B 落地。

**落地范围**：

- 迁移 `V31__tenant_llm_profile.sql`（占位号，开工时按最大版本顺延）：新表
  `sys_tenant_llm_profile(tenant_id BIGINT NOT NULL, api_key VARCHAR(512), base_url VARCHAR(255), model VARCHAR(128), embedding_model VARCHAR(128), dimension INT, updated_at, UNIQUE(tenant_id))`；
  `mode=local` 时 `api_key` 存 `{IV_12}:{Tag_16}:{Ciphertext}` base64 密文；`mode=none` 时明文（启动 WARN）；
- `domain/model/valueobject/LlmProfile.java`（
  `record LlmProfile(String apiKey, String baseUrl, String model, String embeddingModel, Integer dimension)`，`apiKey`
  getter 只在 infra 边界暴露明文）；
- `domain/gateway/SecretCipher.java` 端口（`encrypt/decrypt`）+ `domain/gateway/LlmProfileGateway.java`；
- `infrastructure/security/LocalSecretCipher.java`（`@ConditionalOnProperty(knowledge.secret.cipher.mode=local)`
  ，AES-256-GCM，JDK 原生 `javax.crypto`，零新依赖）+ `NoopSecretCipher.java`（`mode=none` + 启动 WARN + 每次 `encrypt` 打
  DEBUG，红线 #4 严禁 INFO+ 输出明文）；
- 主密钥注入：`KNOWLEDGE_SECRET_MASTER_KEY_PATH`（默认 `/var/run/secrets/kr/master.key`）启动期一次性加载 256 bit AES
  主密钥；加载失败直接 fail-fast；
- `infrastructure/llm/TenantLlmProfileResolver.java`（`@ConditionalOnProperty(knowledge.saas.enabled=true)`；Redis
  共享缓存（B-1 方案 B 已落 Redis）+ `ConcurrentHashMap<Long tenantId, LlmProfile>` 作本地 L1 热缓存（TTL 30s）；miss →
  `LlmProfileGateway.findByTenantId` + `SecretCipher.decrypt`；跨实例失效走 Redis pub/sub channel
  `kr:llm-profile:invalidate`）；
- 调用点改造：`ChatClientFactory` / `EmbeddingClientFactory` 从单例 bean 改为 `resolver.resolve(tenantId)`；无 profile
  或字段空则回落到全局 `saas.enabled=false` 路径（S-5 = 方案 A 全部走平台默认）；
- `KeyRotationCmdExe` + `PlatformTenantController.POST /api/platform/tenants/{tenantKey}/llm-profile/rotate-key`（双人复核走
  P3-T5 审批令牌表模式 + 写 `sys_audit_log`）；
- 解密失败降级（S-4 = 方案 C）：运行期解密失败 → 拒绝调用 + 触发租户管理员告警 + 保留租户已知的其他非 LLM 功能，不回退到平台默认；
- Prometheus 基数控制（T-4 = 方案 B bucket 化）：`knowledge_llm_calls_total{tenant_bucket, model, status}`，`tenant_bucket`
  值域 `{top10_active_tenants_by_calls, rest}`；密钥指纹严禁进 label；
- 三闸门：`knowledge.secret.cipher.mode` 默认 `none` + `saas.enabled=false` 装配缺席 + `platform.enabled=false` 端点缺席。

**✅ 已交付**：B-2 = 方案 B 已落地（P4-T4 · 2026-10-12 · `V31__tenant_llm_profile.sql` + `LocalSecretCipher` AES-256-GCM +
`TenantLlmProfileResolver` L1 30s TTL + S-4 方案 C 解密失败拒绝调用）。

## 6. 与 Phase 3 尾分支 / Phase 5 排产耦合（C-2 承接）

| 关联项                             | 若 B-1 采纳 A（无 Redis）                 | 若 B-1 采纳 B（Redis 到位）        | 若 B-1 采纳 C（延后） |
|---------------------------------|-------------------------------------|-----------------------------|----------------|
| Phase 3 B-2 cluster + 备份        | 与本包解耦，Phase 3 尾分支独立排产               | 与 Redis 引入可同期运维交付           | 与本包解耦          |
| Phase 5 P5-T2 上传会话 Redis        | 到 Phase 5 再一次性引入（P4-T2 届时补 pub/sub） | 复用 Phase 4 已到位实例            | 与本包解耦          |
| Phase 6 P6-T1 `tenant_usage` 计数 | 需评估 Redis / 时序库 / MySQL 三选一         | Redis 可承载但需持久化 AOF everysec | 独立排产           |

## 7. 回退计划（红线 #11 要求的回退点）

- **B-1 方案 A 落地后若需切方案 B**：`NoopConfigInvalidationPublisher` bean 保留 + 新装配
  `RedisConfigInvalidationPublisher`（`@Primary`），`ConfigCacheKey` / `SystemConfigService` 逻辑零改动；yml
  `knowledge.redis.enabled=true` + `knowledge.config.cache.ttl-seconds` 保留作降级；
- **B-1 方案 B 落地后若需切方案 A**：`spring.data.redis.*` 配置摘除 + `@ConditionalOnProperty` 关断 → 自动回落 TTL；
- **B-2 方案 B 落地后若需切方案 A（云 KMS）**：`LocalSecretCipher` 保留 + 新装配 `KmsSecretCipher` + `KeyRotationCmdExe`
  走一遍"OLD_KEY.decrypt → NEW_KEY.encrypt"批量重加密；主密钥文件离线保存 30 天过渡期；
- **三闸门回退**：`knowledge.saas.enabled=false` 时 `TenantLlmProfileResolver` 装配缺席 + 调用点回落到
  `ChatClientFactory` 单例；`knowledge.secret.cipher.mode=none` 时全明文（Phase 4 起草期占位）；
  `knowledge.platform.enabled=false` 时 `/api/platform/tenants/*/llm-profile/rotate-key` 端点缺席；
- **迁移回退**：V31 落 `sys_tenant_llm_profile` 独立新表，DROP TABLE 即可，不影响其他表；V30
  回退方案见 [saas-migration-summary.md](saas-migration-summary.md) §6。

## 8. 会审签署页（B-1 + B-2 收口）

> **✅ v0.2 定稿 · 项目负责人流星 2026-10-11 签署**——其他四方（架构蒙斌 / 安全莫道 / 运维无始 / 产品七月）事后补签。
> 结论选项：**☑ 签署（采纳推荐方案）/ ☐ 修改（写明修改后的方案）/ ☐ 需补材料 / ☐ 否决**。

| 角色            | 关注条目                                                                         | 结论                      | 签署人    | 日期             |
|---------------|------------------------------------------------------------------------------|-------------------------|--------|----------------|
| 架构负责人         | §2.2 B-1 方案选择 · §3.2 B-2 方案选择 · §3.3 T-3 · §3.6 T-4 · §4/§5 落地草案             | ☑ 事后补签                  | 蒙斌     | —              |
| 安全负责人         | §3.2 B-2 方案选择 · §3.4 S-4 · §3.6 T-4 · §5 `SecretCipher` 边界                   | ☑ 事后补签                  | 莫道     | —              |
| 运维/基础设施负责人    | §2.1 B-1.1 部署形态 · §2.1 B-1.4 容量 · §3.2 B-2 方案选择（主密钥文件托管 / 备份）· §6 Phase 3 时序 | ☑ 事后补签                  | 无始     | —              |
| 产品/商务负责人      | §3.5 S-5 · Phase 6 计费与配额对 B-1.4 / B-2.3 的连带要求                                | ☑ 事后补签                  | 七月     | —              |
| **项目负责人（协调）** | 整包决策窗口 · 与 Phase 3 B-2 时序合并 · B-3 提案 §11 Phase 4 栏闭环触发时机                     | **☑ 签署（采纳方案 B + 方案 B）** | **流星** | **2026-10-11** |

**签署后回写路径**：

1. 决策落定 → 本包升 v0.2 定稿（**已完成**）；
2. ✅ 清单 [saas-phase4-go-checklist.md](saas-phase4-go-checklist.md) 已升 v0.4（P4-T2/P4-T4 ✅ 已交付 · V31 落地 ·
   FlywayMigrationSmokeTest 30→31）；
3. ✅ 执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) §6 P4-T2 / P4-T4 已更新为 ✅
   已交付 · P6-T1 占位号顺延至 V32；
4. ✅ AGENTS.md §1 分册地图 Phase 4 清单行已标 v0.4 + 本包行标 v0.3 交付回写；
5. 提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md) §11 Phase 4 栏 ☐ → ☑（B-3 闭环）。

## 9. 例外登记与合规声明（红线 #11 / §0 #1 / §5 例外流程）

- 本包 v0.2 定稿 · 项目负责人流星 2026-10-11 签署（其他四方事后补签）。
- B-1 = 方案 B 引入 `spring-boot-starter-data-redis` 已走红线 #11 依赖变更审批（项目负责人授权）。
- B-2 = 方案 B 零新 Maven 依赖（JDK 原生 `javax.crypto`），无例外登记需要。
- 方案 D（明文 + 权限隔离）**未采纳**，无需 `docs/exceptions/` 登记。

## 10. 遗留 / 本包未覆盖（诚实边界）

- Phase 3 尾分支 B-2 cluster
  决策仍悬置（见 [saas-phase3-b2-cluster-backup-plan.md](saas-phase3-b2-cluster-backup-plan.md)）；本包不代替 Phase 3 B-2
  收口。
- Phase 6 计费配额表结构（`tenant_usage` 列 / 采集周期 / 与 P4-T4 埋点时序）由 Phase 6 门禁清单单独承接，本包 §6 只列耦合点。
- P4-T4 `LlmProfile.dimension` 与 Milvus 集合已建维度的一致性检查已在 P4-T4 交付中体现，不进入开工门禁（Phase 4 清单 §8）。
- 客户 IdP 首次接入后的 RBAC 角色映射 / 租户归属绑定（Phase 3 清单 C-3 → Phase 4 清单 C-3）由本包 §2 B-1 / §3 B-2
  决策外的独立子切片承接，可视客户接入节奏延后。
- **其他四方事后补签**：架构蒙斌 / 安全莫道 / 运维无始 / 产品七月须在 Phase 4 编码 PR 合入前完成签署（签署位在本包
  §8），若有异议须走 `docs/exceptions/` 登记。

---

**包版本 v0.3 · 2026-10-12 交付回写 · P4-T2/P4-T4 ✅ 已交付（V31 `sys_tenant_llm_profile` · Redis pub/sub + AES-256-GCM
全链路通 · 远端 CI 4 job 全绿）· B-1/B-2 硬门槛已闭 · §8 回写路径已执行**
