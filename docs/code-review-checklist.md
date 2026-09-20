# 代码生成检查清单（AI Self-Check Checklist）

> 本分册由根目录 `AGENTS.md` 末章「代码生成检查清单」拆分而来，作为**提交前逐条自检表**。
> 每条后括注对应细则文件；安全（19→23）与测试（24→27）条目为拆分时按新增维度补入的自检项。
> 维护：`@author Knowledge-Repository` · 拆分日期 2026-09-20

AI 生成代码时，逐条自检：

## A. 编码规范类

1. 命名是否符合驼峰规范？常量是否全大写下划线分隔？（[coding-guideline.md](coding-guideline.md) §1）
2. 是否存在魔法值？（§2）
3. 代码格式是否 4 空格缩进？单行是否超过 120 字符？（§3）
4. POJO 布尔属性是否避免了 `is` 前缀？（§1.3）
5. 集合操作是否处理了 NPE？`foreach` 中是否有 `remove` / `add`？（§5）
6. 类是否有 Javadoc（`@author` + `@date`）？（§7）
7. 日志是否使用 SLF4J？是否使用占位符？（§9）
8. 异常是否被正确处理？是否存在空 `catch`？（§8）

## B. 架构 / 分层类（COLA）

9. SQL 是否使用 `#{}` 参数绑定？（[data-and-migration-guideline.md](data-and-migration-guideline.md) §2）
10. 领域模型是否放在 `domain` 层？DO 与领域对象是否分离、DO 未越过 infrastructure 层？（[architecture-decisions.md](architecture-decisions.md) §2）
11. Gateway 接口是否定义在领域层、`GatewayImpl` 是否实现在基础设施层？（COLA 术语，等价原 Repository）（§3/§5/§6）
12. app 层是否只做分发（Service → Executor），用例编排是否在单一 Executor、事务边界是否在 app 层？（§4）
13. 对外契约（Service 接口 + Command/Query/Response DTO）是否归入 client 层、而非散落在 web？（§1）
14. 分层依赖是否单向合规：domain 纯净不依赖其它业务层，infrastructure 反向实现 domain？（§1）
15. 是否使用构造器注入、面向接口，杜绝字段 `@Autowired`？（§7）

## C. RAG 领域类

16. 分块元数据是否完整附加到 Milvus metadata？（[rag-domain-guideline.md](rag-domain-guideline.md) §2）
17. 权限过滤是否在检索时正确应用？（[security-guideline.md](security-guideline.md) §4）
18. 新增文件格式是否在 `DocumentExtractionService` 中添加了专用解析器？（rag §1）
19. 文件校验和（MD5）是否在所有提取路径中计算？（rag §1）
20. 临时文件是否在 `finally` 中清理？（rag §1）

## D. 安全类（拆分补入）

21. 是否存在 SQL 拼接 / `${}` / 未走白名单的动态列名？（[security-guideline.md](security-guideline.md) §1）
22. 是否记录了口令 / 令牌 / 敏感个人信息到日志或对外响应？（§5/§6）
23. 鉴权门禁（一次开考、作废不可再考、超管豁免）是否落在服务端而非仅前端隐藏？（§3/§4）

## E. 数据库 / 迁移 / 测试类（拆分补入）

24. 新增 / 修改迁移脚本是否同步 `FlywayMigrationSmokeTest` 的计数、版本断言与对象抽查？（[testing-guideline.md](testing-guideline.md) §5）
25. 迁移是否 H2 + MySQL 双兼容（无 `COMMENT`/`UNSIGNED`/内联 `INDEX`，布尔用 `BOOLEAN`）？（[data-and-migration-guideline.md](data-and-migration-guideline.md) §3/§4）
26. 关键业务分支与门禁负向路径是否都有单测（Given–When–Then + 中文 `@DisplayName`）？（[testing-guideline.md](testing-guideline.md) §2/§4）
27. `./mvnw test` 是否全绿（`Failures: 0, Errors: 0`）？涉库改动是否跑过迁移冒烟？（§6）
