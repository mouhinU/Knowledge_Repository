# AGENTS.md —  编码规范

> 本规范基于《Java开发手册》v1.5.0（华山版），结合 Knowledge_Repository 项目技术栈（Spring Boot 3.4 / Java 21 / MyBatis-Plus
> 3.5.17 / LangChain4j 1.0 / Milvus 2.5 / PDFBox 3 / Apache Tika 3 / Apache POI 5 / H2 + MySQL / Flyway）进行定制化裁剪。**所有 AI 生成的代码必须严格遵循以下规则。**

---

## 一、命名规约

### 1.1 基本命名

- 命名不能以下划线 `_` 或美元符号 `$` 开头或结尾。
- 严禁拼音与英文混合，更不允许直接使用中文命名。国际通用名称（如 `hangzhou`）可视同英文。
- 杜绝不规范的缩写，避免望文不知义（反例：`AbsClass`、`condi`）。

### 1.2 风格要求

| 元素                      | 风格             | 正例                                     | 反例                              |
|-------------------------|----------------|----------------------------------------|---------------------------------|
| 类名                      | UpperCamelCase | `UserService`、`SearchResultVO`         | `userService`、`SearchResultVo`  |
| 方法名 / 参数名 / 成员变量 / 局部变量 | lowerCamelCase | `localValue`、`getHttpMessage()`        | `LocalValue`、`gethttpmessage()` |
| 常量                      | 全大写 + 下划线分隔    | `MAX_CHUNK_SIZE`、`CACHE_EXPIRED_TIME`  | `MAX_SIZE`                     |
| 包名                      | 全小写，单数形式       | `com.mouhin.knowledge.repository.util`  | `com.mouhin.Knowledge.Repository.Utils` |

- 类名例外（保持全大写后缀）：`DO` / `BO` / `DTO` / `VO` / `AO` / `PO` / `UID`。
- 抽象类以 `Abstract` 或 `Base` 开头；异常类以 `Exception` 结尾；测试类以 `Test` 结尾。
- 枚举类名带 `Enum` 后缀，枚举成员全大写下划线分隔（如 `DocumentStatusEnum.INDEXED`）。

### 1.3 POJO 布尔属性

- **POJO 类中布尔类型变量不要加 `is` 前缀**，否则部分框架解析会引起序列化错误。
- 反例：`Boolean isDeleted` → 应改为 `Boolean deleted`。

### 1.4 接口与实现

- Service / DAO 层：接口名不加修饰，实现类用 `Impl` 后缀。正例：`DocumentRepositoryImpl` 实现 `DocumentRepository`。
- 接口方法不加 `public abstract` 等修饰符，保持简洁。

### 1.5 各层方法命名

| 操作     | 前缀                           |
|--------|------------------------------|
| 获取单个对象 | `get` / `find`               |
| 获取多个对象 | `list`（复数结尾，如 `listDocuments`） |
| 获取统计值  | `count`                      |
| 插入     | `save` / `insert`            |
| 删除     | `remove` / `delete`          |
| 修改     | `update`                     |

### 1.6 数据对象与领域模型命名

**基础数据对象：**

- 数据对象：`xxxDO`（xxx 为数据表名）
- 数据传输对象：`xxxDTO`（xxx 为业务领域名称）
- 展示对象：`xxxVO`（xxx 为网页名称）

**DDD 领域模型命名：**

| 概念                   | 后缀                        | 正例                                         |
|----------------------|---------------------------|--------------------------------------------|
| 聚合根（Aggregate Root）  | 直接用业务名词                   | `Document`                                   |
| 实体（Entity）           | 直接用业务名词                   | `DocumentChunk`、`User`                      |
| 值对象（Value Object）    | 直接用业务名词                   | `Permission`、`ChunkingConfig`、`SearchResult` |
| 领域服务（Domain Service） | `DomainService`           | `DocumentIngestionDomainService`             |
| 仓储接口（Repository）     | `Repository`              | `DocumentRepository`                       |
| 仓储实现                 | `RepositoryImpl`          | `DocumentRepositoryImpl`                   |
| 应用服务                 | `ApplicationService`      | `DocumentIngestionApplicationService`      |
| 领域事件（Domain Event）   | `Event`（过去式）              | `DocumentCreatedEvent`                     |
| 转换器（Converter）       | `Converter`               | `DocumentConverter`                        |

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
- **单方法行数**推荐不超过 80 行。
- IDE 编码设置 UTF-8，换行符使用 Unix 格式（LF）。

---

## 四、OOP 规约

- 所有覆写方法必须加 `@Override` 注解。
- `equals` 比较：推荐使用 `Objects.equals()`。
- DO 类属性类型必须与数据库字段类型匹配。
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
- **所有类必须添加 `@author` 和 `@date`**。
- 推荐用中文注释，专有名词保持英文。

### 本项目类注释模板

```java
/**
 * 文档摄入应用服务
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentIngestionApplicationService {
    // ...
}
```

---

## 八、异常处理

- 可通过预检查规避的 `RuntimeException` 不用 `catch` 处理。
- 捕获异常必须处理，不能空 `catch`。
- 使用 `Optional` 防止 NPE。
- 避免直接 `new RuntimeException()`，使用有业务含义的自定义异常。

---

## 九、日志规约

- **使用 SLF4J API**，不直接使用 Log4j / Logback API。

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger logger = LoggerFactory.getLogger(XxxService.class);
```

- 日志输出使用占位符：`logger.debug("Processing document with key: {}", documentKey)`。
- `trace` / `debug` / `info` 级别输出必须进行日志级别开关判断。
- 异常日志包含堆栈信息：`logger.error("Failed to process: " + e.getMessage(), e)`。

---

## 十、MySQL / H2 数据库

### 10.1 建表规约

- 表名、字段名只用小写字母和数字。
- 索引命名：主键 `pk_字段名`、唯一索引 `uk_字段名`、普通索引 `idx_字段名`。
- **表必备字段**：`id`（`bigint` 主键）、`create_time`（`datetime`/`timestamp`）、`update_time`（`datetime`/`timestamp`）。

### 10.2 SQL 语句

- 使用 `count(*)` 统计行数。
- **禁止使用 `SELECT *`**，明确写出需要的字段（MyBatis-Plus 的 `selectList(null)` 在管理端可接受）。
- SQL 参数使用 `#{}`，**禁止 `${}`**（防 SQL 注入）。

### 10.3 H2 注意事项

- H2 严格模式下标识符大小写敏感，Flyway 迁移列名须小写加引号。
- H2 不支持 MySQL 特有语法：`COMMENT`、`UNSIGNED`、`ON UPDATE CURRENT_TIMESTAMP`、内联 `INDEX`。
- `selectCount` 不能带 `ORDER BY`（H2 严格模式会报错）。

---

## 十一、工程结构与领域驱动设计

### 11.1 项目模块结构

```
knowledge-web            → 表现层（Controller、安全配置、全局异常处理）
knowledge-application    → 应用层（用例编排、事务管理、DTO ↔ 领域对象转换）
knowledge-domain         → 领域层（实体、值对象、领域服务、仓储接口、领域事件）
knowledge-infrastructure → 基础设施层（Repository 实现、DO 实体、Mapper、Converter、Milvus 向量存储、PDF 处理）
```

**依赖方向：** Web → Application → Domain ← Infrastructure

### 11.2 各层职责

| 层级                    | 职责                                                              | 依赖方向          |
|-----------------------|-----------------------------------------------------------------|---------------|
| 表现层（Web）              | 处理 HTTP 请求/响应，参数校验，调用应用层，返回统一响应格式。不包含业务逻辑。                    | → 应用层         |
| 应用层（Application）      | 实现用例，编排领域对象，控制事务边界（`@Transactional`），DTO ↔ 领域对象转换。不包含业务规则。 | → 领域层 + 基础设施层 |
| 领域层（Domain）           | 核心业务逻辑（实体、值对象、领域服务、仓储接口、领域事件）。不依赖基础设施。                          | 仅依赖 Spring Context |
| 基础设施层（Infrastructure） | 实现仓储接口，操作数据库（MyBatis-Plus）和向量库（Milvus），DO ↔ 领域对象转换。不含业务规则。 | → 领域层（实现其接口）  |

### 11.3 领域模型分布

| 模型                 | 说明                                  | 所在模块                         |
|--------------------|-------------------------------------|------------------------------|
| DO                 | 与数据库表一一对应，MyBatis-Plus 注解           | `knowledge-infrastructure` |
| 领域实体               | 业务对象，`@Getter` + `@Setter`          | `knowledge-domain`         |
| 聚合根                | 文档聚合根，包含业务校验方法                       | `knowledge-domain`         |
| 值对象                | 不可变对象（final 类 + final 属性）            | `knowledge-domain`         |
| Converter          | DO ↔ 领域对象转换器                        | `knowledge-infrastructure` |
| Repository         | 仓储接口（领域对象参数/返回值）                    | `knowledge-domain`         |
| RepositoryImpl     | 仓储实现（Mapper + Converter）            | `knowledge-infrastructure` |
| ApplicationService | 用例编排，事务控制                           | `knowledge-application`    |
| DomainService      | 跨实体业务逻辑（分块、权限过滤）                     | `knowledge-domain`         |

### 11.4 编码规范

**领域层：**
- 实体/聚合根：属性为 `private`，使用 `@Getter` + `@Setter`（禁止 `@Data`）。须基于 ID 重写 `equals` / `hashCode`。
- 仓储接口：定义在 `domain.repository` 包，参数和返回值均为领域对象，严禁出现 DO 或 DTO。
- 领域服务：无状态，`@Service` 管理，方法命名体现业务意图。
- 领域事件：Java record 实现 `DomainEvent` 标记接口，命名使用过去式。

**应用层：**
- 类名以 `ApplicationService` 结尾。每个方法对应一个用户用例，使用 `@Transactional` 控制事务。
- 禁止直接操作 DAO 或 Mapper，必须通过仓储接口。
- 构造器注入，不使用 `@Autowired`。

**基础设施层：**
- 仓储实现类以 `RepositoryImpl` 结尾，`@Repository` 注解。
- Converter 为 `final` 工具类，提供 `toDomain()`、`toDO()` 静态方法。
- 不得包含业务规则，只负责技术实现。
- 分页查询使用 `selectCount`（无 ORDER BY）+ `selectList`（LAST LIMIT OFFSET）。

**依赖注入：**
- 所有 Spring Bean 构造器注入，面向接口。
- 领域层保持纯净，不引用基础设施注解。

---

## 十二、RAG 知识库特定规范

### 12.1 多格式文档处理

- 支持格式：PDF / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) / TXT / CSV / HTML / RTF。
- 文件大小限制 200MB，超过拒绝处理。
- 格式检测使用 Apache Tika（`tika.detect()`），按 MIME 类型路由到专用解析器。
- PDF：PDFBox 按页提取，检测扫描型 PDF（低文本密度页面），记录警告日志。
- Word：Apache POI 按段落提取，每 30 段近似切分一个 section。
- Excel：Apache POI 按工作表提取，保留行列结构（Tab 分隔）。
- PowerPoint：Apache POI 按幻灯片提取，遍历文本形状。
- 其他格式：Tika AutoDetectParser 通用解析，按段落分割。
- 文件校验和（MD5）用于去重，上传前检查。
- 临时文件必须在 `finally` 中清理。

### 12.2 向量化

- Embedding 模型通过配置切换（DashScope / Ollama），使用 `@ConditionalOnProperty`。
- Milvus 集合名称通过配置指定，默认 `knowledge_chunks`。
- 每个分块的元数据（document_key、department_id、visibility、allowed_roles、owner_id）必须存入 Milvus metadata，用于权限过滤。

### 12.3 权限隔离

- 权限模型：RBAC + 文档级 ACL。
- 文档可见性：PUBLIC / INTERNAL / RESTRICTED / PRIVATE。
- 检索时通过 `PermissionDomainService.buildFilterExpression()` 构建 Milvus 过滤表达式。
- 超级管理员不受权限限制。

---

## 十三、注释模板

- 作者信息统一填写 `@author Knowledge-Repository` + `@date`。

---

## 代码生成检查清单

AI 生成代码时，逐条自检：

1. 命名是否符合驼峰规范？常量是否全大写下划线分隔？
2. 是否存在魔法值？
3. 代码格式是否 4 空格缩进？单行是否超过 120 字符？
4. POJO 布尔属性是否避免了 `is` 前缀？
5. 集合操作是否处理了 NPE？`foreach` 中是否有 `remove` / `add`？
6. 类是否有 Javadoc（`@author` + `@date`）？
7. 日志是否使用 SLF4J？是否使用占位符？
8. SQL 是否使用 `#{}` 参数绑定？
9. 异常是否被正确处理？是否存在空 `catch`？
10. 领域模型是否放在 `domain` 模块？DO 与领域对象是否分离？
11. 仓储接口是否定义在领域层、实现在基础设施层？
12. 应用服务是否只做编排（不含业务规则），事务边界是否在应用层？
13. 分块元数据是否完整附加到 Milvus metadata？
14. 权限过滤是否在检索时正确应用？
15. 新增文件格式是否在 DocumentExtractionService 中添加了专用解析器？
16. 文件校验和（MD5）是否在所有提取路径中计算？
17. 临时文件是否在 finally 中清理？
