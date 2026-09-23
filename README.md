# Knowledge Repository

> 面向教育场景的一体化知识库 + AI 出卷 / 在线考试 / 自动评分平台。
> 基于 LangChain4j + Milvus 的 RAG 底座，扩展多 Agent 黑板协作流水线与教学业务闭环。

![Java 21](https://img.shields.io/badge/Java-21-orange) ![Spring Boot 3.4.4](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen) ![COLA 5.0](https://img.shields.io/badge/Architecture-COLA%205.0-blue) ![Milvus 2.5.4](https://img.shields.io/badge/Milvus-2.5.4-1ea6c2) ![CI](https://github.com/mouhinU/Knowledge_Repository/actions/workflows/ci.yml/badge.svg) ![Build & Push Docker Image](https://github.com/mouhinU/Knowledge_Repository/actions/workflows/docker-image.yml/badge.svg)

---

## 这是什么

Knowledge Repository 最初是一个「多格式文档 → 向量化 → 语义检索」的 RAG 知识库；随着教学场景需求扩展，现已演进为一个覆盖**备课—出卷—考试—评分—错题讲评**完整链路的一体化平台：

- **知识库 RAG**：PDF / Word / Excel / PPT / TXT / CSV / HTML / RTF 全格式摄入，Milvus 向量检索，RBAC + 文档级 ACL 权限隔离。
- **AI 出卷**：多 Agent 黑板协作流水线（研究 → 命题 → 校准 → 审核 → 查重 → 评分规则），SSE 全程流式推 thinking/output，题型分值方案可归一化校验；出卷即切分落库为逐题行。
- **试卷校对与确定性内容门禁**：管理端逐题审阅 / 改答案 / 配图 / resplit / approve；「出处 / 位置类」记忆题双层拦截。
- **学生在线考试**：倒计时 + 自动锁定 + 填空题 slot 归位 + 看图题配图注入。
- **自动 & 人工评分**：AI 评分 + 复核双通道，多选按集合相等、判断题无选项、`totalScore = Σ max_score`。CAS 双评 + Scheduler 超时恢复防漂移。
- **错题本**：由 `kb_exam_answer` 派生，学生端与管理端共用转换层，支持看图题。
- **看图题配图**：文档图片自动抽取 → SHA-256 去重 → 资产库（534+ 张候选）→ 管理端关键词/文档双搜索 picker → 绑定到题面。
- **AI 文章生成**：Researcher ∥ Writer ∶ Reviewer 三 Agent 黑板协作，同样 SSE 推流。
- **无状态 JWT 鉴权**：管理端账号密码 + HS256 JWT；学生端独立登录；SSE 走 `access_token` query。
- **全程 SSE 进度**：文档入库 / 出卷 / 评分 / 分发四类长任务统一 EventSource 通道。

技术栈：Spring Boot 3.4.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3 / Tika 3 / POI 5 / H2 + MySQL / Flyway V1-V17。

---

## 快速开始

### 前置

- Docker + Docker Compose
- LLM 后端（三段式 per-role）：对话角色默认云端 DeepSeek（`LLM_CHAT_API_KEY`），向量角色默认本机 Ollama（bge-m3）；供应商由各角色 `base-url` 决定
- 端口空闲：`8091` 应用 / `3307` MySQL / `19530` Milvus / `11434` Ollama

### 三步跑起来

```bash
# 1. 克隆 + 配 env
git clone https://github.com/mouhinU/Knowledge_Repository.git
cd Knowledge_Repository
cp .env.example .env && vi .env       # 至少填 MYSQL_PASSWORD / LLM_CHAT_API_KEY / KNOWLEDGE_ADMIN_JWT_SECRET

# 2. 起基础设施（MySQL + Milvus + etcd + MinIO）
docker compose -f docker-compose.infra.yml up -d

# 3a. 本地开发模式（源码热跑，最快）
./mvnw -pl knowledge-web -am spring-boot:run -DskipTests

# 3b. 或走 Docker 部署（构建 + 起应用一体，V2026092201 自增版本号）
bash deploy.sh
```

浏览器打开 <http://localhost:8091/admin.html>，默认种子账号 `admin / admin123`（首次登录后强制改密）。考生端 <http://localhost:8091/exam.html>。

### 直接用 CI 产出的镜像（免本地编译）

GitHub Actions 每次 main 合并都会推镜像到 GHCR。若只想跑现成版本：

```bash
# 一次性：PAT (Container registry Read) 登录 ghcr.io
echo "$GHCR_PAT" | docker login ghcr.io -u <你的GitHub用户名> --password-stdin

# 每次
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=latest \
  docker compose pull knowledge-app && \
IMAGE_NAME=ghcr.io/mouhinu/knowledge_repository APP_VERSION=latest \
  docker compose up -d --no-build knowledge-app
```

完整流程与故障排查见 **[docs/deployment-ci-cd.md](docs/deployment-ci-cd.md)**。

---

## 项目结构（COLA 5.0）

```
knowledge-web              adapter 层     17 Controller + JWT 过滤器 + SSE Store + 静态管理页
knowledge-application      app 层         14 特性包 Executor 用例编排（事务边界、DTO ⇄ 领域对象转换）
knowledge-client           client 层      13 个 *ServiceI 契约 + Cmd/Qry/DTO/VO
knowledge-domain           domain 层      1 聚合根 + 11 实体 + 17 值对象 + 13 领域服务 + 17 Gateway 接口
knowledge-infrastructure   infra 层       Gateway 实现 + Mapper/DO/Converter + Milvus + 解析器 + 12 Agent + LLM/Embedding
docs/                      规范分册       tech-stack / architecture / coding / data-migration / rag-domain / security / testing / code-review / deployment-ci-cd
scripts/                   运维脚本       deploy / docker-build / compose / backup / ... 83 个
```

依赖方向：`adapter → app → client`，`app → domain ← infrastructure`（依赖倒置，infra 反向实现 domain Gateway）。domain 层不引用其它业务层，DO 不越过 infrastructure。

约 **343** 个 Java 源文件 + **24** 个测试类。

---

## 文档索引

| 想了解 | 去哪 |
|---|---|
| **项目全貌 / 能力清单 / 数据模型 / API 一览** | [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) |
| **AI 编码规范与红线**（构造注入 / 无魔法值 / SQL `#{}` / 分层转换 / domain 纯净） | [AGENTS.md](AGENTS.md) |
| 技术选型与依赖版本 | [docs/tech-stack.md](docs/tech-stack.md) |
| COLA 分层与依赖决策 | [docs/architecture-decisions.md](docs/architecture-decisions.md) |
| Java 编码规范（命名 / 常量 / OOP / 集合 / 并发 / 注释 / 异常 / 日志 / 反模式） | [docs/coding-guideline.md](docs/coding-guideline.md) |
| 建表 / SQL / H2 方言 / Flyway 迁移 | [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md) |
| RAG 领域规范（多格式解析 / 分块 / 向量化 / metadata / 考试配图） | [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) |
| 安全（注入 / 上传 / 令牌 / RBAC + ACL / 日志脱敏） | [docs/security-guideline.md](docs/security-guideline.md) |
| 测试（JUnit 5 / Mockito / 分层 / Flyway 冒烟 / 门禁负向用例） | [docs/testing-guideline.md](docs/testing-guideline.md) |
| **CI/CD 部署（GHCR / 本地 Docker 完整流程）** | [docs/deployment-ci-cd.md](docs/deployment-ci-cd.md) |
| 提交前 31 条自检清单 | [docs/code-review-checklist.md](docs/code-review-checklist.md) |
| 运维脚本 | [scripts/README.md](scripts/README.md) |

---

## REST API 概览（17 个 Controller / ~85 个端点）

**管理端**（`X-Admin-Token` 或 `Authorization: Bearer <JWT>`）

- `/api/admin/auth/*` — 登录 / 登出 / 当前身份 / 改密
- `/api/admin/document/*` — 详情 / 列表 / 归档 / 删除 / 预览 / 入库 / 重新入库 / 自定义分块 / 入库进度 SSE
- `/api/admin/paper-review/*` — 试卷校对：逐题查看 / 改答案 / 配图 / resplit / approve
- `/api/admin/exam-review/*` — 答卷校对 / 成绩复核
- `/api/admin/exam-images/*` — 配图资产库：按文档列表 / 全局搜索 / 历史回填
- `/api/admin/wrong-answers/*` — 错题本
- `/api/admin/user/*`、`/api/admin/department/*`、`/api/admin/system/*`

**业务端**

- `/api/document/*` — 上传 / 分片上传 / 仅提取文本
- `/api/knowledge/*` — 语义检索（带权限过滤）
- `/api/agent/*` — AI 出卷流水线、`generate-stream` SSE、`export-word`、分发校验；AI 文章生成、写作历史
- `/api/exam/*` — 学生开考 / 保存作答 / 交卷 / 结果查询（放行清单，无需管理令牌）
- `/api/exam/assets/{assetKey}` — 看图题配图二进制（放行）
- `/api/student/*` — 学生注册 / 登录 / 错题本（独立令牌）

完整清单见 [PROJECT_SUMMARY.md § REST API](PROJECT_SUMMARY.md)。

---

## 数据库与迁移

Flyway V1 → V17，H2 开发 / MySQL 生产同一份迁移脚本。核心表：

```
sys_department / sys_user / sys_role / sys_user_role      权限基座 (V1, V15 加 password_hash + status)
kb_document / kb_document_chunk                            文档与分块
kb_writing_history                                         AI 文章 (V3)
kb_exam_history                                            出卷主表 (V5, V8 加 exam_plan JSON)
kb_exam_question                                           出卷即切分逐题行 (V11, V13 加 scoring_criteria, V17 加 images_json)
kb_exam_session / kb_exam_answer                           在线考试 (V6, V7 加 duration)
kb_exam_grading_trace / 得分明细 / token 记录              评分链路 (V9, V10, V14)
kb_document_image                                          配图资产 (V16)
```

外键与运维：`history.session_id ↔ question.session_key`；`session.exam_history_id ↔ answer.session_id`；错题本派生自 `answer.is_correct=false`。清考试数据：`.buckups/data/clean-exam-data.sh`（本地运维脚本，未纳入版本控制；`--dry-run` / `--yes` / `--with-students` / `--no-backup`）。

---

## CI / CD

两条 GitHub Actions 工作流：

| 工作流 | 触发 | 用途 |
|---|---|---|
| **CI** (`ci.yml`) | push main / PR / workflow_dispatch | `mvn clean verify -DskipTests` + 静态门禁 + SonarCloud Quality Gate |
| **Build & Push Docker Image** (`docker-image.yml`) | push main / tag `v*` / PR (dry-run) | 多阶段 build → 推 `ghcr.io/mouhinu/knowledge_repository:sha-<短SHA>` 与 `latest`；PR 只 build 不推 |

关键设计：

- **workflow 顶层 `permissions: { contents: read, packages: write }`** 覆盖仓库默认，管理员即使把默认改成 Read-only 也能推 GHCR。
- **`concurrency` + `paths` 过滤** 防止刷屏：纯改 README / 文档 / workflow 自身不会触发镜像构建；tag push 与 paths 共存时 paths 被忽略，release 一定出镜像。
- **runner 钉 `ubuntu-24.04`** 避开 2026-10-19 `ubuntu-latest → 26` 静默迁移。
- **Actions 层缓存 `type=gha`** 让二次构建从 ~5min 降到 <1min。
- **兜底路径** `docker/Dockerfile.prebuilt` + `scripts/docker-build.sh`：主机 `mvn package` 产出 fat jar，容器只做 JRE 层，绕开容器内 `mvn dependency:go-offline` 挂 16min+ 的坑。

详细部署与故障排查：[docs/deployment-ci-cd.md](docs/deployment-ci-cd.md)。

---

## 配置与特性开关

所有配置走 `application.yml` + 环境变量覆盖：

| 变量 | 默认 | 说明 |
|---|---|---|
| `LLM_EMBED_PROVIDER` | `ollama` | bge-m3 (1024d) / dashscope text-embedding-v3 |
| `LLM_CHAT_PROVIDER` | `deepseek` | 供应商标识（实际端点由各角色 `*-BASE_URL` 决定）|
| `LLM_CHAT_API_KEY` | — | 对话角色密钥，生产必填 |
| `LLM_STREAMING_MAX_TOKENS` | `16384` | 流式独立预算，避免推理链喂空正文 |
| `KNOWLEDGE_MILVUS_COLLECTION` | `knowledge_chunks` | 换 embedding 维度须 drop 重建 |
| `KNOWLEDGE_STORAGE_PATH` | `./data/documents` | 文档原文件目录 |
| `KNOWLEDGE_EXAM_ASSET_PATH` | `./data/exam-assets` | 看图题配图目录 |
| `KNOWLEDGE_ADMIN_JWT_SECRET` | 必填 | HS256 签名密钥 |
| `KNOWLEDGE_ADMIN_JWT_EXPIRATION_HOURS` | `8` | 令牌有效期 |
| `KNOWLEDGE_EXAM_GRADING_DELAY_MINUTES` | `30` | AI 评分延迟 |
| `KNOWLEDGE_EXAM_REVIEW_REQUIRED` | `true` | false 时校验通过 + 质量达阈可自动发布，但校验不过 / 低分仍强制人工校对（不可绕过） |

---

## 开发指南

**编译 / 测试 / 跑起来**

```bash
./mvnw clean verify                                          # 全量编译 + 单测 + 静态检查
./mvnw test -Dtest='*ScoreRule*' -pl knowledge-domain -am    # 单模块跑特定测试
./mvnw -pl knowledge-web spring-boot:run -DskipTests         # 本地起应用
./mvnw spotless:apply                                        # 格式化（提交前必跑）
```

多模块跑指定测试须加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则空测试模块会让构建失败。

**代码规范核心红线**（详见 [AGENTS.md](AGENTS.md)）

- 构造器注入 + 面向接口，禁止字段 `@Autowired`。
- 禁止魔法值；常量全大写下划线。
- 4 空格缩进，单行 ≤ 120 字符，UTF-8 / LF。
- 日志用 `@Slf4j` + `{}` 占位符，严禁回显口令 / 令牌。
- SQL 一律 `#{}`；动态列名 / 排序须白名单校验后再拼。
- 转换只在层边界：adapter `VO ⇄ DTO`，app `DTO ⇄ Entity`，infra `Entity ⇄ DO`。
- domain 层保持纯净；Gateway 接口在 domain，`GatewayImpl` 在 infra。
- 含 LLM / 外部 IO 的长耗时用例不置于数据库事务内。

**提交前自检**：过 [docs/code-review-checklist.md](docs/code-review-checklist.md) 31 条。

---

## 许可证 & 反馈

Internal use. 问题反馈请开 GitHub Issue 或到 <https://forum.qoder.com/> 讨论。

---

**其他链接**

- [AGENTS.md](AGENTS.md) — AI 编码总纲与导航索引
- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) — 全量能力清单（190 行）
- [docs/](docs/) — 规范分册
- [scripts/](scripts/) — 运维脚本索引
