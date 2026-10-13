package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PdfBoxExtractionStrategy} 契约测试：策略标识、优先级与 MIME 路由谓词。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("PDF 解析策略")
class PdfBoxExtractionStrategyTest {

    private final PdfBoxExtractionStrategy strategy = new PdfBoxExtractionStrategy();

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=PDF_BOX，priority=10")
    void identity() {
        assertThat(strategy.name()).isEqualTo("PDF_BOX");
        assertThat(strategy.priority()).isEqualTo(10);
    }

    @Test
    @DisplayName("仅 application/pdf 命中")
    void supports() {
        assertThat(strategy.supports(withMime("application/pdf"))).isTrue();
        assertThat(strategy.supports(withMime("text/plain"))).isFalse();
        assertThat(strategy.supports(null)).isFalse();
    }
}
