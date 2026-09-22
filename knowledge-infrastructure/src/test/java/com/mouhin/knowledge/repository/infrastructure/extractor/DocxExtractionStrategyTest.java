package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DocxExtractionStrategy} 契约测试。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("Word 解析策略")
class DocxExtractionStrategyTest {

    private final DocxExtractionStrategy strategy = new DocxExtractionStrategy();

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=DOCX，priority=20")
    void identity() {
        assertThat(strategy.name()).isEqualTo("DOCX");
        assertThat(strategy.priority()).isEqualTo(20);
    }

    @Test
    @DisplayName("DOCX 与旧版 msword 命中，pdf 不命中")
    void supports() {
        assertThat(
                        strategy.supports(
                                withMime(
                                        "application/vnd.openxmlformats-officedocument"
                                                + ".wordprocessingml.document")))
                .isTrue();
        assertThat(strategy.supports(withMime("application/msword"))).isTrue();
        assertThat(strategy.supports(withMime("application/pdf"))).isFalse();
    }
}
