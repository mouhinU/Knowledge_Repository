# 编码规范（Coding Guideline）

> 本分册由根目录 `AGENTS.md` 第一~九章 + 第十三章拆分而来，基于《Java 开发手册》v1.5.0（华山版）
> 结合本项目技术栈（见 [tech-stack.md](tech-stack.md)）定制裁剪。
> 分层归属 / 对象转化 / 依赖方向等**架构**规则见 [architecture-decisions.md](architecture-decisions.md)。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20

目录：一 命名 · 二 常量 · 三 格式 · 四 OOP · 五 集合 · 六 并发 · 七 注释 · 八 异常 · 九 日志 · 十 方法规范 · 十一 常见反模式

---

## 一、命名规约

### 1.1 基本命名

- 命名不能以下划线 `_` 或美元符号 `$` 开头或结尾。
- 严禁拼音与英文混合，更不允许直接使用中文命名。国际通用名称（如 `hangzhou`）可视同英文。
- 杜绝不规范的缩写，避免望文不知义（反例：`AbsClass`、`condi`）。

### 1.2 风格要求

| 元素                                  | 风格                | 正例                                   | 反例                                    |
| ------------------------------------- | ------------------- | -------------------------------------- | --------------------------------------- |
| 类名                                  | UpperCamelCase      | `UserService`、`SearchResultVO`        | `userService`、`SearchResultVo`         |
| 方法名 / 参数名 / 成员变量 / 局部变量 | lowerCamelCase      | `localValue`、`getHttpMessage()`       | `LocalValue`、`gethttpmessage()`        |
| 常量                                  | 全大写 + 下划线分隔 | `MAX_CHUNK_SIZE`、`CACHE_EXPIRED_TIME` | `MAX_SIZE`                              |
| 包名                                  | 全小写，单数形式    | `com.mouhin.knowledge.repository.util` | `com.mouhin.Knowledge.Repository.Utils` |

- 类名例外（保持全大写后缀）：`DO` / `BO` / `DTO` / `VO` / `AO` / `PO` / `UID`。
- 抽象类以 `Abstract` 或 `Base` 开头；异常类以 `Exception` 结尾；测试类以 `Test` 结尾（测试细则见 [testing-guideline.md](testing-guideline.md)）。
- 枚举类名带 `Enum` 后缀，枚举成员全大写下划线分隔（如 `DocumentStatusEnum.INDEXED`）。

### 1.3 POJO 布尔属性

- **POJO 类中布尔类型变量不要加 `is` 前缀**，否则部分框架解析会引起序列化错误。
- 反例：`Boolean isDeleted` → 应改为 `Boolean deleted`。

### 1.4 接口与实现

- Service / Gateway 层：接口名不加修饰，实现类用 `Impl` 后缀。正例：`DocumentGatewayImpl` 实现 `DocumentGateway`（COLA 术语，等价于历史命名 `Repository`）。
- app 层用例执行器以 `CmdExe` / `QryExe` 结尾（如 `DocumentAddCmdExe`）。
- 接口方法不加 `public abstract` 等修饰符，保持简洁。

### 1.5 各层方法命名

| 操作         | 前缀                                   |
| ------------ | -------------------------------------- |
| 获取单个对象 | `get` / `find`                         |
| 获取多个对象 | `list`（复数结尾，如 `listDocuments`） |
| 获取统计值   | `count`                                |
| 插入         | `save` / `insert`                      |
| 删除         | `remove` / `delete`                    |
| 修改         | `update`                               |

### 1.6 数据对象与领域模型命名

**基础数据对象：**

- 数据对象：`xxxDO`（xxx 为数据表名）
- 数据传输对象：`xxxDTO`（xxx 为业务领域名称）
- 展示对象：`xxxVO`（xxx 为网页名称）

**领域模型 / COLA 对象命名：**

| 概念                               | 后缀                | 正例                                           |
| ---------------------------------- | ------------------- | ---------------------------------------------- |
| 聚合根（Aggregate Root）           | 直接用业务名词      | `Document`                                     |
| 实体（Entity）                     | 直接用业务名词      | `DocumentChunk`、`User`                        |
| 值对象（Value Object）             | 直接用业务名词      | `Permission`、`ChunkingConfig`、`SearchResult` |
| 领域服务（Domain Service）         | `DomainService`     | `DocumentIngestionDomainService`               |
| 领域能力（Domain Ability）         | `DomainAbility`     | `ChunkingDomainAbility`                        |
| 网关接口（Gateway，原 Repository） | `Gateway`           | `DocumentGateway`                              |
| 网关实现（infrastructure）         | `GatewayImpl`       | `DocumentGatewayImpl`                          |
| 应用服务接口（client 层）          | `ServiceI`          | `DocumentServiceI`                             |
| 应用服务实现（app 层）             | `ServiceImpl`       | `DocumentServiceImpl`                          |
| 命令 / 查询执行器（app 层）        | `CmdExe` / `QryExe` | `DocumentAddCmdExe`                            |
| 命令 / 查询 DTO（client 层）       | `Cmd` / `Qry`       | `DocumentAddCmd`、`DocumentGetQry`             |
| 领域事件（Domain Event）           | `Event`（过去式）   | `DocumentCreatedEvent`                         |
| 转换器（Converter）                | `Converter`         | `DocumentConverter`                            |

> 对象**所在分层**与**跨层传递禁令**见 [architecture-decisions.md](architecture-decisions.md) §2。

---

## 二、常量定义

- **禁止魔法值**：所有常量必须预先定义，不允许直接出现在代码中。
- `long` / `Long` 赋值时使用大写 `L`，不用小写 `l`。
- 按功能归类维护常量，不要用一个常量类维护所有常量。

---

## 三、代码格式

- **缩进**：采用 4 个空格缩进，禁止使用 tab 字符。
- **大括号**：左大括号前不换行，左大括号后换行，右大括号前换行，右大括号后有 `else` 不换行。
- **单行字符数**不超过 120 个，超出换行时第二行缩进 4 个空格。
- **单方法行数**不超过 **50 行**（硬性，细化见 §十 方法与函数规范）。
- IDE 编码设置 UTF-8，换行符使用 Unix 格式（LF）。

---

## 四、OOP 规约

- 所有覆写方法必须加 `@Override` 注解。
- `equals` 比较：推荐使用 `Objects.equals()`。
- DO 类属性类型必须与数据库字段类型匹配（数据对象规范见 [data-and-migration-guideline.md](data-and-migration-guideline.md)）。
- POJO 类属性必须使用包装数据类型。
- DO/DTO/VO 等 POJO 类不要设定任何属性默认值。
- 循环体内字符串连接使用 `StringBuilder.append`。

---

## 五、集合处理

- 覆写 `equals` 必须覆写 `hashCode`。
- **禁止在 `foreach` 循环里进行 `remove` / `add`**，使用 `Iterator` 方式。
- 集合初始化时指定初始大小。
- 使用 `entrySet` 遍历 Map（JDK8+ 使用 `Map.forEach`）。

---

## 六、并发处理

- **线程资源必须通过线程池提供**，不允许自行显式创建线程。
- **线程池不允许使用 `Executors` 创建**，必须通过 `ThreadPoolExecutor` 明确参数。
- **必须回收自定义的 `ThreadLocal` 变量**，使用 `try-finally` 调用 `remove()`。

---

## 七、注释规约

- 类、类属性、类方法注释使用 `/** Javadoc */` 格式。
- **新建或重点修改的 Java 类应补齐 `@author` + `@date` 的 Javadoc**；已有类不强制一次性大改，但新增/关键改动需保持一致。
- 推荐用中文注释，专有名词保持英文。
- **`@author` 取值**：填写当前开发者标识，取自本仓库 `git config user.name` 的返回值（本仓库为 `mouhinU`），不使用固定占位名。
- **`@date` 取值**：填写**编写该注释时的当前系统时间**，格式 `yyyy-MM-dd HH:mm:ss`（24 小时制，如 `2026-09-21 08:23:43`）；由 AI 生成代码时须以运行环境的实时时间填充，不得沿用示例值或臆造时间。
- 作者与时间信息统一采用上述口径（原第十三章并入此处）。

### 本项目类注释模板

```java
/**
 * 文档应用服务实现（app 层，仅分发到 Executor）
 *
 * @author mouhinU
 * @date 2026-09-21 08:23:43
 */
@Service
public class DocumentServiceImpl implements DocumentServiceI {
    // ...
}
```

---

## 八、异常处理

- 可通过预检查规避的 `RuntimeException` 不用 `catch` 处理。
- 捕获异常必须处理，不能空 `catch`。
- 使用 `Optional` 防止 NPE。
- 避免直接 `new RuntimeException()`，使用有业务含义的自定义异常；COLA 统一异常体系（`BizException` / `SysException`）见 [architecture-decisions.md](architecture-decisions.md) §8。
- 业务态冲突（如重复开考、未发布 / 已作废不可操作）经 `GlobalExceptionHandler` 映射为 `409 CONFLICT` 并携带 `errorMessage`，抛出 `IllegalStateException`；参数非法抛 `IllegalArgumentException`（映射为 `400`）。

---

## 九、日志规约

- **统一使用 Lombok `@Slf4j` 注解声明日志对象**：类上加 `@Slf4j`，编译期自动生成名为 **`log`** 的静态字段，直接 `log.debug(...)` 使用，**禁止**再手写 `private static final Logger logger = LoggerFactory.getLogger(...)`，也无需 import `Logger` / `LoggerFactory`。
- `@Slf4j` 底层仍是 SLF4J API，故仍**不得**直接依赖 Log4j / Logback API。

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class XxxService {
    public void handle(String documentKey) {
        log.info("Processing document with key: {}", documentKey);
    }
}
```

- 日志输出使用占位符 `{}`，**禁止 `+` 字符串拼接**：`log.debug("Processing document with key: {}", documentKey)`。
- 一般无需手工判级；仅当参数需要昂贵计算时才用 `log.isXxxEnabled()` 守卫。
- 异常日志把异常对象作为**最后一个参数**传入以保留堆栈：`log.error("处理文档失败, key={}", documentKey, e)`（勿 `+ e.getMessage()` 而丢栈）。
- **禁止记录敏感信息**（口令、令牌、身份证等），安全日志约束见 [security-guideline.md](security-guideline.md) §5。
- **存量迁移**：仓库现有类多为手写 `LoggerFactory`（约 100 个），不强制一次性重构；但**新增 / 重点改动的类一律改用 `@Slf4j`**，随迭代收敛到统一口径。

---

## 十、方法与函数规范（硬性）

> 以下为**不可协商**的硬约束，覆盖并细化第三、四章相关条目（2026-09-21 新增）。

1. **方法名 ≤ 50 字符**。在见名知意的前提下尽量精炼；禁止无意义缩写（§1.1）。名称逼近上限通常意味着职责过重，应拆分为更小的方法。

2. **方法体 ≤ 50 行**（不含空行与注释）。超过即按单一职责拆出私有方法；此条为硬性上限，取代原 §三"推荐 ≤ 80 行"。

3. **参数个数 ≤ 5，且 > 3 即封装**。
    - 参数超过 3 个时，统一封装为请求对象传入（client 层 `XxxCmd` / `XxxQuery`，或 app 内部参数 DTO）；
    - 5 个为绝对上限，达到即必须重构；
    - 避免布尔开关参数（`boolean` flag）——调用点语义模糊，必要时拆成两个方法或用枚举表达意图。

4. **优先编写纯函数**：**相同的输入永远产生相同的输出**，且不产生副作用——不修改入参、不读写可变全局状态、不做 I/O。
    - 确需副作用时（DB / 网络 / 文件 / 系统时间 / 随机数），将其**收敛到系统边界**（infrastructure 的 `Gateway` 与外部调用），核心计算逻辑保持纯；
    - 该原则与 COLA「domain 层纯净」一致，分层与事务边界见 [architecture-decisions.md](architecture-decisions.md) §4 / §5。

> 例外：框架入口（`main`、Controller 方法）、`equals` / `hashCode` / 构造器等样板方法以表达清晰为先，可适度放宽行数与参数限制，但仍受命名与"优先纯函数"意图约束。

---

## 十一、常见反模式（反例 → 正解）

以下均为本项目高频踩坑点，生成 / 审查代码时优先排查：

| 反模式 | 危害 | 正解 |
|---|---|---|
| 字段 `@Autowired` 注入 | 隐藏依赖、难以测试、启动期 NPE | 构造器注入 + `final`，面向接口（[architecture-decisions.md](architecture-decisions.md) §7） |
| `Executors.newXxxThreadPool` / 循环内 `new Thread` | 无界队列 / OOM、线程失控 | `ThreadPoolExecutor` 显式参数（§六） |
| `foreach` 里 `remove` / `add` | `ConcurrentModificationException` | `Iterator.remove()` 或 `removeIf`（§五） |
| SQL 用 `${}` 拼接、`SELECT *` | 注入 + 无谓 IO | 一律 `#{}`，动态列走白名单，显式列字段（§ data & migration / security §1） |
| DO 出现在 app / adapter | 破坏分层、泄漏表结构 | 层边界转化 `Entity/DTO/VO`（architecture §2） |
| 一个 Service 方法堆完整用例、事务里裹 LLM / 外部 IO | 长事务占连接、超时连锁回滚 | 一用例一 Executor，事务收窄，IO 移出事务（architecture §4） |
| 手写 `LoggerFactory.getLogger` / 日志用 `+` 拼接 / 打印口令 · 令牌 | 样板冗余、性能损耗 + 泄密 | 类上 `@Slf4j`（字段 `log`）+ 占位符 `{}`，敏感信息脱敏（§九、security §5） |
| 空 `catch` / 裸 `new RuntimeException` | 吞异常、无业务语义 | 记录或转译；用 `BizException` / `IllegalStateException`（§八、architecture §8） |
| 魔法值散落各处 | 不可维护、易漂移 | 归类预定义常量（§二） |
| POJO 布尔写成 `isDeleted` | 部分框架序列化歧义 | 去 `is` 前缀：`deleted`（§1.3） |
| 类缺 `@author` / `@date` | 无法溯源 | 补 Javadoc（§七） |
