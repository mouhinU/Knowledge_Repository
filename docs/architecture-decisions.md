# 架构决策（COLA 5.0 分层与对象模型）

> 本分册由根目录 `AGENTS.md` 第十一章拆分而来，为项目**权威架构框架**。
> 与编码细则（命名 / 格式 / 日志等）分工：本文件管「放哪层、怎么依赖、对象怎么转化」，
> 具体「怎么写」见 [coding-guideline.md](coding-guideline.md)。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20

> 说明：以阿里巴巴 **COLA 5.0**（Clean Object-oriented and Layered Architecture）为权威架构框架。COLA 5.0 支持基于 package 的轻量级分层，但本项目采用**物理多模块**分层（含独立 client 层），二者取舍以本文件为准。
> 术语映射：`adapter ≈ knowledge-web`，`app ≈ knowledge-application`，`client ≈ knowledge-client`，`domain ≈ knowledge-domain`，`infrastructure ≈ knowledge-infrastructure`。新增代码以物理模块与本分册定义为准。

---

## 1. 分层模型（COLA 五层）

COLA 将系统划分为 adapter / app / client / domain / infrastructure 五个职责单一的分层。

| COLA 层                   | 目标模块（规范名）                 | 现状模块                            | 核心职责                                                                                                          |
| ------------------------- | ---------------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| adapter 适配层            | `knowledge-adapter`                | `knowledge-web`                     | 对外请求适配（HTTP/RPC），参数校验，调用 app 层，把结果组装为 **VO** 返回。不含业务逻辑。                         |
| app 应用层                | `knowledge-app`                    | `knowledge-application`             | 用例编排、事务边界、**Cmd/Qry Executor**、DTO ↔ 领域对象转换、实现 client 暴露的 Service 接口。不含核心业务规则。 |
| client 开放接口层         | `knowledge-client`（**必须独立**） | 现为独立物理模块 `knowledge-client` | 对外契约：Service 接口定义 + **DTO（Cmd/Qry/Response）**。仅被依赖，不反向依赖任何层。                            |
| domain 领域层             | `knowledge-domain`                 | `knowledge-domain`                  | 核心业务逻辑：Entity、DomainService、**Gateway 接口**、领域事件、DomainAbility。不依赖其它业务层。                |
| infrastructure 基础设施层 | `knowledge-infrastructure`         | `knowledge-infrastructure`          | **Gateway 实现**、DAO/Mapper、DO、中间件（DB/向量库/缓存）、外部服务调用、DO ↔ Entity 转换。不含业务规则。        |

**依赖方向（COLA）：**

```
adapter ─▶ app ─▶ client        （client 为对外契约，最稳定）
            │
            ▼
          domain ◀── infrastructure   （infrastructure 实现 domain 的 Gateway，依赖倒置）
```

- **client** 仅被 adapter / app 及外部调用方依赖，**禁止**引用 domain / infrastructure。
- **domain** 保持纯净，只依赖 JDK 与公共组件，**严禁**引用 app / adapter / infrastructure。
- **infrastructure** 依赖 domain（实现其 Gateway 接口），面向接口反向注入。
- 现状 `knowledge-web` / `knowledge-application` 视同 adapter / app；对外契约 DTO、Cmd/Qry、Service 接口归入独立模块 `knowledge-client`。

---

## 2. 对象模型分层（严禁跨层传递）

| 层             | 对象类型 | 说明                                                                                           |
| -------------- | -------- | ---------------------------------------------------------------------------------------------- |
| adapter        | VO       | View Object，面向前端视图展示                                                                  |
| client / app   | DTO      | 对外契约：`XxxCmd`（写）、`XxxQry`（读）、`Response/SingleResponse/MultiResponse/PageResponse` |
| domain         | Entity   | 领域实体 / 值对象 / 聚合根                                                                     |
| infrastructure | DO       | Data Object，与数据库表一一对应，MyBatis-Plus 注解                                             |

- **DO 不得越过 infrastructure 出现在 app / adapter**；app 层出入参为 DTO，内部编排用 domain Entity。
- 转换只在层边界发生：`VO ⇄ DTO`（adapter/app）、`DTO ⇄ Entity`（app）、`Entity ⇄ DO`（infrastructure，经 Converter）。
- 各对象的**命名后缀**规则见 [coding-guideline.md](coding-guideline.md) §1.6。

---

## 3. 术语映射（以 COLA 为准，覆盖旧命名）

| 本项目旧术语（历史代码）     | COLA 规范术语（新增代码必须采用）                           |
| ---------------------------- | ----------------------------------------------------------- |
| Repository（领域抽象）       | **Gateway**                                                 |
| RepositoryImpl               | **GatewayImpl**（`@Repository` / `@Component` 实现）        |
| ApplicationService           | client 层 `XxxServiceI` 接口 + app 层 `XxxServiceImpl` 实现 |
| Service 方法内直接写用例流程 | **Executor**（一个用例一个 `XxxCmdExe` / `XxxQryExe`）      |

> 迁移约定：新增代码一律采用 Gateway / Executor / client DTO；存量 `*Repository` 视同 `*Gateway`，随迭代逐步更名，不强制一次性重构。

---

## 4. 应用层 Executor 模式（CQRS）

- 写操作用 `XxxCmd`、读操作用 `XxxQry`（对齐仓库现状与 [coding-guideline.md](coding-guideline.md) §1.6；语义对应 COLA Command/Query）。
- client 层 Service 接口（如 `DocumentServiceI`）方法接收 Cmd/Qry，返回 `Response` 系列对象。
- app 层 Service 实现类**只做分发**（路由到对应 Executor）；真正的编排逻辑落在单个 Executor（`@Component`），保证**一个 Executor 单一职责、对应一个用例**。
- 写库用例的 `@Transactional` 置于 Executor（或 app Service 方法）；含 LLM / 外部 IO 的长耗时用例**禁止**套大事务。

```java
/**
 * 文档应用服务实现（app 层，仅分发）
 *
 * @author mouhinU
 * @date 2026-09-21 08:23:43
 */
@Service
public class DocumentServiceImpl implements DocumentServiceI {
    private final DocumentAddCmdExe documentAddCmdExe;   // 构造器注入

    @Override
    public Response addDocument(DocumentAddCmd cmd) {
        return documentAddCmdExe.execute(cmd);
    }
}
```

---

## 5. 领域层规范

- 实体 / 聚合根：属性 `private`，`@Getter` + `@Setter`（禁止 `@Data`），基于 ID 重写 `equals` / `hashCode`。
- **Gateway 接口**：定义在 `domain.gateway` 包，出入参均为领域对象，**严禁出现 DO 或 DTO**。
- 领域服务 DomainService：无状态，`@Service` 管理，方法命名体现业务意图；跨实体的业务规则落此。
- 领域能力 DomainAbility：可复用的领域规则（如权限过滤、分块策略），差异化分支以扩展点承载。
- 领域事件：Java record 实现 `DomainEvent` 标记接口，命名使用过去式（`DocumentCreatedEvent`）。

---

## 6. 基础设施层规范

- Gateway 实现类以 `GatewayImpl` 结尾，`@Repository` / `@Component` 注解，只负责技术实现（Mapper + Converter + 外部调用）。
- Converter 为 `final` 工具类，提供 `toDomain()`、`toDO()` 静态方法。
- 不得包含业务规则，只负责技术实现。
- 分页查询使用 `selectCount`（无 ORDER BY）+ `selectList`（LAST LIMIT OFFSET），H2 方言约束见 [data-and-migration-guideline.md](data-and-migration-guideline.md)。

---

## 7. 依赖注入

- 所有 Spring Bean 一律构造器注入、面向接口，禁止字段 `@Autowired`。
- 领域层保持纯净：**允许** Spring 刻板注解（如 DomainService 的 `@Service`），**禁止**依赖 infrastructure / app / adapter。

---

## 8. COLA 组件复用约定

> 仅遵循其规范与对象约定；如需引入组件依赖须经确认，**不得改动 Spring Boot / Java 版本**（版本基线见 [tech-stack.md](tech-stack.md)）。

- 统一返回：`Response` / `SingleResponse<T>` / `MultiResponse<T>` / `PageResponse<T>`（`cola-component-dto`）。
- 统一异常：`BizException`（业务）/ `SysException`（系统），禁止裸抛 `RuntimeException`（异常细则见 [coding-guideline.md](coding-guideline.md) §八）。
- 扩展点：多分支业务差异用 `ExtensionPointI` + 业务身份（bizId）替代 if-else（`cola-component-extension`）。
- 状态机：文档状态等流转可用 `cola-component-statemachine` 显式建模。
