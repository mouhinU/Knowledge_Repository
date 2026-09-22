# Knowledge Repository 项目能力总结

> 最近一次整理：2026-09-22。本文以当前代码（HEAD `f83a2e7`，Flyway V18）为准，覆盖知识库 RAG、AI 出卷、在线考试、评分与错题本、文档配图、试卷作废级联、管理/学生端鉴权等全部已落地能力。

## 项目概述

Knowledge Repository 最初是一个基于 LangChain4j + Milvus 的 RAG 知识库系统，现已演进为面向教育场景的一体化平台：在文档摄入、向量化、语义检索与权限隔离的基础上，扩展出多 Agent 黑板协作的 **AI 出卷流水线**、**出卷即切分落库**、**试卷校对与确定性内容门禁**、**试卷作废级联**、**学生在线考试**、**自动/人工评分**、**错题本**、**看图题配图**、以及管理端与学生端的 **无状态 JWT 鉴权**，并通过 **SSE** 全程实时推送生成/入库/评分进度。

**技术栈：** Spring Boot 3.4.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3.0.4 / Apache Tika 3.1.0 / Apache POI 5.3.0 / H2 + MySQL / Flyway。

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

**代码规模（主源码 Java 文件数，2026-09-22 实测）：**

| 模块 | 文件数 | 核心职责 |
|------|-------|---------|
| knowledge-client | 58 | 13 个 `*ServiceI` 契约接口 + Cmd/Qry/VO/DTO |
| knowledge-domain | 63 | 1 聚合根 + 11 实体 + 17 值对象 + 13 领域服务 + 17 Gateway 接口 + 领域事件 |
| knowledge-application | 133 | 14 个特性包的 Executor 用例编排（新增 docingestion/document/examgeneration/examgrading/examreview/examtaking/adminauth 等） |
| knowledge-infrastructure | 71 | Gateway 实现、Mapper/DO/Converter、Milvus、解析器、12 个黑板 Agent、LLM/Embedding |
| knowledge-web | 24 | 17 个 Controller、JWT 过滤器、SSE Store、安全配置 |
| **合计** | **~349** | 另有测试类 27 个（web 4 / application 18 / domain 4 / infra 1） |

---

## 领域模型

**聚合根：** `Document` —— 文档生命周期状态机（UPLOADED → PROCESSING → INDEXED → ARCHIVED / FAILED）。

**实体（11）：** `User`、`Department`、`Role`、`Student`、`DocumentChunk`、`DocumentImage`、`WritingHistory`、`ExamHistory`（AI 出卷主表）、`ExamQuestion`（拆分题目）、`ExamSession`（学生考试场次）、`ExamAnswer`（答题明细）。

**值对象（17）：** `Permission`（权限上下文）、`ChunkingConfig`、`ChunkingStrategyEnum`、`SearchResult`、`DocumentStatusEnum`、`DocumentVisibilityEnum`、`AdminTokenPayload`（JWT 载荷）、`BlackboardState`/`BlackboardPhase`/`BlackboardProgressEvent`（黑板协作与进度）、`ExamPlan`/`TypePlan`（题型分布方案）、`ExamAlertType`（试卷告警类型）、`ExtractedImage`/`ExtractionResult`/`DocumentImageHit`（图片提取产物）。

**领域服务（13）：**
- `DocumentIngestionDomainService` —— 文本分块（Token 上限、段落/页面边界、重叠）
- `PermissionDomainService` —— 构建 Milvus 过滤表达式，实现 RBAC + 文档级 ACL 隔离
- `ScoreRuleEngine` / `ScorePlanValidator` —— 分值规范归一、按题型权重分配、默认方案构建与校验
- `ExamAnswerNormalizer` —— 跨端统一的答案归一化（trim/大写/多选拆分），三处评分共用
- `ExamBlankCounter` —— 填空题空数统计（前后端唯一口径：连续下划线空 + 空括号累加）
- `ExamContractValidator` —— 出卷契约校验（题号/答案/选项/分值/出处题门禁），不过即 `VALIDATION_FAILED`
- `ExamMetaQuestionDetector` —— **确定性**识别「出处/位置类」记忆题（第几单元/哪一页等），供审核与发布双关卡复用
- `BlackboardAgent` 接口 + `StreamingChatGateway`、`BlackboardProgressCallback`、`IndexProgressCallback`、`ExamGradingProgressCallback` —— 黑板 Agent 契约与流式/进度回调接口（由 web 层持有 SseEmitter 实现）

**Gateway 接口（17）：** Document / DocumentChunk / DocumentImage / DocumentImageExtractor / DocumentExtraction / VectorStore / Department / User / Student / WritingHistory / AdminJwtService / ExamHistory / ExamQuestion / ExamSession / ExamAnswer / ExamDistribution / ExamAlert，均在 `domain.gateway`，出入参为纯领域对象。

---

## 核心功能域

### 1. 文档摄入与多格式解析
上传（`/api/document/upload`，Tika MIME 探测）→ 格式路由到专用解析器 → 文本提取 → MD5/SHA-256 去重 → 建记录（状态 UPLOADED）→ 预览 → 确认入库（分块 + 批量向量化 + 写 Milvus + DB）。支持 PDF、Word(.docx)、Excel(.xlsx)、PowerPoint(.pptx)、TXT、CSV、HTML、RTF，上限 200MB。PDF 按页提取并检测扫描型低文本页；Word 按段落（每约 30 段一 section）；Excel 按工作表保留行列；PPT 按幻灯片遍历文本形状。入库流程与「重新入库 / 自定义分块入库」均异步执行，进度经 SSE（`IndexProgressCallback`）推送。

### 2. 向量检索与权限隔离
Embedding 经 `@ConditionalOnProperty` 在 DashScope（text-embedding-v3）/ Ollama（bge-m3，1024 维，当前默认）间切换。Milvus 集合 `knowledge_chunks`：`id`(VarChar36 PK) / `text` / `metadata`(JSON，含 document_key、visibility、department_id、allowed_roles、owner_id) / `vector`(1024 COSINE)。检索走 `AuthorizedSearchSupport` 的 over-fetch + `PermissionDomainService` 过滤，四档可见性（PUBLIC / INTERNAL / RESTRICTED / PRIVATE），超级管理员旁路。切换 embedding 维度须 drop 重建集合。

### 3. AI 出卷多 Agent 黑板流水线
`ExamGenerationSupport.executeExamPipeline` 编排：研究（researcher）∥ 评分规则（scoring）→ 写作（writer）→ 答案（answerKey）∥ 校准（calibrator）→ 审核（reviewer）∥ 查重（dedup）。逐 token 推 thinking + output，SSE 面板按 phase 呈现。质量分 `< QUALITY_SCORE_THRESHOLD(80)` 时最多 `MAX_REVIEW_RETRIES(2)` 轮打回重写，分值收敛 delta=3 提前停止。12 个 Agent 位于 infrastructure（文章生成 3 个 Researcher/Writer/Reviewer + 出卷 6 个 Exam* + 分发 ExamDistributionAgent + 流式支撑 BlackboardAgentStreamer）。产出 `ExamPlan{schoolLevel,totalFullMark,combined,subjects,types[TypePlan],manualAdjusted}`。

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

---

## SSE 实时进度模式
`SseEmitter` 仅存在于 web 模块。domain 层只定义回调接口（BlackboardProgress / IndexProgress / ExamGradingProgressCallback），web 层的 Store 持有 emitter 并给出实现，ApplicationService 方法接收回调参数异步推送。前端**先建 EventSource 再 POST**（sessionId/streamId 前端生成）以免丢事件；SSE 写须 `synchronized(emitter)`；批量任务多回调共享 streamId、payload 带 sessionId 标签、Controller 用 AtomicInteger 计数归零发 BATCH_COMPLETE 后关闭。出卷进度面板 `ai-exam.js` 的 PHASE 监听只读 phase 结构化字段。DeepSeek-flash 返回 `reasoning_content` → thinking/output 双流。

---

## REST API 清单（按控制器分组）

| 控制器 | 基路径 | 端点数 | 职责 |
|--------|--------|:----:|------|
| DocumentUploadController | `/api/document` | 6 | 上传/分片上传/仅提取文本（状态 UPLOADED） |
| DocumentAdminController | `/api/admin/document` | 15 | 详情/多路列表/统计/分类统计/归档/删除/预览/入库/自定义分块/重新入库/入库进度 SSE |
| KnowledgeQueryController | `/api/knowledge` | 2 | 语义检索（带权限过滤） |
| ExamController | `/api/agent` | 12 | AI 出卷流水线、generate-stream、export-word、出卷历史、分发/校验 SSE |
| PaperReviewController | `/api/admin/paper-review` | 7 | 试卷校对：逐题查看、改答案、配图、resplit、approve、**作废（void）** |
| ExamReviewController | `/api/admin/exam-review` | 10 | 答卷校对 / 成绩复核（学生提交） |
| ExamTakingController | `/api/exam` | 7 | 学生开考、可用试卷、作答保存、交卷、结果 |
| ExamAssetController | `/api/exam/assets` | 1 | 配图二进制流式下载（放行） |
| AdminDocumentImageController | `/api/admin/exam-images` | 3 | 按文档列表配图、全局搜索、历史回填 |
| WrongAnswerController | `/api/admin/wrong-answers` | 3 | 管理端错题本 |
| StudentWrongAnswerController | `/api/student/wrong-answers` | 1 | 学生端错题本 |
| AdminAuthController | `/api/admin/auth` | 4 | 登录/登出/当前身份/改密 |
| StudentAuthController | `/api/student/auth` | 4 | 学生注册/登录/登出/身份 |
| UserAdminController | `/api/admin/user` | 5 | 用户管理 CRUD |
| DepartmentAdminController | `/api/admin/department` | 4 | 部门管理（含树形） |
| SystemConfigController | `/api/admin/system` | 1 | 系统状态 |
| ArticleAgentController | `/api/agent` | 4 | 文章生成/进度 SSE/写作历史 |

---

## 数据库设计（Flyway V1–V18）

H2（开发）+ MySQL（生产，容器 `knowledge-mysql`，宿主机端口 3307，库 `knowledge_repository`）。必备字段 `id`/`create_time`/`update_time`，索引命名 `pk_/uk_/idx_`。迁移演进：

| 版本 | 迁移 | 引入 |
|------|------|------|
| V1 | init_schema | sys_department、sys_user、sys_role、sys_user_role、kb_document、kb_document_chunk |
| V2 | add_chunking_strategy | 分块策略字段 |
| V3 | add_writing_history | kb_writing_history（AI 文章） |
| V4 | add_category | 文档分类 |
| V5 | add_exam_history | kb_exam_history（AI 出卷主表） |
| V6 | add_online_exam | kb_exam_session、kb_exam_answer（在线考试） |
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

关系：`kb_exam_history(session_id) ↔ kb_exam_question(session_key)`；`kb_exam_session(exam_history_id) ↔ kb_exam_answer(session_id)`；错题本派生自 `kb_exam_answer.is_correct=false`。`knowledge_document` 外键 = `base_id`。运维脚本 `scripts/clean-exam-data.sh` 备份后 TRUNCATE 4 张考试表、保留 `kb_student`（`--dry-run` / `--yes` / `--with-students` / `--no-backup`）。

---

## 配置与特性开关

以 `application.yml` 为准，均可用环境变量覆盖：

- `knowledge.embedding.provider`：`ollama`（默认，bge-m3）| `dashscope`（text-embedding-v3）
- `knowledge.llm.provider`：`ollama`（默认）| `deepseek`（当前运行用 `deepseek-flash`）| `dashscope`；`temperature` 0.7、`max-tokens` 4096；**流式独立预算** `streaming.max-tokens` 16384 / `streaming.timeout-seconds` 300（避免推理模型思考链耗尽预算喂空正文）
- `knowledge.milvus`：host/port/collection=`knowledge_chunks`/dimension=1024/database=default
- `knowledge.blackboard.search`：max-results 10 / min-score 0.5
- `knowledge.storage.path`：`./data/documents`
- `knowledge.admin.jwt`：secret（`KNOWLEDGE_ADMIN_JWT_SECRET`）/ expiration-hours 8
- `knowledge.exam.grading-delay-minutes`：30
- `knowledge.exam.review-required`（`EXAM_REVIEW_REQUIRED`，**默认 true**）：true=所有试卷须人工校对通过后发布；false=校验通过 + 质量达阈可自动发布，但校验不过/低分仍强制人工校对（不可绕过）
- `knowledge.exam.asset-path`：`./data/exam-assets`（看图题配图二进制目录）

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

`static/` 下 14 个 HTML：入口 `admin.html` + `admin/index.html`（仪表盘）、`admin/login.html`、`admin/documents.html`（文档管理）、`admin/search.html`（知识检索）、`admin/ai-writing.html`（AI 文章）、`admin/ai-exam.html`（AI 出卷 + SSE 进度）、`admin/paper-review.html`（试卷校对）、`admin/exam-review.html`（答卷校对/成绩复核）、`admin/wrong-answers.html`（错题本）、`admin/users.html`、`admin/departments.html`、`admin/system.html`；考生端独立 SPA `exam.html`。访问 `http://localhost:8091/admin.html`。

---

## 启动前置条件

1. **启动基础设施**：`docker compose -f docker-compose.infra.yml up -d`（etcd + MinIO + Milvus），等端口 3307 / 19530 就绪。
2. **配置 LLM/Embedding**：本地 Ollama（bge-m3 + deepseek 兼容端点）**或** 云端 DeepSeek（`DEEPSEEK_API_KEY`）**或** DashScope（`DASHSCOPE_API_KEY`）。项目根 `.env` 至少填 `MYSQL_PASSWORD / DEEPSEEK_API_KEY / KNOWLEDGE_ADMIN_JWT_SECRET`。
3. **选择运行模式**：
   - 源码热跑（最快，改动即时）：`./mvnw -pl knowledge-web -am spring-boot:run -DskipTests`
   - 拉 CI 镜像（免编译，生产/演示推荐）：见上节"部署路径 2"，需一次性 `docker login ghcr.io -u <user> --password-stdin`（PAT 需 `read:packages` / Container registry Read 权限）
   - 本地一键构建部署（无 CI / 内网 / 想改 Dockerfile）：`bash deploy.sh`
4. **多模块跑指定测试**须加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则空测试模块会让构建失败。
5. **提交前**：`./mvnw spotless:apply` 过格式化，走 [docs/code-review-checklist.md](docs/code-review-checklist.md) 31 条。
