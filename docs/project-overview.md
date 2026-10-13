## Knowledge Repository · 项目文档与业务流程梳理

> 生成日期：2026-10-13 · 基于代码库实际结构梳理，覆盖 COLA 五层架构、核心业务域、SaaS 多租户体系与前端页面。

---

### 一、项目概览

Knowledge Repository 是一个基于 RAG（检索增强生成）的智能知识库平台，提供文档管理、语义检索、AI 出题组卷、AI
写作、在线考试与智能阅卷等完整业务闭环。项目采用 **COLA 5.0（Clean Object-oriented Layered Architecture）** 五层架构，后端
Spring Boot 3.4 / Java 21，向量引擎 Milvus 2.5.4，支持 SaaS 多租户隔离。

**核心能力**：多格式文档解析 → 智能分块 → 向量化入库 → 语义检索 → AI Agent 编排（出题/写作/阅卷）→ 在线考试 → 智能评分 →
错题分析。

---

### 二、技术栈

| 领域     | 技术                        | 版本           | 说明                                             |
|--------|---------------------------|--------------|------------------------------------------------|
| 运行时    | Java                      | 21           | 虚拟线程已启用（`spring.threads.virtual.enabled=true`） |
| 框架     | Spring Boot               | 3.4.4        | Web + Security + Actuator                      |
| LLM 编排 | LangChain4j BOM           | 1.0.1        | Chat / Embedding / Milvus 集成                   |
| 向量数据库  | Milvus                    | 2.5.4        | 集合 `knowledge_chunks`，维度 1024（bge-m3）          |
| ORM    | MyBatis-Plus              | 3.5.17       | 含 `TenantLineInnerInterceptor` 多租户拦截           |
| 数据库迁移  | Flyway                    | Boot 管理      | V1–V35，H2/MySQL 双方言兼容                          |
| 关系数据库  | MySQL                     | 8.0（Docker）  | 生产环境；H2 用于开发/测试                                |
| 缓存     | Redis                     | 7-alpine     | 配置缓存 + pub/sub 失效 + 上传会话                       |
| 对象存储   | MinIO SDK                 | 8.5.17       | 与本地磁盘双驱动，`@ConditionalOnProperty` 切换           |
| 文档解析   | PDFBox 3 / Tika 3 / POI 5 | —            | MIME 路由 + 策略栈组合解析                              |
| 弹性容错   | Resilience4j              | 2.2.0        | 断路器 + 重试（LLM 调用隔离）                             |
| 可观测    | Micrometer + Prometheus   | —            | `/actuator/prometheus` 指标暴露                    |
| 代码规范   | Checkstyle + Spotless     | 10.12 / 2.43 | google-java-format AOSP 风格                     |
| 反向代理   | Caddy                     | 2-alpine     | 多副本部署时负载均衡                                     |

---

### 三、COLA 五层模块结构

```
knowledge-repository (parent POM)
├── knowledge-client        ← 对外契约：DTO / Cmd / Qry / ServiceI 接口
├── knowledge-domain        ← 领域层：Entity / VO / Gateway 端口 / 领域服务 / 领域事件
├── knowledge-infrastructure ← 基础设施层：Gateway 实现 / LLM Agent / 解析策略 / 持久化
├── knowledge-application   ← 应用层：CmdExe / QryExe 用例编排 / 系统配置 / 转换
└── knowledge-web           ← 适配层：Controller / Security Filter / Spring Boot 启动
```

**依赖方向**：`web → application → client + domain`，`infrastructure → domain`。domain 层保持纯净，不依赖任何外层。

**模块职责速查**：

| 模块             | 核心内容                                                                                         |
|----------------|----------------------------------------------------------------------------------------------|
| client         | 14 个 ServiceI 接口、50+ DTO（Cmd/Qry/VO）                                                         |
| domain         | 15 Entity、30+ VO、35+ Gateway 端口、`BlackboardAgent` / `QualityGate` 等领域服务、`TenantContext`      |
| infrastructure | MyBatis-Plus Mapper + DO、Milvus 向量网关、11 个 LLM Agent 实现、多格式解析策略栈、JWT/OIDC 安全、MinIO/本地双存储、分布式锁 |
| application    | 按子域组织的 CmdExe/QryExe、`SystemConfigService` 三级读取、`QuotaGuard` 配额拦截、DTO↔Entity 转换              |
| web            | 24 个 REST Controller、`AdminTokenAuthFilter` + `PlatformTokenAuthFilter` 双鉴权链、全局异常处理          |

---

### 四、核心业务域与流程

#### 4.1 文档管理域

**端到端流程**：上传 → 临时文件 → 文本提取（策略栈路由）→ 校验和去重 → 对象存储 → Document 聚合根 → 分块（5 种策略）→
向量化（Milvus）→ 领域事件。

**支持的文档格式**：PDF（PDFBox 文本提取）、DOCX/DOC（Word 段落）、XLSX/XLS（Excel 工作表）、PPTX/PPT（PowerPoint
幻灯片）、TXT/CSV/HTML/Markdown（纯文本）、RTF（Tika 兜底）、Vision 模型 OCR（特性开关，处理扫描件）。

**分块策略**（`ChunkingStrategyEnum`）：

| 策略         | 说明                                  |
|------------|-------------------------------------|
| FIXED_SIZE | Token 上限分块，段落优先 → 句子级回退 → 字符级兜底     |
| RECURSIVE  | 层级分隔符：双换行 → 单换行 → 中英文句界 → 空格 → 强制切分 |
| SENTENCE   | 中英文标点句界分块，合并至 Token 上限              |
| PAGE       | 一页一块                                |
| PARAGRAPH  | 段落边界合并，列表项感知，长段落回退句子切分              |

所有策略维护 `charPageMap`（字符偏移 → 页码映射），每个块携带 `startPage`/`endPage` 元数据，并继承文档的 RBAC
元数据（departmentId、visibility、allowedRoles、ownerId）。

**异步索引**：`IndexAsyncCmdExe` 通过 `CompletableFuture` + `IndexProgressCallback` 实现 SSE 实时进度推送。

#### 4.2 RAG 语义检索域

**检索流程**：查询 → 构建 Permission 上下文 → **超额取**（`max * 3 + 10`，上限 500）→ Milvus 向量搜索 + 租户过滤 → **后置
ACL 过滤**（`PermissionDomainService.hasAccess()`）→ 可选 Reranker 精排 → 截断 → 文档名回填。

**RBAC + 文档级 ACL**：

| 可见性级别      | 访问规则                   |
|------------|------------------------|
| PUBLIC     | 所有已认证用户                |
| INTERNAL   | 同部门用户                  |
| RESTRICTED | 角色交集匹配 `allowed_roles` |
| PRIVATE    | 仅文档所有者                 |

管理员短路所有 ACL 检查。缺失文档元数据 = 拒绝（最小权限原则）。

#### 4.3 AI 出题域（黑板架构 · 10 Agent）

**架构模式**：黑板模式（Blackboard Pattern），多个 LLM Agent 通过共享 `BlackboardState` 协作。

**7 步异步流水线**：

```
Step 1  知识检索 ─────────────────── 语义搜索（零结果时 LLM 补充）
         ↓
Step 2  Research Agent ‖ Scoring Agent ── 并行：知识点提取 ‖ 评分方案生成
         ↓
Step 3  Writer Agent ─────────────── 生成完整试卷 Markdown（重试轮读取审阅反馈）
         ↓
Step 4  AnswerKey ‖ Difficulty ‖ Bloom ── 并行：参考答案 ‖ 难度校准 ‖ 布鲁姆分类
         ↓
Step 5  Reviewer ‖ Deduplicator ──── 并行：质量审阅(0-100 分) ‖ 去重
         ↓
       [质量分 < 阈值(默认 80) → 自动重试 Step 3-5，最多 2 轮]
         ↓
Step 6  生成后拆分（V2）────────── Markdown → 结构化 ExamQuestion + 契约校验
         ↓
Step 7  状态决议 ───────────────── PUBLISHED / REVIEWABLE / VALIDATION_FAILED
```

**附加 Agent**（特性开关）：`ExamKnowledgeDedupAgent`（写后知识点去重）、`KnowledgeGapAnalyzerAgent`（反幻觉标记——无知识库证据的题目）。

**分布方案生成**（独立流程）：`ExamDistributionAgent` 多轮循环，4 阶段（分类 → 计数 → 赋分 → 评估），`ScorePlanValidator`
硬校验 + LLM 评估，连续轮次相同维度失败时收敛。

#### 4.4 AI 写作域

简化的 3 Agent 顺序流水线：知识检索 → Researcher Agent → 研究质量门禁 → Writer Agent → Reviewer Agent → 写作质量门禁 →
保存历史。复用 `BlackboardState` 和 `QualityGate` 基础设施，无并行、无重试循环。

#### 4.5 试卷审阅域（人机协同）

**1-Proof 审阅门**：AI 生成试卷 → 逐题人工审阅 → 契约校验 → 发布。

- 逐题编辑（内容、选项、正确答案、图片绑定）
- 重新拆分（`ResplitPaperCmdExe`，手动编辑后重跑 Markdown → 结构化）
- 审批发布（`ApprovePaperCmdExe`）：重跑 `ExamContractValidator`（确定性非 LLM 校验），通过则 PUBLISHED，不通过则阻塞发布（不可绕过）
- 作废（`VoidPaperCmdExe`）

#### 4.6 在线考试与阅卷域

**考试流程**：学生登录 → 浏览可用试卷 → 开始考试 → 渲染题目快照 → 答题（自动保存 + `sendBeacon` 防丢）→ 提交 → 状态转
SUBMITTED。

**阅卷引擎**：

- **并发控制**：fencing token 模式——`claimForGrading()` 原子 CAS `SUBMITTED → GRADING`，UUID 令牌。心跳续期，不匹配表示被抢占。
- **客观题**：直接比对（选择题字母提取、判断题 token 折叠、全半角转换），多选题支持半分（子集 = 半分，错选 = 0）。
- **主观题**：AI 评分——`StreamingChatGateway` 流式调用 LLM，评分 prompt 含题目 + 参考答案 + 学生答案，正则解析分数与理由。
- **终态**：原子 CAS `GRADING → AI_GRADED` + 总分。
- **定时触发**：`ExamGradingScheduler` 延迟可配（默认 30 分钟），支持批量异步触发。
- **人工覆写**：`ReviewAnswerCmdExe` 可覆盖 AI 评分。

#### 4.7 错题分析域

- 查询所有错题（`ExamAnswer` 中未满分行），分页 + 统计概览（总错题数、知识点数、学生数、最高频题型、平均得分率）。
- AI 汇总：最多 50 条错题构建 prompt → LLM 生成分析报告（错因分析、知识薄弱点、分题型表现、改进建议）。

#### 4.8 质量门禁系统

`QualityGate` 领域接口：`evaluate(phase, blackboard) → QualityGateResult`。

| 门禁                  | 检查内容                                               | 不通过行为                        |
|---------------------|----------------------------------------------------|------------------------------|
| ResearchQualityGate | 检索块数 ≥ `quality-gate.research.min-chunks`（默认 1）    | 0 块 → SKIP（走 LLM）；不足 → RETRY |
| WritingQualityGate  | 输出长度 ≥ `quality-gate.writing.min-length`（默认 50 字符） | 过短 → RETRY_CURRENT           |

所有门禁实现自动注入为 `List<QualityGate>`，编排器在每阶段后调用 `evaluateGates()`，结果记入审计日志和指标。

---

### 五、系统配置体系

**三级读取模式**（`SystemConfigService`）：

```
Tier 1  租户级  →  sys_config WHERE tenant_id = <当前租户>
         ↓ miss
Tier 2  平台级  →  sys_config WHERE tenant_id = 1（default 种子租户）
         ↓ miss
Tier 3  内置默认 →  SystemConfigDefaults.getDefault()（Java 硬编码，永不返回 null）
```

**缓存架构**：`ConcurrentHashMap<ConfigCacheKey(tenantKey, configKey), SystemConfig>`，按租户天然分片。TTL 默认 300 秒，Redis
pub/sub 跨实例即时失效。

**运行时可配参数**（20 项，6 大类）：

| 类别             | 配置项                                              | 默认值    | 说明               |
|----------------|--------------------------------------------------|--------|------------------|
| feature-toggle | `feature.vision.enabled`                         | false  | PDF 视觉 OCR 总开关   |
| feature-toggle | `feature.reranker.enabled`                       | false  | 搜索结果 Reranker 精排 |
| feature-toggle | `exam.quality-score-threshold`                   | 80     | 质量分重试阈值          |
| feature-toggle | `exam.review-required`                           | true   | 发布前人工审阅          |
| feature-toggle | `exam.knowledge-dedup.enabled`                   | true   | 写后知识点去重          |
| feature-toggle | `exam.plan.auto-balance.enabled`                 | true   | 分布方案自动平衡         |
| feature-toggle | `exam.plan.auto-balance.max-rounds`              | 3      | 最大平衡轮次           |
| exam           | `exam.grading-delay-minutes`                     | 30     | 自动阅卷延迟           |
| exam           | `exam.grading-timeout-minutes`                   | 15     | 阅卷超时             |
| llm            | `llm.chat.temperature`                           | 0.7    | 对话温度             |
| llm            | `llm.chat.max-tokens`                            | 4096   | 最大输出 Token       |
| llm            | `llm.chat.streaming-max-tokens`                  | 16384  | 流式最大 Token       |
| llm            | `llm.embedding.model-name`                       | bge-m3 | Embedding 模型     |
| search         | `search.max-results`                             | 20     | 最大检索结果数          |
| search         | `search.min-score`                               | 0.3    | 最低相似度            |
| quality-gate   | `quality-gate.research.min-chunks`               | 1      | 研究最低块数           |
| quality-gate   | `quality-gate.research.low-confidence-threshold` | 3      | 低置信阈值            |
| quality-gate   | `quality-gate.writing.min-length`                | 50     | 写作最低长度           |
| extraction     | `extraction.max-pages-per-doc`                   | 20     | 单文档最大解析页数        |

所有配置均可通过管理后台「系统配置」页面实时修改，即时生效。

---

### 六、SaaS 多租户架构

#### 6.1 三闸门控制

| 闸门    | 配置键                            | 默认    | 控制范围                                           |
|-------|--------------------------------|-------|------------------------------------------------|
| 多租户总闸 | `knowledge.saas.enabled`       | false | TenantLine 拦截、租户上下文、LLM profile                |
| 配额执行闸 | `knowledge.saas.quota.enabled` | false | QuotaGuard、用量采集、@Primary 装饰器                   |
| 平台管理闸 | `knowledge.platform.enabled`   | false | `/api/platform/**` 端点、Reconciler、Schema Runner |

附加：`knowledge.platform.oidc.enabled`（企业 IdP OIDC 校验，默认 false）。

#### 6.2 四层隔离策略

| 层级       | 隔离方式                                                                                  | 关键实现                                                              |
|----------|---------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| **数据库**  | `TenantLineInnerInterceptor` 自动注入 `WHERE tenant_id=?`；8 张平台表白名单豁免                     | `KnowledgeTenantLineHandler`                                      |
| **向量库**  | Phase 2：共享集合 + `metadata.tenant_id` JSON 谓词过滤；Phase 3 就绪：SILO 独立集合 `kc_{tenantKey}`   | `MilvusVectorStoreService` / `SiloCollectionNaming`               |
| **文件存储** | 路径前缀 `{tenantKey}/`——MinIO：`{tenantKey}/{category}/{yyyy}/{mm}/{uuid}.{ext}`；本地磁盘向后兼容 | `MinioObjectStorageGatewayImpl` / `LocalObjectStorageGatewayImpl` |
| **配置**   | 三级读取：租户 → 平台(default) → 内置默认；缓存按租户分片                                                  | `SystemConfigService`                                             |

#### 6.3 租户生命周期状态机

```
PENDING ─→ PROVISIONING ─→ ACTIVE ←→ SUSPENDED
              ↓                ↓
           FAILED          ARCHIVED ─→ DELETING ─→ DELETED
              ↑                ↓
              └── retry ──── PROVISIONING（重放）
```

**开通（Provisioning）**：4 步幂等状态机——校验 → 复制默认配置 → 创建根部门 → 创建首管理员（邀请码 72h
有效）。每步独立事务，失败可重放（已完成步骤自动跳过）。

**归档**：ACTIVE/SUSPENDED → ARCHIVED，设置 `archivedAt` 时间戳（30 天保留期起点），立即失效缓存。

**删除**：双人复核——操作员 A 签发审批令牌（SHA-256 哈希，原文仅展示一次）→ 保留期届满 → 操作员 B 确认（
`operatorId ≠ approverOperatorId`）→ ARCHIVED → DELETING → `TenantDeletionReconciler` 定时扫描（每 5 分钟，批量 20）→
两步硬删（PURGE_DB_ROWS + PURGE_VECTORS）→ DELETED 终态。

#### 6.4 认证与鉴权

**双 JWT 体系**：

| 体系        | 算法                           | 作用域                            | 载荷差异                                         |
|-----------|------------------------------|--------------------------------|----------------------------------------------|
| 租户管理员 JWT | HS256（`admin.jwt.secret`）    | `/api/admin/**`、`/api/exam/**` | `{sub, username, admin, roles[], tenantKey}` |
| 平台运维 JWT  | HS256（`platform.jwt.secret`） | `/api/platform/**`             | `{sub, username, scope:"platform"}`          |

`scope=platform` 声明为跨系统复用防护——即使密钥意外相同，管理员令牌也不会被平台过滤器接受。

**企业 IdP OIDC**：`PlatformTokenAuthFilter` 本地 JWT 不命中时回退 RS256/JWKS 校验（零新依赖，纯 JDK 加密）。三闸门门控，默认关闭。

**邀请式设密**：`SecureRandom` 32 字节 Base64URL → 仅存 SHA-256 哈希 → 原文一次性返回 → 落地页 `set-password.html`
五态分支（UNKNOWN/PENDING/USED/EXPIRED/REVOKED）。

#### 6.5 配额与计费

**用量采集**：`@Primary` 装饰器自动拦截——`UsageCapturingChatModelDecorator`（CHAT_TOKENS）、
`UsageCapturingEmbeddingModelDecorator`（EMBEDDING_TOKENS）。月度周期，原子递增。

**配额执行**（`QuotaGuard`）：写操作前置检查，三策略严重度排序：

| 策略       | 行为               | HTTP 状态码              |
|----------|------------------|-----------------------|
| BLOCK    | 拒绝请求             | 402 Payment Required  |
| THROTTLE | 限流 + Retry-After | 429 Too Many Requests |
| ALERT    | 告警日志，不阻塞         | —                     |

**用量指标白名单**：`CHAT_TOKENS` / `EMBEDDING_TOKENS` / `DOC_COUNT` / `STORAGE_BYTES`。

#### 6.6 Per-Tenant LLM Profile

`sys_tenant_llm_profile` 表存储每租户独立的 LLM API Key / Base URL / 模型配置。密钥通过 `SecretCipher`
端口加密（AES-256-GCM，主密钥文件 `/var/run/secrets/kr/master.key`），支持 KMS/Vault 扩展。`TenantLlmProfileResolver` 两级缓存（L1
本地 30s TTL → L2 DB 回源），解密失败拒绝调用并告警（不回退平台默认）。

---

### 七、部署拓扑

#### 7.1 基础设施（docker-compose.infra.yml）

| 服务     | 镜像             | 端口          | 用途                    |
|--------|----------------|-------------|-----------------------|
| mysql  | mysql:8.0      | 3307:3306   | 关系数据库                 |
| etcd   | etcd:v3.5.18   | 内部          | Milvus 元数据            |
| minio  | minio:latest   | 9001        | 对象存储（Milvus + 可选文档存储） |
| milvus | milvus:v2.5.4  | 19530, 9091 | 向量数据库                 |
| redis  | redis:7-alpine | 6379        | 配置缓存 + pub/sub        |

#### 7.2 应用（docker-compose.yml）

| 服务            | 端口   | JVM                     | 健康检查                              |
|---------------|------|-------------------------|-----------------------------------|
| knowledge-app | 8091 | -Xms512m -Xmx1024m G1GC | `/actuator/health` 每 15s，启动期 150s |

#### 7.3 多副本（docker-compose.scale.yml）

Caddy 2 反向代理 + 多 `knowledge-app` 副本（默认 2），应用端口仅绑定 127.0.0.1（Caddy 独享），Caddy 暴露 80/443 自动负载均衡。

---

### 八、前端页面总览

#### 8.1 管理后台（12 页面）

| 分组        | 页面                   | 功能                                                    |
|-----------|----------------------|-------------------------------------------------------|
| **概览**    | `index.html`         | 仪表盘：文档总数/已索引/处理中/失败，分类分布图，最近文档                        |
| **知识库**   | `documents.html`     | 文档管理：拖拽上传（≤200MB 分块）、解析预览、分块编辑、向量化 SSE 进度             |
|           | `search.html`        | 语义检索：可配用户 ID、最大结果数、管理员模式                              |
| **AI 工坊** | `ai-writing.html`    | AI 写作：3 Agent 流水线 + SSE 流式输出 + 写作历史                   |
|           | `ai-exam.html`       | AI 出题：3 步向导（分布方案 → 分值校验 → 10 Agent 生成）+ 考试历史          |
| **考试管理**  | `paper-review.html`  | 试卷审阅：逐题审阅、编辑、图片绑定、契约校验、审批发布                           |
|           | `exam-review.html`   | 阅卷审阅：批量/单次 AI 评分 SSE、人工覆写、发布成绩                        |
|           | `wrong-answers.html` | 错题本：筛选、AI 汇总分析、统计面板                                   |
| **系统管理**  | `users.html`         | 用户管理：CRUD、部门分配、管理员角色、启用/禁用                            |
|           | `departments.html`   | 部门管理：树形组织架构                                           |
|           | `system.html`        | 系统配置：6 大类 20 项运行时参数 + 运行状态（Embedding 模型、Milvus、知识库统计） |
| **认证**    | `login.html`         | 管理员登录                                                 |

#### 8.2 学生端（1 页面）

`exam.html`（~3300 行，自包含）：学生注册/登录 → 浏览可用试卷 → 开始考试 → 答题（自动保存 + `sendBeacon`）→ 倒计时（服务端时钟同步）→
提交 → 查看错题。

#### 8.3 邀请设密（1 页面）

`set-password.html`：新租户首管理员通过邀请码设置初始密码，五态分支引导。

#### 8.4 公共 JS（common.js）

- **认证**：JWT 存 `sessionStorage`，fetch 拦截器注入 `X-Admin-Token` + `X-Tenant-Id`（从 JWT payload 解析），EventSource
  拦截器追加 `access_token` 查询参数
- **布局**：分组侧边栏（5 组 11 项）+ 面包屑 + Toast/Modal/Confirm 组件
- **工具**：XSS 转义、Markdown 渲染、分页、图片灯箱、UUID/Token 生成

---

### 九、数据库迁移全景（V1–V35）

| 版本      | 说明                                                                                                                  |
|---------|---------------------------------------------------------------------------------------------------------------------|
| V1      | 初始 Schema：`sys_department` / `sys_user` / `sys_role` / `sys_user_role` / `kb_document` / `kb_document_chunk` + 种子数据 |
| V2      | `kb_document` 加 `chunking_strategy` 列                                                                               |
| V3      | 创建 `kb_writing_history`（AI 写作历史）                                                                                    |
| V4      | 创建 `kb_category` + document/chunk 加 `category` 列                                                                    |
| V5      | 创建 `kb_exam_history`（AI 出题历史）                                                                                       |
| V6      | 在线考试表：`kb_student` / `kb_exam_session` / `kb_exam_answer`                                                           |
| V7      | 考试时长 `duration_minutes`                                                                                             |
| V8      | 分布方案 `exam_plan`（JSON）                                                                                              |
| V9      | 阅卷溯源 `ai_input` / `ai_raw_output`                                                                                   |
| V10     | 评分明细 `score_detail`（JSON 6 维质量分）                                                                                    |
| V11     | 结构化题目 `kb_exam_question`                                                                                            |
| V12     | 试卷审阅 `reviewed_by` / `reviewed_time`                                                                                |
| V13     | 题目评分标准 `scoring_criteria`                                                                                           |
| V14     | 阅卷 fencing token `grading_token`                                                                                    |
| V15     | 用户认证 `password_hash`（BCrypt）+ `status`                                                                              |
| V16     | 文档图片 `kb_document_image`                                                                                            |
| V17     | 题目图片绑定 `images_json`                                                                                                |
| V18     | 考试作废 `voided` + 一人一卷复合索引                                                                                            |
| V19     | 解析缓存 `kb_extraction_cache`                                                                                          |
| V20     | 评分精度 `quality_score` INT → DECIMAL(5,2)                                                                             |
| V21     | 运行配置 `sys_config` + 6 项种子                                                                                           |
| V22     | 多会话登录 `kb_student_session`                                                                                          |
| **V23** | **SaaS 起点**：`sys_tenant` + `sys_tenant_placement`，种子默认租户                                                            |
| V24     | 15 张业务表加 `tenant_id` 列 + 回填                                                                                         |
| V25     | 6 个唯一索引切为租户维 `(tenant_id, key)`                                                                                     |
| V26     | `tenant_id` NOT NULL DEFAULT 1                                                                                      |
| V27     | 开通状态机 `sys_tenant_provision_step`                                                                                   |
| V28     | `sys_config` 租户维 + 邀请表 `sys_user_invite` + `must_change_password`                                                   |
| V29     | 归档与删除：`sys_tenant_delete_approval` / `sys_tenant_deletion_step` / `sys_audit_log`                                   |
| V30     | 解析缓存加租户维（闭 G7 跨租户泄漏）                                                                                                |
| V31     | Per-tenant LLM 配置 `sys_tenant_llm_profile`                                                                          |
| V32     | 用量计量 `tenant_usage`                                                                                                 |
| V33     | 套餐限额 `tenant_plan_limit`                                                                                            |
| V34     | 核心外键约束（13 CASCADE + 2 RESTRICT）                                                                                     |
| V35     | 种子 13 项运行时配置键（视觉/Reranker/LLM/检索/门禁/解析）                                                                             |

---

### 十、REST API 端点汇总

#### 10.1 知识检索

| 方法   | 路径                          | 说明     |
|------|-----------------------------|--------|
| POST | `/api/knowledge/search`     | 语义检索   |
| GET  | `/api/knowledge/categories` | 知识分类列表 |

#### 10.2 文档管理

| 方法     | 路径                                  | 说明        |
|--------|-------------------------------------|-----------|
| POST   | `/api/document/upload`              | 单文件上传     |
| POST   | `/api/document/upload/init`         | 分块上传初始化   |
| POST   | `/api/document/upload/chunk`        | 上传单块      |
| POST   | `/api/document/upload/complete`     | 完成上传组装    |
| GET    | `/api/admin/document/list`          | 文档列表      |
| GET    | `/api/admin/document/stats`         | 知识库统计     |
| POST   | `/api/admin/document/{key}/reindex` | 重新索引（SSE） |
| POST   | `/api/admin/document/{key}/reparse` | 重新解析      |
| POST   | `/api/admin/document/{key}/index`   | 确认索引（SSE） |
| DELETE | `/api/admin/document/{key}`         | 删除文档      |

#### 10.3 AI Agent

| 方法   | 路径                                       | 说明                    |
|------|------------------------------------------|-----------------------|
| POST | `/api/agent/exam/generate-stream`        | AI 出题（异步 7 步流水线，SSE）  |
| POST | `/api/agent/article/generate`            | AI 写作（异步 3 Agent，SSE） |
| POST | `/api/agent/wrong-answer/analyze-stream` | 错题分析（SSE）             |
| POST | `/api/agent/exam/distribution-stream`    | 分布方案生成（SSE）           |
| GET  | `/api/agent/exam/history`                | 出卷历史                  |
| GET  | `/api/agent/article/history`             | 写作历史                  |

#### 10.4 在线考试

| 方法   | 路径                       | 说明         |
|------|--------------------------|------------|
| GET  | `/api/exam/available`    | 可用试卷列表     |
| POST | `/api/exam/start`        | 开始考试       |
| POST | `/api/exam/{key}/save`   | 保存答案（断点续传） |
| POST | `/api/exam/{key}/submit` | 提交试卷       |

#### 10.5 阅卷与审阅

| 方法   | 路径                                                   | 说明            |
|------|------------------------------------------------------|---------------|
| POST | `/api/admin/exam-review/{key}/trigger-grading-async` | 触发 AI 阅卷（SSE） |
| POST | `/api/admin/exam-review/batch-trigger-grading-async` | 批量触发阅卷        |
| POST | `/api/admin/exam-review/answer/{id}/review`          | 人工覆写评分        |
| POST | `/api/admin/exam-review/{key}/publish`               | 发布成绩          |
| GET  | `/api/admin/paper-review/pending`                    | 待审阅试卷         |
| POST | `/api/admin/paper-review/{key}/approve`              | 审批发布          |
| POST | `/api/admin/paper-review/{key}/void`                 | 作废试卷          |

#### 10.6 认证

| 方法   | 路径                                       | 说明    |
|------|------------------------------------------|-------|
| POST | `/api/admin/auth/login`                  | 管理员登录 |
| POST | `/api/admin/auth/change-password`        | 修改密码  |
| GET  | `/api/admin/auth/invite-status`          | 邀请码预检 |
| POST | `/api/admin/auth/set-password-by-invite` | 邀请设密  |
| POST | `/api/student/auth/register`             | 学生注册  |
| POST | `/api/student/auth/login`                | 学生登录  |

#### 10.7 平台管理（`platform.enabled=true`）

| 方法              | 路径                                      | 说明             |
|-----------------|-----------------------------------------|----------------|
| GET/POST        | `/api/platform/tenants`                 | 租户列表 / 创建      |
| POST            | `/api/platform/tenants/{key}/provision` | 开通             |
| POST            | `/api/platform/tenants/{key}/suspend`   | 停用             |
| POST            | `/api/platform/tenants/{key}/archive`   | 归档             |
| POST            | `/api/platform/tenants/{key}/delete`    | 确认删除           |
| GET/POST/DELETE | `/api/platform/limits`                  | 配额 CRUD        |
| GET             | `/api/platform/billing/export`          | 计费导出           |
| GET/POST        | `/api/platform/schema-health`           | SILO Schema 巡检 |

#### 10.8 系统管理

| 方法      | 路径                             | 说明                     |
|---------|--------------------------------|------------------------|
| GET     | `/api/admin/system/status`     | 系统状态                   |
| GET/PUT | `/api/admin/system/configs`    | 配置读写                   |
| POST    | `/api/admin/config/invalidate` | 缓存失效（Redis pub/sub 广播） |
