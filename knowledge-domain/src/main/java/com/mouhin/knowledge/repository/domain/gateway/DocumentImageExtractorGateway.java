package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 文档内嵌图片提取网关（领域层抽象，依赖倒置）。
 * <p>
 * 与 {@link DocumentExtractionGateway}（文本）平行：从 PDF / Word / Excel / PowerPoint 等原始文件中
 * 抽取位图，返回带尺寸与来源页信息的二进制列表，交由应用层落盘与持久化。具体解析技术
 * （PDFBox / POI）由基础设施层实现。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public interface DocumentImageExtractorGateway {

    /**
     * 从文件提取内嵌图片。
     *
     * @param filePath 原始文件路径（持久存储或临时文件）
     * @param fileName 原始文件名（用于格式识别，可为 null）
     * @return 提取到的图片列表；无图片或格式不支持图片提取时返回空列表
     * @throws IOException 读取 / 解析文件失败
     */
    List<ExtractedImage> extractImages(Path filePath, String fileName) throws IOException;
}
