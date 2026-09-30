# SaaS 化改造 · 三方评审材料包（Review Pack）

> 用途：**会前预读**。供架构负责人、安全负责人、运维/基础设施负责人在提案审批会上当场判断「签署 / 需补材料 / 否决」。
> 权威文档：变更提案 [saas-multi-tenant-proposal.md](saas-multi-tenant-proposal.md)（架构决策）· 可执行计划 [saas-multi-tenant-execution-plan.md](saas-multi-tenant-execution-plan.md)（任务拆解）· 规范基线 [../AGENTS.md](../AGENTS.md) §0 红线 #11
> 版本：v1.0 · 配套提案 v0.1（2026-09-29）

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
| O1 | 多副本能力：`docker-compose` 去固定化（移除 `container_name`/固定端口）、`--scale knowledge-app=2` 可行性、前置网关选型 | P5-T4、提案 §2.8 | ☐ 可承接 ☐ 需补料 ☐ 否决 |
| O2 | Redis 依赖：上传会话外置（P5-T2）+ 配置分布式失效（P4-T2）引入新组件；容量/HA/降级路径（无 Redis 时 TTL 兜底）是否可接受 | P4-T2 · P5-T2 | ☐ 可承接 ☐ 需补料 ☐ 否决 |
| O3 | Milvus：P2-T0 spike 结果（partition-key vs 多 collection 取舍）；SILO prefix/独立 collection 甚至 cluster 模式的运维成本 | P2-T0/T4、提案 §2.4、plan §11 S4 | ☐ 可承接 ☐ 待 spike 结果 ☐ 否决 |
| O4 | MinIO 接入：由 Milvus 内部依赖改为应用侧 SDK 直连，bucket 规划/网络策略/备份 | P5-T1、提案 §2.5 | ☐ 可承接 ☐ 需补料 ☐ 否决 |
| O5 | Flyway 逐库 runner 可操作性：SILO 逐库迁移的错峰、失败隔离、schema 一致性体检；**迁移停机窗口**（P1-T5 唯一索引切换，plan §13 第 5 条）需运维预估 | P3-T6、X-T4、plan §13.5 | ☐ 可承接 ☐ 需补料（窗口预估） ☐ 否决 |
| O6 | 监控与告警基数：`tenant` 高基数**不进 Prometheus 标签**，计量独立落库；现有 `/actuator/prometheus` 匿名暴露在新租户模型下是否需收紧 | P6-T1、提案 §4、`docs/exceptions/20260923-observability-prometheus-registry.md` | ☐ 可承接 ☐ 需补料 ☐ 否决 |

---

## 3. 未决项与阻塞点（提案 §10 + plan §13，会上需给出结论或指定期限）

| # | 未决项 | 需谁拍板 | 阻塞范围 | 时限建议 |
|---|---|---|---|---|
| U1 | **R1 Milvus partition-key 技术决策**（partition-key 下推 vs 多 collection） | 架构 + 运维 | Phase 2 全部（P2-T1~T5）；若退化多 collection 则 P3-T6 逐库 Flyway 变复杂 | P2-T0 spike **2 周内**出报告（plan §11 S4） |
| U2 | **SILO 准入门槛**（合同/数据量/QPS 阈值） | 产品/商务 + 架构 | P3-T1 placement 决策器规则 | 会前给初值或授权后续专项 |
| U3 | **数据驻留 `data_region`** 是否要求物理分区部署 | 运维 + 合规 | compose/网络策略、Phase 5 布局（O2 未决） | 本次会议给方向即可 |
| U4 | **SSO**（是否接企业 IdP，OIDC/SAML） | 产品 | X-T5 前端深度、若需要则须 Phase 3 前完成（R5） | 可延后至 Phase 3 开工前 |
| U5 | **前端跨域/Cookie** 策略（子域 vs `X-Tenant-Id` 头） | 产品 + 运维 + 安全 | X-T5、P1-T3 resolver 优先级 | 与 U4 一并会商 |
| U6 | **迁移停机窗口**（P1-T5 唯一索引切换：停机 or 双写） | 运维 | X-T4 演练方案、S2 灰度节奏 | 依最大租户表规模，运维 **1 周内**预估 |

> 以上不阻塞 Phase 0 与 Phase 1（仅 `default` 租户运行部分）开工；U1/U2 阻塞 Phase 2/3 全面展开。

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
| 架构负责人 | §2.1（A1–A5）、§3 U1/U2 | ☑ 签署（口头） | mouhinU（项目负责人代行） | 2026-09-29 |
| 安全负责人 | §2.2（S1–S6）、§3 U4/U5 | ☑ 签署（口头） | mouhinU（项目负责人代行） | 2026-09-29 |
| 运维/基础设施负责人 | §2.3（O1–O6）、§3 U1/U3/U6 | ☐ 签署 ☑ 需补料（条目：U1 Milvus 环境、U3 集群承接） | ______ | ______ |

**一句话结论**（每方必填）：

- 架构：口头授权进入 Phase 1 地基（P1-T1/T2/T3），运行时仅 `default` 租户、`saas.enabled=false` 可回退；R1 向量决策仍待 P2-T0 spike 出数。
- 安全：同意 Phase 0 修复 + Phase 1 单租户语义地基先行；Phase 1-T10 跨租户负向用例纳入 CI 门禁后方可灰度。
- 运维：待补——Milvus 环境与多副本承接需书面确认，方开 Phase 2/5。

**附带决议**（勾选即视为会议记录）：
- ☑ 同意 Phase 0（P0-T1~T5）口头先行，不等三方齐签。
- ☑ 同意 Phase 1 地基（P1-T1/T2/T3）口头先行，受 `saas.enabled=false` 门控。
- ☑ 例外 EX-saas-001 随本提案一并批准（口头）。
- ☐ U1~U6 各项责任人与时限已按 §3 表格指派。（运维侧待正式会议指派）
- ☑ **Phase 2 未闭环项已由专项收口清单承接**：[saas-phase2-go-checklist.md](saas-phase2-go-checklist.md) v0.1（12 项分四类：B 硬阻塞 3 / S 软阻塞 3 / T 技术细节 3 / C 交叉项 3）；B-1/B-2/B-3 三项运维书面签署后视为 U1/U3 正式确认，同步回写本评审包运维栏与本提案 §11。

> 记录人：QoderWork ｜ 性质：项目负责人口头授权，非正式三方书面会签。正式书面签署与运维承接确认需在开启 Phase 2 前补齐。
