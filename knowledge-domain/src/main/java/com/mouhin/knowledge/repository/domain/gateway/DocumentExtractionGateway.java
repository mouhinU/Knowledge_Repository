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

    /**
     * 校验文件合法性（大小、格式），非法时抛业务异常。
     *
     * @param filePath 文件路径
     * @param fileSize 文件字节数
     * @param fileName 原始文件名（用于格式识别）
     * @throws com.mouhin.knowledge.repository.domain.model.valueobject.BusinessException 文件不合法时
     */
    void validateFile(Path filePath, long fileSize, String fileName);

    /**
     * 提取文本（按 MIME 类型路由到专用解析器）。
     *
     * @param filePath 已落盘路径
     * @param fileSize 字节数
     * @param fileName 原始文件名（用于格式识别）
     * @return 提取结果
     * @throws IOException 读取或解析失败
     */
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

    /**
     * 从已落盘路径提取文本。
     *
     * @param filePath 文件路径
     * @return 提取结果
     * @throws IOException 读取或解析失败
     */
    ExtractionResult extractFromPath(Path filePath) throws IOException;

    /**
     * 计算文件 MD5 校验和。
     *
     * @param filePath 文件路径
     * @return 十六进制 MD5 摘要字符串
     * @throws IOException 读取文件失败
     */
    String calculateChecksum(Path filePath) throws IOException;
}
