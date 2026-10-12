# Knowledge Repository 项目能力总结

> 最近一次整理：2026-10-12。本文以当前代码（Flyway **V33**，5 模块 537 主 Java / 173 测试 / 24+ 数据表）为准，覆盖知识库 RAG、AI 出卷、在线考试、评分与错题本、文档配图、PDF 视觉增强、LLM 韧性护栏、试卷作废级联、管理/学生端鉴权、**SaaS 多租户 Phase 1–6 全链交付**（受 `knowledge.saas.enabled=false` + `knowledge.platform.enabled=false` + `knowledge.platform.oidc.enabled=false` + `knowledge.saas.quota.enabled=false` 四闸门默认关闭门控，生产态零装配）等全部已落地能力。架构流程图见 [docs/architecture-diagram.html](docs/architecture-diagram.html)。

## 项目概述

Knowledge Repository 最初是一个基于 LangChain4j + Milvus 的 RAG 知识库系统，现已演进为面向教育场景的一体化平台：在文档摄入、向量化、语义检索与权限隔离的基础上，扩展出多 Agent 黑板协作的 **AI 出卷流水线**、**出卷即切分落库**、**试卷校对与确定性内容门禁**、**试卷作废级联**、**学生在线考试**、**自动/人工评分**、**错题本**、**看图题配图**、**PDF 混合视觉解析（Phase A2/B/C/D）**、**LLM 三段式角色（chat/embedding/vision）+ Resilience4j 熔断/重试护栏**、**运行时特性开关（sys_config）**、以及管理端与学生端的 **无状态 JWT 鉴权**，并通过 **SSE** 全程实时推送生成/入库/评分进度。

在既有能力之上，正按变更提案 [docs/saas-multi-tenant-proposal.md](docs/saas-multi-tenant-proposal.md) 与执行计划 [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md) 推进 **SaaS 化改造（混合多租户）**：**Phase 1**（地基 + 迁移波 + 脊柱串联 P1-T1~T10）与 **Phase 2**（向量隔离 P2-T1~T5 · S-2=方案 b LangChain4j 单 store + `metadata.tenant_id` JSON 谓词）**已交付归档**，**Phase 3**（生命周期 + SILO 引入）**P3-T1~T4 + P3-T5 Archive&Delete 子切片 + P3-T6 SILO schema 巡检 runner + P3-T7 OIDC + P3-T7 邀请式设密回访子切片已交付**，**Phase 4**（配置隔离与密钥保管）**全量交付**，**Phase 5**（对象存储与水平扩展）**全量交付**，**Phase 6**（配额与计费）**全量交付**（详见「SaaS 多租户 · Phase 1–6 交付现状」节）。所有 SaaS 代码在四闸门门控下默认**不装配、不触碰数据**，现网行为零变化。

**技术栈：** Spring Boot 3.4.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3.0.4 / Apache Tika 3.1.0 / Apache POI 5.3.0 / Resilience4j 2.2.0 / H2 + MySQL 8 / Flyway / Lombok 1.18.36 / JDK HmacSHA256 (JWT)。

---

## 架构设计（COLA 5 层）

项目采用 **COLA 5.0** 分层，物理拆分为 5 个 Maven 模块（较早期 DDD 4 层新增了独立的 `knowledge-client` 契约层）：

```
knowledge-web             adapter 适配层     Controller、安全过滤器、SSE 进度 Store、全局异常、静态管理页
knowledge-application     app 应用层         用例编排（Executor）、事务边界、DTO ⇄ 领域对象转换，仅分发不含核心规则
knowledge-client          client 契约层      对外 Service 接口（*ServiceI）+ Cmd/Qry/DTO/VO，仅被依赖不反向依赖
knowledge-domain          domain 领域层      聚合根/实体/值对象、领域服务、Gateway 接口、领域事件，保持纯净
knowledge-infrastructure  infra 基础设施层   Gateway 实现、Mapper/DO、Milvus 向量存储、多格式解析、LLM/Embedding、黑板 Agent
```

依赖方向：`adapter → app → client`，`app → domain ← infrastructure`（infrastructure 反向实现 domain 的 Gateway 接口，依赖倒置）。domain 层不引用任何其它业务层，DO 不越过 infrastructure。

**代码规模（主源码 Java 文件数，2026-10-09 实测）：**

| 模块 | 主 Java | 测试 | 核心职责 |
|------|:------:|:----:|---------|
| knowledge-client | 75 | 0 | 14 个 `*ServiceI` 契约接口（含 **`PlatformTenantServiceI`** P3-T1 · **`AdminAuthServiceI`** P3-T7 回访追加 `queryInviteStatus`）+ 61 个 Cmd/Qry/VO/DTO（含 P3-T1~T5 租户 10 类 + **P3-T7 邀请式设密回访 3 类** `InviteStatusVO`/`TenantReissueInviteCmd`/`TenantReissueInviteVO`） |
| knowledge-domain | 102 | 14 | 1 聚合根（`Document`）+ **18 实体**（含 **`Tenant`/`TenantProvisionStep`/`TenantDeleteApproval`/`TenantDeletionStep`/`UserInvite`/`StudentSession`** P3-T2/T3/T5）+ 24 值对象（含 **`TenantKey`/`TenantContext`**）+ 14 领域服务 + **34 Gateway 接口**（新增 `TenantGateway`/`TenantProvisionStepGateway`/`TenantDeleteApprovalGateway`/`TenantDeletionStepGateway`/`TenantDataPurgeGateway`/`TenantDirectory`/`UserInviteGateway`/`StudentSessionGateway`/`PlatformJwtService`/`PlatformIdpTokenService` P3-T7 等 13 个）+ 4 领域事件 + `ProvisioningContext`/`ProvisioningStep` 契约（P3-T2） |
| knowledge-application | 172 | 99 | 15 特性包 Executor 用例编排（原 14 + **platform** P3-T1/T2/T3/T5：TenantCreate/TenantList/TenantGet/TenantProvision/TenantSuspend/TenantResume/ArchiveTenant/ConfirmTenantDeletion/TenantDeletionPreview 九个 CmdExe/QryExe；**+ P3-T7 邀请式设密回访 2 个** `InviteStatusQryExe`（adminauth 包）/`ReissueInviteCmdExe`（platform 包））+ **`ProvisioningStateMachine`**（P3-T2 状态机）+ **`TenantDeletionReconciler`**（P3-T5 幂等硬删续跑）+ **`TenantContextPropagatingExecutor`**（P1-T7 异步上下文透传）+ `TenantContextHolder`（P1-T5 ThreadLocal 租户上下文）+ 16 `*ServiceImpl` 分发（含 `PlatformTenantServiceImpl`） |
| knowledge-infrastructure | 156 | 44 | **20 GatewayImpl**（原 15 + `TenantGatewayImpl`/`TenantProvisionStepGatewayImpl`/`TenantDeleteApprovalGatewayImpl`/`TenantDeletionStepGatewayImpl`/`TenantDataPurgeGatewayImpl` + **`PlatformIdpTokenGatewayImpl`** P3-T7）+ 21 DO + 20 Mapper（含 `TenantPurgeMapper` 步骤级幂等硬删）+ 13 黑板 Agent + 11 解析策略 + 6 LLM 韧性封装 + **`security/oidc/` 包 4 类**（`JwkSetProvider`/`HttpJwkSetProvider`/`RsaJwk`/`PlatformIdpTokenGatewayImpl`，JDK `Signature`+`KeyFactory`+`java.net.http.HttpClient`，**零新依赖**）+ **`tenant/KnowledgeTenantLineHandler`+`TenantIdResolver`**（P1-T3/T4 MyBatis-Plus 拦截器）+ Milvus/Embedding/JWT/observability |
| knowledge-web | 32 | 16 | 19 Controller（含 **`PlatformTenantController`** P3-T1 `/api/platform/tenants/**` **11 端点**（P3-T7 回访追加 `POST /{tenantKey}/invites/reissue`）、**`AdminAuthController`** P3-T7 回访追加 `GET /api/admin/auth/invite-status`）、**`PlatformTokenAuthFilter`**（P3-T4 平台端独立鉴权 · P3-T7 追加 IdP token RS256/JWKS 校验分支）、**`TenantResolverFilter`**（P1-T5 请求头解析租户上下文）、`AdminTokenAuthFilter`（P3-T7 回访 `PUBLIC_PREFIXES` 追加 `invite-status`）、`HtmlCacheBusterFilter`、3 类 SseEmitter Store、WebSecurityConfig、全局异常、静态管理台（含 `set-password.html` P3-T7 四态引导 + 倒计时增强） |
| **合计** | **537** | **173** | 710 个 Java 文件；另有 Flyway 迁移 **33** 个（V1–V33，含 P4-T3 `V30__tenant_scope_extraction_cache.sql` · P4-T4 `V31__tenant_llm_profile.sql` · P6-T1 `V32__tenant_usage.sql` · P6-T2 `V33__tenant_plan_limit.sql`） |

---

## 领域模型

**聚合根：** `Document` —— 文档生命周期状态机（UPLOADED → PROCESSING → INDEXED → ARCHIVED / FAILED）。

**实体（18）：** `User`、`Department`、`Role`、`Student`、`DocumentChunk`、`DocumentImage`、`WritingHistory`、`ExamHistory`（AI 出卷主表）、`ExamQuestion`（拆分题目）、`ExamSession`（学生考试场次）、`ExamAnswer`（答题明细）、`SystemConfig`（V21 新增，运行时特性开关 + 参数）、`StudentSession`（V22，学生登录会话）、`Tenant`（**V23，SaaS Phase 1 · P1-T2** 租户主表）、`TenantProvisionStep`（**V27，P3-T2** 开通步骤）、`TenantDeleteApproval`（**V29，P3-T5** 删除审批令牌）、`TenantDeletionStep`（**V29，P3-T5** 硬删步骤）、`UserInvite`（**V28，P3-T3** 一次性设密邀请）。

**值对象（24）：** `Permission`（权限上下文）、`ChunkingConfig`、`ChunkingStrategyEnum`、`SearchResult`、`DocumentStatusEnum`、`DocumentVisibilityEnum`、`AdminTokenPayload`（JWT 载荷）、`BlackboardState`/`BlackboardPhase`/`BlackboardProgressEvent`（黑板协作与进度）、`ExamPlan`/`TypePlan`（题型分布方案）、`ExamAlertType`（试卷告警类型）、`ExtractedImage`/`ExtractionResult`/`DocumentImageHit`（图片提取产物）、`ExtractionPhase`/`ExtractionContext`/`ExtractionPageDetail`（Phase A2 抽取上下文）、`SystemConfigValue`（配置值封装）、**`TenantKey`（P1-T2 租户业务标识值对象）**、**`TenantContext`（P1-T5 租户上下文）** 等。

**领域服务（14）：**
- `DocumentIngestionDomainService` —— 文本分块（Token 上限、段落/页面边界、重叠）
- `PermissionDomainService` —— 构建 Milvus 过滤表达式，实现 RBAC + 文档级 ACL 隔离
- `ScoreRuleEngine` / `ScorePlanValidator` —— 分值规范归一、按题型权重分配、默认方案构建与校验
- `ExamAnswerNormalizer` —— 跨端统一的答案归一化（trim/大写/多选拆分），三处评分共用
- `ExamBlankCounter` —— 填空题空数统计（前后端唯一口径：连续下划线空 + 空括号累加）
- `ExamContractValidator` —— 出卷契约校验（题号/答案/选项/分值/出处题门禁），不过即 `VALIDATION_FAILED`
- `ExamMetaQuestionDetector` —— **确定性**识别「出处/位置类」记忆题（第几单元/哪一页等），供审核与发布双关卡复用
- `ExtractionStrategyStackParser` —— 抽取策略栈解析（MIME → 有序策略序列，支持 PDF_HYBRID 兜底路由）
- `BlackboardAgent` 接口 + `StreamingChatGateway`、`BlackboardProgressCallback`、`IndexProgressCallback`、`ExamGradingProgressCallback` —— 黑板 Agent 契约与流式/进度回调接口（由 web 层持有 SseEmitter 实现）

**Gateway 接口（34，`domain.gateway` 包，出入参纯领域对象）：** Document / DocumentChunk / DocumentImage / DocumentImageExtractor / DocumentExtraction / ExtractionCacheRepository（V19）/ VectorStoreGateway / Department / User / Student / StudentSessionGateway（V22）/ WritingHistory / AdminJwtService / PlatformJwtService（P3-T4）/ ExamHistory / ExamQuestion / ExamSession / ExamAnswer / ExamDistribution / ExamAlert / SystemConfig（V21）/ ChatModel / StreamingChatGateway / ContentExtractor / VisionChatGateway（Phase B）/ BlackboardAuditLog / BlackboardMetrics / PlatformAuditLog（P3-T5 审计）+ **SaaS Phase 1/3 新增**：`TenantGateway`（P1-T2）/ `TenantProvisionStepGateway`（P3-T2）/ `TenantDeleteApprovalGateway`（P3-T5）/ `TenantDeletionStepGateway`（P3-T5）/ `TenantDataPurgeGateway`（P3-T5）/ `TenantDirectory`（P1-T6 供 app 层反查租户）/ `UserInviteGateway`（P3-T3）/ **`PlatformIdpTokenService`（P3-T7 企业 IdP token 校验端口）**。

---

## 核心功能域

### 1. 文档摄入与多格式解析（Phase A2/C/D 策略化）
上传（`/api/document/upload`，含分片上传 `upload/init` → `upload/chunk` → `upload/complete`，Tika MIME 探测）→ 交给 **`CompositeExtractionService`** 按 `ExtractorRoutingProperties` 配置的策略栈路由 → 文本提取 → MD5/SHA-256 去重 → 建记录（状态 UPLOADED）→ 预览 → 确认入库（分块 + 批量向量化 + 写 Milvus + DB）。支持 PDF、Word(.docx)、Excel(.xlsx)、PowerPoint(.pptx)、TXT、CSV、HTML、RTF，上限 200MB。入库流程与「重新入库 / 自定义分块入库 / 重新解析」均异步执行，进度经 SSE（`IndexProgressCallback`）推送。

**解析策略栈（`infrastructure/extractor/`，11 个策略，按 MIME 有序回落）：** `PdfBoxExtractionStrategy`（PDF 按页 + 扫描型低文本告警）、`PdfHybridExtractionStrategy`（Phase C：低文本页送视觉模型 OCR，`replace_lowtext / append / replace_all` 三种合并模式，默认关闭）、`VisionModelExtractionStrategy`（Phase B：调用 `knowledge.llm.vision` 角色，默认 `paddleocr-vl` 本地端点，`local-cpu` 模式，`prefer-embedded-images` 优先复用内嵌位图）、`DocxExtractionStrategy`（每 30 段一 section）、`XlsxExtractionStrategy`（按 Sheet 保留行列）、`PptxExtractionStrategy`（按幻灯片遍历形状）、`PlainTextExtractionStrategy`、`TikaFallbackExtractionStrategy`（AutoDetectParser 兜底）、`PageRenderer`（PDF 页→图，150 DPI）、`VisionBudgetGuard`（Phase D：`daily-pages-global` + `enforcement=log-only|enforce` 成本护栏）、`ExtractionSupport`。默认路由仅 `[PDF_BOX, DOCX, XLSX, PPTX, PLAIN_TEXT, TIKA_FALLBACK]`，PDF_HYBRID/VISION 须显式开启且 `knowledge.llm.vision.enabled=true` 方参与，零副作用。

**解析结果持久缓存（V19 · Phase C）：** `kb_extraction_cache` 按「文件内容摘要 + 策略 + 模型 + 提示词摘要 + 渲染模式」复合键去重，`result_json` 存序列化 `ExtractionResult`；跨进程避免同一扫描件在重解析/reindex 时重复触发昂贵的视觉识别，命中率经 `ExtractionMetrics` 上报。

### 2. 向量检索与权限隔离
Embedding 经 `@ConditionalOnProperty` 在 DashScope（text-embedding-v3）/ Ollama（bge-m3，1024 维，当前默认）间切换。Milvus 集合 `knowledge_chunks`：`id`(VarChar36 PK) / `text` / `metadata`(JSON，含 document_key、visibility、department_id、allowed_roles、owner_id) / `vector`(1024 COSINE)。检索走 `AuthorizedSearchSupport` 的 over-fetch + `PermissionDomainService` 过滤，四档可见性（PUBLIC / INTERNAL / RESTRICTED / PRIVATE），超级管理员旁路。切换 embedding 维度须 drop 重建集合。

### 3. AI 出卷多 Agent 黑板流水线
`ExamGenerationSupport.executeExamPipeline` 编排：研究（researcher）∥ 评分规则（scoring）→ 写作（writer）→ 答案（answerKey）∥ 校准（calibrator）→ 审核（reviewer）∥ 查重（dedup）。逐 token 推 thinking + output，SSE 面板按 phase 呈现。质量分 `< QUALITY_SCORE_THRESHOLD(80)` 时最多 `MAX_REVIEW_RETRIES(2)` 轮打回重写，分值收敛 delta=3 提前停止；V20 起 `quality_score` 由 INT 改为 **DECIMAL(5,2)**，支持每节点独立打分与综合评分两位小数精细化。13 个 Agent 位于 `infrastructure/agent/`（文章生成 3 个 Researcher/Writer/Reviewer + 出卷 6 个 ExamResearcher/ExamScoring/ExamWriter/AnswerKeyGenerator/ExamCalibrator/ExamReviewer/ExamDeduplicator 与 ExamDistributionAgent + 应用层 2 个 ExamContentRenderAgent / ExamContentValidatorAgent + 流式支撑 BlackboardAgentStreamer）。产出 `ExamPlan{schoolLevel,totalFullMark,combined,subjects,types[TypePlan],manualAdjusted}`。

### 4. 出卷即切分落库
试卷生成后立即由 `ExamQuestionSplitSupport` 拆成 `kb_exam_question` 逐题行（含 `options_json`、`correct_answer`、`analysis`、`scoring_criteria`、`blank_count`、`images_json`），并跑 `ExamContractValidator`。`ExamPaperParser` 负责分节识别与题型归类：`matchPlanType` 双方剥括注后按 baseName 比较 + 回落顺序匹配，`resolveKernel(name, matched, rawHeading)` 让「（单选题）」这类括注参与关键词回落，修复了历史「整卷塌成 SHORT_ANSWER、选择题选项丢失」的问题。重新切分（resplit）按题号保留已绑定的 `images_json`。

### 5. 试卷校对与确定性内容门禁（Paper Review）
管理端 `PaperReviewController`（`/api/admin/paper-review`）提供逐题查看（stem/optionsJson/correctAnswer/analysis/imagesJson）、改答案、配图、resplit、approve。与「答卷校对 / 成绩复核」的 `ExamReviewController`（`/api/admin/exam-review`）职责不同。**双层防线**禁止「出处/位置类」记忆题（符合用户偏好，尤其语文/英语）：`ExamReviewerAgent` 尾部 `applyDeterministicMetaRecallCheck` 命中即把质量分压到 `DETECTOR_SCORE_CAP=70`（触发打回重写并把命中题面写进 reviewFeedback）；`ExamContractValidator` 逐题 `isMetaRecall(stem)` 命中即产 issue → `VALIDATION_FAILED` 阻止自动发布并在校对页 ❌ 清单显示。检测器刻意排除「段/篇」以免误伤阅读理解题。

### 5.5 试卷作废级联（Void Paper · V18）
`VoidPaperCmdExe` 提供教师端「作废一份 AI 试卷」用例（`PaperReviewController` 挂 `POST /api/admin/paper-review/{sessionId}/void`）。事务内两步：① `kb_exam_history.status` 由任意非 VOIDED 状态置为 `STATUS_VOIDED`，作废人与时间复用 `reviewed_by / reviewed_time` 审计列；② 级联把该卷下所有 `kb_exam_session.voided=TRUE`（V18 新增列 + 复合索引 `idx_exam_session_student_history`）。作废后学生不可再开考此卷，`ListPublishedHistoryQryExe` 会自动过滤掉非 PUBLISHED 状态；已有场次仍可显示与查阅，答题记录与评分轨迹保留，前端考生命运题（`exam.html`）与管理端试卷列表（`ai-exam.js`）在作废场次上展示「已作废」徽标。幂等：目标卷已是 VOIDED 时短路返回，但仍级联保证一致性。守卫测试：`VoidPaperCmdExeTest` + `ExamStartFromHistoryGuardTest`。

### 6. 学生在线考试
考生端 SPA `static/exam.html`，接口走放行清单内的 `/api/exam`（`ExamTakingController`，勿用受管理令牌保护的 `/api/agent`）。状态机 IN_PROGRESS → SUBMITTED → AUTO_GRADED → REVIEWED → PUBLISHED。倒计时 5min 橙 / 1min 红闪、归零锁定 + 30s 自动提交。填空题按 `data-slot` 归位、空数口径统一取自 `ExamBlankCounter`。看图题配图按印刷题号 ↔ questionNumber 注入 `q.images`，assetKey 须匹配 `^[a-f0-9]{32}$`。

### 7. 自动与人工评分
AI 评分（`ExamGradingServiceI` / examgrading 包）借助 `ExamScoringAgent`、`ScoreRuleEngine`、`ExamAnswerNormalizer`，评分进度经 `ExamGradingProgressCallback` 流式推送，`grading-delay-minutes`（默认 30）控制延迟。选择题按归一化答案比对，多选须集合相等，判断题不 emit options。CAS 双评 + Scheduler 超时恢复防漂移；`totalScore = Σ max_score`，未答记 null。评分轨迹与 token 记录落 `kb_exam_grading_trace` 等表。成绩复核 `ListAnswersWithGradingQryExe` 读 `images_json` 回填图片。

### 8. 错题本
由 `kb_exam_answer` 派生（`is_correct=false` 或未答 `effectiveScore<max` 也算错题）。学生端 `StudentWrongAnswerController`（`/api/student/wrong-answers`）与管理端 `WrongAnswerController`（`/api/admin/wrong-answers`）共用 `WrongAnswerConverter`，仅认 AI_GRADED / REVIEWED / PUBLISHED（AUTO_GRADED 不计），支持看图。

### 9. 看图题配图（图片资产库）
文档图片提取器（`DocumentImageExtractorGateway`）在摄入/回填时抽取正文图片，按内容 SHA-256 文档内去重落库 `kb_document_image`，二进制写入 `${knowledge.exam.asset-path}/<documentKey>/`。`assetKey = UUID.randomUUID().toString().replace("-","")`（匹配 `^[a-f0-9]{32}$`）。浏览器经 `ExamAssetController`（`GET /api/exam/assets/{assetKey}`，放行清单内）加载，题目侧以 `images_json = ["k1","k2"]` 绑定。管理端 `AdminDocumentImageController`（`/api/admin/exam-images`）提供按文档列表、关键词/文档搜索（供校对全局选图，534+ 张候选）、历史文档回填。当前配图来源为**人工 picker**（AI 出卷暂不产图）。

### 10. 鉴权与安全
管理端弃用 Basic Auth，改为账号密码 + **HS256 无状态 JWT**（V15）。`AdminTokenAuthFilter` 认 `X-Admin-Token` / `Bearer` / SSE `access_token` 参数，放行 health、`/api/student`、`/api/exam`、`auth/login|logout`。`sys_user` 加 `password_hash` + `status`（ACTIVE/DISABLED，默认 ACTIVE），种子 `admin/admin123`。`AdminJwtGatewayImpl` 用 JDK HmacSHA256 + Jackson 零依赖，密钥 `KNOWLEDGE_ADMIN_JWT_SECRET`。前端 fetch 注令牌、401 跳登录、EventSource 追 token、login.html sessionStorage 存令牌；管理端登录响应为**扁平体**（`token` 在顶层非 `data.token`）。学生端 `StudentAuthController`（`/api/student/auth`）独立。

### 11. AI 文章生成（黑板）
`ArticleAgentController`（`/api/agent`）以 Researcher∥Writer∶Reviewer 三 Agent 黑板协作从知识库检索并成文，同样 SSE 推进度、写 `writing_history`。与出卷共用 `BlackboardAgent` 契约与限流（`AgentExecutorFactory` 的 AbortPolicy → 满则 SSE error + 429）。

### 12. LLM 三段式角色与 Resilience4j 韧性护栏（Phase R1 / R2）
LLM 连接治理拆成 **chat / embedding / vision 三个独立角色**（`knowledge.llm.*`），各自 `base-url`+`api-key`+`model-name`+`temperature`+`max-tokens`+三段超时（`connect/read/call-timeout-seconds`），供应商由 base-url 决定并统一走 **OpenAI 兼容协议**，密钥通过 per-role env 注入便于独立轮换与最小权限。

- **chat**（默认 `deepseek-flash`）：出题 / 评分 / 黑板 Agent / 文章生成，`temperature` 0.7、`max-tokens` 4096、`call-timeout` 180s。**流式独立预算** `chat.streaming.max-tokens=16384` / `timeout-seconds=300`——推理模型（返回 `reasoning_content`）会把 token 耗在思考链，须显著大于非流式，避免正文被"喂空"。
- **embedding**（默认本地 Ollama `bge-m3`）：短平快高并发，`connect/read/call` 2/10/15s 与 chat 连接池隔离；Ollama 超时耗尽返**空串而非异常**，检索/生成分支须判空。
- **vision**（默认关闭，`LLM_VISION_ENABLED=true` 方启用）：Phase B 引入的第三种角色，`local-cpu` 模式指向 `paddleocr-vl` 或云端视觉端点，`temperature=0.0`，供 PDF_HYBRID 与图片抽取调用，OpenAI 兼容协议由 `OpenAiCompatibleVisionChatGateway` 实现。

**韧性护栏（`infrastructure/llm/` · Phase R2）：** `LlmResilience` 集中装配 Resilience4j `CircuitBreaker` + `Retry`；`ResilientChatModel` / `ResilientEmbeddingModel` / `ResilientStreamingChatGateway` 三个装饰器包裹 LangChain4j 原生模型；per-role 熔断窗口独立（一端点抖动不殃及他角色）—— chat `sliding-window=20 / failure-rate=50% / slow-call=60s / wait=30s / half-open-calls=5`；chat **重试 max-attempts=3 指数退避 500ms × 2**；chat-stream **max-attempts=1**（重试会向已吐字的 UI 重复推送增量，仅保留熔断快速失败）；embedding max-attempts=2 / 200ms；vision max-attempts=2 / 300ms。配置项 `knowledge.llm.resilience.{chat|chat-stream|embedding|vision}` 全部 env 可覆盖。

### 13. SaaS 多租户 · Phase 1–6 交付现状（受四闸门默认关闭门控）

> 权威文档：变更提案 [docs/saas-multi-tenant-proposal.md](docs/saas-multi-tenant-proposal.md)（架构决策）· 可执行计划 [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md)（Phase 0–6 任务清单）· Phase 2 生效基线 [docs/saas-phase2-go-checklist.md](docs/saas-phase2-go-checklist.md)（v0.3 已交付归档）· Phase 3 开工门禁 [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md)（**v0.3 全量授权开工** · 2026-10-09 五方书面齐签）· 会审决策建议 [docs/saas-phase3-review-brief.md](docs/saas-phase3-review-brief.md)（v0.2 定稿，S-2/S-3/T-2/R1 四条建议已被采纳）· Phase 4 门禁 [docs/saas-phase4-go-checklist.md](docs/saas-phase4-go-checklist.md)（**v0.4 全量交付** · 2026-10-12）· Phase 4 B-1/B-2 决策包 [docs/saas-phase4-b1-b2-decision-pack.md](docs/saas-phase4-b1-b2-decision-pack.md)（v0.3 交付回写）· 数据迁移总结 [docs/saas-migration-summary.md](docs/saas-migration-summary.md)（V23–V33 归档）· Phase 5 门禁 [docs/saas-phase5-go-checklist.md](docs/saas-phase5-go-checklist.md)（**v0.4 全量交付** · 2026-10-12）· Phase 6 门禁 [docs/saas-phase6-go-checklist.md](docs/saas-phase6-go-checklist.md)（**v0.2 全量交付** · 2026-10-12）。

**四闸门默认全关（生产态零装配 / 零数据触碰）：**

- `knowledge.saas.enabled=false` — 关闭整个 SaaS 脊柱（`TenantResolverFilter` / `KnowledgeTenantLineHandler` MyBatis-Plus 拦截器 / `PlatformTenantController` / `ProvisioningStateMachine` / `TenantDeletionReconciler` 全部不注册为 Bean，SQL 不注入 `tenant_id`）。
- `knowledge.platform.enabled=false` — 关闭平台管理员端点（`/api/platform/**` + `PlatformTokenAuthFilter`）。
- `knowledge.platform.oidc.enabled=false` — 关闭企业 IdP OIDC 校验分支（`PlatformIdpTokenGatewayImpl` / `HttpJwkSetProvider` 不装配；即便平台端点已开仍走本地平台 JWT）。
- `knowledge.saas.quota.enabled=false` — 关闭配额与计费拦截（`QuotaGuard` / `TenantUsageRecorder` / `PlatformBillingController` / `PlatformLimitController` 不装配；业务 API 无配额检查）。

四闸门独立，任一单独打开都不会绕过其它三道；全关时启动日志、健康端点、静态管理页、考生端与旧单租户业务链路完全等同 Phase 0 基线。

**Phase 1（地基 · 已交付归档）**：`P1-T1` Flyway `V23–V26` 建 `sys_tenant`/`sys_tenant_placement` + 15 张业务表加 `tenant_id NOT NULL DEFAULT 1` + V25 六张复合唯一切换 + V26 回填存量行为 `tenant_id=1`；`P1-T2` 域建模 `Tenant` 实体 + `TenantKey` 值对象 + `TenantContext`；`P1-T3` MyBatis-Plus `KnowledgeTenantLineHandler` + `P1-T4` `TenantIdResolver`；`P1-T5` `TenantContextHolder` ThreadLocal + `TenantResolverFilter`；`P1-T6` `TenantDirectory` domain 端口；`P1-T7` `TenantContextPropagatingExecutor` 异步上下文透传；`P1-T8` `TenantIgnoreHolder`（管理员旁路）；`P1-T9` 单测覆盖；`P1-T10` 规范回写。真实 MySQL 8 生产迁移冒烟全绿。

**Phase 2（向量隔离 · 已交付归档 · S-2=方案 b）**：`P2-T1~T5` 在 LangChain4j `MilvusEmbeddingStore` 单集合 `knowledge_chunks` 上引入 `metadata.tenant_id` JSON 谓词过滤，`AuthorizedSearchSupport` 拼接 `metadata["tenant_id"] == "<key>"` 前置条件；集合形态零改动、Milvus SDK 零引入、partition-key 与 SILO 目标态一并转 Phase 3 会审。`P2-T0` toy spike 已验语义正确与 JSON 谓词等价。

**Phase 3 已交付子切片（P3-T1~T4 部分授权 + P3-T5 Archive&Delete + P3-T7 OIDC + P3-T7 邀请式设密回访 全量授权段）**：

- `P3-T1` **`PlatformTenantController`** 平台管理端点 10 个（`/api/platform/tenants/**`：list / get / create / provision / suspend / resume / archive / delete / deletion-approval / deletion-preview），配套 `PlatformTenantServiceI` + 9 个 CmdExe/QryExe，commits `372994a`。
- `P3-T2` **`ProvisioningStateMachine`** 状态机（`PENDING → VALIDATING → PROVISIONING → ACTIVE`，任一步失败 → `FAILED` 或 `VALIDATION_FAILED`），`ProvisioningStep` 领域契约 + `TenantProvisionStep` 实体/DO/Mapper/Gateway，`V27__provision_state_machine.sql`，commit `55a0872`。
- `P3-T3` **建库默认配置**：新租户激活时写入 `sys_config` 默认参数 + `PUBLIC_PREFIXES` 与 `sys_role_menu` 种子；`V28__tenant_scope_sys_config_and_invite.sql` 给 `sys_config` 加 `tenant_id` 列并切租户内唯一约束；邀请式设密另立分支，commit `73b0626`。
- `P3-T4` **`PlatformTokenAuthFilter`**：平台端点独立鉴权，`X-Platform-Token` 或 `Bearer`，与 `AdminTokenAuthFilter` 完全解耦；SUSPENDED 状态软拦（管理员可管理但业务 API 403），commit `eece61a`。
- `P3-T5` **Archive & Delete 编排子切片**：归档 + 一次性双人复核删除令牌（`sys_tenant_delete_approval` 表，approver ≠ requester）+ `TenantDeletionReconciler` 步骤级幂等硬删续跑（`sys_tenant_deletion_step`，每步 `status=PENDING/RUNNING/DONE/FAILED`，重启可续）+ 只读 dry-run 预览（`TenantDeletionPreviewQryExe` 展示每表待删计数）+ `V29__tenant_archive_and_deletion.sql`（`sys_tenant` 加 `archived_at`/`purge_started_at`/`purge_completed_at` 三列 + 三新表 `sys_tenant_delete_approval`/`sys_tenant_deletion_step`/`sys_audit_log`）+ H2 全链路集成测试，commit `ed51f63`（分支 `feat/p3-t5-archive-delete`）。
- `P3-T7` **企业 IdP OIDC 接入**（C-3=方案 B）：`PlatformTokenAuthFilter` 追加 IdP token RS256 校验分支——本地平台 JWT 不命中时回退企业 IdP 令牌，通过 JWKS 缓存（`HttpJwkSetProvider` TTL 15min）拿 `PublicKey` 用 JDK `Signature.SHA256withRSA` 验签；domain 端口 `PlatformIdpTokenService` + infra `PlatformIdpTokenGatewayImpl`（claims 校验：`iss` 等值、`aud ∪ azp` 与 client-id 交集、`exp`/`nbf` 时钟偏差容忍、`sub` 非空、任一 blank fail-fast）+ `application.yml` 8 项 env placeholder，**零新依赖**（JDK 原生 `KeyFactory`/`java.net.http.HttpClient`），三重门控 `saas.enabled=false` + `platform.enabled=false` + `oidc.enabled=false`，commit `93dcbb9`。
- `P3-T7` **邀请式设密回访子切片**（承接第 15 条范围边界「②回访 P3-T3 邀请式设密」）：三段能力补齐——**落地页状态预检查** `GET /api/admin/auth/invite-status?code=XXX`（`InviteStatusQryExe` 走 `UserInviteService.findByRawCode` SHA-256 匹配 + 状态四态判定 `PENDING`/`USED`/`EXPIRED`/`REVOKED`/`UNKNOWN`；UNKNOWN 路径**不查 Tenant/User 网关**、`LinkedHashMap` 响应体仅 `status`+`hint`，与 USED/EXPIRED/REVOKED 大小近似一致抗枚举；PENDING 才附脱敏 `tenantKeyHint` 前 3 字符+`…` + `usernameHint` 首字符+`***` + `expiresAt`）· **平台运维重签** `POST /api/platform/tenants/{tenantKey}/invites/reissue`（`ReissueInviteCmdExe` 复用 `UserInviteService.issue` 语义零漂移，同 tenantKey+username 定位既有 PENDING → 全部 REVOKED + 落新；`revokedPrevious` 回显；审计 detail 不含 rawCode）· **前端 `set-password.html` 四态引导 + 倒计时**（预检查失败降级到允许提交但顶部 warn banner；`parseExpiresAt` 手工 regex 避开浏览器 `Date` 解析差异；60s 倒计时；UNKNOWN/PENDING/USED/EXPIRED/REVOKED 五态分支）；`AdminTokenAuthFilter.PUBLIC_PREFIXES` 追加 `invite-status`；测试 8+7+1 集成（`InviteStatusQryExeTest`/`ReissueInviteCmdExeTest`/`TenantProvisioningInviteIntegrationTest.inviteReissueFollowUpFullLoop`），三闸门保持默认 false，全 reactor `./mvnw test` 绿 1142 用例，spotless + checkstyle EXIT 0，commit `9aa358b`。

**Phase 3 未开工 / 阻塞项（透明声明）**：

- ⛔ **P3-T5 partition-key 切换子切片**（S-2=方案 c）：`MilvusSdkAdapter`（T-2=方案 B）骨架 + `knowledge_chunks_pk` partition-key 集合建表 + provider 多 store 缓存 —— **待运维 B-2 cluster 扩容 + 备份方案书面落定为硬前置**。P3-T0 万级 spike（`spikes/milvus-partition-key-scale/`）实测 partition-key 在 POOL 代表规模（10k–20k/≤100 租户 IVF_FLAT）下无剪枝性能收益、反较裸标量/JSON 谓词慢 1.2–2.5×；50k 档触发 standalone ~3.82 GiB OOM 已作为 B-1 内存预留证据。R1 决策不回退，仅将"性能收益"标注为未经证实。现网维持 Phase 2 单 store + JSON 谓词。
- ⛔ **P3-T6 SILO Flyway 逐库 runner**（T-3=方案 A collection-only 语义）：遍历 SILO 集合跑 schema 对齐 —— **依赖 P3-T5 partition-key/SILO 落地的 `SiloCollectionAdmin` 骨架 + B-2 cluster 前置**；现网 `sys_tenant_placement` 恒 POOL 无任何 SILO 集合可枚举，空跑无意义。**用户 2026-10-09 明确选方案 A：严格持 gate 暂缓 P3-T6**。
- ⏳ **P3-T7 IdP 首登绑定完整闭环**（P3-T3 邀请式设密 → IdP 首次登录绑定）：**落地页预检查 + 平台运维重签 + 前端四态引导子切片已交付**；**IdP 首登绑定 RBAC 角色映射 / 邀请码关联租户归属**未做，作为随客户 IdP 首次接入时按需排产的后续子切片。

**Phase 4（配置隔离与密钥保管 · 全量交付）**：

- `P4-T1` **`ConfigCacheKey(tenantKey, configKey)`** record 落 `domain/model/valueobject/`（T-2 定案方案 A）；`SystemConfigService` 三级读取顺序落码——**Tier 1 租户级 → Tier 2 平台级 `default` → Tier 3 `SystemConfigDefaults` 内置默认**；`PLATFORM_TENANT_KEY = TenantContext.DEFAULT_TENANT_KEY = "default"` 复用 V23 种子租户为平台锚；`lookupUnderTenant` helper + `TenantContext.callWithTenant` 作用域保护；`evictTenant` 兄弟前缀污染 bug 修复；`saas.enabled=false` 关闭态行为等价。配套 `SystemConfigServiceTenantIsolationTest` 4 例 + `SystemConfigServiceThreeTierReadTest` 13 例。
- `P4-T2` **Redis pub/sub 分布式失效**：`ConfigInvalidationPublisher` 端口 + `RedisConfigInvalidationPublisher`/`NoopConfigInvalidationPublisher` 双驱动 + TTL 兜底（`knowledge.config.cache.ttl-seconds` 默认 300）；**AES-256-GCM 本地主密钥**（B-2 方案 B，JDK 原生 `javax.crypto` 零新依赖）。
- `P4-T3` **V30 `kb_extraction_cache` 加租户维**：唯一键切 `(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)` + `TenantLineInnerInterceptor` 自动追加，闭提案 G7 跨租户解析缓存泄漏；`IGNORE_TABLES` 移除 `kb_extraction_cache`。配套 `ExtractionCacheTenantScopeMigrationTest` 3 例 + `ExtractionCacheTenantIsolationIntegrationTest` 1 例。
- `P4-T4` **V31 `sys_tenant_llm_profile`** + `TenantLlmProfileResolver` + `SecretCipher` domain 端口 + `LocalSecretCipher`/`NoopSecretCipher` 双驱动 + `KeyRotationCmdExe` 双人复核（走 P3-T5 审批令牌表模式）。

四闸门新增 `knowledge.saas.quota.enabled=false` 默认 false；CI 扩至 4 job（build · unit-tests · config-isolation · tenant-isolation），远端 CI 全绿。

**Phase 5（对象存储与水平扩展 · 全量交付）**：

- `P5-T1` **`ObjectStorageGateway`** 端口 + `LocalObjectStorageGatewayImpl`（默认）/ `MinioObjectStorageGatewayImpl` 双驱动 + MinIO SDK 8.5.17 + `@Configuration + @Bean + @ConditionalOnProperty` 条件装配；对象存储路径含 `{tenantKey}` 前缀实现租户物理隔离。
- `P5-T2` **`UploadSessionStore`** 端口 + `RedisUploadSessionStore`/`NoopUploadSessionStore` 双驱动，解决多副本部署下上传会话状态共享。
- `P5-T3` **assetKey 租户校验**：`TenantLineInnerInterceptor` 自动追加 `tenant_id` 条件到 assetKey 查询 SQL，防止跨租户 assetKey 猜测。
- `P5-T4` **compose 去固定化** + `docker-compose.scale.yml` Caddy 前置反向代理，支撑应用层水平扩展。
- `P5-T5` **`DistributedLockGateway`** 端口 + `TenantGateway.listActiveByCursor` 租户游标遍历 + `ExamGradingScheduler` 租户感知改造；`saas.enabled=false` 时 `NoopDistributedLockGateway` 占位。

Phase 5 退出条件达成（2 副本无冲突 · `ConcurrentHashMap.*upload` 零命中 · 对象存储路径含 `{tenantKey}` 前缀 · 定时任务分布式锁 + 租户游标分片），全 reactor `./mvnw test` 绿。

**Phase 6（配额与计费 · 全量交付）**：

- `P6-T1` **V32 `tenant_usage`** + `TenantUsageRecorder` 端口 + `TenantUsageRecorderImpl`/`NoopTenantUsageRecorder` 双驱动 + `UsageCapturingChatModelDecorator`/`UsageCapturingEmbeddingModelDecorator` @Primary 装饰器自动采集 + `UsageMetric` 白名单枚举（`CHAT_TOKENS`/`EMBEDDING_TOKENS`/`DOC_COUNT`/`STORAGE_BYTES`）。
- `P6-T2` **V33 `tenant_plan_limit`** + `TenantPlanLimitGateway` + `TenantPlanLimit` VO + `QuotaPolicy` 枚举（`BLOCK`/`THROTTLE`/`ALERT`）+ `PlatformLimitController` CRUD。
- `P6-T3` **`QuotaGuard`**（`@ConditionalOnProperty quota.enabled=true`）三策略 `BLOCK > THROTTLE > ALERT` + `QuotaExceededException` → 402 / `ThrottleRequiredException` → 429 + `GlobalExceptionHandler` 适配 + 6 CmdExe 集成（upload×3 + exam×2 + article×1）。
- `P6-T4` **`PlatformBillingController`** CSV/JSON 导出 + `PlatformLimitController` GET/POST/DELETE。

`knowledge.saas.quota.enabled` 默认 false，全 reactor BUILD SUCCESS，16 项 B/S/T/C 全项已落地。

**X-T5 前端租户透传（已交付）**：`common.js` fetch 拦截器自动附加 `X-Tenant-Id` 请求头，前端根据当前登录上下文解析租户标识并透传至后端，配合 `TenantResolverFilter` 完成多租户请求链路闭环；`saas.enabled=false` 时拦截器不附加头，行为等同单租户基线。

**红线遵守**：全程 `saas.enabled=false` + `platform.enabled=false`（+ `oidc.enabled=false` + `quota.enabled=false`）四闸门默认关闭；生产 MySQL 8 已实跑 V21→V33 schema 校验全绿但业务默认租户 id=1 行为与 Phase 0 完全一致；每切片交付均触发全 reactor `./mvnw test` BUILD SUCCESS（Phase 4-6 交付点全 reactor绿，CI 4 job 全绿），spotless + checkstyle 通过；无新增 Maven 依赖（红线 #11）；跨层依赖遵循 domain 端口 + infra GatewayImpl 反向实现。

**治理零漂移**：Phase 3–6 交付链在多份文档同步回写（AGENTS.md §1 分册地图 Phase 3–6 行 + §2 决策树 · execution-plan 头部状态戳与 §5–§8 任务表 · 各 Phase go-checklist 状态升版），版本戳随交付升版（Phase 4 v0.4 · Phase 5 v0.4 · Phase 6 v0.2）。

---

## SSE 实时进度模式
`SseEmitter` 仅存在于 web 模块。domain 层只定义回调接口（BlackboardProgress / IndexProgress / ExamGradingProgressCallback），web 层的 Store 持有 emitter 并给出实现，ApplicationService 方法接收回调参数异步推送。前端**先建 EventSource 再 POST**（sessionId/streamId 前端生成）以免丢事件；SSE 写须 `synchronized(emitter)`；批量任务多回调共享 streamId、payload 带 sessionId 标签、Controller 用 AtomicInteger 计数归零发 BATCH_COMPLETE 后关闭。出卷进度面板 `ai-exam.js` 的 PHASE 监听只读 phase 结构化字段。DeepSeek-flash 返回 `reasoning_content` → thinking/output 双流。

---

## REST API 清单（按控制器分组）

| 控制器 | 基路径 | 端点数 | 职责 |
|--------|--------|:----:|------|
| DocumentUploadController | `/api/document` | 6 | 上传 / 分片上传（init·chunk·complete·status·cancel）/ 仅提取文本（状态 UPLOADED） |
| DocumentAdminController | `/api/admin/document` | 15 | 详情/多路列表/统计/分类统计/归档/删除/预览/入库/自定义分块/重新入库/重新解析/入库进度 SSE |
| KnowledgeQueryController | `/api/knowledge` | 2 | 语义检索（带权限过滤）+ 分类枚举 |
| ExamController | `/api/agent` | 12 | AI 出卷流水线、generate-stream、export-word、出卷历史、分发/校验 SSE |
| PaperReviewController | `/api/admin/paper-review` | 7 | 试卷校对：逐题查看、改答案、配图、resplit、approve、**作废（void）** |
| ExamReviewController | `/api/admin/exam-review` | 10 | 答卷校对 / 成绩复核（学生提交）、批量触发评分 SSE |
| ExamTakingController | `/api/exam` | 7 | 学生开考、可用试卷、作答保存、交卷、结果、答案回读 |
| ExamAssetController | `/api/exam/assets` | 1 | 配图二进制流式下载（放行） |
| AdminDocumentImageController | `/api/admin/exam-images` | 3 | 按文档列表配图、全局搜索、历史回填 |
| WrongAnswerController | `/api/admin/wrong-answers` | 3 | 管理端错题本（列表/统计摘要/学生列表） |
| StudentWrongAnswerController | `/api/student/wrong-answers` | 1 | 学生端错题本 |
| AdminAuthController | `/api/admin/auth` | 4 | 登录/登出/当前身份/改密 |
| StudentAuthController | `/api/student/auth` | 4 | 学生注册/登录/登出/身份 |
| UserAdminController | `/api/admin/user` | 5 | 用户管理 CRUD |
| DepartmentAdminController | `/api/admin/department` | 4 | 部门管理（含树形） |
| SystemConfigController | `/api/admin/system` | 3 | **V21 新增**：`GET /status` 系统状态 + `GET /configs` 特性开关/参数列表 + `PUT /configs` 运行时热更新（三层：内存缓存→DB→系统默认） |
| ArticleAgentController | `/api/agent` | 4 | 文章生成/进度 SSE/写作历史 |
| WrongAnswerAnalysisController | `/api/admin/wrong-answers/analysis` | 2 | 错题分析聚合（题型分布 / 知识点分布） |
| **PlatformTenantController** | `/api/platform/tenants` | **10** | **SaaS Phase 3 · P3-T1 新增**：list / get / create / provision / suspend / resume / archive / delete / deletion-approval / deletion-preview（四闸门全关时不注册） |
| **PlatformSchemaHealthController** | `/api/platform/schema-health` | **2** | **SaaS Phase 3 · P3-T6 新增**：`GET` / `POST` schema 健康巡检（IDLE/HEALTHY/DEGRADED，四闸门全关时不注册） |
| **PlatformBillingController** | `/api/platform/billing` | **2** | **SaaS Phase 6 · P6-T4 新增**：用量 CSV/JSON 导出（`quota.enabled=true` 时可用） |
| **PlatformLimitController** | `/api/platform/limits` | **4** | **SaaS Phase 6 · P6-T2/T4 新增**：套餐限额 CRUD（GET/POST/DELETE，`quota.enabled=true` 时可用） |

共 **22 个业务 Controller + 3 个安全/上下文过滤器（`AdminTokenAuthFilter` + `PlatformTokenAuthFilter` P3-T4/T7 + `TenantResolverFilter` P1-T5）+ 3 个 SSE 进度 Store + 全局异常处理**。

---

## 数据库设计（Flyway V1–V33 · 24+ 表）

H2（开发）+ MySQL 8（生产，容器 `knowledge-mysql`，宿主机端口 3307，库 `knowledge_repository`）。必备字段 `id`/`create_time`/`update_time`，索引命名 `pk_/uk_/idx_`。迁移演进：

| 版本 | 迁移 | 引入 |
|------|------|------|
| V1 | init_schema | sys_department、sys_user、sys_role、sys_user_role、kb_document、kb_document_chunk |
| V2 | add_chunking_strategy | 分块策略字段 |
| V3 | add_writing_history | kb_writing_history（AI 文章） |
| V4 | add_category | 文档分类（kb_category） |
| V5 | add_exam_history | kb_exam_history（AI 出卷主表） |
| V6 | add_online_exam | kb_exam_session、kb_exam_answer、kb_student（在线考试） |
| V7 | add_exam_duration | 考试时长 |
| V8 | add_exam_plan | exam_plan（题型分布方案 JSON） |
| V9 | add_exam_grading_trace | kb_exam_grading_trace（评分轨迹） |
| V10 | add_exam_score_detail | 得分明细 |
| V11 | add_exam_question | kb_exam_question（出卷即切分逐题，按 session_key） |
| V12 | add_exam_paper_review | 试卷校对状态 |
| V13 | add_exam_question_scoring_criteria | scoring_criteria 列 + SplitSupport |
| V14 | add_exam_grading_token | 评分 token 记录 |
| V15 | add_user_auth | sys_user.password_hash + status，管理端 JWT |
| V16 | add_document_image | kb_document_image（配图资产） |
| V17 | add_exam_question_images | kb_exam_question.images_json |
| V18 | add_exam_session_voided | kb_exam_session.voided 布尔列 + 复合索引 `idx_exam_session_student_history`，支撑试卷作废级联与「一人一卷一次」开考守卫 |
| **V19** | **create_extraction_cache** | **kb_extraction_cache**：解析结果持久缓存（Phase C），按「文件摘要+策略+模型+提示词+渲染模式」复合键去重，`result_json` 存序列化 ExtractionResult，避免同一扫描件在重解析/reindex 时重复触发昂贵的视觉识别 |
| **V20** | **exam_score_precision** | 出卷质量评分精度：`kb_exam_history.quality_score` 由 INT 改为 **DECIMAL(5,2)**，支持两位小数（如 80.58），为每节点独立打分与综合评分精细化 |
| **V21** | **create_sys_config** | **sys_config**：运行时特性开关 + 可调参数表，三层获取（内存缓存 → DB → 系统默认），配套 SystemConfigController GET/PUT 支持热更新，无需重启 |
| **V22** | **create_student_session** | **kb_student_session**：学生登录会话表（服务端可撤销 token 存储，`saas.enabled=false` 门控下不参与查询），支撑多端登出与令牌轮换 |
| **V23** | **create_sys_tenant** | **SaaS Phase 1 · P1-T1**：**sys_tenant**（`tenant_key` VARCHAR(64) 全局唯一 + `name` + `plan` + `status ∈ {ACTIVE,SUSPENDED,ARCHIVED,DELETED}` + `data_region` 合规预留）+ **sys_tenant_placement**（`isolation_mode ∈ {POOL,SILO}` + `milvus_collection` + `shard_key`）。两张平台级元数据表不带 `tenant_id` 列（进 P1-T7 拦截器 ignore 名单）。种子：`tenant_key='default'` 的默认租户 + POOL 放置，保证 `saas.enabled=false` 时单租户语义可平滑回退 |
| **V24** | **add_tenant_id_columns** | **SaaS Phase 1 · P1-T4**：15 张业务表加 `tenant_id BIGINT NULL`（先 nullable 使 `saas.enabled=false` 阶段行为零变化，收紧在 V26）——kb_document / kb_document_chunk / kb_document_image / kb_category / kb_exam_history / kb_exam_session / kb_exam_question / kb_exam_answer / kb_student / kb_student_session / kb_writing_history / kb_exam_grading_trace / kb_exam_score_detail / kb_exam_grading_token / sys_user；平台表 sys_tenant / sys_tenant_placement 不在此列。Phase 4 才租户化的 sys_config（P4-T1）与 kb_extraction_cache（P4-T3）此处不加列避免跨 Phase 漂移。回填用 scalar 子查询取默认租户 id（ANSI，H2/MySQL 双方言），不假设常量 |
| **V25** | **switch_unique_indexes** | **SaaS Phase 1 · P1-T5**：唯一约束从"全局唯一"切"租户内唯一"——`uk_category_name → uk_category_tenant_name(tenant_id, name)`、`uk_student_username → uk_student_tenant_username(tenant_id, username)` 等六张表；保留全局唯一：`kb_document_image.asset_key`（内容哈希跨租户去重）、`kb_document_image(document_id, sha256)`、`kb_student_session.session_token`（UUID）、`sys_user_role(user_id, role_id)`（两端已随各自租户）。V24 已把全部存量行回填到 default 租户，加复合唯一与旧全局唯一等价、无损 |
| **V26** | **make_tenant_id_not_null** | **SaaS Phase 1 · P1-T6**：`tenant_id` 由 NULL 收紧 `NOT NULL DEFAULT 1`（DEFAULT 兜底保证 P1-T7 拦截器未注册的过渡期存量写路径不违约）+ `MODIFY COLUMN` H2/MySQL 双方言已验；同时 DROP 六张已具 `UNIQUE(tenant_id, key)` 复合前导索引的表上 V24 冗余单列 idx_<t>_tenant 以降写放大，其余 9 张保留单列 tenant 索引。生产 MySQL 8 冒烟全绿——默认租户 ACTIVE id=1 |
| **V27** | **provision_state_machine** | **SaaS Phase 3 · P3-T2**：新表 **sys_tenant_provision_step**（`provision_request_id` + `step_name` + `status ∈ {PENDING,RUNNING,DONE,FAILED,SKIPPED}` + `error_detail` + `started_at`/`completed_at`）+ `sys_tenant` 加 `provision_request_id VARCHAR(64)` 追踪列；S-5=方案 A（UUID v4 幂等键 + Flyway 版本号天然幂等 + `hasCollection` 前置查 + upsert + FAILED 可查 error_detail），支撑开通从单事务写入拆成可幂等重放的多步编排 |
| **V28** | **tenant_scope_sys_config_and_invite** | **SaaS Phase 3 · P3-T3**（C-4=方案 A 提前 P4-T1）：三件事——① `sys_config` 加 `tenant_id NOT NULL DEFAULT 1` 并把唯一键从 `(config_key)` 切 `(tenant_id, config_key)`，V21 存量 6 行 + 运行期新增由 DEFAULT 1 落 default 租户与既有单租户读路径等价；② 新表 **sys_user_invite**（一次性设密邀请，`invite_code_hash` SHA-256 存 64 hex，raw code 仅在 provision 响应体一次性返回不落库/不落日志）；③ `sys_user` 加首登强制改密标记列。`sys_user_invite` 进 P1-T7 `IGNORE_TABLES` 白名单避免自引用悖论 |
| **V29** | **tenant_archive_and_deletion** | **SaaS Phase 3 · P3-T5**（S-4=方案 A）：`sys_tenant` 加 `archived_at`（保留期起点）/ `deleted_at`（终态时间）/ `deletion_request_id`（删除幂等键 UUID）三列，`status` 取值扩展新增 **DELETING**（ARCHIVED→DELETING→DELETED 中间态，reconciler 认领标记）；新表 **sys_tenant_delete_approval**（一次性删除审批令牌，双人复核载体，SHA-256 存 hash，raw token 不入库）/ **sys_tenant_deletion_step**（步骤级幂等硬删账本，镜像 provision_step，重启可续跑）/ **sys_audit_log**（平台端高敏操作 DB 持久化审计，Phase 4 合规替换 SLF4J 审计）；文件层物理清理受 P5-T1 MinIO 约束延后。双闸门下仅 schema 演进，运行期行为不变 |
| **V30** | **tenant_scope_extraction_cache** | **SaaS Phase 4 · P4-T3**：`kb_extraction_cache` 加 `tenant_id NOT NULL DEFAULT 1` + 唯一键切 `(tenant_id, checksum, strategy, model_name, prompt_hash, render_mode)`，`TenantLineInnerInterceptor` 自动追加 `tenant_id`；闭 G7 跨租户解析缓存泄漏；`IGNORE_TABLES` 白名单移除 `kb_extraction_cache`（当前 8 张） |
| **V31** | **tenant_llm_profile** | **SaaS Phase 4 · P4-T4**：新表 **sys_tenant_llm_profile**（per-tenant LLM/embedding profile，`api_key_encrypted` AES-256-GCM 密文 + `model_name` + `base_url` + `temperature` + `max_tokens` + `enabled`），配套 `SecretCipher` 端口 + `LocalSecretCipher`/`NoopSecretCipher` 双驱动 + `KeyRotationCmdExe` 双人复核 |
| **V32** | **tenant_usage** | **SaaS Phase 6 · P6-T1**：新表 **tenant_usage**（租户用量计量，`usage_metric` + `counter_value` + `period_start`/`period_end` + `tenant_id`），配套 `TenantUsageRecorder` 端口 + `TenantUsageRecorderImpl`/`NoopTenantUsageRecorder` 双驱动 + `UsageCapturingChatModelDecorator`/`UsageCapturingEmbeddingModelDecorator` @Primary 装饰器自动采集 + `UsageMetric` 白名单枚举（`CHAT_TOKENS`/`EMBEDDING_TOKENS`/`DOC_COUNT`/`STORAGE_BYTES`） |
| **V33** | **tenant_plan_limit** | **SaaS Phase 6 · P6-T2**：新表 **tenant_plan_limit**（租户套餐限额，`plan_tier` + `usage_metric` + `limit_value` + `policy` ∈ {`BLOCK`,`THROTTLE`,`ALERT`} + `tenant_id`），配套 `TenantPlanLimitGateway` + `QuotaGuard` 三策略前置拦截 |

关系：`kb_exam_history(session_id) ↔ kb_exam_question(session_key)`；`kb_exam_session(exam_history_id) ↔ kb_exam_answer(session_id)`；错题本派生自 `kb_exam_answer.is_correct=false`。`knowledge_document` 外键 = `base_id`。运维脚本 `.buckups/data/clean-exam-data.sh`（本地运维脚本，未纳入版本控制）备份后 TRUNCATE 4 张考试表、保留 `kb_student`（`--dry-run` / `--yes` / `--with-students` / `--no-backup`）。

---

## 配置与特性开关

以 `application.yml` 为准，均可用环境变量覆盖：

- `knowledge.llm.chat`：对话角色（出题/评分/黑板/文章），`base-url`+`api-key`+`model-name` 决定供应商（默认 DeepSeek `deepseek-flash`）；`temperature` 0.7、`max-tokens` 4096；per-role 超时 `connect/read/call-timeout-seconds` 5/120/180；**流式独立预算** `chat.streaming.max-tokens` 16384 / `timeout-seconds` 300（避免推理模型思考链耗尽预算喂空正文）。密钥 env：`LLM_CHAT_API_KEY`
- `knowledge.llm.embedding`：向量角色，`base-url`+`api-key`+`model-name` 决定供应商（默认本地 Ollama `bge-m3`）；per-role 短超时 `connect/read/call-timeout-seconds` 2/10/15，与 chat 连接池隔离。密钥 env：`LLM_EMBED_API_KEY`
- **`knowledge.llm.vision`（Phase B 新增）**：视觉角色，`enabled` 默认 **false**（`LLM_VISION_ENABLED` 打开），`mode` 默认 `local-cpu`，`base-url` 默认 `http://localhost:8080/v1`（PaddleOCR-VL），`model-name` `paddleocr-vl`，`temperature=0.0`，`prefer-embedded-images=true` 优先复用 PDF 内嵌位图；`connect/read/call-timeout-seconds` 3/30/60。密钥 env：`LLM_VISION_API_KEY`
- **`knowledge.llm.resilience`（Phase R2 新增）**：per-role Resilience4j 熔断/重试参数（chat 熔断滑窗 20 / 失败率 50% / 慢调 60s / 半开 5 / 等 30s；chat retry 3 次 500ms×2；chat-stream retry 1 次；embedding retry 2 次 200ms；vision retry 2 次 300ms）
- **`knowledge.extractor`（Phase A2/C 新增）**：`enabled-strategies` 白名单（默认 `[PDF_BOX, DOCX, XLSX, PPTX, PLAIN_TEXT, TIKA_FALLBACK]`，PDF_HYBRID/VISION 不入默认）；`routing` 允许按 MIME 覆写有序策略栈（如 `application/pdf: [PDF_HYBRID, PDF_BOX]`）；`default-stack` 兜底；`vision.enabled` 与 `knowledge.llm.vision.enabled` 二者同真才启用视觉增强；`vision.trigger=scanned_only|image_heavy|always`，`merge=replace_lowtext|append|replace_all`，`render-fallback-dpi=150`，`min-text-len-per-page=200`，`max-pages-per-doc=20`，`max-bytes-per-image=4MB`；**Phase D 成本护栏**：`vision.budget.daily-pages-global`（默认极大）+ `vision.budget.enforcement=log-only|enforce`
- `knowledge.milvus`：host/port/collection=`knowledge_chunks`/dimension=1024/database=default
- `knowledge.blackboard.search`：max-results 10 / min-score 0.5
- `knowledge.storage.path`：`./data/documents`
- `knowledge.admin.jwt`：secret（`KNOWLEDGE_ADMIN_JWT_SECRET`）/ expiration-hours 8
- `knowledge.exam.grading-delay-minutes`：30
- `knowledge.exam.review-required`（`EXAM_REVIEW_REQUIRED`，**默认 true**）：true=所有试卷须人工校对通过后发布；false=校验通过 + 质量达阈可自动发布，但校验不过/低分仍强制人工校对（不可绕过）
- `knowledge.exam.asset-path`：`./data/exam-assets`（看图题配图二进制目录）
- **`sys_config`（V21 新增）**：运行时特性开关 + 参数表，三层获取（内存缓存 → DB → 系统默认），管理员通过 `PUT /api/admin/system/configs` 热更新，无需重启即可开启/关闭某项能力（如 `vision.enabled` 灰度）
- **`knowledge.saas.enabled`（SaaS Phase 1 · P1-T5 新增，**默认 false**）**：SaaS 脊柱总开关。false 时 `TenantResolverFilter` / `KnowledgeTenantLineHandler` MyBatis-Plus 拦截器 / `TenantContextPropagatingExecutor` 全部不装配，SQL 不注入 `tenant_id` 谓词；业务读写等同单租户基线。env：`KNOWLEDGE_SAAS_ENABLED`
- **`knowledge.platform.enabled`（SaaS Phase 3 · P3-T1/T4 新增，**默认 false**）**：平台管理员端点开关。false 时 `PlatformTenantController`（`/api/platform/tenants/**`）+ `PlatformTokenAuthFilter` 不注册；即便 `saas.enabled=true`，打开 `platform.enabled=true` 才允许平台端点做租户开通/暂停/归档/删除编排。env：`KNOWLEDGE_PLATFORM_ENABLED`
- **`knowledge.platform.oidc`（SaaS Phase 3 · P3-T7 新增，**默认 false**）**：企业 IdP OIDC 接入，三重门控中最后一段——即便 `saas.enabled=true` + `platform.enabled=true`，本项 false 时平台端点仍只认本地平台 JWT。8 项配置：`enabled`（`KNOWLEDGE_PLATFORM_OIDC_ENABLED`）/ `issuer` / `client-id` / `audience` / `jwks-uri` / `jwks-cache-ttl-seconds`（默认 900）/ `clock-skew-seconds`（默认 60）/ `jwks-connect-timeout-seconds`（默认 5）/ `jwks-read-timeout-seconds`（默认 15）。`enabled=true` 时启动 fail-fast 校验 issuer 非空且 `audience ∪ client-id` 至少一个非空

超时：Ollama 300s / 其它 120s。Ollama 超时耗尽返回空串（非异常），检索/生成分支均须判空。

---

## 基础设施与部署

**镜像与仓库**

- **CI 自动出镜像**：`.github/workflows/docker-image.yml` 每次 main 合并（且改到 Java / pom / Dockerfile / docker / .mvn / mvnw 时才触发）自动 build 并推送到 `ghcr.io/mouhinu/knowledge_repository`，标签 `latest` + `sha-<短 SHA>`；`v*` tag push 时额外挂版本号。PR 走 dry-run（build 不 push），Dockerfile 变更可在 PR 里先验证。
- **本地 Dockerfile（多阶段）**：`Dockerfile` 用 `maven:3.9-eclipse-temurin-21` 编译 → `eclipse-temurin:21-jre` 运行。⚠️ 容器内 `mvn dependency:go-offline` 在建网慢 / 无 mirror 时会挂 16min+，兜底见下条。
- **本地单阶段（免容器内编译）**：`docker/Dockerfile.prebuilt` 只 COPY 主机 `./mvnw package` 产出的 fat jar，秒级 build。配合 `scripts/docker-build.sh` 使用。

**部署路径（选一即可）**

1. **本地开发（源码热跑，最快）**：`./mvnw -pl knowledge-web -am spring-boot:run -DskipTests`。
2. **GHCR pull（免编译，推荐生产/演示）**：
   ```bash
   echo "$GHCR_PAT" | docker login ghcr.io -u <user> --password-stdin    # 一次性
   IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=latest \
     docker compose pull knowledge-app && \
   IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=latest \
     docker compose up -d --no-build knowledge-app
   ```
3. **本地一键构建部署（无 CI / 内网）**：`bash deploy.sh` —— 容器内 `mvn clean package`（含测试）重建镜像、重创建容器、自动递增版本标签 `V{yyyymmdd}{seq}` 并清理旧镜像。⚠️ `docker restart` 不刷新镜像，代码改动须走 `deploy.sh` 或 `--build`。Docker TZ=Asia/Shanghai。

**运行时基线**

- **端口：** 应用 8091 / MySQL 宿主机 3307 / Milvus 19530 / etcd & MinIO 见 `docker-compose.infra.yml`。
- **健康检查：** `curl http://localhost:8091/actuator/health`，**冷启动约 124s**（Flyway + Milvus 连接池预热）；compose `start_period` 已上调到 150s 避免误判 starting。
- **资源约束：** 容器 `mem_limit: 1536m`，JVM `-Xms512m -Xmx1024m -XX:+UseG1GC`（留 Metaspace / 线程栈 / CodeCache / Milvus gRPC netty 堆外余量）。
- **韧性：** MilvusConfig `@Bean + @Lazy` + 退避重试；HikariCP max-pool 10 / min-idle 2 / leak-detect 10s（全 env 可覆盖）；Agent 线程池 AbortPolicy → 满则 SSE error + 429；`TransactionTemplate` 只包快 DB 写，Milvus / Embedding / LLM 移事务外。⚠️ redeploy 偶发 Milvus `DEADLINE_EXCEEDED` 启动竞态，容器约 54–90s 自愈，勿回滚。

**CI/CD 关键设计**

- **Workflow-level `permissions: { contents: read, packages: write }`**：不再依赖仓库 Settings → Actions → General 的默认 workflow permissions；管理员即使把默认改成 Read-only，仍能推 GHCR。
- **`concurrency` + `cancel-in-progress: true`**：同分支 / 同 PR 串行，新 push 自动作废进行中的旧 run，防止刷屏。
- **`paths` 过滤**：docker-image 只在 Java / pom / Dockerfile / docker / .mvn / mvnw 变化时才 build；改 README / docs / workflow 自身不触发。tag push 与 paths 共存时 paths 被 GitHub 忽略，release 必然出镜像。
- **Runner 钉版**：两条 workflow 都 `runs-on: ubuntu-24.04`；避开 2026-10-19 `ubuntu-latest` 静默迁移到 Ubuntu 26（actions/runner-images#14748）。
- **Actions 版本对齐 Node 24**：`checkout@v5` / `cache@v5` / `setup-java@v5` 全套 v5，消除 GitHub runner 关于 Node 20 弃用的告警。
- **GHCR 镜像名 lowercase 归一**：`${{ github.repository }}` 会保留大小写 `mouhinU/Knowledge_Repository`，Docker tag 拒绝 uppercase，`Compute image tag` 步骤用 `tr '[:upper:]' '[:lower:]'` 归一到 `mouhinu/knowledge_repository`。
- **层缓存 `type=gha`**：Actions 内置缓存层，二次构建从 ~5min 缩到 <1min。

**前端形态**：管理端多页 + `common.js`（`KR.initLayout`），无 CSP 严格限制；知识库嵌入 HTML 非 iframe。

**详细部署与故障排查见** [docs/deployment-ci-cd.md](docs/deployment-ci-cd.md)。

---

## 管理界面（静态页面）

`static/` 下共 14 个 HTML：**旧单页 `admin.html` 已改为重定向存根**（meta-refresh + location.replace 转发 query/hash 到 `/admin/index.html`），管理台实际由 `static/admin/` 下 **12 页多页面 SPA** 组成——`index.html`（仪表盘）、`login.html`、`documents.html`（文档管理）、`search.html`（知识检索）、`ai-writing.html`（AI 文章）、`ai-exam.html`（AI 出卷 + SSE 进度）、`paper-review.html`（试卷校对）、`exam-review.html`（答卷校对 / 成绩复核）、`wrong-answers.html`（错题本）、`users.html`、`departments.html`、`system.html`（V21 特性开关），配共享 `assets/css/common.css`（`:root` 色板真源，主色 #3b82f6 + slate 灰阶 + 圆角 6/10/14）与 `assets/js/common.js`（`KR.initLayout` 侧边栏/tab 编排）；`ai-exam` 主逻辑外置 `ai-exam.js`。考生端独立 SPA `static/exam.html`（样式全走 CSS 变量与管理端 token 对齐）。访问入口 `http://localhost:8091/admin.html`（自动跳转），Security 已 `permitAll("/admin/**")`。

---

## 启动前置条件

1. **启动基础设施**：`docker compose -f docker-compose.infra.yml up -d`（etcd + MinIO + Milvus），等端口 3307 / 19530 就绪。
2. **配置 LLM/Embedding（三段式 per-role）**：对话角色 `knowledge.llm.chat` 默认云端 DeepSeek（密钥 `LLM_CHAT_API_KEY`）；向量角色 `knowledge.llm.embedding` 默认本地 Ollama（bge-m3）。项目根 `.env` 至少填 `MYSQL_PASSWORD / LLM_CHAT_API_KEY / KNOWLEDGE_ADMIN_JWT_SECRET`。
3. **选择运行模式**：
   - 源码热跑（最快，改动即时）：`./mvnw -pl knowledge-web -am spring-boot:run -DskipTests`
   - 拉 CI 镜像（免编译，生产/演示推荐）：见上节"部署路径 2"，需一次性 `docker login ghcr.io -u <user> --password-stdin`（PAT 需 `read:packages` / Container registry Read 权限）
   - 本地一键构建部署（无 CI / 内网 / 想改 Dockerfile）：`bash deploy.sh`
4. **多模块跑指定测试**须加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则空测试模块会让构建失败。
5. **提交前**：`./mvnw spotless:apply` 过格式化，走 [docs/code-review-checklist.md](docs/code-review-checklist.md) 31 条。
