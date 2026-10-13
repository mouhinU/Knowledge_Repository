package com.mouhin.knowledge.repository.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ExtractionStrategyStackParser} 单元测试（Phase D）。 */
@DisplayName("D 解析策略栈解析器")
class ExtractionStrategyStackParserTest {

    @Test
    @DisplayName("null / 空 / auto → 空栈（回退配置默认）")
    void blankOrNullYieldsEmpty() {
        assertThat(ExtractionStrategyStackParser.parse(null)).isEmpty();
        assertThat(ExtractionStrategyStackParser.parse("  ")).isEmpty();
        assertThat(ExtractionStrategyStackParser.parse("auto")).isEmpty();
        assertThat(ExtractionStrategyStackParser.parse("AUTO")).isEmpty();
    }

    @Test
    @DisplayName("合法枚举名（大小写不敏感）被接受，非法值丢弃，保序去重")
    void validatesAgainstWhitelist() {
        assertThat(ExtractionStrategyStackParser.parse("PDF_HYBRID, pdf_box"))
                .containsExactly("PDF_HYBRID", "PDF_BOX");
        assertThat(ExtractionStrategyStackParser.parse("VISION,BOGUS,vision"))
                .containsExactly("VISION");
        assertThat(ExtractionStrategyStackParser.parse("NOT_A_STRATEGY,also_bogus")).isEmpty();
    }

    @Test
    @DisplayName("isKnown 白名单判定")
    void isKnownChecksMembership() {
        assertThat(ExtractionStrategyStackParser.isKnown("vision")).isTrue();
        assertThat(ExtractionStrategyStackParser.isKnown("VISION")).isTrue();
        assertThat(ExtractionStrategyStackParser.isKnown("nope")).isFalse();
        assertThat(ExtractionStrategyStackParser.isKnown(null)).isFalse();
    }
}
