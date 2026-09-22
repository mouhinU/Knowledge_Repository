package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档解析策略枚举
 *
 * <p>与 {@code ContentExtractor#name()} 一一对应，作为路由配置（{@code knowledge.extractor.routing}）里可引用的稳定标识。
 * {@code VISION} / {@code PDF_HYBRID} 为 Phase B / Phase C 预留占位，当前阶段尚无对应实现。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public enum ExtractionStrategyEnum {

    /** PDFBox 逐页文本提取 */
    PDF_BOX,

    /** Word（DOCX / DOC）段落提取 */
    DOCX,

    /** Excel（XLSX / XLS）工作表提取 */
    XLSX,

    /** PowerPoint（PPTX / PPT）幻灯片提取 */
    PPTX,

    /** 纯文本族（TXT / CSV / HTML / Markdown） */
    PLAIN_TEXT,

    /** Tika 通用兜底 */
    TIKA_FALLBACK,

    /** 视觉模型解析（Phase B 起） */
    VISION,

    /** PDF 混合解析：文本层 + 视觉补全（Phase C 起） */
    PDF_HYBRID
}
