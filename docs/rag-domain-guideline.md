# RAG 知识库领域规范（文档处理与向量化）

> 本分册由根目录 `AGENTS.md` 第十二章拆分而来（§12.3 权限隔离并入 [security-guideline.md](security-guideline.md)）。
> 相关解析依赖版本见 [tech-stack.md](tech-stack.md)，分块 / 检索涉及的领域对象归属见 [architecture-decisions.md](architecture-decisions.md)。
> 维护：`@author mouhinU` · 拆分日期 2026-09-20

---

## 一、多格式文档处理

- 支持格式：PDF / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) / TXT / CSV / HTML / RTF。
- 文件大小限制 200MB，超过拒绝处理（上传侧安全约束见 [security-guideline.md](security-guideline.md) §2）。
- 格式检测使用 Apache Tika（`tika.detect()`），按 MIME 类型路由到专用解析器。
- PDF：PDFBox 按页提取，检测扫描型 PDF（低文本密度页面），记录警告日志。
- Word：Apache POI 按段落提取，每 30 段近似切分一个 section。
- Excel：Apache POI 按工作表提取，保留行列结构（Tab 分隔）。
- PowerPoint：Apache POI 按幻灯片提取，遍历文本形状。
- 其他格式：Tika AutoDetectParser 通用解析，按段落分割。
- 文件校验和（MD5）用于去重，上传前检查。
- 临时文件必须在 `finally` 中清理。
- 新增文件格式须在 `DocumentExtractionService` 中添加专用解析器（检查清单第 18 项）。

## 二、向量化

- Embedding 模型通过配置切换（DashScope / Ollama），使用 `@ConditionalOnProperty`（版本基线见 [tech-stack.md](tech-stack.md)）。
- Milvus 集合名称通过配置指定，默认 `knowledge_chunks`。
- 每个分块的元数据（`document_key`、`department_id`、`visibility`、`allowed_roles`、`owner_id`）必须存入 Milvus metadata，用于权限过滤（过滤规则见 [security-guideline.md](security-guideline.md) §5）。

## 三、文档存储（P5-T1 · Phase 5）

> 解析后的文档与考试素材通过 `ObjectStorageGateway` 端口持久化。Phase 5 引入 MinIO 对象存储后，本地磁盘与 MinIO 通过配置切换，调用方无感。

- 文档存储由 `ObjectStorageGateway` 端口统一承接（端口定义与分层见 [architecture-decisions.md](architecture-decisions.md) §7），`DocumentIngestionSupport.copyToStorage()` 返回 `StoredObject` 值对象，`storageKey` 对调用方不透明。
- 考试素材（图片等）由 `DocumentImageSupport` 通过同一端口写入，路径 `{tenantKey}/exam-assets/{documentKey}/{fileName}`，与文档存储共享租户前缀隔离。
- 双驱动配置：`knowledge.storage.backend={local,minio}`，默认 `local`（向后兼容存量 `Document.storagePath` 绝对路径格式）。切换至 MinIO 后新上传走对象存储，存量文件在 `local` 模式下仍可读取。
- 租户隔离：MinIO 模式下对象键含 `{tenantKey}` 前缀，天然隔离；本地模式下路径含 `{tenantKey}` 子目录，配合 `TenantLineInnerInterceptor` 实现存储层 + 数据层双重隔离。
- 路径穿越防护见 [security-guideline.md](security-guideline.md) §3（`PathGuard` 本地 + MinIO 双侧校验）。
- 上传大小限制 200MB 与 MIME 检测仍由上传链路前端守卫（[security-guideline.md](security-guideline.md) §2），`ObjectStorageGateway` 在存储阶段提供第二层路径安全校验。

## 四、检索与考试联动（项目扩展）

- 考试出卷链路支持「出卷即切分」与看图题配图注入：分块快照由 Markdown 渲染而来不含图片，开考时按印刷题号回填 `assetKey` 数组（`kb_exam_question.images_json`）供学生答题页渲染。
- 检索 / 开考等带副作用的用例，其权限过滤与状态门禁落在单一 `CmdExe` / `QryExe`；写库事务在 Executor，含 LLM / 外部 IO 的长耗时用例禁止套大事务（模式见 [architecture-decisions.md](architecture-decisions.md) §4）。

> §12.3 权限隔离（RBAC + 文档级 ACL、可见性枚举、`buildFilterExpression`、超管豁免）已统一归入 [security-guideline.md](security-guideline.md) §5。
