package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link XlsxExtractionStrategy} 契约测试。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("Excel 解析策略")
class XlsxExtractionStrategyTest {

    private final XlsxExtractionStrategy strategy = new XlsxExtractionStrategy();

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=XLSX，priority=30")
    void identity() {
        assertThat(strategy.name()).isEqualTo("XLSX");
        assertThat(strategy.priority()).isEqualTo(30);
    }

    @Test
    @DisplayName("XLSX 与旧版 ms-excel 命中，docx 不命中")
    void supports() {
        assertThat(
                        strategy.supports(
                                withMime(
                                        "application/vnd.openxmlformats-officedocument"
                                                + ".spreadsheetml.sheet")))
                .isTrue();
        assertThat(strategy.supports(withMime("application/vnd.ms-excel"))).isTrue();
        assertThat(strategy.supports(withMime("application/msword"))).isFalse();
    }
}
