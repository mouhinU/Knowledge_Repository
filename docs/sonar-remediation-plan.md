# SonarCloud Quality Gate 整改路线图

> 快照时间：2026-09-22 · 分支：`main` · 泄漏周期起点：2026-09-03
> 数据源：`/api/issues/search?sinceLeakPeriod=true&issueStatuses=OPEN,CONFIRMED`
> 相关规范：[AGENTS.md §0/§1](../AGENTS.md)、[docs/code-review-checklist.md](code-review-checklist.md)

## 一、现状

**Quality Gate**：`Sonar way`（内置），共 6 条卡点，3 条不通过。

| 条件 | 阈值 | 当前 | 结论 |
|---|---|---|---|
| `new_reliability_rating` | ≤ A (1) | **C (3)** | ❌ |
| `new_security_rating` | ≤ A (1) | **C (3)** | ❌ |
| `new_duplicated_lines_density` | ≤ 3% | **3.16%** (1464 / 46351) | ❌ |
| `new_maintainability_rating` | ≤ A (1) | A (1) | ✅ |
| `new_security_hotspots_reviewed` | = 100% | 100% | ✅ |
| `new_coverage` | ≥ 80% | 未上报 | ⚪ 跳过 |

**新代码 issue 总盘（490）**

- 按软件质量拆分：RELIABILITY 影响 183 条（MEDIUM 100 / LOW 25 / INFO 58），SECURITY 影响 2 条（MEDIUM），MAINTAINABILITY 影响 399 条（当前评级已 A，**不需要处理**）。
- 按类型拆分：CODE_SMELL 439 / BUG 49 / VULNERABILITY 2。
- 按扩展名：`.java` 311 / `.html` 165 / `.css` 14。

**要回绿只需清 127 条 QG 阻塞项**（RELIABILITY LOW+MED 125 + SECURITY MED 2），**外加压降重复度**。剩余 363 条 MAINTAINABILITY smell 不阻塞，另案排期。

## 二、按规则汇总的修复清单

### 2.1 Web:InputWithoutLabelCheck — 30 条 · MEDIUM

**现象**：`<input>` 元素没有可访问的 label 关联。

**分布**：`documents.html ×12` · `ai-exam.html ×5` · `ai-writing.html ×3` · `search.html ×3` · `wrong-answers.html ×3` · `exam-review.html ×2` · `exam.html ×2`。

**修法**：给每个 `<input id="X">` 补一种关联：
1. 首选：让可见 `<label>` 加 `for="X"`；
2. 或：用 `<label>` 直接包裹 `<input>`（父 label 视为关联）；
3. 兜底：给 `<input>` 加 `aria-label="…"`（当没有可见标签时用）。

**工时**：~30 × 2min = 1 小时。风险：极低（不改行为，只补属性）。

### 2.2 Web:S6853 — 25 条 · MEDIUM

**现象**：`<label>` 未与表单控件建立关联（Sonar 消息："A form label must be associated with a control and have accessible text."）。这条与 §2.1 是**同一问题的两面** —— S6853 从 label 侧看，InputWithoutLabelCheck 从 input 侧看；补 `for=` 一次满足。

**分布**：与 §2.1 完全重叠（每对相邻 label+input/select 会同时命中两条规则）。

**修法**：见 §2.1。

**工时**：与 §2.1 合并计算。

### 2.3 Web:S6848 — 18 条 · MEDIUM

**现象**：非原生交互元素（如可点击的 `<div class="agent-panel-header">`、modal overlay）缺少 `role` + 键盘支持。

**分布**：`ai-exam.html ×9` · `ai-writing.html ×5` · `documents.html ×2` · `exam-review.html ×1` · `wrong-answers.html ×1`。

**修法**（两种模式）：
- **折叠面板**（`<div class="agent-panel-header" onclick="…toggle('open')">`）：加 `role="button"` `tabindex="0"` + `onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();this.parentElement.classList.toggle('open')}"`。样板见 `ai-exam.html:521-523`（score-rule 弹窗已用同款）。
- **遮罩关闭**（`<div class="modal-overlay" onclick="if(event.target===this)close…">`）：加 `role="presentation"` `tabindex="-1"` + `onkeydown="if(event.key==='Escape')close…()"`。

**工时**：~18 × 3min = 1 小时。风险：中 —— 键盘 handler 逻辑要与鼠标 handler 等价，需浏览器手测。

### 2.4 Web:MouseEventWithoutKeyboardEquivalentCheck — 18 条 · LOW

**现象**：`<div onclick>` 等没有对应的 `onkeydown / onkeyup`。

**分布**：与 §2.3 高度重叠（同一元素同时命中两条规则），改 S6848 时顺带满足本条。

**修法**：与 §2.3 一并处理，无独立工作量。

### 2.5 java:S8786 — 15 条 · MEDIUM

**现象**：正则回溯导致的超线性复杂度（ReDoS 风险，故归 RELIABILITY）。

**分布**：`AnswerKeyParser.java ×5` · `ExamPaperParser.java ×5` · `ExamContentRenderAgent.java ×3` · `DocumentIngestionDomainService.java ×1` · `EnhancedPdfTextExtractor.java ×1`。

**修法**：
- 拆分嵌套量词（`(a+)+` → `a+a*`）；
- 用占有量词 `++` / `*+` 或原子组 `(?>…)`；
- 极端情况改用 `String.contains` / `indexOf` 手工解析。

**工时**：~15 × 8min = 2 小时。风险：**中高** —— 正则改写容易破坏既有语义。每条改动必须补一条命中回归测试（现有 `ExamPaperParserTest` 已存在，扩用例即可）。

**执行结果（PR-4 · 2026-09-22）**：以占有量词（`\s*+` / `\d++` / `[X]{m,n}+` / `[^…)】]*+`）消除相邻重叠量词的多项式回溯，语义等价（仅禁止不可能导致成功匹配的回溯路径），全套 `mvn test`（domain 28 + application 105）通过，spotless 校验通过。

- `AnswerKeyParser.java ×5`：`ANSWER_MARK` / `ANALYSIS_MARK` / `CRITERIA_MARK`（`\s*+` 界定 `(.*)` 边界）、`INLINE_SCORE_PAREN`（首个字符类排除 `分` 使锚点确定 + 后段 `*+`）、`BARE_LABEL_ONLY`（可选冒号两侧 `\s*+` 与两端 `\*{0,2}+` 全部所有格化）。
- `ExamPaperParser.java ×4`：`SECTION_PATTERN`、`QUESTION_SCORE_PATTERN`、`QUESTION_SCORE_STRIP`、`parseOptions` 内联选项切分正则。**附带修复一处潜在编译错误**：`SECTION_PATTERN` 原串内 `[一二三四五六七八九十]+` 之后为单反斜杠 `\s`（Java 字符串字面量非法转义），本次一并改回 `\\s` 所有格形式。
- `ExamContentRenderAgent.java`：`extractOptionsFromContent` 的内联切分正则（同上所有格化）+ `cleanContent` 的水平线/分值括注/尾空白三处所有格化。
- 回归测试：`AnswerKeyParserTest` +1（冒号两侧空格）、`ExamPaperParserTest` +1（选项值内联分值括注剥离）。

**暂缓 2 处（原因）**：`DocumentIngestionDomainService` 的段落分割正则 `(?:^|\n\s*\n)\s*(.+?)(?=\n\s*\n|$)`（DOTALL）—— 安全修法需把 `\s` 收敛为 `[ \t]` 或改判分段语义，会改变 3+ 连续空行的分段口径，无对应单测保护，留待带测试的专项重构；`EnhancedPdfTextExtractor` 的 `[ \t]+\n` 经判定字符类与后继 `\n` 互斥、线性可证，待重扫 Sonar 确认是否为该条，若非则不动。**收尾项**：本轮无 token 无法即时回查 Sonar 精确 15 条行号（组件键匿名 API 暂返回空），改以工程判据覆盖全部具备重叠量词特征的模式；PR-5 合并后统一带 token 复核 S8786 归零情况。


### 2.6 javascript:S7773 — 11 条 · MEDIUM

**现象**：ES6 起 `Number.isNaN / Number.parseInt` 优于全局 `isNaN / parseInt`。

**分布**：`exam.html ×4` · `documents.html ×4` · `exam-review.html ×1` · `search.html ×1` · `wrong-answers.html ×1`。

**修法**：全局查找替换：`isNaN(` → `Number.isNaN(`；`parseInt(` → `Number.parseInt(`。**注意 `parseInt(x, 16)` 等带进制参数的调用也要一并改**。

**工时**：~11 × 1min = 15 分钟。风险：低，但 `isNaN` 语义与 `Number.isNaN` 略有差异 —— 前者会尝试强转，后者只对真正 `NaN` 返回 true。若原意是"排除非数字字符串"，需要 `!Number.isFinite(Number(x))` 替代。逐点核对。

### 2.7 javascript:S7781 — 5 条 · LOW

**现象**：`String.replace(搜索串, ...)` 只替换首次出现，应改 `replaceAll`。

**分布**：全在 `exam.html:3214` 附近。

**修法**：`str.replace(/x/g, ...)` 或 `str.replaceAll(x, ...)`。

**工时**：5 × 2min = 10 分钟。风险：低。

### 2.8 javascript:S2245 — 2 条 · MEDIUM（安全）

**现象**：`Math.random()` 生成伪随机数用于业务标识 —— 归 SECURITY。

**分布**：`ai-writing.html:358`（sessionId）· `exam-review.html:294`（streamId）。

**修法**：统一改用 `crypto.getRandomValues(new Uint8Array(8))` 生成短 token，或已有 `crypto.randomUUID()`（`exam-review.html:293` 已优先走这条，只是 fallback 里用了 Math.random）：

```js
function randToken(n = 8) {
    const bytes = new Uint8Array(n);
    crypto.getRandomValues(bytes);
    let s = '';
    for (const b of bytes) s += (b % 36).toString(36);
    return s;
}
```

**工时**：2 × 10min = 20 分钟。风险：低，语义完全等价。

### 2.9 Web:S6844 — 2 条 · LOW

**现象**：`<a>` 用作 button（无 href 却有 onclick）。

**分布**：`documents.html:35-36`。

**修法**：改成 `<button type="button" class="…">` 或 `<a href="#" role="button">` — 前者更彻底。

**工时**：2 × 3min = 6 分钟。

### 2.10 Web:S5256 — 1 条 · MEDIUM

**现象**：`<table>` 缺 `<th scope="col">` 表头。

**分布**：`system.html:34`。

**修法**：给表头补 `<thead><tr><th scope="col">…`。

**工时**：1 × 5min。

### 2.11 重复度：3.16% → <3%（专项）

**基线**：新代码 1464 duplicated / 46351 total；要跌破 3% 至少清出 ~74 行（安全余量 ~200）。

**执行结果（PR-5 · 2026-09-22）**：无 token 拉不到 Sonar 精确 dup blocks，改用工程扫描（≥8 行 sliding-window、去注释与空白，同文件多命中聚类 + 跨文件命中），锁定 intra-file 为主战场，完成 4 项抽取：

- `DocumentIngestionDomainService.mergePages(pages, sep)` + `MergedPages` record：4 处 `StringBuilder merged + charPageMap` 8-18 行重复块 → 单 helper，附带删掉一处未使用局部变量。
- `ExamPaperParser.splitQuestionBody(block, contentBuilder, rawOptionsBuilder)`：2 处 27/26 行"题干+选项块解析"合并为一处。
- `ParserUtils.OPTION_SPLIT_PATTERN`：跨文件共享选项切分正则常量，`ExamPaperParser` 与 `ExamContentRenderAgent` 都从字面量 `Pattern.compile(...)` 改为 `ParserUtils.OPTION_SPLIT_PATTERN`，防未来 ReDoS 修复只改一侧漂移。
- `ai-exam.js` 新增 `_pstBodyHtml()` + `_waitForSseReady(flagGetter, timeoutMs)`：两处 8 行 `pst-body` HTML 模板 + 两处 6 行 SSE 轮询 promise 收敛为单实现；`node --check` 通过。

回归验证：`mvn test -pl knowledge-application -am` 28（domain）+ 105（application）全绿，`spotless:check` 通过；HTML/JS 无自动化回归，仅 `node --check` 保语法（无浏览器验证条件，页面功能层面依赖 PR 后手工点检）。

**扫描后剩余 Java intra-file dup 主源**（8-行窗口近似）：`ExamController` 280 · `DocumentUploadController` 104 · `ExamGenerationSupport` 88 · `DocumentAdminController` 88 · `DocumentImageExtractorService` 80 · 合计 ~640 行。前三者主要是「try { return ResponseEntity.ok(service.xxx(...)); } catch (Exception e) { log.error(...); return ResponseEntity.badRequest().body(Map.of(\"errorCode\",\"BAD_REQUEST\",...)); }}」的 Controller 样板，`ExamGenerationSupport` 与 `DocumentImageExtractorService` 属于同类"方法体分步骨架"重复。**已归为架构级改动，独立提交待确认**（详见 §四 后续动作）。


## 三、分批 PR 建议

以"每批独立可回滚 + 每批都能推进 QG 至少一条"为原则：

| 批次 | 主题 | 涉及规则 | 文件面 | 预期效果 | 工时 |
|---:|---|---|---|---|--:|
| **PR-1** | 静态 HTML a11y 快赢 | InputWithoutLabelCheck · S6853 · S6844 · S5256 | 8 个 HTML | Reliability 30+25+2+1=**58 条 MEDIUM/LOW 消除** | ~2.5h |
| **PR-2** | 键盘可交互化 | S6848 · MouseEventWithoutKeyboardEquivalentCheck | 5 个 HTML | Reliability 再消 **18 条 MEDIUM**（S6848） + 18 条 LOW（部分与 PR-1 重叠） | ~1h |
| **PR-3** | JS 现代化 | S7773 · S7781 · S2245 | 5 个 HTML | Reliability 消 16 条 + **Security 消 2 条 → `new_security_rating` 回 A** | ~45min |
| **PR-4** | Java 正则 ReDoS | java:S8786 | 5 个 Java | Reliability 消 15 条；**需要回归测试补强** | ~2h |
| **PR-5** | 重复度专项 | Duplication | 跨栈 | 目标 `new_duplicated_lines` 从 1464 → <1390 | ~4-5h（含定位） |

**关键路径**：PR-1..4 全清后 Reliability 从 C → **A 或 B**（剩余 25 条 LOW + 100+ 条 INFO 里可能有残留），Security 从 C → **A**。若 Reliability 只到 B 还差一口气，追加 PR-6（清理 java:S8688 58 条 `LocalDateTime.now()` — 引入 `Clock` 注入）作为兜底。

**PR-5 需要独立探查**：先跑一次带 token 的 `/api/duplications/show?componentKey=…`，把 top 20 duplicate blocks 落成表格再决策。

## 四、验收与回滚

**每批 CI 门禁**（合入前必过）：

1. `mvn -B verify` 通过；
2. 前端资源无 404 / 无 JS 语法错误（可用 `npx html-validate` 或 `tidy -qe` 扫一遍）；
3. 手测：admin 页面键盘可达性（Tab 到折叠面板 / 弹窗，Enter/Space 触发，Esc 关弹窗）；
4. Sonar `wait` 完成后拉一次 `project_status` 快照，确认对应条件项 actual 值下降。

**回滚**：

- 每个 PR 保持"单一主题 + 独立可 revert"；
- 若 PR 引入 UI 行为回归（比如键盘 handler 抢了原生 keydown），先 revert 该 PR、修 bug 后重推；
- Java 正则改造必须保留原 `Pattern` 常量与 `PatternTest` 断言，回归测试红了直接 revert。

**里程碑**：

- M0：本 plan 文档合入 + PR-1..3 完成 → 预期 Security A、Reliability 逼近 A/B。
- M1：PR-4 + PR-5 完成 → 预期 QG 三项全绿。
- M2（可选）：把 363 条 MAINTAINABILITY smell 按 rule 拆到后续 sprint。

## 五、附录 A：数据快照

原始 API 响应保存在 `/Users/mac/.qoderworkcn/workspace/mtk55rgejj4nl99e/sonar/page1.json`（490 issues，500 KB 级）。要更新只需：

```bash
curl -sS "https://sonarcloud.io/api/issues/search?componentKeys=mouhinU_Knowledge_Repository&branch=main&issueStatuses=OPEN,CONFIRMED&sinceLeakPeriod=true&ps=500&p=1" -o page1.json
```

## 六、附录 B：文件影响面 Top 15

| 文件 | 新代码 issue 数 |
|---|---:|
| `knowledge-web/src/main/resources/static/admin/documents.html` | 37 |
| `knowledge-web/src/main/resources/static/exam.html` | 34 |
| `knowledge-web/src/main/resources/static/admin/ai-exam.html` | 31 |
| `knowledge-application/…/util/ExamPaperParser.java` | 28 |
| `knowledge-domain/…/service/DocumentIngestionDomainService.java` | 19 |
| `knowledge-web/src/main/resources/static/admin/ai-writing.html` | 19 |
| `knowledge-web/src/main/resources/static/admin/exam-review.html` | 18 |
| `knowledge-web/src/main/resources/static/admin/assets/css/common.css` | 14 |
| `knowledge-infrastructure/…/pdf/EnhancedPdfTextExtractor.java` | 14 |
| `knowledge-application/…/executor/examgeneration/ExamGenerationSupport.java` | 13 |
| `knowledge-domain/…/service/ScoreRuleEngine.java` | 12 |
| `knowledge-infrastructure/…/agent/ExamReviewerAgent.java` | 11 |
| `knowledge-web/src/main/resources/static/admin/wrong-answers.html` | 11 |
| `knowledge-infrastructure/…/agent/ExamDistributionAgent.java` | 9 |
| `knowledge-infrastructure/…/persistence/gateway/ExamSessionGatewayImpl.java` | 9 |

**HTML 类规则高度集中在 3 个 admin 页 + exam.html**：PR-1..3 主要工作都在这几份文件，改造时按文件一次改到位比按规则切分开销小。
