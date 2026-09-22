# 数据库与迁移规范（MySQL / H2 / Flyway）

> 本分册由根目录 `AGENTS.md` 第十章拆分而来。DO ↔ Entity 转化规则见
> [architecture-decisions.md](architecture-decisions.md) §6，迁移脚本的冒烟测试见 [testing-guideline.md](testing-guideline.md)。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20

---

## 一、建表规约

- 表名、字段名只用小写字母和数字。
- 索引命名：主键 `pk_字段名`、唯一索引 `uk_字段名`、普通索引 `idx_字段名`。
- **表必备字段**：`id`（`bigint` 主键）、`create_time`（`datetime`/`timestamp`）、`update_time`（`datetime`/`timestamp`）。

## 二、SQL 语句

- 使用 `count(*)` 统计行数。
- **禁止使用 `SELECT *`**，明确写出需要的字段（MyBatis-Plus 的 `selectList(null)` 在管理端可接受）。
- SQL 参数使用 `#{}`，禁止把请求入参直接拼进 SQL；动态列名 / 排序须白名单校验后再拼，禁止裸 `${}`（见 [security-guideline.md](security-guideline.md) §1）。

## 三、H2 注意事项（跨方言兼容）

- H2 严格模式下标识符大小写敏感，Flyway 迁移列名须小写加引号。
- H2 不支持 MySQL 特有语法：`COMMENT`、`UNSIGNED`、`ON UPDATE CURRENT_TIMESTAMP`、内联 `INDEX`。
- `selectCount` 不能带 `ORDER BY`（H2 严格模式会报错）。

## 四、Flyway 迁移实践（项目约定）

- 迁移脚本置于 `knowledge-web/src/main/resources/db/migration/`，命名 `V{n}__{snake_case_描述}.sql`，严格递增不跳号、不复用旧版本号。
- 新增列的布尔标记统一用 `BOOLEAN DEFAULT FALSE`（而非 `TINYINT(1)`），与既有 V6 `is_correct BOOLEAN` 保持一致，确保 H2 + MySQL 双兼容。
- 每引入一版迁移，须同步更新 `FlywayMigrationSmokeTest` 的 `EXPECTED_MIGRATION_COUNT` 常量、最终版本断言，并抽查本版新增代表性对象（表 / 列）存在。
- 迁移内**不得硬编码明文密钥 / 口令**；种子管理员口令以 BCrypt 哈希写入（见 [security-guideline.md](security-guideline.md) §3）。
- 加索引 / 改列以「向前兼容」为原则：新列给默认值，避免直接破坏既有读路径。
