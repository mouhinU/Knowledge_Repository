package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.List;

/**
 * 文档文本提取结果（值对象）
 *
 * <p>原先定义为基础设施层 {@code DocumentExtractionService} 的内部记录，为满足 COLA 依赖倒置（应用层仅依赖领域层网关抽象）而下沉到领域层，作为
 * {@code DocumentExtractionGateway} 的返回契约。
 *
 * @param pageTexts 分页 / 分节文本列表
 * @param totalPages 页数 / 分节总数
 * @param likelyScanned 是否疑似扫描件（低文本密度）
 * @param checksum 文件 MD5 校验和
 * @param detectedFormat 检测到的格式（pdf / docx / xlsx / pptx / text / generic ...）
 * @param warnings 提取过程中的警告信息
 * @param encrypted 是否加密文档
 * @param title 文档标题（元数据）
 * @param author 文档作者（元数据）
 * @author mouhinU
 * @date 2026-09-17
 */
public record ExtractionResult(
        List<String> pageTexts,
        int totalPages,
        boolean likelyScanned,
        String checksum,
        String detectedFormat,
        List<String> warnings,
        boolean encrypted,
        String title,
        String author) {
    /** 向后兼容的简化构造器 */
    public ExtractionResult(
            List<String> pageTexts,
            int totalPages,
            boolean likelyScanned,
            String checksum,
            String detectedFormat) {
        this(
                pageTexts,
                totalPages,
                likelyScanned,
                checksum,
                detectedFormat,
                List.of(),
                false,
                null,
                null);
    }

    /**
     * 无策略命中时的中性空结果（0 页、无文本、格式标记为 empty）。
     *
     * <p>供 {@code CompositeExtractionService} 在遍历完所有候选策略仍未命中时返回，避免 {@code null} 冒泡到应用层。
     *
     * @return 空提取结果
     */
    public static ExtractionResult empty() {
        return new ExtractionResult(List.of(), 0, false, null, "empty");
    }
}
