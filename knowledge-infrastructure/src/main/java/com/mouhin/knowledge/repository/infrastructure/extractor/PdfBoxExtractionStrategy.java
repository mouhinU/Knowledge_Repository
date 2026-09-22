package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import com.mouhin.knowledge.repository.infrastructure.pdf.EnhancedPdfTextExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PDF 解析策略（PDFBox 实现）。
 *
 * <p>委托 {@link EnhancedPdfTextExtractor} 按页提取并过滤页眉页脚，返回带 {@code ocrRecommended} / {@code encrypted}
 * / 元数据的全字段结果，供 Phase C 混合策略路由。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class PdfBoxExtractionStrategy implements ContentExtractor {

    /** 本策略命中的 MIME 类型。 */
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("application/pdf");

    @Override
    public String name() {
        return ExtractionStrategyEnum.PDF_BOX.name();
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return candidate != null && SUPPORTED_MIME_TYPES.contains(candidate.mimeType());
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        Path filePath = candidate.filePath();
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
