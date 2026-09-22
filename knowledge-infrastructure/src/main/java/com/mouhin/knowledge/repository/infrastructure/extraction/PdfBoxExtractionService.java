package com.mouhin.knowledge.repository.infrastructure.extraction;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.pdf.EnhancedPdfTextExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PDF 文档提取服务（PDFBox 实现）
 *
 * <p>使用 {@link EnhancedPdfTextExtractor} 按页提取，过滤页眉页脚。
 *
 * @author beginningness
 * @date 2026-09-22 16:21:33
 */
@Service
@Slf4j
public class PdfBoxExtractionService {

    /**
     * 从 PDF 文件提取文本
     *
     * @param filePath PDF 文件路径
     * @return 提取结果
     * @throws IOException 读取失败
     */
    public ExtractionResult extract(Path filePath) throws IOException {
        EnhancedPdfTextExtractor extractor = new EnhancedPdfTextExtractor();
        EnhancedPdfTextExtractor.PdfExtractionResult result = extractor.extract(filePath);

        List<String> pageTexts =
                result.pages().stream()
                        .map(EnhancedPdfTextExtractor.PageContent::filteredText)
                        .toList();

        for (String warning : result.warnings()) {
            log.warn("PDF extraction warning: {}", warning);
        }

        log.info(
                "PDF extracted (enhanced): {} pages, encrypted={}, ocrRecommended={}, warnings={}",
                result.getTotalPages(),
                result.encrypted(),
                result.ocrRecommended(),
                result.warnings().size());

        return new ExtractionResult(
                pageTexts,
                result.getTotalPages(),
                result.ocrRecommended(),
                ExtractionSupport.calculateChecksum(filePath),
                "pdf",
                result.warnings(),
                result.encrypted(),
                result.metadata().title(),
                result.metadata().author());
    }
}
