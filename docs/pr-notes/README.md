# PR Notes — 归档索引

本目录存放**已提交 PR 的正文快照**，用于日后追溯设计取舍、评审上下文与例外理由。规范文档不放这里——那些进 [docs/](../) 根目录。

约定：

- 文件名 = PR 的头分支名或主题短名，kebab-case，例：`agents_md_compliance.md`、`rag-v2-scoring-pipeline.md`。
- 顶部保留「PR 链接 / 创建日期 / 合并 commit」元信息；合并后可补 merge sha 与关闭日期。
- 归档时把描述里的 `../blob/main/...` 类外部链接改回仓库相对路径（`../xxx.md`），保持文档间互引一致。

现有归档：

- [agents_md_compliance.md](agents_md_compliance.md) — AGENTS.md 合规整改 + 开发线合并（分支 `agents_md_compliance`，2026-09-21 提交 PR）
