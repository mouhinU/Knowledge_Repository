# 技术选型（Technology Stack）

> 本分册由根目录 `AGENTS.md` 拆分而来，映射原「引言技术栈」与「COLA 基线说明」。
> 版本一经确定，AI 生成代码时**不得擅改框架 / JDK 版本**；如需升级须先确认，并以代码评审与版本治理为落地机制。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20
> 术语映射：`adapter ≈ knowledge-web`，`app ≈ knowledge-application`，`client ≈ knowledge-client`，`domain ≈ knowledge-domain`，`infrastructure ≈ knowledge-infrastructure`。

---

## 1. 语言与框架基线

| 组件        | 版本         | 说明                                                                                                |
| ----------- | ------------ | --------------------------------------------------------------------------------------------------- |
| Java        | **21**       | 高于 COLA 5.0 基线（JDK 17+），遵循 COLA 约定不改变版本                                             |
| Spring Boot | **3.4.x**    | COLA 5.0 基线要求 Spring Boot 3.x，本项目满足且高于基线                                             |
| 架构规范    | **COLA 5.0** | 采用物理多模块分层（含独立 client 层），详见 [architecture-decisions.md](architecture-decisions.md) |

> 编码手册基线：《Java 开发手册》v1.5.0（华山版）。

## 2. 核心依赖版本（root pom `<properties>`）

| 能力                | 组件          | 版本                        |
| ------------------- | ------------- | --------------------------- |
| ORM / 持久层        | MyBatis-Plus  | 3.5.17                      |
| LLM 编排            | LangChain4j   | 1.0.1                       |
| 向量数据库          | Milvus SDK    | 2.5.4                       |
| PDF 解析            | Apache PDFBox | 3.0.4                       |
| 格式探测 / 通用解析 | Apache Tika   | 3.1.0                       |
| Office 文档         | Apache POI    | 5.3.0                       |
| 嵌入式 / 测试库     | H2            | 2.2.224                     |
| 生产数据库          | MySQL         | 8.0                         |
| 数据库迁移          | Flyway        | 由 Spring Boot BOM 统一管理 |
| 样板代码            | Lombok        | 1.18.36                     |

## 3. Maven 多模块（现状）

根 `pom.xml` 声明五个模块，与 COLA 五层一一对应：

```
knowledge-client          对外契约：Service 接口 + Cmd/Qry/Response DTO
knowledge-domain          领域层：Entity / DomainService / Gateway 接口 / 领域事件
knowledge-infrastructure  基础设施层：GatewayImpl / Mapper / DO / 中间件适配
knowledge-application     应用层：ServiceImpl（分发）+ CmdExe/QryExe（编排）
knowledge-web             适配层：Controller / 拦截器 / 静态资源 / Flyway 迁移脚本
```

> **勘误更新**：`knowledge-client` 现为**已独立的物理模块**（原 `AGENTS.md` 第十一章「现散落于 knowledge-web/dto」的描述已过时）。新增对外契约一律落入 `knowledge-client`。
> 模块与层的职责边界、依赖方向以 [architecture-decisions.md](architecture-decisions.md) 为准。

## 4. 运行时与配置切换

- **Embedding 模型**通过配置切换（DashScope / Ollama），以 `@ConditionalOnProperty` 装配，详见 [rag-domain-guideline.md](rag-domain-guideline.md)。
- **Milvus 集合名**由配置指定，默认 `knowledge_chunks`。
- **双数据库方言**：生产 MySQL、测试 / 本地 H2（`MODE=MySQL`）。Flyway 迁移脚本须二者兼容，约束见 [data-and-migration-guideline.md](data-and-migration-guideline.md)。

## 5. 本地运行与改动入口

- 编译：`./mvnw -q -DskipTests compile`
- 测试：`./mvnw test`（细则见 [testing-guideline.md](testing-guideline.md) §6）
- 运行应用：`./mvnw spring-boot:run -pl knowledge-web`
- 启动基础设施：`docker compose -f docker-compose.infra.yml up -d`
- 改 API 看 `knowledge-web`，改用例看 `knowledge-application`，改领域看 `knowledge-domain`，改 DB / 向量 / 外部适配看 `knowledge-infrastructure`，改契约看 `knowledge-client`。

## 6. 版本纪律（红线）

1. 任何 AI 生成代码不得引入未在本文档列出的新框架 / 新主版本，除非经确认。
2. 引入 COLA 组件（`cola-component-*`）依赖须经确认，且**不改动 Spring Boot / Java 版本**（见 [architecture-decisions.md](architecture-decisions.md) §组件复用约定）。
3. 依赖坐标统一在根 `pom.xml` 的 `<properties>` / `dependencyManagement` 管理，子模块不写死版本。
