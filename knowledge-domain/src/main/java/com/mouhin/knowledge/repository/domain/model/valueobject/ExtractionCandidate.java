package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 解析策略的路由 + 执行输入参数对象。
 *
 * <p>遵循红线 #11（参数 &gt;3 封装为对象）：把 MIME 检测结果、文件大小、预扫描提示、嵌入图、合并配置统一打包， 传入 {@code
 * ContentExtractor#supports} 与 {@code extract}。{@code nativeHints} / {@code embeddedImages} 为 Phase
 * C（PDF 混合）与 Phase B（视觉）预留，A2 阶段可为空。
 *
 * @param filePath 已归一化的绝对路径
 * @param fileSize 字节数
 * @param fileName 原始文件名
 * @param mimeType Tika 检测出的 MIME 类型
 * @param nativeHints 预扫描提示（PDF：totalPages / ocrRecommended / perPageTextLens），可空
 * @param embeddedImages 预取嵌入图（可空）
 * @param config 合并后的解析配置
 * @author mouhinU
 * @date 2026-09-23
 */
public record ExtractionCandidate(
        Path filePath,
        long fileSize,
        String fileName,
        String mimeType,
        Map<String, Object> nativeHints,
        List<ExtractedImage> embeddedImages,
        ExtractionConfig config) {

    /**
     * 构造仅含基本字段的候选（A2 阶段各文本族策略使用的简化入口）。
     *
     * @param filePath 文件路径
     * @param fileSize 字节数
     * @param fileName 文件名
     * @param mimeType MIME 类型
     * @return 使用默认配置、空提示、空嵌入图的候选对象
     */
    public static ExtractionCandidate basic(
            Path filePath, long fileSize, String fileName, String mimeType) {
        return new ExtractionCandidate(
                filePath,
                fileSize,
                fileName,
                mimeType,
                Map.of(),
                List.of(),
                ExtractionConfig.defaults());
    }
}
