# 例外记录目录（docs/exceptions/）

本目录存放**已登记/已批准的规范例外记录**，落实 [../../AGENTS.md](../../AGENTS.md)
§5「例外申请与审批流程」：当某项规范无法适用或存在冲突时，以文件形式记录理由、替代方案、风险评估、回退计划、到期时间与批准人，使例外
**可审计、可到期、可复审**，而不是口头绕过。

## 目录内容与格式

- **`_template.yml`** —— 例外记录模板（推荐格式，YAML 结构化便于脚本扫描到期日）。复制为 `EX-YYYYMMDD-NNN.yml` 或主题名（如
  `saas-multi-tenant-001.yml`）后填写。
- **`*.md` / `*.yml`** —— 具体例外记录。早期记录为 Markdown（如 `20260921-allow-custom-logger.md`、
  `20260923-observability-prometheus-registry.md`），**新增一律用 YAML 模板**；历史 Markdown 记录到期复审时迁移为 YAML。
- 每条记录的 `affected_rule` 必须精确引用 AGENTS.md §0 红线编号或某分册章节；`scope` 必须可枚举（模块/包/端点/表），禁止「全局适用」式模糊登记。

## 使用流程

1. **发起**：在相关 PR 描述中新增一段 `Exception-Note`，包含：受影响规范、理由、替代方案、风险评估、回退计划、所需时间窗口。
2. **签署**：指定至少一位架构负责人或安全负责人（运维类例外加运维/基础设施负责人）在 PR Review 中以 comment +
   `/approve-exception` 标签签署；PR 描述需引用对应 issue 或设计文档。
3. **落档**：审批合并后，在本目录创建/更新对应 `EX-*.yml` 文件，`status: approved`，注明到期时间或需复审的里程碑。*
   *未落档的例外视为无效**。
4. **引用**：其他文档（提案、计划、检查清单）通过相对链接引用例外文件，不复制内容，避免漂移。SaaS
   提案的例外索引见 [../saas-multi-tenant-proposal-review-pack.md](../saas-multi-tenant-proposal-review-pack.md) §5。

## 复审节奏

- **到期即复审**：例外到期时，责任人须发起复审 Issue，并在 **30 天内**完成「恢复规范 / 续期（重签到期日）/ 撤销（
  `status: revoked`）」三选一的决议。
- **变更触发复审**：例外所依赖的功能/架构发生变更（如白名单表结构调整、端点鉴权模型变化）时，不等到期立即复审。
- **无人复审的后果**：逾期未复审的记录由巡检者直接置为 `expired`，对应豁免失效——引用它的代码须恢复遵守原规范。
- 建议巡检节奏：每月一次扫描本目录 `expiry` 字段，临期 30 天内的记录在对应 Issue 中点名提醒。

## 当前记录索引

| 文件                                                                                             | 主题                        | 状态                |
|------------------------------------------------------------------------------------------------|---------------------------|-------------------|
| [_template.yml](_template.yml)                                                                 | 例外记录模板                    | —                 |
| [saas-multi-tenant-001.yml](saas-multi-tenant-001.yml)                                         | 平台级容器表豁免 tenant_id 列      | draft             |
| [20260921-allow-custom-logger.md](20260921-allow-custom-logger.md)                             | 特殊场景自定义 Logger            | 已登记（Markdown 旧格式） |
| [20260923-observability-prometheus-registry.md](20260923-observability-prometheus-registry.md) | /actuator/prometheus 匿名暴露 | 已登记（Markdown 旧格式） |
