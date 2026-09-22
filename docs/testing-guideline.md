# 测试要求（Testing Guideline）

> 本分册**从仓库现有测试归纳而成**（原 `AGENTS.md` 未设测试章节），旨在固化项目已成型的测试风格与门禁。
> 现状参考：27 个 `*Test.java`，JUnit 5 + Mockito + AssertJ（经 `spring-boot-starter-test` 引入）。
> 被测对象的分层职责见 [architecture-decisions.md](architecture-decisions.md)，迁移脚本约束见 [data-and-migration-guideline.md](data-and-migration-guideline.md)。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20

---

## 1. 技术栈与依赖

- 测试框架 **JUnit 5（Jupiter）**：`@Test` / `@DisplayName` / `@Nested` / `@BeforeEach` / `@ParameterizedTest` + `@MethodSource`。
- Mock 用 **Mockito**，断言用 **JUnit `Assertions` 或 AssertJ**。
- 依赖仅由 `spring-boot-starter-test` 提供，**不引入 Testcontainers / 外部 DB**——保持离线、快速、确定。

## 2. 命名与结构

- 测试类以 `Test` 结尾（见 [coding-guideline.md](coding-guideline.md) §1.2），与被测类同包镜像放置于 `src/test/java`。
- **测试类同样须带 Javadoc**：`@author` 取 `git config user.name`（如 `mouhinU`）、`@date` 用编写当下系统时间 `yyyy-MM-dd HH:mm:ss`（24 小时制）。
- 每个 `@Test` 配中文 `@DisplayName` 描述业务意图（现有 181 处 `@DisplayName`，为项目既定风格）。
- 同一被测对象的多组场景用 `@Nested` 内部类分组（如 `ExamGradingCharacterizationTest` 的 `SingleChoice/MultiChoice/TrueFalse/...`）。
- 用例遵循 **Given–When–Then** 三段式，命名与断言消息体现"为什么"。

## 3. 分层测试策略（对齐 COLA）

- **app 层 Executor 为单测主战场**：直接 `new` 被测 Executor，用 `mock(XxxGateway.class)` 打桩其 Gateway / Support 协作者，验证编排与门禁（现有 `VoidPaperCmdExeTest`、`ExamStartFromHistoryGuardTest`、`ListPublishedHistoryQryExeStudentFilterTest` 等均此范式）。
- **优先构造器注入式手工 mock**（`mock(...)` + 构造器传参 + `ArgumentCaptor` 捕获落库对象），项目**未使用** `@Mock` / `@InjectMocks` / `@ExtendWith(MockitoExtension.class)`；新增测试保持一致风格，不混入注解式 mock 以免上下文漂移。
- **domain 层纯逻辑**（如 `PermissionDomainServiceTest`、`ExamMetaQuestionDetectorTest`）直接断言，无需 mock。
- **基础设施 IO**（图片抽取 / 解析）用 `@TempDir` 落地临时文件测试（如 `DocumentImageExtractorServiceTest`）。
- **接口 / 安全过滤器**（`AdminTokenAuthFilterTest`）以 mock `FilterChain` / `HttpServletRequest` 验证拦截与放行清单。

## 4. 门禁类用例（必须覆盖负向路径）

- 带副作用的用例（开考、作废、评分认领）必须覆盖**拒绝分支与并发分支**：
  - 状态门禁：未发布 / 已作废 / 重复开考等**前置抛异常**，断言异常**消息语义**（如作废卷须报"已作废"而非"未发布"——门禁顺序敏感）。
  - 幂等：重复作废等应返回成功且不产生重复写（`VoidPaperCmdExeTest.voidAlreadyVoidedIsIdempotent`）。
  - 并发：`ExamGradingConcurrencyTest` 式心跳 / 认领竞态须显式建测。
- Mock 打桩注意 POJO 真实赋值：对普通实体勿 `when(x.getY())`，应 `x.setY(...)` 直接构造（历史踩坑）。
- 新增接口方法时，Mockito 对 `mock()` 返回默认值（`false` / 空集合），通常不破坏既有用例；但新逻辑主干须补正向 + 负向断言，不能只依赖默认桩通过。

## 5. Flyway 迁移冒烟（强制）

- `FlywayMigrationSmokeTest` 在 H2（`MODE=MySQL`）内存库一次性回放全部迁移，**不启动 Spring 上下文**。
- **每新增一版迁移脚本，必须同步该测试**：`EXPECTED_MIGRATION_COUNT` 常量、最终版本断言（`current.getVersion()`）、并抽查本版新增代表性对象（表 / 列）存在。漏更会让 `./mvnw test` 直接失败。

## 6. 运行与验收

- 全量：项目根 `./mvnw test`（多模块，依赖未构建时 `-Dsurefire.failIfNoSpecifiedTests=false` 避免空模块报错）。
- 单类：`./mvnw test -Dtest=XxxTest -Dsurefire.failIfNoSpecifiedTests=false`。
- 判定：`BUILD SUCCESS` 且各模块 `Failures: 0, Errors: 0`；负向路径日志（预期 `ERROR` 行）不算失败。
- 提交前：相关单测全绿；涉库改动确认迁移冒烟通过；行为改动尽量补一条 E2E（curl / 浏览器）验证记录（如本仓库历史 E2E：重复开考 `409`、级联作废 `voided` 落库、可用列表按生过滤）。

## 7. 覆盖率导向（建议，非硬阈值）

- 优先保障 **app 层用例编排 + domain 层规则**的可测性；Controller 薄适配层可由 Executor 测试间接覆盖。
- 以"关键业务分支 + 门禁负向路径"被覆盖为准，而非盲目追求行覆盖率数字。
