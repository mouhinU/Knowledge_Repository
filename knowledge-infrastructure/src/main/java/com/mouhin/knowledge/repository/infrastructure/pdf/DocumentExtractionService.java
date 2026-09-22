package com.mouhin.knowledge.repository.infrastructure.pdf;

import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.extraction.DocxExtractionService;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import com.mouhin.knowledge.repository.infrastructure.extraction.PdfBoxExtractionService;
import com.mouhin.knowledge.repository.infrastructure.extraction.PlainTextExtractionService;
import com.mouhin.knowledge.repository.infrastructure.extraction.PptxExtractionService;
import com.mouhin.knowledge.repository.infrastructure.extraction.TikaFallbackExtractionService;
import com.mouhin.knowledge.repository.infrastructure.extraction.XlsxExtractionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

/**
 * 多格式文档提取服务（编排层）
 *
 * <p>负责 MIME 检测 + 路由分派；具体提取逻辑委托到 6 个 MIME 族提取服务。
 *
 * @author mouhinU
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class DocumentExtractionService implements DocumentExtractionGateway {

    /** 最大文件大小：200MB */
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;

    /** 支持的文件扩展名 */
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(
                    "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "csv", "md", "html",
                    "htm", "rtf");

    private final Tika tika = new Tika();
    private final PdfBoxExtractionService pdfBoxExtractionService;
    private final DocxExtractionService docxExtractionService;
    private final XlsxExtractionService xlsxExtractionService;
    private final PptxExtractionService pptxExtractionService;
    private final PlainTextExtractionService plainTextExtractionService;
    private final TikaFallbackExtractionService tikaFallbackExtractionService;

    public DocumentExtractionService(
            PdfBoxExtractionService pdfBoxExtractionService,
            DocxExtractionService docxExtractionService,
            XlsxExtractionService xlsxExtractionService,
            PptxExtractionService pptxExtractionService,
            PlainTextExtractionService plainTextExtractionService,
            TikaFallbackExtractionService tikaFallbackExtractionService) {
        this.pdfBoxExtractionService = pdfBoxExtractionService;
        this.docxExtractionService = docxExtractionService;
        this.xlsxExtractionService = xlsxExtractionService;
        this.pptxExtractionService = pptxExtractionService;
        this.plainTextExtractionService = plainTextExtractionService;
        this.tikaFallbackExtractionService = tikaFallbackExtractionService;
    }

    /** 校验文件 */
    @Override
    public void validateFile(Path filePath, long fileSize, String fileName) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format(
                            "File size %d exceeds maximum %d bytes", fileSize, MAX_FILE_SIZE));
        }
        if (fileName != null) {
            String ext = ExtractionSupport.getExtension(fileName);
            if (!SUPPORTED_EXTENSIONS.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + ext + ". Supported: " + SUPPORTED_EXTENSIONS);
            }
        }
    }

    // ==================== 公开 API ====================

    /**
     * 从文件中提取文本（按页/结构单元拆分）
     *
     * @param filePath 文件路径
     * @param fileSize 文件大小
     * @param fileName 文件名
     * @return 提取结果
     */
    @Override
    public ExtractionResult extractText(Path filePath, long fileSize, String fileName)
            throws IOException {
        validateFile(filePath, fileSize, fileName);
        String mimeType = tika.detect(filePath);

        log.info("Extracting text from: {} (type={}, size={})", fileName, mimeType, fileSize);

        return switch (mimeType) {
            case "application/pdf" -> pdfBoxExtractionService.extract(filePath);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword" ->
                    docxExtractionService.extract(filePath);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel" ->
                    xlsxExtractionService.extract(filePath);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/vnd.ms-powerpoint" ->
                    pptxExtractionService.extract(filePath);
            case "text/plain", "text/csv", "text/html", "text/markdown" ->
                    plainTextExtractionService.extract(filePath, mimeType);
            default -> tikaFallbackExtractionService.extract(filePath, mimeType);
        };
    }

    /** 从文件路径提取（简化版） */
    @Override
    public ExtractionResult extractFromPath(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : null;
        return extractText(filePath, fileSize, fileName);
    }

    /** 计算文件 MD5 校验和 */
    @Override
    public String calculateChecksum(Path filePath) throws IOException {
        return ExtractionSupport.calculateChecksum(filePath);
    }
}
