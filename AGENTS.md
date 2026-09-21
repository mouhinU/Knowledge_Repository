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
9. **新建或修改 Java 类时的 Javadoc 要求**：公共/库级别类应补齐完整 Javadoc；普通/内部类为推荐。`@author`/`@date` 可选，鼓励以 Git 提交元数据为主。
10. **谨慎变更框架 / JDK 版本**；依赖以 [docs/tech-stack.md](docs/tech-stack.md) 为准。重大变更需提交变更提案（包含影响评估、回退计划、测试覆盖）并获得架构/负责人审批。
11. **方法与函数目标值**：方法名 ≤50 字符；方法体目标 ≤50 行；参数 ≤5（若 >3 建议封装为 `Cmd`/`Query`/参数对象）；优先纯函数（相同输入→相同输出，副作用收敛到 infrastructure 边界）。允许例外，需在 PR 中说明并由 reviewer 批准。详见 [docs/coding-guideline.md](docs/coding-guideline.md) §十。

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

---

## 2. 按任务定位分册（决策树）

- 我要**新建一个类**（Controller / Service / Executor / Entity / Gateway / DO / DTO / VO）？→ 先看 [docs/architecture-decisions.md](docs/architecture-decisions.md)（定它属于哪层、叫什么、依赖谁），再看 [docs/coding-guideline.md](docs/coding-guideline.md)（怎么写）。
- 我要**改依赖 / 加库 / 动配置 / 本地编译运行**？→ [docs/tech-stack.md](docs/tech-stack.md)。
- 我要**建表 / 写迁移 / 写 SQL / 配 Mapper**？→ [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md)，涉密或含参数拼接另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动文档解析 / 分块 / 向量化 / 出题配图**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md)，权限与过滤另查 [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**动 LLM Agent / LangChain4j 编排**？→ [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) + [docs/architecture-decisions.md](docs/architecture-decisions.md)（实现落 infrastructure `.../agent/`，编排在 app Executor）。
- 我要**改静态页 / `common.js` / 令牌头**？→ [docs/security-guideline.md](docs/security-guideline.md)（`knowledge-web/src/main/resources/static/`）。
- 我要**加鉴权 / 改权限 / 处理上传 / 发令牌 / 存口令**？→ [docs/security-guideline.md](docs/security-guideline.md)。
- 我要**写测试 / 提交前自验**？→ [docs/testing-guideline.md](docs/testing-guideline.md) + [docs/code-review-checklist.md](docs/code-review-checklist.md)。

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
