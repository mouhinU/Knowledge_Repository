# 安全注意事项（Security Guideline）

> 本分册**聚合**根目录 `AGENTS.md` 中散落于第十章（SQL 注入）、第十二章 §12.1（文件限制）、
> §12.3（权限隔离）的安全约束，并补充项目现有鉴权实现（管理员 / 考生令牌、口令哈希、上传上限）形成的既定规范。
> 相关代码位置以本仓库现状为准；技术栈见 [tech-stack.md](tech-stack.md)。
> 维护：`@author beginningness` · 拆分日期 2026-09-20

---

## 1. 注入防护（最高优先级）

- SQL 参数一律使用 MyBatis `#{}` 预编译占位；**禁止裸 `${}`** 以及把请求入参直接拼进 SQL（原第十章 §10.2）。
- 动态排序 / 表名列名等确需拼接的场景，必须走**白名单枚举校验**后再拼装。
- Milvus 过滤表达式由 `PermissionDomainService.buildFilterExpression()` 统一构建，**不接受调用方传入原始表达式字符串**（见 §4）。
- 日志 / 响应回显避免拼接可控换行，防日志注入。

## 2. 文件上传与解析安全

- 上传大小硬上限 **200MB**，超限拒绝；服务端由 `spring.servlet.multipart.max-file-size: 200MB`、`max-request-size: 210MB` 兜底（与解析侧限制一致，原 §12.1）。
- 格式以 **Apache Tika `tika.detect()` 探测 MIME** 为准，不信任客户端扩展名；按 MIME 路由专用解析器（见 [rag-domain-guideline.md](rag-domain-guideline.md)）。
- 文件校验和（MD5）用于去重，**上传前检查**（注意：此处 MD5 仅作内容指纹去重，非密码用途，勿用于完整性 / 身份凭证语义）。
- 临时文件必须在 `finally` 中清理，避免磁盘残留与跨请求泄漏。
- 解析第三方文档（PDF/Office）须在受控资源限制内进行，警惕压缩炸弹 / XML 外部实体（XXE），POI / Tika 保持依赖版本更新（版本见 [tech-stack.md](tech-stack.md)）。

## 3. 认证与令牌

- **口令存储**：一律 `BCryptPasswordEncoder` 哈希落库（`sys_user.password_hash`、`kb_student.password_hash`），迁移种子账号亦以 BCrypt 哈希写入，**严禁明文**。
- **管理员侧**：`X-Admin-Token` 请求头，JWT 自包含身份与过期时间，由 `AdminTokenAuthFilter` 校验，**不落库 session_token**；`/api/admin/auth/login`、`/logout` 在放行清单内。
- **考生侧**：`X-Student-Token` 请求头，服务端签发 session token 并落 `kb_student.session_token`，带 `token_expiry`（有效时长由 `TOKEN_VALIDITY_HOURS` 常量控制）。登出须走**专用方法**将 token 置空——通用 update 在 MyBatis-Plus `NOT_NULL` 策略下不会把 `session_token` 写回 `null`。
- 受保护接口按角色区分：学生读卷用 `X-Student-Token`，管理端校对 / 作废用 `X-Admin-Token`；令牌无效时接口须**明确拒绝或安全降级**（如可用卷列表在无有效令牌时回退为不泄露敏感范围的结果）。
- 前端令牌统一在 `common.js` 拦截器注入，禁止在各页面散落硬编码。

## 4. 权限与数据隔离（RBAC + 文档级 ACL）

- 权限模型：RBAC + 文档级 ACL（原 §12.3）。
- 文档可见性枚举：`PUBLIC` / `INTERNAL` / `RESTRICTED` / `PRIVATE`。
- 检索时通过 `PermissionDomainService.buildFilterExpression()` 构建 Milvus 过滤表达式，元数据（`department_id`、`visibility`、`allowed_roles`、`owner_id`）随分块写入向量库 metadata（见 [rag-domain-guideline.md](rag-domain-guideline.md) §2）。
- **超级管理员不受权限限制**；其余主体默认最小权限，越权即拒绝。
- 考试链路：一份卷仅允许考试一次、试卷作废后不可再开考 / 不再出现在可用列表——这些为**服务端硬门禁**（`ExamStartFromHistoryCmdExe`），不得只在 UI 层隐藏入口。

## 5. 日志与敏感信息

- 禁止记录口令、令牌、个人敏感信息（身份证 / 手机号等）；日志统一用 Lombok `@Slf4j`，用法见 [coding-guideline.md](coding-guideline.md) §九。
- 异常日志可含堆栈，但对外响应只回 `errorMessage` 摘要，不泄漏内部结构 / SQL / 文件绝对路径。

## 6. 错误处理对外的信息面

- 业务态冲突统一经 `GlobalExceptionHandler` 映射：`IllegalStateException → 409 CONFLICT`、`IllegalArgumentException → 400`、越权 / 未认证 `→ 401`；泛化异常 `→ 500` 且**不回显内部信息**（`"An unexpected error occurred"`）。

## 7. 高风险改动确认

以下改动一旦触碰，**执行前必须向用户复述影响面并取得确认**，禁止擅自进行：

- 鉴权 / 令牌 / 口令 / 权限模型的任何变更（§3、§4）；
- 触及 SQL 拼接面，或新增动态列名 / 排序逻辑（§1）；
- Flyway 迁移链改动，**尤其修改已发布的历史脚本**——迁移只增不改历史，新列遵循向前兼容（见 [data-and-migration-guideline.md](data-and-migration-guideline.md) §4）；
- 文件上传 / 解析扩展：引入新格式或放开大小 / 类型限制（§2）；
- `GlobalExceptionHandler` 对外信息面变更（§6）；
- Milvus 权限过滤 `buildFilterExpression()` 逻辑改动（§4）；
- `deploy.sh` / `docker-compose*.yml` / 镜像与数据卷清理。

处置纪律：

1. **不可逆 / 破坏性操作**（删库、清卷、`docker compose ... --remove-orphans`、`git reset --hard`、`push --force`、`rm`）一律先确认再执行，绝不擅自 skip 校验或绕过钩子。
2. 改动前先备份并确认可回滚；修改用户目录文件（非版本控制内）前先复制留底。
3. 涉密 / 高风险改动收尾须跑：**门禁负向用例 + 迁移冒烟**（[testing-guideline.md](testing-guideline.md) §4/§5），并把验证结论写进变更说明（模板见 [code-review-checklist.md](code-review-checklist.md) §三）。
