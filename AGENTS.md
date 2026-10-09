# AGENTS.md — 项目规范导航地图（Index / Map）

> 本文件是 Knowledge_Repository 的 **AI 编码总纲与导航索引**，不再是单本厚手册。
> 全部细则按维度拆分至 `docs/` 下若干分册，**按需查阅**。基线：《Java 开发手册》v1.5.0（华山版）+ **COLA 5.0 架构规范**。
> 技术栈速览：Spring Boot 3.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3 / Tika 3 / POI 5 / H2 + MySQL / Flyway。
> **所有 AI 生成的代码必须严格遵循以下红线与对应分册。**

> 说明：本文使用 **COLA 规范术语**（adapter / app / client / domain / infrastructure）。物理模块：`adapter ≈ knowledge-web`，`app ≈ knowledge-application`，`client ≈ knowledge-client`，`domain ≈ knowledge-domain`，`infrastructure ≈ knowledge-infrastructure`。层职责以 [docs/architecture-decisions.md](docs/architecture-decisions.md) 为准。

---

## 0. 不可协商的核心红线（Always-on，无需翻分册即须遵守）

1. **构造器注入、面向接口**，禁止字段 `@Autowired`。
2. **禁止魔法值**；常量全大写下划线，归类维护。
3. **4 空格缩进**，单行 ≤120 字符，LF 换行，UTF-8。
4. **日志：优先使用 Lombok `@Slf4j`**（自动生成字段 `log`）+ 占位符 `{}`；在不使用 Lombok 或需自定义 logger 名称的特殊场景允许使用 `LoggerFactory`，但需在 PR 描述中说明理由。**严禁**在日志或响应中回显口令、令牌等敏感信息。
5. **SQL 参数一律 `#{}`**，禁止把请求入参直接拼进 SQL；动态列名 / 排序须白名单校验后再拼，禁止裸 `${}`。
6. **DO 不越过 infrastructure**；转换只在层边界：adapter `VO⇄DTO`，app `DTO⇄Entity`，infra `Entity⇄DO`。
7. **domain 层保持纯净**，不依赖 app / adapter / infrastructure；Gateway 接口在 domain、`GatewayImpl` 在 infrastructure。
8. **app 层 Service 只做分发**，用例编排落单一 `CmdExe`/`QryExe`；写库事务在 Executor。含 LLM / 外部 IO 的长耗时用例不得置于数据库事务（transaction）内；建议将可能 >200ms 的外部调用异步化或在事务外处理，并在设计文档中说明处理方式。
9. **前端实时通信优先级**：凡属于“服务端主动推送/进度更新/长连接状态同步”场景，优先使用 **SSE**；只有明确需要双向低延迟、双向消息交互或双工流控时才选 **WebSocket**；**前端轮询**仅作为最后兜底方案，必须说明原因并限制频率。新增通信通道时，需在设计说明中写明选择理由、退化方案与超时/重连策略。
10. **新建或修改 Java 类时的 Javadoc 要求**：公共/库级别类应补齐完整 Javadoc；普通/内部类为推荐。`@author`/`@date` 可选，鼓励以 Git 提交元数据为主。
11. **谨慎变更框架 / JDK 版本**；依赖以 [docs/tech-stack.md](docs/tech-stack.md) 为准。重大变更需提交变更提案（包含影响评估、回退计划、测试覆盖）并获得架构/负责人审批。
12. **方法与函数目标值**：方法名 ≤50 字符；方法体目标 ≤50 行；参数 ≤5（若 >3 建议封装为 `Cmd`/`Query`/参数对象）；优先纯函数（相同输入→相同输出，副作用收敛到 infrastructure 边界）。允许例外，需在 PR 中说明并由 reviewer 批准。详见 [docs/coding-guideline.md](docs/coding-guideline.md) §十。

---

## 1. 分册地图（按维度拆分，按需阅读）

| 分册                                                                         | 覆盖维度                                                                                      | 何时必读                                                | 来源章节                         |
| ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------- | -------------------------------- |
| [docs/tech-stack.md](docs/tech-stack.md)                                     | **技术选型**：JDK/Spring/COLA 基线、依赖版本、五模块布局、本地运行命令、配置切换、版本纪律    | 引入依赖 / 改配置 / 本地编译运行 / 拿不准版本时         | 原引言 + §11 基线注              |
| [docs/architecture-decisions.md](docs/architecture-decisions.md)             | **架构决策**：COLA 五层、依赖方向、对象模型分层、术语映射、Executor/CQRS、DI、组件复用        | 新增/移动任何类、定分层与依赖、命名对象时               | 原第十一章（含 1.4/1.6 交叉）    |
| [docs/coding-guideline.md](docs/coding-guideline.md)                         | **编码规范**：命名、常量、格式、OOP、集合、并发、注释、异常、日志、方法与函数规范、常见反模式 | 写任何 Java 代码时                                      | 原第一~九章 + 第十三章           |
| [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md) | **数据库与迁移**：建表、SQL、H2 方言、Flyway 实践                                             | 建表 / 写迁移 / 写 SQL / Mapper 时                      | 原第十章                         |
| [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md)                 | **RAG 领域规范**：多格式解析、向量化、metadata、考试链路配图                                  | 动文档抽取 / 分块 / 向量 / 出题相关代码时               | 原第十二章（12.1/12.2）          |
| [docs/security-guideline.md](docs/security-guideline.md)                     | **安全注意事项**：注入防护、上传/解析、认证令牌、RBAC+ACL、日志脱敏、高风险改动确认           | 涉及鉴权 / 权限 / 上传 / SQL / 令牌 / 口令 / 对外响应时 | 原 §10.2+§12.1+§12.3 聚合 + 现状 |
| [docs/testing-guideline.md](docs/testing-guideline.md)                       | **测试要求**：JUnit5/Mockito 风格、分层测试、门禁负向用例、Flyway 冒烟、运行验收              | 写/改测试，或提交前自验时                               | 新（从仓库现有测试归纳）         |
| [docs/code-review-checklist.md](docs/code-review-checklist.md)               | **提交前检查清单**：风险分级、工作流、变更模板、31 条自检                                     | 每次生成代码收尾自检                                    | 原末章清单（扩充）               |
| [docs/saas-multi-tenant-proposal.md](docs/saas-multi-tenant-proposal.md)    | **SaaS 化改造 · 变更提案**：混合多租户架构、隔离模型、影响评估、回退计划、审批门                | 涉及"租户/tenant_id/跨租户隔离/平台管理员/配额"决策或改动任何数据出口时 | 新（红线 #11 提案）              |
| [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md) | **SaaS 化改造 · 可执行计划**：Phase 0–6 任务清单、依赖图、每任务锚点与验收 | 提案批准开工后按此排产 PR；每 Phase 结束回读 X-T6 更新规范 | 新（配套提案）                   |
| [docs/saas-phase2-go-checklist.md](docs/saas-phase2-go-checklist.md) | **SaaS Phase 2 门禁清单（生效基线）**：12 项分四类（B 硬阻塞 / S 软阻塞 / T 技术细节 / C 交叉项）+ 五方签署页 + 交付归档记录 | Phase 2 已交付归档；**v0.3 状态：P2-T1~P2-T5 于 2026-09-30 依次合入 `ddbcfb3`/`5c1982d`/`10fc424`/`908aac9`/`c702fce`，退出条件达成**（S-1 方案 B + S-2 方案 b 落地；partition-key/SILO/万级时延阈值/运维 B 类正式签署转 Phase 3 会审）；Phase 3 起前保留为"生效基线"，下游 [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md) 已升 **v0.3（全量授权开工 · 2026-10-09 五方齐签，18 项 B/S/T/C）** | 新（配套提案 §10 R1 + 执行计划 §4） |
| [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md) | **SaaS Phase 3 开工门禁清单**：18 项分四类（B 硬阻塞 3 / S 软阻塞 5 / T 技术细节 6 / C 交叉项 4）+ 五方签署页 | Phase 3（生命周期 + partition-key/SILO 引入，P3-T0~T7）开工前必读；**v0.3 状态：全量授权开工（2026-10-09 五方书面齐签）**——架构蒙斌 / 安全莫道 / 运维·基础设施无始 / 产品·商务七月 / 项目负责人流星；运维 B-1/B-2/B-3 已承接签署（建集合/drop 权限 · 单机 vs **cluster 决策列为 P3-T5 前置** · 内存/cluster 预留随 spike 50k OOM 实证加固）；S/T/C 全部定案：**S-1=C**（手工 placement，SILO 延后 P3-T5）/ **S-2=c**（延后 partition-key 切换并入 P3-T5，现网维持 LangChain4j JSON 谓词）/ **S-3** 门禁锚定现网 JSON/标量路径、partition-key 不设性能收益断言、绝对阈值随 cluster 校准 / **S-4=A**（Archive 30 天 + 临时 token 双人复核）/ **S-5=A**（UUID v4 幂等键）；**T-1** 延后 P3-T5 / **T-2=B**（`MilvusSdkAdapter` 骨架 + LangChain4j 回退 bean，暂不抽 `VectorStorePort`）/ **T-3=A**（`SiloCollectionAdmin` collection-only `kc_{tenantKey}`）/ **T-4=B**（独立 `PlatformTokenAuthFilter`，已交付）/ **T-5=C** / **T-6=C**；**C-1/C-2** 已指派责任人与里程碑 / **C-3=B**（Phase 3 内接企业 IdP OIDC → ⚠️范围扩展，新增任务 **P3-T7**）/ **C-4=A**（提前 P4-T1）。**P3-T1~T4 已交付**（`372994a`/`55a0872`/`73b0626`/`eece61a`），**P3-T5 Archive & Delete 编排子切片已交付**（`ed51f63`，分支 `feat/p3-t5-archive-delete`：归档 + 一次性双人复核删除令牌 + reconciler 步骤级幂等硬删续跑 + 只读 dry-run 预览 + `V29` 迁移，全 reactor `./mvnw test` 绿；**partition-key 切换（S-2=c）/ `MilvusSdkAdapter`（T-2=B）/ `knowledge_chunks_pk` 未含，待 B-2 cluster 扩容+备份前置续交，起 PR 前须先确认 B-2**），**P3-T7 企业 IdP OIDC 接入已交付**（`93dcbb9`，分支 `feat/p3-t5-archive-delete`：`PlatformTokenAuthFilter` 追加 IdP 校验分支——本地平台 JWT 不命中时回退企业 IdP 令牌 RS256/JWKS 校验；domain 端口 `PlatformIdpTokenService` + infra `PlatformIdpTokenGatewayImpl`（JDK 原生 `Signature`/`KeyFactory` + `java.net.http` 取 JWKS，**零新依赖**）+ `JwkSetProvider`/`HttpJwkSetProvider`（TTL 缓存），默认关闭 `knowledge.platform.oidc.enabled=false` 三闸门门控，全 reactor `./mvnw test` 绿 1126 用例），**P3-T7 邀请式设密回访子切片已交付**（`9aa358b`，分支 `feat/p3-t5-archive-delete`：落地页状态预检查 `GET /api/admin/auth/invite-status?code=XXX`（`InviteStatusQryExe` 五态 UNKNOWN/PENDING/USED/EXPIRED/REVOKED + 脱敏 `tenantKeyHint`/`usernameHint` + UNKNOWN 不查网关抗枚举）· 平台运维重签 `POST /api/platform/tenants/{tenantKey}/invites/reissue`（`ReissueInviteCmdExe` 复用 `UserInviteService.issue` 语义零漂移：同用户既有 PENDING 全部 REVOKED + 落新）· 前端 `set-password.html` 五态分支 + 60s 倒计时 + 预检查失败降级；三闸门保持默认 false，全 reactor `./mvnw test` 绿 **1142** 用例，spotless + checkstyle EXIT 0，零新 Maven 依赖），**P3-T6 SILO 逐库 runner 待开工**（依赖 P3-T5 延后的 `SiloCollectionAdmin`/T-3 Milvus DDL 与 **B-2 cluster 前置**，现网无 SILO 集合可枚举，暂缓），**partition-key 切换（S-2=c）续交仍待 B-2 确认**，全程双门控 `saas.enabled=false`+`platform.enabled=false`。R1 partition-key 目标态延续不回退、定性改为隔离/运维目标态（性能收益未经证实）。原 18 项：B-1/B-2/B-3 三项运维承接不再消解（建集合/drop 集合/cluster 决策为硬门槛）；S-1 SILO 准入门槛/S-2 现网集合迁移策略/S-3 万级时延阈值 P95/S-4 Archive 保留期与双人复核/S-5 Provisioning 状态机幂等键；T-1 provider 多 store 缓存/T-2 partition-key schema 与 LangChain4j 兼容/T-3 SiloCollectionAdmin API/T-4 平台端鉴权分层/T-5 复杂 ACL 下推边界/T-6 SILO placement 决策器；每项签署/变更后回读提案 §11、评审包 §6、执行计划 §5 保持零漂移 | 新（承接 Phase 2 清单 §2 B/§3 S/§4 T 延后项 + 执行计划 §5 Phase 3 本体） |
| [docs/saas-phase3-review-brief.md](docs/saas-phase3-review-brief.md) | **SaaS Phase 3 会审决策建议一页纸**：把 P3-T0 万级 spike 实证浓缩为 S-2/S-3/T-2 + R1 复核的可直接定夺建议（含连带排产影响与否决对冲预案） | 参加 Phase 3 会审、需就 partition-key/SILO 取舍定夺时；清单 §3 S/§4 T 勾选位需要证据支撑时 | 新（配套清单 §3/§4 + 提案 §10 R1 + spike RESULTS 证据；**v0.2 定稿 · 2026-10-09 会审采纳其四条建议后升版**，纯决策辅助留档、权威以清单为准） |
| [docs/saas-phase4-go-checklist.md](docs/saas-phase4-go-checklist.md) | **SaaS Phase 4 开工门禁清单**：17 项分四类（B 硬阻塞 3 / S 软阻塞 5 / T 技术细节 5 / C 交叉项 4）+ 五方签署页 + 起草与升版路径 | Phase 4（`sys_config` 读取顺序/缓存 key + Redis pub/sub 分布式失效 + `kb_extraction_cache` 加租户维 + per-tenant LLM/embedding profile）开工前必读；**v0.1 状态（2026-10-09 起草，待会审，不代表 Phase 4 已授权开工）**：B-1 Redis 引入方案（与 Phase 5 P5-T2 同期评估）/ B-2 密钥保管 KMS vs 本地主密钥 vs Vault（Phase 4 唯一硬门槛之一）/ B-3 提案 §11 Phase 4 栏闭环三项均未签；S-1 P4-T1 缓存 key 结构 / S-2 Redis 缺席 TTL 兜底 / S-3 `kb_extraction_cache` 唯一约束切换冲突扫描 / S-4 解密失败安全降级 / S-5 未配 API Key 的租户 LLM 是否禁用五项待定；T-1 `TenantLlmProfileResolver` 缓存 / T-2 `ConfigCacheKey` 分层落点 / T-3 `SecretCipher` API 边界 / T-4 Prometheus 租户维度基数控制 / T-5 checksum per-tenant 加盐 五项待定；C-1 CI 门禁扩展 / C-2 Phase 3 尾分支时序 / C-3 IdP RBAC 延后承接 / C-4 Phase 4 埋点与 Phase 6 排产 四项待指派。**Phase 3 P3-T1~T4/T5 归档 & Delete 子切片/T7 OIDC/T7 邀请式设密回访子切片已交付；P3-T5 partition-key 分支/P3-T6 SILO 逐库 runner 受 Phase 3 B-2 cluster 扩容+备份硬前置门控未闭，Phase 4 会审须评估并行/串行时序**（详见 §5 C-2）。全程三闸门 `knowledge.saas.enabled=false`+`knowledge.platform.enabled=false`+`knowledge.platform.oidc.enabled=false` 保持默认关闭；P4-T1 迁移已由 Phase 3 清单 C-4=方案 A 提前并入 P3-T3（`V28__tenant_scope_sys_config_and_invite.sql`），Phase 4 起草时只处理读取顺序与缓存 key 抽象 | 新（承接 Phase 3 清单 §5 C-3 IdP RBAC 延后 / §7 第 14/15 条 Phase 3 尾分支时序 + 执行计划 §6 P4-T1~T4） |

---

## 2. 按任务定位分册（决策树）

- 我要**新建一个类**（Controller / Service / Executor / Entity / Gateway / DO / DTO / VO）？→ 先看 [docs/architecture-decisions.md](docs/architecture-decisions.md)（定它属于哪层、叫什么、依赖谁），再看 [docs/coding-guideline.md](docs/coding-guideline.md)（怎么写）。
- 我要**改依赖 / 加库 / 动配置 / 本地编译运行**？→ [docs/tech-stack.md](docs/tech-stack.md)。
- 我要**建表 / 写迁移 / 写 SQL / 配 Mapper**？→ [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md)，涉密或含参数拼接另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动文档解析 / 分块 / 向量化 / 出题配图**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md)，权限与过滤另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动 LLM Agent / LangChain4j 编排**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) + [docs/architecture-decisions.md](docs/architecture-decisions.md)（实现落 infrastructure `.../agent/`，编排在 app Executor）。
- 我要**改静态页 / `common.js` / 令牌头**？→ [docs/security-guideline.md](docs/security-guideline.md)（`knowledge-web/src/main/resources/static/`）。
- 我要**加鉴权 / 改权限 / 处理上传 / 发令牌 / 存口令**？→ [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**配 CI/CD 或把 Actions 产物部署到本地 Docker**？→ [docs/deployment-ci-cd.md](docs/deployment-ci-cd.md)（GHCR 推镜像 / Artifacts 拉 jar / 离线 tar 三方案）。
- 我要**写测试 / 提交前自验**？→ [docs/testing-guideline.md](docs/testing-guideline.md) + [docs/code-review-checklist.md](docs/code-review-checklist.md)。
- 我要**动 Milvus `EmbeddingStore` / partition-key / SILO / Phase 3 生命周期（`PlatformTenantController`/Provisioning 状态机/Archive & Delete 编排/SILO Flyway 逐库 runner）/ 平台端 OIDC 接入**？→ 先过 Phase 3 门禁清单 [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md)（**v0.3 全量授权开工 · 2026-10-09 五方齐签**，18 项 B/S/T/C；P3-T1~T4 已交付、P3-T5/T6/T7 解锁），取舍证据配读 [决策建议一页纸 docs/saas-phase3-review-brief.md](docs/saas-phase3-review-brief.md)（**v0.2 定稿**，P3-T0 spike 实证 → S-2/S-3/T-2/R1 四条建议已被会审采纳），再查排产 [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md) §5 P3-T0~T7（含 C-3=方案 B 新增的企业 IdP OIDC 接入任务 P3-T7）与生效基线 Phase 2 [docs/saas-phase2-go-checklist.md](docs/saas-phase2-go-checklist.md) v0.3（已交付归档，作为下游承接参考），最后核对决策权威 [docs/saas-multi-tenant-proposal.md](docs/saas-multi-tenant-proposal.md) §10 R1。
- 我要**动 Phase 4 本体（`sys_config` 读取顺序 / `ConfigCacheKey` / Redis pub/sub 分布式失效 / `kb_extraction_cache` 加租户维 / per-tenant LLM+embedding profile / `SecretCipher` 密钥保管 / `TenantLlmProfileResolver`）**？→ 先过 Phase 4 门禁清单 [docs/saas-phase4-go-checklist.md](docs/saas-phase4-go-checklist.md)（**v0.1 起草 · 2026-10-09 · 待会审，不代表 Phase 4 已授权开工**，17 项 B/S/T/C；B-1 Redis / B-2 密钥保管为 Phase 4 硬门槛；C-2 Phase 3 尾分支时序须评估并行/串行），再查排产 [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md) §6 P4-T1~T4 与生效基线 Phase 3 [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md) v0.3（Phase 3 尾分支 P3-T5 partition-key/P3-T6 SILO 逐库 runner 仍受 B-2 cluster 硬前置门控），最后核对 Phase 4 依赖的 Phase 5 Redis 决策 [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md) §7 P5-T2。

---

## 3. 相关文档

- 项目能力总览： [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
- 运维脚本： [scripts/README.md](scripts/README.md)
- 细则与检查项：见 [docs](docs) 目录各分册

---

## 4. 维护约定

- 本文件只保留 **红线 + 导航**；细则一律下沉到 [docs](docs) 分册。
- 新增规范优先写进对应分册；必要时在本文件补一行入口说明。
- 规范间用相对链接互引，避免重复复制与漂移。
- 兼容历史命名与层次术语的权威说明见 [docs/architecture-decisions.md](docs/architecture-decisions.md)。

## 5. 例外申请与审批流程（建议）

- 目的：提供一个可操作流程，当某项规范无法适用或存在冲突时，团队能有明确路径申请例外并记录理由与批准人。
- 提交流程：在相关 PR 中新增一段“例外说明”（Exception-Note），包含：受影响规范、理由、替代方案、风险评估、回退计划、所需时间窗口。
- 批准人：指定至少一位架构负责人或安全负责人签署（可在 PR Review 中通过 comment+`/approve-exception` 标签），并在 PR 描述中引用对应 issue 或设计文档。
- 时效与记录：所有例外需在仓库中创建或更新 `docs/exceptions/` 下的记录文件（YAML 或 Markdown），注明到期时间或需复审的里程碑。
- 复审：例外到期或功能变更时，责任人应发起复审 Issue 并在 30 天内完成是否恢复规范的决议。

## 6. CI 校验建议（可逐步实施）

- 目标：把可自动化的规范逐步移入 CI 校验，降低人工误差并提高合规性。
- 初始建议项：
  - 代码格式：使用 Checkstyle / Spotless 严格化缩进与行长（可在 PR hook 中运行）。
  - 静态分析：SpotBugs / PMD 检查常见 bug 模式与未处理异常。
  - 日志/敏感信息扫描：在 CI 中运行简单规则扫描（禁止在日志模板或响应中包含 `password|token|secret` 等关键字），必要时集成更强的 SAST。
  - SQL 模式检查：在构建时运行自定义脚本或 linters，检查是否使用 `#{}` 参数化和禁止裸 `${}`（对 Mapper XML 做抽样检测）。
  - Javadoc 存在性：对 `public` 类和方法运行存在性检查（非强制内容完整度）。
- 推进方式：先把格式与行长/缩进作为强制校验，其他规则做为 warning 级别，逐步提升为 fail-on-error，当团队熟悉规则并处理历史遗留后，再收紧为必须通过。

---

上述条目为建议性补充，便于把规范从“文字约束”变为“可执行规则 + 审批流程”。如需我直接把 CI 校验脚本模板（Checkstyle 配置、Spotless 配置、简单敏感字符串扫描脚本）生成到仓库中，我可以继续创建这些文件并提交 patch。
