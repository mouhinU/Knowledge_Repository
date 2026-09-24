package com.mouhin.knowledge.repository.infrastructure.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文档提取公共工具单测：聚焦 {@code calculateChecksum} 的异常边界（目录 / 不存在路径 / 空文件固定 64 位 hex） 与 {@code getExtension}
 * 的扩展名提取语义（无扩展名回空串、多点取最后段）。 happy-path 内容摘要已由 {@code CompositeExtractionServiceTest} 间接覆盖，此处不重复穷举。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("提取公共工具：checksum 边界与扩展名 (ExtractionSupport)")
class ExtractionSupportTest {

    /** 空字节序列的 SHA-256 固定值（NIST 标准测试向量）。 */
    private static final String SHA256_OF_EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir Path tempDir;

    @Test
    @DisplayName("空文件 → 64 位小写 hex，等于空输入 SHA-256 标准向量")
    void emptyFileProducesKnownDigest() throws IOException {
        Path empty = tempDir.resolve("empty.pdf");
        Files.createFile(empty);

        String checksum = ExtractionSupport.calculateChecksum(empty);

        assertEquals(64, checksum.length(), "hex 长度须匹配 VARCHAR(64) 列宽");
        assertEquals(SHA256_OF_EMPTY, checksum);
        assertTrue(checksum.equals(checksum.toLowerCase()), "应为小写 hex");
    }

    @Test
    @DisplayName("同内容两次摘要一致（内容寻址缓存命中前提），不同内容必不同")
    void digestIsContentAddressed() throws IOException {
        Path a = Files.write(tempDir.resolve("a.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        Path b = Files.write(tempDir.resolve("b.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        Path c = Files.write(tempDir.resolve("c.txt"), "hallo".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                ExtractionSupport.calculateChecksum(a), ExtractionSupport.calculateChecksum(b));
        assertTrue(
                !ExtractionSupport.calculateChecksum(a)
                        .equals(ExtractionSupport.calculateChecksum(c)));
    }

    @Test
    @DisplayName("目录而非文件 → IOException 冒泡，不静默产出错误摘要")
    void directoryThrowsIoException() {
        assertThrows(
                IOException.class, () -> ExtractionSupport.calculateChecksum(tempDir), "目录不可作摘要源");
    }

    @Test
    @DisplayName("不存在路径 → IOException（NoSuchFileException 族）")
    void missingFileThrowsIoException() {
        Path ghost = tempDir.resolve("not-exists.pdf");
        assertThrows(IOException.class, () -> ExtractionSupport.calculateChecksum(ghost));
    }

    @Test
    @DisplayName("getExtension：常规 / 多点 / 无扩展名 / 尾部点号语义")
    void getExtensionSemantics() {
        assertEquals("pdf", ExtractionSupport.getExtension("doc.pdf"));
        assertEquals("gz", ExtractionSupport.getExtension("archive.tar.gz"), "多点取最后一段");
        assertEquals("", ExtractionSupport.getExtension("noext"));
        assertEquals("", ExtractionSupport.getExtension("file."));
        assertEquals(
                "PDF",
                ExtractionSupport.getExtension("A.PDF"),
                "TODO(行为可疑): Javadoc 声称返回小写，实现未做 toLowerCase，此处锁定当前原样返回行为");
    }
}
