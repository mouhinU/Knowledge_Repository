# SaaS 化改造 · 三方评审材料包（Review Pack）

> 用途：**会前预读**。供架构负责人、安全负责人、运维/基础设施负责人在提案审批会上当场判断「签署 / 需补材料 / 否决」。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（任务拆解）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> 版本：v1.2 · 配套提案 v0.4（2026-10-09）· §6 运维栏由「☑ 需补料（U1/U3）」转「☑ 签署」（运维/基础设施负责人无始 2026-10-09 承接 B-1/B-2/B-3）；§6 附带决议追加"Phase 3 全量授权（五方齐签）"条目，U1/U2/U4/U6 闭环（U3 数据驻留给方向延后 Phase 5）；清单升 v0.3、执行计划升 v1.0（新增 P3-T7 OIDC）、提案升 v0.4

---

## 1. 一页摘要（TL;DR）

**决策要点**
- **隔离模型**：混合模式（Hybrid）——中小租户走 POOL（共享库 + `tenant_id` 列），大/合规租户走 SILO（独立库），由 `sys_tenant_placement` 路由。
- **租户定义**：租户 = 公司/组织（B 端付费主体）；现有 `department` 降级为租户内部组织单元，不再充当租户边界。
- **改造范围**：全量四工作流（WL-A 数据/鉴权/向量隔离 · WL-B 租户生命周期 · WL-C 配额计费 · WL-D 水平扩展），对应 **Phase 0–6 路线图**（依赖：0→1→{2,4}→3→5→6）。
- **关键前提**：当前代码库**零租户概念**（全库 grep `tenant` 零命中），本次是单租户应用→多租户平台的架构跃迁，非局部增强。

**关键数字**
- **15 张业务表** ALTER 加 `tenant_id`（P1-T4/T6，nullable→回填→NOT NULL 三段式）。
- 约 **94 个端点 / 17 个 controller** 需携带租户上下文（除 2 个登录前 auth 端点）（提案 §6）。
- **`EmbeddingStore` 进程级单例 → Provider 注册表重构**（P2-T1，改造量最高的基础设施点，风险 R1）。
- **4 处异步/线程池工作流**需租户快照传播（`ExamGenerationSupport` / `ArticleGenerationSupport` / `CompletableFuture` 提交点 / `ExamGradingScheduler`）（提案 §2.2、P1-T9）。

**存量安全修复（可先行，不等审批结论）**
- **G10**：`SearchCmd` 身份可伪造（`userId/departmentId/isAdmin` 来自请求体）→ P0-T1 只信令牌回读。
- **G5**：`AdminPrincipalSupport.toPermission` 恒传 `roles=null, admin=true` → P0-T2 角色链路接线。
- **G6**：`ExamHistoryGatewayImpl.listPublished/listPage` 全局可见无谓词 → P0-T3 加过滤。
- 三项属**改造前必修**：不先修，加租户后越权面反而扩大（提案 §6 安全评估）。Phase 0 与租户语义解耦，获口头同意即可开工。

---

## 2. 三方关注清单与签署位（对应提案 §11 审批栏）

### 2.1 架构负责人

| # | 评审项 | 依据锚点 | 判断 |
|---|---|---|---|
| A1 | 混合模型（POOL+SILO）选择是否成立；R2 双轨长期复杂度与「只支持一种」退让方案 | 提案 §0/§2.1/§10 R2 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| A2 | 依赖方向红线合规：Gateway 接口在 domain、`GatewayImpl` 在 infra；`TenantContext` 落 domain 层、DO 不越 infrastructure | [../AGENTS.md](../AGENTS.md) 红线 #6/#7、[architecture-decisions.md](architecture-decisions.md)、提案 §2.2/§2.3 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| A3 | 租户上下文传播红线：`ThreadLocal`/`ScopedValue` `finally` 清理、异步快照装饰 executor、定时任务显式遍历租户（R4 选型结论） | 提案 §2.2、P1-T2/T9、plan §10 R4 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| A4 | `EmbeddingStore` 单例→Provider 重构合理性；P2-T0 spike 前置是否足以对冲 R1 | 提案 §2.4/§10 R1、P2-T0/T1 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| A5 | 回退计划充分性：总开关 + 三段式加列 + 逐 Phase 独立回滚 + undo 配对 | 提案 §9、plan §12 DoD 第 3 条 | ☐ 同意 ☐ 需补料 ☐ 否决 |

### 2.2 安全负责人

| # | 评审项 | 依据锚点 | 判断 |
|---|---|---|---|
| S1 | 跨租户越权面：**G10 优先**（身份只信令牌回读），G5/G6 修复方案与 Phase 0 排期 | P0-T1/T2/T3、`knowledge-web/src/main/java/com/mouhin/knowledge/repository/web/security/AdminPrincipalSupport.java`、`knowledge-infrastructure/src/main/java/com/mouhin/knowledge/repository/infrastructure/persistence/gateway/ExamHistoryGatewayImpl.java` | ☐ 同意 ☐ 需补料 ☐ 否决 |
| S2 | JWT claim 设计：`{sub, username, admin, tenantId, roles, iat, exp}`；resolver 三源（header/子域/claim）冲突即拒；单 JWT secret 保留 vs per-tenant 派生 | 提案 §2.6/§2.7、P1-T8、P1-T3 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| S3 | SILO 凭据管理：独立库数据源凭据的存储/轮转/最小权限；与 P4-T4 `SecretCipher`（KMS/本地主密钥）机制是否统一 | 提案 §6 安全评估、P4-T4、plan §10 R2 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| S4 | 跨租户负向用例覆盖度：P1-T10（脊柱 5 类出口）+ P2-T5（向量层）是否满足「每个数据出口 A 建 B 不可见」门禁要求；纳入 CI（X-T3） | 提案 §8、plan §2/§4/§9 | ☐ 同意 ☐ 需补料 ☐ 否决 |
| S5 | 对象存储/文件出口租户校验：`/api/exam/assets/{assetKey}` 叠加 `tenant_id` 反查比对（P5-T3，不匹配 404 不暴露存在性）；路径归一化防护保留（P0-T4）；MinIO prefix 租户隔离（P5-T1） | 提案 §2.5、P5-T1/T3、[security-guideline.md](security-guideline.md) | ☐ 同意 ☐ 需补料 ☐ 否决 |
| S6 | 审计与权限边界：生命周期动作全量入审计（提案 §3）；平台管理员 vs 租户管理员权限边界与**审计留存周期**（O3，待会上敲定） | 提案 §3/§10 O3、P3-T1 | ☐ 同意 ☐ 需补料（O3 数值） ☐ 否决 |

### 2.3 运维 / 基础设施负责人

| # | 评审项 | 依据锚点 | 判断 |
|---|---|---|---|
| O1 | 多副本能力：`docker-compose` 去固定化（移除 `container_name`/固定端口）、`--scale knowledge-app=2` 可行性、前置网关选型 | P5-T4、提案 §2.8 | ☐ 可承接 ☐ 需补料 ☐ 否决（**Phase 5 项，不在 2026-10-09 Phase 3 B 类签署范围，待 Phase 5 承接确认**） |
| O2 | Redis 依赖：上传会话外置（P5-T2）+ 配置分布式失效（P4-T2）引入新组件；容量/HA/降级路径（无 Redis 时 TTL 兜底）是否可接受 | P4-T2 · P5-T2 | ☐ 可承接 ☐ 需补料 ☐ 否决（**Phase 4/5 项，不在 2026-10-09 Phase 3 B 类签署范围，待 Phase 5 承接确认**） |
| O3 | Milvus：P2-T0 spike 结果（partition-key vs 多 collection 取舍）；SILO prefix/独立 collection 甚至 cluster 模式的运维成本 | P2-T0/T4、提案 §2.4、plan §11 S4 | ☑ **可承接（2026-10-09 无始 B-1/B-2 签署）**：P2-T0 + P3-T0 spike 已出数 → S-2=方案 c（partition-key 切换并入 P3-T5）；现网 standalone 建/drop 集合权限承接，cluster 扩容 + 备份列为 P3-T5 起前硬前置 ☐ 否决 |
| O4 | MinIO 接入：由 Milvus 内部依赖改为应用侧 SDK 直连，bucket 规划/网络策略/备份 | P5-T1、提案 §2.5 | ☐ 可承接 ☐ 需补料 ☐ 否决（**Phase 5 项，不在 2026-10-09 Phase 3 B 类签署范围；P3-T5 Archive 文件层物理清理依赖其完成，未完成则先落 DB/Milvus 两处**） |
| O5 | Flyway 逐库 runner 可操作性：SILO 逐库迁移的错峰、失败隔离、schema 一致性体检；**迁移停机窗口**（P1-T5 唯一索引切换，plan §13 第 5 条）需运维预估 | P3-T6、X-T4、plan §13.5 | ☑ **可承接（2026-10-09 无始签署，Phase 3 范围）**：T-3=方案 A（collection-only，`kc_{tenantKey}`，遍历本库集合）；迁移窗口随 S-2=方案 c 在 P3-T5 partition-key 切换前 2 周跑 C-2 演练，脚本归档 `scripts/migration-drills/` ☐ 否决 |
| O6 | 监控与告警基数：`tenant` 高基数**不进 Prometheus 标签**，计量独立落库；现有 `/actuator/prometheus` 匿名暴露在新租户模型下是否需收紧 | P6-T1、提案 §4、`docs/exceptions/20260923-observability-prometheus-registry.md` | ☐ 可承接 ☐ 需补料 ☐ 否决 |

---

## 3. 未决项与阻塞点（提案 §10 + plan §13，会上需给出结论或指定期限）

| # | 未决项 | 需谁拍板 | 阻塞范围 | 时限建议 |
|---|---|---|---|---|
| U1 | **R1 Milvus partition-key 技术决策**（partition-key 下推 vs 多 collection） | 架构 + 运维 | Phase 2 全部（P2-T1~T5）；若退化多 collection 则 P3-T6 逐库 Flyway 变复杂 | ✅ **已闭环（2026-10-09）**：P2-T0 + P3-T0 spike 出数，S-2=方案 c（切换并入 P3-T5）、R1 目标态延续不回退（性能收益标注未经证实） |
| U2 | **SILO 准入门槛**（合同/数据量/QPS 阈值） | 产品/商务 + 架构 | P3-T1 placement 决策器规则 | ✅ **已闭环（2026-10-09）**：S-1=方案 C（Phase 3 手工 placement，决策器算法延后 Phase 3.5/产品化窗口） |
| U3 | **数据驻留 `data_region`** 是否要求物理分区部署 | 运维 + 合规 | compose/网络策略、Phase 5 布局（O2 未决） | ✅ **给方向（2026-10-09）**：不影响 Phase 3 生命周期本体，物理分区部署延后 Phase 5 布局评估 |
| U4 | **SSO**（是否接企业 IdP，OIDC/SAML） | 产品 | X-T5 前端深度、若需要则须 Phase 3 前完成（R5） | ✅ **已闭环（2026-10-09）**：C-3=方案 B（Phase 3 内接企业 IdP OIDC → 新任务 P3-T7，扩展 T-4 已交付 `PlatformTokenAuthFilter`） |
| U5 | **前端跨域/Cookie** 策略（子域 vs `X-Tenant-Id` 头） | 产品 + 运维 + 安全 | X-T5、P1-T3 resolver 优先级 | ✅ **已闭环**：由 P1-T3 三源解析（header/子域/claim）支撑，Phase 3 无前端改动 |
| U6 | **迁移停机窗口**（P1-T5 唯一索引切换：停机 or 双写） | 运维 | X-T4 演练方案、S2 灰度节奏 | ✅ **已闭环（2026-10-09）**：运维承接，S-2=方案 c → 迁移演练（C-2）在 P3-T5 partition-key 切换前 2 周跑，脚本归档 `scripts/migration-drills/` |

> 以上不阻塞 Phase 0 与 Phase 1（仅 `default` 租户运行部分）开工；**U1~U6 已于 2026-10-09 Phase 3 全量授权会审全部闭环/给方向**（详见 §6 附带决议末条 + [saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) §7 第 13 条）。

---

## 4. 回退与灰度策略摘要（提案 §9）

1. **总开关** `knowledge.saas.enabled`（默认 `false`）：关闭时 TenantContext 恒 `default`、拦截器 ignore 全部、无 tenant claim 的旧 JWT 照常通过——行为与现单租户**完全等价**（X-T2 提供骨架与回归对照表）。
2. **三段式加列**：`ADD COLUMN tenant_id NULL` → 回填 `default` → `NOT NULL` + 复合索引；任一段可独立回滚（nullable 阶段 `UPDATE SET NULL` 即退）。
3. **Phase 0 独立可上线**：三项存量安全修复不改租户语义，与灰度开关无关。
4. **旧 JWT 兼容窗口**：无 `tenantId` claim 的老 token 视为 `default` 租户，**兜底保留 30 天**（P1-T8），窗口内强制轮换。
5. **灰度节奏**：先内部租户→逐步放量；异常按**租户粒度**暂停而非全局停机；SILO 迁移保留 pool 数据副本直至验收；唯一索引切换前保留旧索引定义。

---

## 5. 例外登记索引（`docs/exceptions/`，依据 [../AGENTS.md](../AGENTS.md) §5）

本提案涉及的规范例外已按下述格式登记，模板见 [_template.yml](exceptions/_template.yml)：

| 编号 | 标题 | 状态 | 到期/复审 |
|---|---|---|---|
| [EX-saas-001](exceptions/saas-multi-tenant-001.yml) | 平台级容器表（`sys_tenant`/`sys_platform_admin`/Flyway schema history）豁免 `tenant_id` 列 | draft | 随 Phase 3 退出条件复审 |
| （历史）2026-09-21 | 特殊场景自定义 Logger | 见 [exceptions/](exceptions/) | 已登记 |
| （历史）2026-09-23 | `/actuator/prometheus` 匿名暴露 | 见 [exceptions/](exceptions/) | 90 天窗口内，评审项 O6 关联 |

新增例外流程：PR 中 `Exception-Note` → 架构或安全负责人签署 → 落 `docs/exceptions/EX-*.yml` → 到期 30 天内复审。

---

## 6. 签署页

> 结论选项：**签署（同意）/ 需补材料（列明条目号）/ 否决（列明理由）**。三方齐签后本提案转为基线，[saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md) 生效排产（plan X-T1）。

| 角色 | 关注章节 | 结论（☐ 签署 ☐ 需补料 ☐ 否决） | 签署人 | 日期 |
|---|---|---|---|---|
| 架构负责人 | §2.1（A1–A5）、§3 U1/U2 | ☑ 签署（口头 2026-09-29 mouhinU 代行）→ ☑ **Phase 3 全量授权书面签署** | 蒙斌 | 2026-10-09 |
| 安全负责人 | §2.2（S1–S6）、§3 U4/U5 | ☑ 签署（口头 2026-09-29 mouhinU 代行）→ ☑ **Phase 3 全量授权书面签署** | 莫道 | 2026-10-09 |
| 运维/基础设施负责人 | §2.3（O1–O6）、§3 U1/U3/U6 | ☑ **签署（B-1/B-2/B-3 承接：现网 Milvus standalone 建/drop 集合权限 + 账号权限 + 监控；cluster 扩容 + 备份列为 P3-T5 起前硬前置）** ☐ 需补料 ☐ 否决 | 无始 | 2026-10-09 |

**一句话结论**（每方必填）：

- 架构：口头授权进入 Phase 1 地基（P1-T1/T2/T3），运行时仅 `default` 租户、`saas.enabled=false` 可回退；R1 向量决策 P2-T0/P3-T0 spike 已出数，S-2=方案 c（partition-key 切换并入 P3-T5）。**架构负责人蒙斌于 2026-10-09 就 Phase 3 全量授权书面签署。**
- 安全：同意 Phase 0 修复 + Phase 1 单租户语义地基先行；Phase 1-T10 跨租户负向用例纳入 CI 门禁后方可灰度；S-4=方案 A（Archive 30 天 + 双人复核）、T-4=方案 B（独立 PlatformTokenAuthFilter，已交付）、C-3=方案 B（OIDC → P3-T7）。**安全负责人莫道于 2026-10-09 就 Phase 3 全量授权书面签署。**
- 运维：**已签署（2026-10-09 无始）**——B-1 承接现网 Milvus 2.5.4 standalone `io.milvus` SDK 建/drop 集合权限 + 账号权限 + 监控接入 + 内存/磁盘预留；B-2 cluster 扩容 + 备份方案列为 P3-T5 起前硬前置（P3-T0 spike 已证 50k/100t 触发 standalone ~3.82 GiB OOM）；B-3 运维栏闭环。U1（Milvus 环境）/U6（迁移停机窗口，随 S-2=方案 c 在 P3-T5 切换前演练）随之闭环；对象存储/Redis（O2/O4）仍随 Phase 5 落地。

**附带决议**（勾选即视为会议记录）：
- ☑ 同意 Phase 0（P0-T1~T5）口头先行，不等三方齐签。
- ☑ 同意 Phase 1 地基（P1-T1/T2/T3）口头先行，受 `saas.enabled=false` 门控。
- ☑ 例外 EX-saas-001 随本提案一并批准（口头）。
- ☑ U1~U6 各项责任人与时限已按 §3 表格指派（**2026-10-09 运维书面签署后闭环**：U1 Milvus 环境 + U6 迁移窗口由运维承接；U2 SILO 准入门槛 = S-1 方案 C 手工 placement、决策器延后 Phase 3.5；U4 SSO = C-3 方案 B → P3-T7 OIDC；U3 数据驻留给方向延后 Phase 5 布局；U5 前端跨域由 P1-T3 三源解析支撑）。
- ☑ **Phase 2 未闭环项已由专项收口清单承接**：[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) v0.3（12 项分四类：B 硬阻塞 3 / S 软阻塞 3 / T 技术细节 3 / C 交叉项 3，Phase 2 已交付归档）；B-1/B-2/B-3 三项运维书面签署后视为 U1/U3 正式确认，同步回写本评审包运维栏与本提案 §11。
- ☑ **Phase 2 开工授权（2026-09-30 项目负责人口头采纳，与 Phase 0/1 同模式）**：清单 v0.2 落地 S-1 方案 B（SILO 授权延后至 Phase 3）+ S-2 方案 b（LangChain4j JSON 谓词路径，`metadata.tenant_id` 走 `IsEqualTo` 下推）+ S-3 门禁降级（partition-key 万级时延阈值不再是 Phase 2 CI gate 断言）；B-1/B-2/B-3 触发条件在 Phase 2 范围内已消解（不动集合/权限/拓扑），T-1/T-2/T-3 联动结论落定；**正式运维三方签署（本 §6 运维栏 ☑ 需补料 → ☑ 签署）随 Phase 3 partition-key + SILO 引入时补齐**，届时创建 `docs/saas-phase3-go-checklist.md` 参照本清单格式。R1 决策（partition-key 为 POOL 目标态）不回退，落地节奏分两段。
- ☑ **Phase 2 交付归档（2026-09-30 同日完成）**：P2-T1~P2-T5 依次合入 `ddbcfb3`/`5c1982d`/`10fc424`/`908aac9`/`c702fce`，退出条件达成——provider 骨架就位、`MilvusVectorStoreService` 不再直连 bean、`metadata.tenant_id` 100% 写入非空 + 检索必带 `IsEqualTo` 前置、SILO 命名工具静态校验通过、跨租户负向 `@SpringBootTest` 与 CI `tenant-isolation` 独立 job 就位；全 reactor `./mvnw test` BUILD SUCCESS、单测新增 100 用例全绿、`P2-T5` 集成 4 用例 compile 通过 + 本机无 Docker 时 `disabledWithoutDocker=true` skipped（首次真绿跑由 GitHub Actions 承担）。清单 v0.2→v0.3、执行计划 v0.7→v0.8、提案 v0.2→v0.3、AGENTS.md §1 分册地图行同步；partition-key/SILO/万级时延阈值/运维 B 类正式签署留待 Phase 3 会审。
- 📝 **Phase 3 开工门禁清单已起草（2026-09-30）**：[saas-phase3-go-checklist.md](saas-phase3-go-checklist.md) v0.1（**待会审**，18 项 B/S/T/C 承接 Phase 2 消解/延后 6 项 + Phase 3 本体新引入 12 项）；B-1/B-2/B-3 三项运维书面签署（Milvus cluster 承接 / 建集合 drop 集合权限 / 备份与 DDL 流程）为 Phase 3 唯一硬门槛，不再通过 S-2 方案 b 消解；S-1 SILO 准入门槛 + S-2 现网集合迁移策略 + S-3 万级时延阈值 P95 + S-4 Archive 保留期与双人复核 + S-5 Provisioning 幂等键；T-1 provider 多 store 缓存 / T-2 partition-key schema 与 LangChain4j 兼容 / T-3 SiloCollectionAdmin API + 逐库 Flyway 集成 / T-4 平台端鉴权分层 / T-5 复杂 ACL 下推边界 / T-6 SILO placement 决策器；C-1 CI 扩展 / C-2 迁移演练 / C-3 SSO 决策 / C-4 sys_config 与 P3-T3 耦合。执行计划 v0.8→v0.9（加 P3-T0 spike 行 + S6 Sprint 条）+ 提案 v0.3→v0.4 待签署时同步；运维栏 ☑ 需补料（U1 Milvus 环境、U3 集群承接）在 B-1/B-2 通过后方可闭环。**待运维书面签署 + 架构 S/T 结论落定后升清单 v0.2 授权开工**。
- 🔬 **P3-T0 万级 spike 已执行（2026-10-07）· 证据提交，触发 R1 复核**：[../spikes/milvus-partition-key-scale/RESULTS.md](../spikes/milvus-partition-key-scale/RESULTS.md)。三组规模（10k/3t、10k/50t、20k/100t，IVF_FLAT / Bounded / 本机 standalone）实测 partition-key 检索 P95=13.4–20.9ms，**全部慢于同谓词普通标量(8.3–10.1ms)/LangChain4j JSON 谓词(10.3–12.2ms) 1.2–2.5 倍，无剪枝性能收益**；三组跨租户 `leak=0`；`50k/100t` 触发 Milvus standalone(~3.8GB) OOM 崩溃（直接支撑本评审包 U1/运维栏"Milvus 环境承接"须含内存/cluster 预留，即清单 B-1）。**会审输入**：建议评估清单 S-2 采纳方案 c（延后 partition-key、维持更快且零迁移的 LangChain4j JSON 谓词）、S-3 P95 阈值锚定 JSON/标量路径、T-2 SDK 兼容已验证；并复核提案 §10 R1（partition-key 为 POOL 目标态）当初的性能前提——在会审给出新结论前 R1 不回退，但性能收益标注为"未经证实"。此为证据，**不提前升版本戳**（提案/评审包/清单统一在 v0.2 授权时升）。
- ✅ **Phase 3 全量授权（2026-10-09 五方书面齐签）**：承接上两条 spike 证据与会审输入，五方（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星）于 2026-10-09 全部书面齐签——**本 §6 运维栏 ☑ 需补料 → ☑ 签署**（B-1/B-2/B-3 承接，cluster 扩容 + 备份列为 P3-T5 起前硬前置）。会审定夺：R1 partition-key 目标态延续不回退（性能收益标注未经证实，正当性转隔离/运维/超大规模期权）· S-1=方案 C · S-2=方案 c（partition-key 切换并入 P3-T5）· S-3=定性共识（绝对值 cluster 校准）· S-4=方案 A · S-5=方案 A · T-1 延后 P3-T5 · T-2=方案 B · T-3=方案 A · T-4=方案 B（已交付）· T-5=方案 C · T-6=方案 C · C-1/C-2 已指派 · **C-3=方案 B（Phase 3 内接 OIDC → 新任务 P3-T7）** · C-4=方案 A。清单升 **v0.3 全量授权**、执行计划升 **v1.0**（新增 P3-T7 OIDC 任务行）、提案升 **v0.4**、本评审包升 **v1.2**、决策建议一页纸升 **v0.2 定稿**；**解锁 P3-T5/T6/T7**（P3-T1~T4 已于 v0.2 部分授权期交付 `372994a`/`55a0872`/`73b0626`/`eece61a`）。U1/U2/U4/U6 闭环、U3 给方向延后 Phase 5、U5 由 P1-T3 支撑。全程双门控 `knowledge.saas.enabled=false` + `knowledge.platform.enabled=false`。

> 记录人：QoderWork ｜ 性质：Phase 0/1/2 为项目负责人口头授权先行（非正式三方书面会签）；**Phase 3 已于 2026-10-09 完成五方正式书面会签**（架构蒙斌 / 安全莫道 / 运维无始 / 产品·商务七月 / 项目负责人流星），运维承接确认（U1/U3/U6）随 B-1/B-2/B-3 签署闭环。
