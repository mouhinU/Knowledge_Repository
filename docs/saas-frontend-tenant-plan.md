# SaaS 前端租户化改造设计（对应执行计划 [X-T5](saas-multi-tenant-execution-plan.md) 的 UI 级细化）

> 依赖：[P1-T3](saas-multi-tenant-execution-plan.md) `TenantResolverFilter`（三级解析）· [P1-T8](saas-multi-tenant-execution-plan.md) JWT `tenantId` claim · [P3-T4](saas-multi-tenant-execution-plan.md)（403 `TENANT_SUSPENDED`）。
> 范围：`knowledge-web/src/main/resources/static/` 下全部原生 JS/HTML（**不引入前端框架**），含 `admin/` 11 页多页面控制台 + `exam.html` 学生端 + `admin.html` 重定向存根。
> 共享资源：[admin/assets/js/common.js](../../knowledge-web/src/main/resources/static/admin/assets/js/common.js)（已含 `fetch` / `EventSource` 全局拦截器，注入 `X-Admin-Token`）+ `ai-exam.js` + `paper-review.js` + `admin/assets/css/common.css`。

---

## 1. 租户解析策略（前端侧）

后端 [P1-T3](saas-multi-tenant-execution-plan.md) 支持三级解析：`X-Tenant-Id` 头 → 子域名 `{key}.example.com` → JWT claim `tenantId`，三者冲突即拒绝。前端**主用 subdomain + JWT claim 双验，`X-Tenant-Id` 头作备用通道**：

| 通道 | 适用场景 | 前端动作 |
| --- | --- | --- |
| 子域名 | 生产正式接入（租户绑域名） | 前端零改动——后端从 Host 解析；前端仅展示时以 JWT claim 为准 |
| JWT claim | 恒有（登录后） | 登录响应体带 `tenantId`，前端存全局变量并随请求注入头，供后端做双验 |
| `X-Tenant-Id` 头 | 本地开发 / 单域名部署（无子域条件） | `common.js` 拦截器统一注入（§2），值取自登录响应 |

**claim 与头不一致时后端返 401 `TENANT_MISMATCH`**，前端强制登出（§2）。

- 登录页 [admin/login.html](../../knowledge-web/src/main/resources/static/admin/login.html)：登录前无租户上下文。子域模式下无需输入；单域名部署时增加"公司编码"输入框（可选字段），仅用于登录接口在**无子域名时辅助定位租户**（后端 P1-T3 不依赖它做鉴权，只作登录查询参数），避免用户盲输账号。
- 登录响应体扩展：`{ token, tenantId, tenantName, tenantLogo }`。前端处理：
  - `tenantId` / `tenantName` 写入 `window.KR_TENANT`（JS 全局）；
  - 持久化到 `sessionStorage`（键 `kr_tenant`，与现有 `kr_admin_token` 同生命周期，**不用 localStorage**，避免共用机器残留）；
  - `tenantLogo` 有值则替换侧边栏 logo `src`。

## 2. fetch 拦截器统一注入 `X-Tenant-Id`

现状：[common.js](../../knowledge-web/src/main/resources/static/admin/assets/js/common.js) 70–93 行已有 `patchFetch`（对 `/api/` 请求注入 `X-Admin-Token`，401 跳登录），全部 admin 页面与 `ai-exam.js`/`paper-review.js` 均直接调裸 `fetch`，**依赖该全局拦截生效**。改造点：

- **注入头**：在现有 `patchFetch` 内、`headers.set('X-Admin-Token', tok)` 旁增加 `headers.set('X-Tenant-Id', getTenantId())`（登录/登出端点按现有 `isAuthEndpoint` 跳过，登录请求例外——需带公司编码参数而非头）。**不新增 `apiFetch` 命名函数**，复用全局 patch 即可覆盖 11 页 + 2 个共享 JS 的全部调用点，改动收敛在一处。
- **403 `TENANT_SUSPENDED` 全局处理**：拦截响应 `status===403` 且响应体 `error_code==='TENANT_SUSPENDED'`（需 `res.clone().json()` 嗅探，注意吞掉已读流问题；解析失败按普通 403 走既有 Toast）→ `location.replace('/admin/suspended.html')`。新增降级页 [admin/suspended.html](../../knowledge-web/src/main/resources/static/admin/suspended.html)（纯静态提示"企业空间已暂停，请联系管理员"，不请求业务 API，避免死循环跳转）。
- **401 `TENANT_MISMATCH`**：并入现有 401 分支——命中即 `clearToken()` + 清 `kr_tenant` + 跳登录页，并携带 `?reason=mismatch` 让登录页提示"令牌与租户不匹配，请重新登录"。

## 3. 侧边栏租户标识

- [admin/index.html](../../knowledge-web/src/main/resources/static/admin/index.html) 及所有 admin 页共用 common.js 的布局渲染函数（分组侧边栏 + 顶栏面包屑）：在**侧边栏顶部 / header** 增加租户名 chip（`tenantName`，`tenantLogo` 作图标）。改动落在 common.js 布局模板一处，11 页自动生效。
- 单账号恒定单租户，**不做跨租户切换下拉**（提案 §2 决策 6 由产品拍板，默认关闭）。
- 平台管理员：登录响应 `platformAdmin===true` 时显示"切换到租户视图"入口，新页 [admin/platform.html](../../knowledge-web/src/main/resources/static/admin/platform.html)（调 `/api/platform/**`，对应 [P3-T1](saas-multi-tenant-execution-plan.md)：租户创建/暂停/恢复/归档列表与操作）。**仅平台 admin 角色可见**：非平台角色直接访问该页 URL 时前端跳回 index，后端 403 兜底。

## 4. AI 实验室 / 出卷 / 错题本 / 学生端

- [ai-exam.js](../../knowledge-web/src/main/resources/static/admin/assets/js/ai-exam.js)（1623 行，含 `fetch` 与 SSE）与 [paper-review.js](../../knowledge-web/src/main/resources/static/admin/assets/js/paper-review.js)：均走全局 fetch/EventSource 拦截，**零改动**复用 §2/§5 的统一注入；只需回归验证其 SSE 建连点（`ai-exam.js` 168/674/1085 行 `new EventSource(...)`）被 §5 补丁覆盖。
- [exam.html](../../knowledge-web/src/main/resources/static/exam.html) 学生端（3480 行，含内联脚本）：学生 JWT 已带 `tenantId`（[P1-T8](saas-multi-tenant-execution-plan.md) 同链路签发学生 token），**页面加载时从 token payload 解析（base64url decode `payload`），不弹输入框**；内联 fetch 若未走 common.js（exam.html 不引该共享脚本，token 键为 `kr_student_token`），需在 exam.html 内联脚本补一个与 §2 同构的最小 fetch 包装：注入 `X-Tenant-Id` + 401/403 分派（复用同一 `TENANT_MISMATCH`/`TENANT_SUSPENDED` 语义，降级提示改为内嵌横幅而非跳转 admin 页）。
- **SSE（EventSource）**：EventSource 不支持自定义头。现有 common.js 96–117 行补丁已追加 `access_token` 查询参数；租户参数两种方案：
  1. URL 追加 `&tenantId=...`（前端可做，后端需在 SSE 控制器解析该参数）；
  2. **后端从 token claim / session 属性取（推荐，更稳）**——`access_token` 已随 URL 携带，[P1-T3](saas-multi-tenant-execution-plan.md) ResolverFilter 对 SSE 请求走 JWT claim 一级即可，前端仅需在 §2 的 EventSource 补丁里**同步追加 `X-Tenant-Id` 等价的 `tenant_id` 查询参数作备用**，并在设计说明中记录"选择 JWT claim 为主、URL 参数为备"的理由（红线 #9 的通信通道说明）。

## 5. 静态资源 cache-busting 联动

复用已上线的 [HtmlCacheBusterFilter](../../knowledge-web/src/main/java/com/mouhin/knowledge/repository/web/filter/HtmlCacheBusterFilter.java)（V2026092904 版本已部署）：HTML 内 `script/link` 引用会被自动追加 `?v=`。**本次改动无需新增缓存机制**，只需保证：common.js / ai-exam.js 等文件内容变更时应用版本位自动刷新即可；`suspended.html`、`platform.html` 两个新页面自动纳入该 filter 的 HTML 响应改写范围（filter 按 content-type 匹配 html，天然覆盖，回归验证一次即可）。

## 6. 任务清单（格式对齐执行计划表）

| 任务 | 文件锚点 | 复杂度 | 依赖 | 验收要点 |
| --- | --- | --- | --- | --- |
| X5-T1 | [common.js](../../knowledge-web/src/main/resources/static/admin/assets/js/common.js) `patchFetch`：注入 `X-Tenant-Id`（值取 `window.KR_TENANT`/sessionStorage） | M | P1-T3 · P1-T8 | 11 页 + 2 共享 JS 全部 AJAX 带正确头；登录端点不注入 |
| X5-T2 | common.js 全局错误分派：403 `TENANT_SUSPENDED` → 跳转；401 `TENANT_MISMATCH` → 强制登出；新增 `admin/suspended.html` | S | X5-T1 · P3-T4 | 后端手工置 SUSPENDED 后任意页面 3s 内落入降级页且不死循环 |
| X5-T3 | [admin/login.html](../../knowledge-web/src/main/resources/static/admin/login.html)：公司编码采集（单域名模式）+ 登录响应 `tenantId/tenantName/tenantLogo` 落 `KR_TENANT`/sessionStorage | M | P1-T8 | 子域模式免输入可登录；`?reason=mismatch` 提示文案正确 |
| X5-T4 | common.js 布局模板：侧边栏/header 租户名 chip（+logo） | S | X5-T3 | 各页刷新后 chip 显示正确租户名；无租户数据时不渲染而非显示 undefined |
| X5-T5 | 新增 [admin/platform.html](../../knowledge-web/src/main/resources/static/admin/platform.html)：平台管理员视图（`/api/platform/**` 列表 + 创建/暂停/恢复/归档）+ 入口按 `platformAdmin` 显隐 | L | P3-T1 · X5-T1 | 租户管理员访问 URL 直接跳回 index；操作后列表状态即时更新 |
| X5-T6 | [exam.html](../../knowledge-web/src/main/resources/static/exam.html) 内联脚本：从学生 JWT 解析 tenantId + 最小 fetch 包装（头注入 + 401/403 分派） | S | P1-T8 | 学生端全链路无手工输入租户 |
| X5-T7 | common.js `patchEventSource`：追加 `tenant_id` 查询参数（备用通道），主通道仍为 JWT claim | S | P1-T3 | `ai-exam.js` 三处 SSE 建连均带参且后端 claim 校验优先 |
| X5-T8 | 双租户端到端手工切换测试清单执行（见 §7） | M | X5-T1..T7 | §7 全部断言通过并在 PR 附记录 |

## 7. 测试与验收

- **双租户切换**：租户 A、B 各建管理员账号。无痕窗口分别登录 A/B（或先后清 `sessionStorage` 切换），断言：
  1. DevTools Network 中**所有** `/api/` 请求（页面跳转、搜索、文档列表、出卷 SSE、试卷复核）均带正确 `X-Tenant-Id`，与 JWT claim 一致；
  2. 用 A 会话手工改头为 B 发列表请求 → 后端返 401 `TENANT_MISMATCH`，前端跳回登录页；
  3. A 会话下访问各列表页（documents/search/users/exam-review…）只见 A 数据，无 B 残留。
- **降级路径**：P3-T4 联调——suspend A 后 A 会话任意请求 403 → `suspended.html`；resume 后可正常重登。
- **缓存**：`HtmlCacheBusterFilter` 已上线，脚本内容变更后 URL 自动 `?v=`，验证 `suspended.html`/`platform.html` 也带版本位且新 JS 不被旧缓存吞。

## 8. 与后端的契约（摘要）

| 契约项 | 约定 | 后端锚点 |
| --- | --- | --- |
| 头名 | `X-Tenant-Id` | [P1-T3](saas-multi-tenant-execution-plan.md) ResolverFilter 第一优先级 |
| 403 语义 | `error_code=TENANT_SUSPENDED` | [P3-T4](saas-multi-tenant-execution-plan.md) |
| 401 语义 | `error_code=TENANT_MISMATCH`（头与 claim 不一致） | [P1-T3](saas-multi-tenant-execution-plan.md) 冲突拒绝 |
| JWT claim | `tenantId`（老 token 缺省视为 `default`，30 天兼容窗） | [P1-T8](saas-multi-tenant-execution-plan.md) |
| 平台端点 | `/api/platform/**`，`sys_platform_admin` 表鉴权，`knowledge.platform.enabled=true` 才注册 | [P3-T1](saas-multi-tenant-execution-plan.md) |

> 开放问题（提案决策点 6）：X5-T5 平台视图深度（只做列表+暂停/恢复，还是含 provisioning 向导）由产品/教务拍板；SSO（企业 IdP）不在本期范围，subdomain 解析改动不影响未来接入。
