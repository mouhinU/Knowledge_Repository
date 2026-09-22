package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 文档解析全局配置值对象（合并后的单次解析上下文）。
 *
 * <p>由基础设施层从 {@code knowledge.extractor.*} 配置装配，随 {@link ExtractionCandidate} 传入各策略，
 * 使策略执行无副作用地读取阈值参数。Phase A2 阶段仅承载默认值，视觉相关字段在 Phase B 起被真正消费。
 *
 * @param maxPagesPerDoc 单文档最多处理的页数（含视觉补全），防止超大文档耗尽预算
 * @param minTextLenPerPage 判定"文本层稀疏页"（可能需要视觉补全）的每页最小字符数
 * @param maxBytesPerImage 单张图片送入视觉模型的字节上限
 * @param renderFallbackDpi 无嵌入图时页面栅格化兜底分辨率
 * @param merge 视觉结果与文本层合并策略：replace_lowtext | append | replace_all
 * @param timeoutSeconds 单次视觉调用超时（秒）
 * @param maxTotalSecondsPerDoc 单文档视觉增强总时长上限（秒）
 * @author mouhinU
 * @date 2026-09-23
 */
public record ExtractionConfig(
        int maxPagesPerDoc,
        int minTextLenPerPage,
        int maxBytesPerImage,
        int renderFallbackDpi,
        String merge,
        int timeoutSeconds,
        int maxTotalSecondsPerDoc) {

    /** 合并策略：视觉结果仅替换文本层稀疏页。 */
    public static final String MERGE_REPLACE_LOWTEXT = "replace_lowtext";

    /** 合并策略：视觉结果追加到文本层之后。 */
    public static final String MERGE_APPEND = "append";

    /** 合并策略：视觉结果整体替换文本层。 */
    public static final String MERGE_REPLACE_ALL = "replace_all";

    /**
     * Phase A2 默认配置（纯文本族策略不消费这些字段，仅保证候选对象构造完整）。
     *
     * @return 内置默认配置
     */
    public static ExtractionConfig defaults() {
        return new ExtractionConfig(20, 200, 4 * 1024 * 1024, 150, MERGE_REPLACE_LOWTEXT, 60, 300);
    }
}
