# 文档索引 — Knowledge Repository

> 本文件是项目文档的**统一导航入口**。按使用场景分类，快速定位你需要的文档。
> 最后更新：2026-10-13。

---

## 按角色定位

### 我是新人，想了解项目

| 文档                                                               | 说明                                   |
|------------------------------------------------------------------|--------------------------------------|
| [README.md](README.md)                                           | 项目简介、快速开始、技术栈、API 概览                 |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)                         | 全量能力清单（609 行），覆盖 12 个功能域、领域模型、API 一览 |
| [docs/project-overview.md](docs/project-overview.md)             | 项目文档与业务流程梳理（COLA 五层、核心业务域、SaaS 体系、前端页面） |
| [docs/architecture-diagram.html](docs/architecture-diagram.html) | 架构图（可视化）                             |
| [docs/architecture-diagram.md](docs/architecture-diagram.md)     | 架构图（Markdown / Mermaid 纯文本版，与 .html 同源） |

### 我要写代码

| 文档                                                               | 说明                                                        |
|------------------------------------------------------------------|-----------------------------------------------------------|
| [AGENTS.md](AGENTS.md)                                           | **AI 编码总纲**：12 条红线 + 分册导航 + 任务决策树                         |
| [docs/coding-guideline.md](docs/coding-guideline.md)             | Java 编码规范（命名 / 常量 / OOP / 集合 / 并发 / 异常 / 日志 / 方法规范 / 反模式） |
| [docs/architecture-decisions.md](docs/architecture-decisions.md) | COLA 五层分层、对象模型、术语映射、Executor/CQRS、DI 规则                   |
| [docs/tech-stack.md](docs/tech-stack.md)                         | 技术选型、依赖版本、五模块布局、本地运行命令                                    |
| [docs/code-review-checklist.md](docs/code-review-checklist.md)   | 提交前 31 条自检清单（风险分级 + 变更模板）                                 |

### 我要建表 / 写迁移 / 写 SQL

| 文档                                                                           | 说明                            |
|------------------------------------------------------------------------------|-------------------------------|
| [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md) | 建表规范、SQL 规则、H2 方言差异、Flyway 实践 |
| [docs/database-migrations.md](docs/database-migrations.md)                   | 全量 Flyway 迁移速览（V1 → V35，逐版本动作摘要）  |
| [docs/security-guideline.md](docs/security-guideline.md)                     | SQL 注入防护、参数化查询、敏感数据处理         |

### 我要动文档解析 / 分块 / 向量化 / 出题

| 文档                                                                                     | 说明                               |
|----------------------------------------------------------------------------------------|----------------------------------|
| [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md)                           | RAG 领域规范：多格式解析、向量化、metadata、考试配图 |
| [docs/extraction-strategy-refactor-plan.md](docs/extraction-strategy-refactor-plan.md) | 文档抽取策略架构设计（含 10 条 ADR，长期参考）      |
| [docs/security-guideline.md](docs/security-guideline.md)                               | 上传安全、权限隔离、ACL 过滤                 |

### 我要改鉴权 / 权限 / 上传 / 令牌

| 文档                                                       | 说明                                  |
|----------------------------------------------------------|-------------------------------------|
| [docs/security-guideline.md](docs/security-guideline.md) | 注入防护、上传解析、JWT/RBAC+ACL、日志脱敏、高风险变更确认 |

### 我要动 SaaS 多租户改造

| 文档                                                                                                 | 说明                                                     |
|----------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| [docs/saas-multi-tenant-proposal.md](docs/saas-multi-tenant-proposal.md)                           | **变更提案**：混合多租户架构、隔离模型、影响评估、回退计划、审批门（决策权威）              |
| [docs/saas-multi-tenant-proposal-review-pack.md](docs/saas-multi-tenant-proposal-review-pack.md)   | 三方评审材料包：会前预读，供架构/安全/运维当场判断签署                          |
| [docs/saas-multi-tenant-execution-plan.md](docs/saas-multi-tenant-execution-plan.md)               | 可执行计划：Phase 0–6 任务清单、依赖图、每任务锚点与验收                      |
| [docs/saas-migration-summary.md](docs/saas-migration-summary.md)                                   | 数据迁移总结（V23–V33 归档）：逐版本动作、H2/MySQL 双方言惯用法、验证清单          |
| [docs/saas-frontend-tenant-plan.md](docs/saas-frontend-tenant-plan.md)                             | 前端租户化改造设计：静态页 / `common.js` / 令牌头的 UI 级细化              |
| [docs/saas-pending-tasks.md](docs/saas-pending-tasks.md)                                           | 未完成任务总清单：跨 Phase 尚未完成项的状态、阻塞原因与责任人                     |
| [docs/saas-phase2-go-checklist.md](docs/saas-phase2-go-checklist.md)                               | Phase 2 门禁清单（生效基线）：向量隔离 P2-T1~T5，已交付归档                  |
| [docs/saas-phase3-go-checklist.md](docs/saas-phase3-go-checklist.md)                               | Phase 3 开工门禁清单：生命周期 + partition-key/SILO，18 项 B/S/T/C   |
| [docs/saas-phase3-review-brief.md](docs/saas-phase3-review-brief.md)                               | Phase 3 会审决策建议一页纸：P3-T0 万级 spike 实证浓缩（决策辅助留档）           |
| [docs/saas-phase3-b2-cluster-backup-plan.md](docs/saas-phase3-b2-cluster-backup-plan.md)           | Phase 3 B-2 收口决策包：Milvus cluster 拓扑 / 备份恢复 / 迁移窗口       |
| [docs/saas-phase4-go-checklist.md](docs/saas-phase4-go-checklist.md)                               | Phase 4 开工门禁清单：配置 / 缓存 / LLM 租户化，17 项 B/S/T/C           |
| [docs/saas-phase4-b1-b2-decision-pack.md](docs/saas-phase4-b1-b2-decision-pack.md)                 | Phase 4 硬门槛决策包：Redis 引入（B-1）+ 密钥保管（B-2）收口              |
| [docs/saas-phase5-go-checklist.md](docs/saas-phase5-go-checklist.md)                               | Phase 5 开工门禁清单：对象存储与水平扩展，16 项 B/S/T/C                   |
| [docs/saas-phase6-go-checklist.md](docs/saas-phase6-go-checklist.md)                               | Phase 6 开工门禁清单：配额与计费，16 项 B/S/T/C                       |
| [docs/saas-phase6-detailed-design.md](docs/saas-phase6-detailed-design.md)                         | Phase 6 详细设计：`tenant_usage` / `tenant_plan_limit` / `QuotaGuard` 落地设计 |

### 我要写测试 / 提交前自验

| 文档                                                             | 说明                                         |
|----------------------------------------------------------------|--------------------------------------------|
| [docs/testing-guideline.md](docs/testing-guideline.md)         | JUnit 5 / Mockito 风格、分层测试、Flyway 冒烟、门禁负向用例 |
| [docs/code-review-checklist.md](docs/code-review-checklist.md) | 提交前 31 条自检                                 |

### 我要部署 / 改 CI/CD

| 文档                                                   | 说明                                                        |
|------------------------------------------------------|-----------------------------------------------------------|
| [docs/deployment-ci-cd.md](docs/deployment-ci-cd.md) | **完整部署手册**：GHCR 推镜像 / Artifacts 拉 jar / 离线 tar 三方案 + 故障排查 |
| [scripts/README.md](scripts/README.md)               | 运维脚本索引（83 脚本，8 大类）                                        |

### 我要查历史决策 / 例外记录

| 目录                                   | 说明                  |
|--------------------------------------|---------------------|
| [docs/pr-notes/](docs/pr-notes/)     | PR 正文归档（设计取舍、评审上下文） |
| [docs/exceptions/](docs/exceptions/) | 编码规范例外记录（审批流程、到期复审） |

---

## 按文档类型

### 规范类（长期有效，编码必读）

- [AGENTS.md](AGENTS.md) — AI 编码总纲与导航索引
- [docs/tech-stack.md](docs/tech-stack.md) — 技术选型与版本纪律
- [docs/architecture-decisions.md](docs/architecture-decisions.md) — 架构决策（COLA 分层、对象模型、Executor 模式）
- [docs/coding-guideline.md](docs/coding-guideline.md) — Java 编码规范
- [docs/data-and-migration-guideline.md](docs/data-and-migration-guideline.md) — 数据库与迁移规范
- [docs/rag-domain-guideline.md](docs/rag-domain-guideline.md) — RAG 领域规范
- [docs/security-guideline.md](docs/security-guideline.md) — 安全注意事项
- [docs/testing-guideline.md](docs/testing-guideline.md) — 测试要求

### 工具类（按需查阅）

- [docs/code-review-checklist.md](docs/code-review-checklist.md) — 提交前自检清单
- [docs/deployment-ci-cd.md](docs/deployment-ci-cd.md) — CI/CD 部署手册
- [scripts/README.md](scripts/README.md) — 运维脚本手册

### 参考类（设计决策、历史归档）

- [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) — 项目能力总结（2026-09-27）
- [docs/project-overview.md](docs/project-overview.md) — 项目文档与业务流程梳理（2026-10-13）
- [docs/extraction-strategy-refactor-plan.md](docs/extraction-strategy-refactor-plan.md) — 文档抽取策略架构设计（858 行，含
  ADR）
- [docs/architecture-diagram.html](docs/architecture-diagram.html) — 架构图
- [docs/architecture-diagram.md](docs/architecture-diagram.md) — 架构图（Markdown / Mermaid 纯文本版，与 .html 同源）
- [docs/database-migrations.md](docs/database-migrations.md) — 全量 Flyway 迁移速览（V1 → V35）
- [docs/grafana/extraction-dashboard.json](docs/grafana/extraction-dashboard.json) — Grafana 监控面板

### 归档类（历史记录，非活跃参考）

- [docs/pr-notes/](docs/pr-notes/) — PR 正文归档（3 份）
- [docs/exceptions/](docs/exceptions/) — 编码规范例外记录（2 份）

---

## 文档维护约定

1. **规范类**文档由 AGENTS.md 导航，修改时需同步更新索引。
2. **工具类**文档随功能演进更新，无固定周期。
3. **参考类**文档为里程碑产物，除非架构变更否则不动。
4. **归档类**文档只增不删，作为历史追溯依据。
5. 所有文档间互引使用**相对路径**（如 `[coding-guideline](docs/coding-guideline.md)`），避免硬编码仓库 URL。

---

## 快速链接

- 新人入门：[README.md](README.md) → [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)
-
写代码：[AGENTS.md](AGENTS.md) → [coding-guideline.md](docs/coding-guideline.md) → [code-review-checklist.md](docs/code-review-checklist.md)
- 部署上线：[deployment-ci-cd.md](docs/deployment-ci-cd.md) → [scripts/README.md](scripts/README.md)
- 查历史决策：[pr-notes/](docs/pr-notes/) + [exceptions/](docs/exceptions/)
