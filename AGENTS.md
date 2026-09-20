# AGENTS.md — 项目规范导航地图（Index / Map）

> 本文件是 Knowledge_Repository 的 **AI 编码总纲与导航索引**，不再是单本厚手册。
> 全部细则按维度拆分至 `docs/` 下若干分册，**按需查阅**。基线：《Java 开发手册》v1.5.0（华山版）+ **COLA 5.0 架构规范**。
> 技术栈速览：Spring Boot 3.4 / Java 21 / MyBatis-Plus 3.5.17 / LangChain4j 1.0.1 / Milvus 2.5.4 / PDFBox 3 / Tika 3 / POI 5 / H2 + MySQL / Flyway。
> **所有 AI 生成的代码必须严格遵循以下红线与对应分册。**

---

## 0. 不可协商的核心红线（Always-on，无需翻分册即须遵守）

1. **构造器注入、面向接口**，禁止字段 `@Autowired`。
2. **禁止魔法值**；常量全大写下划线，归类维护。
3. **4 空格缩进**，单行 ≤120 字符，LF 换行，UTF-8。
4. **SLF4J + 占位符**记日志；**严禁**日志 / 响应回显口令、令牌、敏感信息。
5. **SQL 参数一律 `#{}`**，禁止把请求入参直接拼进 SQL；动态列名 / 排序须白名单校验后再拼，禁止裸 `${}`。
6. **DO 不越过 infrastructure**；转换只在层边界：adapter `VO⇄DTO`，app `DTO⇄Entity`，infra `Entity⇄DO`。
7. **domain 层保持纯净**，不依赖 app / adapter / infrastructure；Gateway 接口在 domain、`GatewayImpl` 在 infrastructure。
8. **app 层 Service 只做分发**，用例编排落单一 `CmdExe`/`QryExe`；写库事务在 Executor；含 LLM / 外部 IO 的长耗时用例禁止套大事务。
9. **每个类必带** `@author Knowledge-Repository` + `@date` 的 Javadoc。
10. **不擅改框架 / JDK 版本**；依赖以 [docs/tech-stack.md](docs/tech-stack.md) 为准。

---

## 1. 分册地图（按维度拆分，按需阅读）

| 分册 | 覆盖维度 | 何时必读 | 来源章节 |
|------|----------|----------|----------|
| [docs/tech-stack.md](docs/tech-stack.md) | **技术选型**：JDK/Spring/COLA 基线、依赖版本、五模块布局、配置切换、版本纪律 | 引入依赖 / 改配置 / 拿不准版本时 | 原引言 + §11 基线注 |
| [docs/architecture-decisions.md](docs/architecture-decisions.md) | **架构决策**：COLA 五层、依赖方向、对象模型分层、术语映射、Executor/CQRS、DI、组件复用 | 新增/移动任何类、定分层与依赖、命名对象时 | 原第十一章（含 1.4/1.6 交叉） |
| [docs/coding-guideline.md](docs/coding-guideline.md) | **编码规范**：命名、常量、格式、OOP、集合、并发、注释、异常、日志 + 类注释模板 | 写任何 Java 代码时 | 原第一~九章 + 第十三章 |
| [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md) | **数据库与迁移**：建表、SQL、H2 方言、Flyway 实践 | 建表 / 写迁移 / 写 SQL / Mapper 时 | 原第十章 |
| [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) | **RAG 领域规范**：多格式解析、向量化、metadata、考试链路配图 | 动文档抽取 / 分块 / 向量 / 出题相关代码时 | 原第十二章（12.1/12.2） |
| [docs/security-guideline.md](docs/security-guideline.md) | **安全注意事项**：注入防护、上传/解析、认证令牌、RBAC+ACL、日志脱敏、错误信息面 | 涉及鉴权 / 权限 / 上传 / SQL / 令牌 / 口令 / 对外响应时 | 原 §10.2+§12.1+§12.3 聚合 + 现状 |
| [docs/testing-guideline.md](docs/testing-guideline.md) | **测试要求**：JUnit5/Mockito 风格、分层测试、门禁负向用例、Flyway 冒烟、运行验收 | 写/改测试，或提交前自验时 | 新（从仓库现有测试归纳） |
| [docs/code-review-checklist.md](docs/code-review-checklist.md) | **提交前检查清单**：27 条逐条自检（编码/架构/RAG/安全/测试） | 每次生成代码收尾自检 | 原末章清单（扩充） |

---

## 2. 按任务定位分册（决策树）

- 我要**新建一个类**（Controller / Service / Executor / Entity / Gateway / DO / DTO / VO）？→ 先看 [docs/architecture-decisions.md](docs/architecture-decisions.md)（定它属于哪层、叫什么、依赖谁），再看 [docs/coding-guideline.md](docs/coding-guideline.md)（怎么写）。
- 我要**改依赖 / 加库 / 动配置**？→ [docs/tech-stack.md](docs/tech-stack.md)（版本纪律）。
- 我要**建表 / 写迁移 / 写 SQL / 配 Mapper**？→ [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md)，涉密或含参数拼接另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动文档解析 / 分块 / 向量化 / 出题配图**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md)，权限与过滤另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动 LLM Agent / LangChain4j 编排**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) + [docs/architecture-decisions.md](docs/architecture-decisions.md)（实现落 infrastructure `.../agent/`，编排在 app Executor）。
- 我要**改静态页 / `common.js` / 令牌头**？→ [docs/security-guideline.md](docs/security-guideline.md)（`knowledge-web/src/main/resources/static/`）。
- 我要**加鉴权 / 改权限 / 处理上传 / 发令牌 / 存口令**？→ [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**写测试 / 提交前自验**？→ [docs/testing-guideline.md](docs/testing-guideline.md) + [docs/code-review-checklist.md](docs/code-review-checklist.md)。

---

## 3. 相关文档

- 项目能力目录（当前系统全景、REST 清单、迁移清单、功能域）见 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)。
- 运维 / 脚本说明见 [scripts/README.md](scripts/README.md)。

---

## 4. 维护约定

- 本 `AGENTS.md` 只承载 **红线 + 导航**；细则一律落在 `docs/` 分册中，避免本文件再次膨胀。
- 新增规范：优先并入相应分册；若开辟新维度，回本文件 §1 表格登记一行。
- 分册之间用相对链接互引，勿复制粘贴造成规则漂移。
- 分册首行的"来源章节"标注保留可追溯性；与历史 `*Repository` 命名的兼容口径以 [docs/architecture-decisions.md](docs/architecture-decisions.md) §3 术语映射为准。
