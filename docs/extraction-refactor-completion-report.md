# 文档解析策略模式重构 · 收尾复核报告

> 日期：2026-09-23 · 交付方式：逐阶段本地提交（**未 push**）· 全部阶段完成后统一验证
> 阶段状态矩阵以 [extraction-strategy-refactor-plan.md](extraction-strategy-refactor-plan.md) §0.1 为唯一权威来源（本报告不重复状态表，避免漂移），此处只做**提交账本 + 验证证据 + 端到端联调结论**。

## 1. 提交账本（origin/main 之后的本地提交）

| 提交 | 内容 | 层 |
|---|---|---|
| `361d22f` | Phase A2 — `ContentExtractor` SPI + `CompositeExtractionService` 顶替 MIME switch | domain/infra |
| `5deb575` | Phase R1 — 三段式 LLM 配置 + per-role HttpClient + 密钥边界 | infra/config |
| `769df40` | Phase R2 — per-role 熔断/重试韧性 + Micrometer 可观测 | infra |
| `72bb8f1` | Phase B — 视觉模型接入（默认关闭） | domain/infra |
| `1a1feea` | Phase C — PDF 混合视觉增强 + 跨进程解析缓存 + 异步增强 | domain/infra/app |
| `732fc87` | Phase D — 管理端重解析端点 + 成本护栏 + Micrometer 观测 + Grafana | domain/client/infra/app/web |
| `a00731f` | 方案文档 v1.5 — Phase C/D/R3 状态推进为代码完成（零漂移） | docs |
| `cab417a` | Flyway 冒烟同步 V19 计数（18→19）+ `kb_extraction_cache` 抽查 | web/test |
| `e475cb0` | 引入 `micrometer-registry-prometheus` 打通 `/actuator/prometheus` | web/config/security |
| `dc25174` | 变更提案记录（红线 #11 归档）CP-2026-0923-OBS-01 | docs |
| `e3fa3b4` | 变更提案复审跟踪 TODO | docs |

> 另有用户本人提交 `79e1513`「新增前后端实时通信约束」（AGENTS.md 等 3 个无关文档），与本次重构无涉，未混入上述任何提交。

## 2. 统一验证（阶段全部完成后执行）

**2.1 全反应堆单元/集成测试**：`./mvnw test` → **BUILD SUCCESS**，六模块全绿（Client / Domain / Infrastructure / Application 109 / Web 18）。

**2.2 迁移双库冒烟**：
- H2（`FlywayMigrationSmokeTest`，内存 MODE=MySQL）：19 个版本脚本一次性回放成功，终版 v19，`kb_extraction_cache` 建表抽查通过。
- MySQL 8.0（部署启动实测）：`Successfully validated 19 migrations` → `Migrating schema to version "19 - create extraction cache"` → `now at version v19`，无异常。

**2.3 部署健康**：预构建镜像 → `--force-recreate knowledge-app` → 容器 healthy，`/actuator/health` 返回 `{"status":"UP"}`，JVM 预热期无 ERROR/Exception。

**2.4 可观测端点**：`/actuator/prometheus` = HTTP 200（240 个指标族，`uri` 标签模板化低基数）；`/actuator/health` = 200；`/actuator/metrics` = 401（令牌保护，白名单保持窄）。

## 3. 视觉链路端到端联调（本地 Ollama `AuditAid/PaddleOCR-VL-1.6-0.9B`）

前置：以 `/tmp` compose override **仅注入环境变量**（不改动任何被提交的配置/代码）开启 `LLM_VISION_ENABLED` + `EXTRACTOR_VISION_ENABLED` + 白名单放行 `PDF_HYBRID` + 放宽 CPU 超时（实测单页 OCR ≈ 144s，远超默认 60s，故上调至 300s）。

构造 1 页"无文本层"扫描 PDF → 上传（status=UPLOADED）→ `POST /api/admin/document/{key}/reparse?strategy=PDF_HYBRID,PDF_BOX` 触发异步增强。

**3.1 Hybrid 增强生效**：强制栈 `[PDF_HYBRID, PDF_BOX]`；PDFBox 基线判定 `1 scanned / ocrRecommended=true` → 视觉识别该页 → 落库 `result_json` 的 `detectedFormat":"pdf-hybrid"`，`pageTexts` 为 OCR 文本（中英混排逐字准确）。日志 `Composite extract ok [strategy=PDF_HYBRID]` + `vision enhance done … format=pdf-hybrid`。

**3.2 持久缓存 5 列键**：`kb_extraction_cache` 以 (checksum, strategy=PDF_HYBRID, model, prompt_hash, render_mode=embedded-pref) 复合键写入，INSERT 成功。

**3.3 二次命中（100%）**：再次 reparse，日志 `pdf-hybrid cache hit: <checksum>`，耗时 ≈ 370ms，**无新增视觉调用**（`knowledge_extraction_vision_invocations_total` 仍为 1）。

**3.4 指标实证（Prometheus 抓取）**：
```
knowledge_extraction_cache_miss_total{strategy="PDF_HYBRID"} 1.0
knowledge_extraction_cache_hit_total{strategy="PDF_HYBRID"}  1.0
knowledge_extraction_invocations_total{mime="application/pdf",outcome="ok",strategy="PDF_HYBRID"} 1.0
knowledge_extraction_latency_seconds_{count,sum=167.63,max} ...
knowledge_extraction_vision_invocations_total{model="AuditAid/PaddleOCR-VL-1.6-0.9B:latest"} 1.0
```

**3.5 回读链路**：`GET /api/admin/document/{key}/preview` 返回该页 `charCount=145` 的 OCR 文本，确认增强结果贯通用户可读路径。

**3.6 测试足迹清理**：删除测试文档（API，DB 行清零）+ 按 checksum 清除其缓存行（表回到 0）+ 物理文件移至系统废纸篓（可恢复，非永久删除）+ 容器以无 override 方式重建回退到已提交的"视觉默认关闭"配置 + 删除 `/tmp` 临时件。回退后 `/actuator/health` UP，视觉网关回到默认禁用态（`paddleocr-vl @ :8080`）。

## 4. 关键约束落实核对

- 构造器注入 / 面向接口、`#{}`/白名单、DO 不越 infra、domain 纯净、`@Slf4j` 且日志/指标不含敏感与高基数字段——均通过；长耗时视觉调用走 `visionExecutor` 异步、不置于 DB 事务。
- 视觉/hybrid **三重默认关闭**（不进 BUILTIN_ROUTING + `supports()` 查 `props.isEnabled()` + 不进 `enabled-strategies` 白名单），零副作用；本次 e2e 仅用运行期 env 临时开启，未持久化。
- 依赖变更（`micrometer-registry-prometheus`）走红线 #11 提案归档：见 [exceptions/20260923-observability-prometheus-registry.md](exceptions/20260923-observability-prometheus-registry.md)。

## 5. 遗留 / 后续（非阻塞）

1. 生产前收紧 `/actuator/prometheus` 暴露面（独立 `management.server.port` 或网络策略）——已在提案 TODO 跟踪。
2. 部署 Prometheus + Grafana 数据源，观察真实流量下的解析指标曲线。
3. CPU OCR 单页 ≈ 144s，仅适合离线/低并发批处理；高吞吐需 GPU/云视觉（改配置即可切换，无需改码）。

## 6. 结论

文档解析策略模式重构（A1/A2/B/C/D + R1/R2/R3）全部代码完成、逐阶段本地提交、统一验证（全量测试 + 双库迁移 + 部署健康 + 观测端点 + 视觉端到端）全部通过。系统处于可交付状态，**未 push**，等待负责人复核后决定推送与部署时机。
