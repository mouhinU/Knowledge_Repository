package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 多格式文档文本提取网关（领域层端口，由基础设施层实现）
 *
 * <p>屏蔽 PDFBox / POI / Tika 等解析技术细节，应用层仅依赖本接口。返回值与异常均为 领域 / JDK 类型，不泄露基础设施类型。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface DocumentExtractionGateway {

    /** 校验文件（大小、格式合法性），非法时抛业务异常 */
    void validateFile(Path filePath, long fileSize, String fileName);

    /** 提取文本（按 MIME 类型路由到专用解析器） */
    ExtractionResult extractText(Path filePath, long fileSize, String fileName) throws IOException;

    /**
     * 提取文本，可选强制指定解析策略栈。
     *
     * @param filePath 已落盘路径
     * @param fileSize 字节数
     * @param fileName 原始文件名
     * @param forcedStrategyStack 强制策略名列表（已由白名单校验）；为空表示走配置默认路由
     * @return 提取结果
     * @throws IOException 读取或解析失败
     */
    default ExtractionResult extractText(
            Path filePath, long fileSize, String fileName, List<String> forcedStrategyStack)
            throws IOException {
        return extractText(filePath, fileSize, fileName);
    }

    /** 从已落盘路径提取文本 */
    ExtractionResult extractFromPath(Path filePath) throws IOException;

    /** 计算文件 MD5 校验和 */
    String calculateChecksum(Path filePath) throws IOException;
}
