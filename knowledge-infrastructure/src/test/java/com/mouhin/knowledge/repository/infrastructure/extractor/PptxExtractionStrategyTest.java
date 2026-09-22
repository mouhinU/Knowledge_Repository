package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PptxExtractionStrategy} 契约测试。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("PowerPoint 解析策略")
class PptxExtractionStrategyTest {

    private final PptxExtractionStrategy strategy = new PptxExtractionStrategy();

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=PPTX，priority=40")
    void identity() {
        assertThat(strategy.name()).isEqualTo("PPTX");
        assertThat(strategy.priority()).isEqualTo(40);
    }

    @Test
    @DisplayName("PPTX 与旧版 ms-powerpoint 命中，xlsx 不命中")
    void supports() {
        assertThat(
                        strategy.supports(
                                withMime(
                                        "application/vnd.openxmlformats-officedocument"
                                                + ".presentationml.presentation")))
                .isTrue();
        assertThat(strategy.supports(withMime("application/vnd.ms-powerpoint"))).isTrue();
        assertThat(strategy.supports(withMime("application/vnd.ms-excel"))).isFalse();
    }
}
