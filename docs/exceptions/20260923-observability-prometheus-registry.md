# 变更提案：引入 micrometer-registry-prometheus 并匿名暴露 /actuator/prometheus

- 类型：依赖新增 + 运行期可观测端点暴露（安全面变更）
- 提案编号：CP-2026-0923-OBS-01
- 关联提交：`e475cb0`（feat(observability): 引入 micrometer-registry-prometheus 打通 /actuator/prometheus 抓取）
- 关联里程碑：文档解析策略模式重构 Phase D / Phase R3（观测落地）
- 起草日期：2026-09-23

## 受影响规范

- `AGENTS.md` 红线 #11「谨慎变更框架 / JDK 版本；依赖以 docs/tech-stack.md 为准，重大变更需提交变更提案（影响评估、回退计划、测试覆盖）」——本提案即为其要求的变更提案记录。
- `AGENTS.md` 红线 #4「严禁在日志 / 响应中回显敏感信息」——指标暴露面须确认无敏感 / 高基数标签。
- `docs/security-guideline.md`「对外响应与暴露面」——新增一条匿名可达的运维端点，属需评估的安全相关变更。

## 批准人

- 架构负责人：mouhin（用户，于本会话直接下达「加入对应的依赖」指令）
- 批准方式：会话内明确指示实施；本记录作为正式留档补齐 §5「例外 / 变更须落 `docs/exceptions/` 记录文件」的归档要求。

## 生效时间

- 2026-09-23

## 到期时间 / 复审

- 复审触发点（先到者为准）：
  1. 引入独立 `management.server.port` 或将 actuator 迁至内网 / 网关鉴权之后（届时本匿名暴露例外可撤销）；
  2. 里程碑：下一次生产部署前。
- 默认复审窗口：生效后 90 天内（至 2026-12-22）复核是否收紧暴露面。

## 变更内容

1. `knowledge-web/pom.xml` 新增 `io.micrometer:micrometer-registry-prometheus`，版本由 Spring Boot 3.4.4 的 BOM 统一管理（实测解析 1.14.5），**未写死版本号、未改动 Spring Boot / Java 版本**。
2. `application.yml`：`management.endpoints.web.exposure.include` 追加 `prometheus`，并置 `management.endpoint.prometheus.enabled=true`。
3. `AdminTokenAuthFilter`：将 actuator 匿名放行由单一 `/actuator/health` 泛化为 `PUBLIC_ACTUATOR_PATHS = {/actuator/health, /actuator/prometheus}`；其余 actuator 端点（如 `/actuator/metrics`）仍受管理端令牌保护。

## 理由

Grafana 面板 `docs/grafana/extraction-dashboard.json` 需要 Prometheus 抓取 Spring Boot 指标。Prometheus 是服务端定期拉取（scrape），无法携带会过期的管理端 JWT，因此抓取端点必须能在无令牌下可达。`ExtractionMetrics` 已把解析观测指标写入 `MeterRegistry`，缺少 registry 与暴露端点则无法被抓取，面板形同虚设。

## 影响评估

- **运行期**：新增一个 Prometheus 文本编码端点，随 actuator 复用既有 HTTP 端口 8091；无新增进程、无 schema 变更。
- **依赖树**：仅新增 `micrometer-registry-prometheus`（及其传递的 `prometheus-metrics-*`），版本受 BOM 约束，与既有 `micrometer-core`（infra）一致，不引入版本冲突。
- **性能**：Prometheus registry 维护指标的成本与既有 `MeterRegistry` 相同量级；抓取为按需拉取，非推送。
- **兼容**：不改框架 / JDK 主版本；对业务代码零改动（`ExtractionMetrics` 之前已在写 `MeterRegistry`）。
- **回退半径**：小且集中（见下）。

## 安全评估（重点）

- **暴露内容**：仅低基数运维指标。代码层已约束 `ExtractionMetrics` 标签集合为 `strategy/mime/outcome/reason/model`，**不含** `documentKey`、`fileName`、用户身份等高基数 / 敏感字段；HTTP 指标 `uri` 由 Spring 模板化（如 `/actuator/health`、`UNKNOWN`），不放大基数、不泄露资源标识。
- **鉴权边界**：匿名放行是**窄白名单**，仅 health + prometheus；`/actuator/metrics` 等调试型端点仍 401（已加负向用例固化）。
- **残余风险**：与任意暴露 actuator 的环境一致——指标本身可能反映请求速率、错误分布、JVM 状况等运营信息。**缓解 / 收敛建议**（复审时推进）：
  1. 为管理端点配置独立 `management.server.port` 并仅绑定内网网卡；
  2. 或在反向代理 / 网络策略层按来源 IP 限制 `/actuator/prometheus`；
  3. 生产镜像默认关闭或加 basic-auth（如引入）。

## 测试覆盖

- 全反应堆 `./mvnw test` → **BUILD SUCCESS**（Client / Domain / Infra / Application / Web 全绿）。
- `AdminTokenAuthFilterTest` 用例 5 → 6：
  - `publicPaths_skipped` 增加 `/actuator/prometheus` 匿名放行断言；
  - 新增 `nonWhitelistedActuator_requiresAuth`，断言 `/actuator/metrics` 无令牌返回 401（守住窄白名单）。
- 部署冒烟（镜像 `V2026092227`）：容器 healthy；`/actuator/health` = 200、`/actuator/prometheus` = **200 且导出 240 个指标族**、`/actuator/metrics` = **401**；抓取内容确认 `uri` 为模板化低基数值。

## 回退计划

三处独立、可原子回滚，撤销提交 `e475cb0` 或按需回退其中任一项即可：

1. 移除 `knowledge-web/pom.xml` 中 `micrometer-registry-prometheus` 依赖；
2. `application.yml` 的 `exposure.include` 去掉 `prometheus` 并删除 `endpoint.prometheus` 块；
3. `AdminTokenAuthFilter` 将 `PUBLIC_ACTUATOR_PATHS` 收回为仅 `/actuator/health`（并同步回退相应测试）。

回退后系统回到本提案前状态：指标仍写入 `MeterRegistry`（`/actuator/metrics` 可查），只是不再暴露 Prometheus 抓取端点；不影响任何解析业务链路。

## 验证证据

- 依赖解析：`dependency:tree` 显示 `io.micrometer:micrometer-registry-prometheus:jar:1.14.5:compile`（BOM 供给）。
- 抓取实测：`curl localhost:8091/actuator/prometheus` → HTTP 200，240 个 `# HELP/# TYPE` 指标族。
- 文档零漂移同步：`docs/tech-stack.md` 依赖表新增「指标可观测」行；`docs/extraction-strategy-refactor-plan.md` §0.3 观测项转 ✅；`docs/grafana/extraction-dashboard.json` 描述去掉「前置未加」措辞。

## 复审链接

- 待创建收紧暴露面（独立 management 端口 / 网络策略）的 Issue，指派给架构负责人。
