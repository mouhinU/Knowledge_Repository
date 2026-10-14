# 技术选型（Technology Stack）

> 本分册由根目录 `AGENTS.md` 拆分而来，映射原「引言技术栈」与「COLA 基线说明」。
> 版本一经确定，AI 生成代码时**不得擅改框架 / JDK 版本**；如需升级须先确认，并以代码评审与版本治理为落地机制。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20
> 术语映射：`adapter ≈ knowledge-web`，`app ≈ knowledge-application`，`client ≈ knowledge-client`，
`domain ≈ knowledge-domain`，`infrastructure ≈ knowledge-infrastructure`。

---

## 1. 语言与框架基线

| 组件          | 版本           | 说明                                                                                |
|-------------|--------------|-----------------------------------------------------------------------------------|
| Java        | **21**       | 高于 COLA 5.0 基线（JDK 17+），遵循 COLA 约定不改变版本                                           |
| Spring Boot | **3.4.x**    | COLA 5.0 基线要求 Spring Boot 3.x，本项目满足且高于基线                                          |
| 架构规范        | **COLA 5.0** | 采用物理多模块分层（含独立 client 层），详见 [architecture-decisions.md](architecture-decisions.md) |

> 编码手册基线：《Java 开发手册》v1.5.0（华山版）。

## 2. 核心依赖版本（root pom `<properties>`）

| 能力          | 组件                               | 版本                                                                                                                   |
|-------------|----------------------------------|----------------------------------------------------------------------------------------------------------------------|
| ORM / 持久层   | MyBatis-Plus                     | 3.5.17（含 `mybatis-plus-jsqlparser` 3.5.17 伴生模块，P1-T7 租户/分页插件所需，MP 3.5.9+ 起从 extension 拆分独立可选）                        |
| LLM 编排      | LangChain4j                      | 1.0.1                                                                                                                |
| 向量数据库       | Milvus SDK                       | 2.5.4                                                                                                                |
| PDF 解析      | Apache PDFBox                    | 3.0.4                                                                                                                |
| 格式探测 / 通用解析 | Apache Tika                      | 3.1.0                                                                                                                |
| Office 文档   | Apache POI                       | 5.3.0                                                                                                                |
| 嵌入式 / 测试库   | H2                               | 2.2.224                                                                                                              |
| 生产数据库       | MySQL                            | 8.0                                                                                                                  |
| 数据库迁移       | Flyway                           | 由 Spring Boot BOM 统一管理                                                                                               |
| 样板代码        | Lombok                           | 1.18.36                                                                                                              |
| 层边界映射       | MapStruct                        | 1.6.3（红线 #6 转换器优先技术；app / infra 层引入 `org.mapstruct:mapstruct` 为 compile（`Mappers.getMapper` 与生成的 `XxxConverterImpl` 在运行/测试期需可用）；`lombok-mapstruct-binding` 0.2.0 桥接，注解处理器顺序 lombok→binding→mapstruct-processor，见根 pom `annotationProcessorPaths`） |
| LLM 韧性护栏    | Resilience4j                     | 2.2.0（per-role 熔断/重试/滑窗统计）                                                                                           |
| 对象存储        | MinIO Java SDK                   | 8.5.17（P5-T1 双驱动：本地磁盘 / MinIO 配置切换，仅 infra 层引入）                                                                      |
| 指标可观测       | Micrometer + Prometheus registry | 由 Spring Boot BOM 统一管理（`micrometer-core` in infra；`micrometer-registry-prometheus` in web，暴露 `/actuator/prometheus`） |
| 缓存 / 分布式协调  | Redis（`spring-boot-starter-data-redis`） | 由 Spring Boot BOM 统一管理（in infra；用于配置 pub/sub 失效 · 分布式锁 · 上传会话，P4-T2/P5-T2 引入）                                        |
| 集成测试容器      | Testcontainers（mysql / milvus / junit-jupiter） | 由 Spring Boot BOM 统一管理（仅 `knowledge-web` test scope，SaaS 集成测试）                                                       |

## 3. Maven 多模块（现状）

根 `pom.xml` 声明五个模块，与 COLA 五层一一对应：

```
knowledge-client          对外契约：Service 接口 + Cmd/Qry/Response DTO
knowledge-domain          领域层：Entity / DomainService / Gateway 接口 / 领域事件
knowledge-infrastructure  基础设施层：GatewayImpl / Mapper / DO / 中间件适配
knowledge-application     应用层：ServiceImpl（分发）+ CmdExe/QryExe（编排）
knowledge-web             适配层：Controller / 拦截器 / 静态资源 / Flyway 迁移脚本
```

> **勘误更新**：`knowledge-client` 现为**已独立的物理模块**（原 `AGENTS.md` 第十一章「现散落于
> knowledge-web/dto」的描述已过时）。新增对外契约一律落入 `knowledge-client`。
> 模块与层的职责边界、依赖方向以 [architecture-decisions.md](architecture-decisions.md) 为准。

## 4. 运行时与配置切换

- **LLM 三段式角色（Phase R1）**：chat / embedding / vision 三个独立角色（`knowledge.llm.*`），各自 `base-url` + `api-key` +
  `model-name` + per-role 超时预算（`connect/read/call-timeout-seconds`），供应商由 base-url 决定，统一走 OpenAI
  兼容协议。默认：chat = DeepSeek `deepseek-flash`，embedding = 本地 Ollama `bge-m3`（1024 维），vision = `paddleocr-vl`（默认关闭，
  `LLM_VISION_ENABLED=true` 启用）。密钥通过 per-role env 注入（`LLM_CHAT_API_KEY` / `LLM_EMBED_API_KEY` /
  `LLM_VISION_API_KEY`），便于独立轮换与最小权限。
- **Resilience4j 韧性护栏（Phase R2）**：per-role 独立熔断 + 重试（`knowledge.llm.resilience.*`）。chat 重试 3
  次指数退避；chat-stream 强制 max-attempts=1（避免 SSE 重复推送）；embedding / vision 重试 2 次。装饰器
  `ResilientChatModel` / `ResilientEmbeddingModel` / `ResilientStreamingChatGateway` 包裹 LangChain4j 原生模型。
- **文档解析策略路由（Phase A2）**：`knowledge.extractor.enabled-strategies` 白名单 + `routing` 按 MIME
  覆写有序策略栈；PDF_HYBRID / VISION 默认不参与路由（零副作用），须显式开启。`VisionBudgetGuard` 提供每日页数配额与
  `log-only|enforce` 模式。
- **Embedding 模型**通过配置切换（DashScope / Ollama），以 `@ConditionalOnProperty`
  装配，详见 [rag-domain-guideline.md](rag-domain-guideline.md)。
- **Milvus 集合名**由配置指定，默认 `knowledge_chunks`，维度 1024，COSINE 距离。切换 embedding 维度须 drop 重建集合。
- **双数据库方言**：生产 MySQL 8、测试 / 本地 H2（`MODE=MySQL`）。Flyway
  迁移脚本须二者兼容，约束见 [data-and-migration-guideline.md](data-and-migration-guideline.md)。
- **运行时特性开关（V21）**：`sys_config` 表 + 三层获取（内存缓存 → DB → 系统默认），管理员通过
  `PUT /api/admin/system/configs` 热更新，无需重启。

## 5. 本地运行与改动入口

- 编译：`./mvnw -q -DskipTests compile`
- 测试：`./mvnw test`（细则见 [testing-guideline.md](testing-guideline.md) §6）
- 运行应用：`./mvnw spring-boot:run -pl knowledge-web`
- 启动基础设施：`docker compose -f docker-compose.infra.yml up -d`
- 改 API 看 `knowledge-web`，改用例看 `knowledge-application`，改领域看 `knowledge-domain`，改 DB / 向量 / 外部适配看
  `knowledge-infrastructure`，改契约看 `knowledge-client`。

## 6. 版本纪律（红线）

1. 任何 AI 生成代码不得引入未在本文档列出的新框架 / 新主版本，除非经确认。
2. 引入 COLA 组件（`cola-component-*`）依赖须经确认，且**不改动 Spring Boot / Java 版本**
   （见 [architecture-decisions.md](architecture-decisions.md) §组件复用约定）。
3. 依赖坐标统一在根 `pom.xml` 的 `<properties>` / `dependencyManagement` 管理，子模块不写死版本。
4. **MapStruct 引入登记（红线 #11 一次性豁免）**：2026-10-14 经架构口头确认落地，影响面仅
    - **代码面**：根 pom（版本 properties / `dependencyManagement` / `annotationProcessorPaths`）+ app / infra `pom.xml`（新增 `mapstruct`
      compile 依赖）+ app/infra converter 包（`Converter` 由 `final class` → `@Mapper interface + INSTANCE`）+ 调用点（GatewayImpl / Executor 内静态调用改
      `INSTANCE.` / `INSTANCE::`）。**web / client / domain 零改动**。
    - **运行时影响**：**编译期代码生成** + 运行期轻依赖（`org.mapstruct:mapstruct` API jar：`@Mapper` 注解 + `Mappers.getMapper` 工厂 +
      生成的 `XxxConverterImpl` 类）；注解处理器 `mapstruct-processor` 只在 `annotationProcessorPaths` 里，不进产物。
      `Mappers.getMapper` 首次调用一次性 `Class.forName(..Impl)`，之后走单例，与原静态工具行为等价。
    - **回退计划**：删除根 pom 三条 properties + `dependencyManagement` 中 mapstruct 声明 + 编译器插件 `annotationProcessorPaths` + app/infra 两条
      mapstruct 依赖；将 converter 还原为 `final class`（Git 历史），调用点写回 `XxxConverter.toDomain(..)`；**不涉及数据库/迁移/配置**。
    - **测试覆盖**：全 reactor 1199 用例基线保持不变（domain 160 / app 348 / infra 594 / web 97）；转换语义经 GatewayImpl 单测 + Executor 单测 + web
      集成测试端到端覆盖，另有 `WrongAnswerConverterImagesTest` 直接断言转换器行为。`spotless:check` + `checkstyle:check` 零违规。
