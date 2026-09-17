package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 多格式文档文本提取网关（领域层端口，由基础设施层实现）
 *
 * <p>屏蔽 PDFBox / POI / Tika 等解析技术细节，应用层仅依赖本接口。返回值与异常均为
 * 领域 / JDK 类型，不泄露基础设施类型。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public interface DocumentExtractionGateway {

    /**
     * 校验文件（大小、格式合法性），非法时抛业务异常
     */
    void validateFile(Path filePath, long fileSize, String fileName);

    /**
     * 提取文本（按 MIME 类型路由到专用解析器）
     */
    ExtractionResult extractText(Path filePath, long fileSize, String fileName) throws IOException;

    /**
     * 从已落盘路径提取文本
     */
    ExtractionResult extractFromPath(Path filePath) throws IOException;

    /**
     * 计算文件 MD5 校验和
     */
    String calculateChecksum(Path filePath) throws IOException;
}
