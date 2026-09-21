# PR #<待填>：AGENTS.md 合规整改 + 开发线合并

> 归档说明：本文件为已提交 PR 的正文快照，便于后续追溯评审上下文与设计取舍。
> 分支：`agents_md_compliance` → `main`（合并后分支可删）
> 创建日期：2026-09-21
> PR 链接：https://github.com/mouhinU/Knowledge_Repository/pulls?q=agents_md_compliance

---

**Title:**

```
chore(agents): AGENTS.md 合规整改 · 大方法与魔法数字治理 + CI/CD 部署文档
```

**Description:**

## Summary

- 依据 [AGENTS.md](../../AGENTS.md) 与 [docs/coding-guideline.md](../coding-guideline.md) 对存量代码做四批**行为保持**整改：`@Slf4j` 统一日志（100 文件）、高确定性魔法数字→具名常量（9 处）、>100 行大方法 Extract Method（6 个）、4 参方法→命令/查询对象封装（6 个）。
- 移除遗留 `formatter-maven-plugin` 与未用配置，格式化统一由 **Spotless (google-java-format AOSP) + Checkstyle** 双通道保证。
- 新增 [docs/deployment-ci-cd.md](../deployment-ci-cd.md)：GitHub Actions → 本地 Docker 三方案（GHCR 推镜像 / Artifacts 拉 jar / 离线 tar），并在 AGENTS.md 决策树补入口。
- 全量 `./mvnw clean verify` 通过：6 模块编译，**0 checkstyle 违规**，**spotless 幂等**，16 单测 + 1 Flyway V18 迁移冒烟测试全绿。

## Commits 分组

| 组 | 提交 | 说明 |
| --- | --- | --- |
| **工具链** | `993333a` | 移除 formatter-maven-plugin 遗留配置 |
| | `03785ca`, `26b386a`, `ded25ec` | Spotless + Checkstyle 引入、CI 规则化、作者/时间/日志规约 |
| **日志迁移** | `7e7ebe6` | 100 文件 `LoggerFactory` → Lombok `@Slf4j`，`logger.` → `log.` 全量替换 |
| **AGENTS.md §红线/§十 整改** | `cfcb05e` | 阶段 1：HTTP 401 与 rounding/awaitTermination 常量抽取（9 处高确定性魔法数字） |
| | `c19bcf8` | 阶段 2：6 个 >100 行方法 Extract Method（gradeExamInternal / ExamContractValidator.validate / ScorePlanValidator.validate / chunkParagraph / extractFromDocument / ExamWriterAgent.execute） |
| | `f9b2c52` | 阶段 3：6 个 4 参方法封装为 Cmd/Query（IndexCmdExe / PreviewFromDocumentQryExe / PreviewFileQryExe / SearchImagesQryExe / findOverlapStart） |
| **文档** | `27983fc` | docs(deployment)：CI/CD→本地 Docker 部署方案 + AGENTS.md 决策树入口 |

## Test plan

- [x] `./mvnw -B spotless:apply` → `spotless:check` 幂等（两次运行 diff 为空）
- [x] `./mvnw -B clean verify` 六模块全 SUCCESS
- [x] Checkstyle 0 violations（LineLength=120，severity=warning 门禁）
- [x] 单元测试：16 + 1 Flyway 迁移冒烟，全部通过
- [x] 手工冒烟：`docker compose up -d` 起 knowledge-app，`/actuator/health` UP
- [x] 出卷链路端到端：预览 → 分值方案确认 → 出卷 → 评分 → 复核，无回归

## 关键设计决策与例外

1. **`@Slf4j` 迁移用脚本一次性完成**（100/100 文件），零残留 `LoggerFactory`；GJPF 换行的手写 logger 声明用「消费到分号」的宽容正则覆盖。
2. **`AnswerKeyParser.parse`（126 行）与 `gradeExamInternal`（121 行）** 属单遍有状态解析/fenced 并发循环，按 AGENTS.md §十「允许例外，PR 说明由 reviewer 批准」处理，保留内联。
3. **阶段 3 分层归位**：`PreviewFileQryExe` 入参含 `MultipartFile`，其 `PreviewFileQuery` 落在 **app 内部 `application.dto` 包**（非 client 层），避免 client 依赖 spring-web；其余 3 个纯业务命令落 `client.dto`。
4. **`findOverlapStart` 顺带清理**：原 4 参含未使用的 `text` 形参，封装 `ChunkWindow` 值对象后一并去除。
5. **魔法数字**：本批只处理高确定性值（HTTP 401 / 四舍五入 scale / awaitTermination 秒数）。文本截断长度、token 估算除数、分值查表、Word 排版的数值各站语义不同，需先定统一口径再抽，避免过早抽象。

## 附带收益（同分支上先前的开发线）

本分支基于此前 `agents_cola_upgrade` 线，一并带来 24 个功能/契约提交（COLA 分层升级、评分链路阶段 1-G / 2-A ~ 2-F、Agent 地图化 AGENTS.md 等）。这些提交已在开发分支积累多轮，`clean verify` 全绿。

如需拆分：可指示 reviewer 按 **Review-by-commit** 视图，`993333a..f9b2c52`（合规整改六提交）与更早的功能提交界限清晰，可按需分批 approve。

## 风险与回滚

- **风险等级：中**。批量日志迁移触及 100 文件，但仅替换 logger 声明与 `logger.`→`log.`，无逻辑变化；`clean verify` + 出卷链路手工冒烟覆盖。
- **参数封装** 改变 `DocumentIngestionServiceI` 契约（4 参 → 1 参 Cmd/Query）。当前**无外部 SDK 消费方**，仅 controller 调用点同步更新；若未来开放 API 给第三方，需在 release note 中标注 breaking change。
- **回滚**：`git revert --no-commit 27983fc f9b2c52 c19bcf8 cfcb05e 7e7ebe6 993333a && git commit`，或整体 `git revert -m 1 <merge-sha>` 一键撤 PR。

## Links

- 分支：https://github.com/mouhinU/Knowledge_Repository/tree/agents_md_compliance
- 对比：https://github.com/mouhinU/Knowledge_Repository/compare/main...agents_md_compliance

---
