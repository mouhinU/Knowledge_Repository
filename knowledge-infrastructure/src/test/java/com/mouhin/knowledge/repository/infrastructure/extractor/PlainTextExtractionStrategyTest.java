package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PlainTextExtractionStrategy} 测试：契约（name/priority/supports）+ 真实按段落提取。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("纯文本解析策略")
class PlainTextExtractionStrategyTest {

    private final PlainTextExtractionStrategy strategy = new PlainTextExtractionStrategy();

    @TempDir Path tempDir;

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=PLAIN_TEXT，priority=50")
    void identity() {
        assertThat(strategy.name()).isEqualTo("PLAIN_TEXT");
        assertThat(strategy.priority()).isEqualTo(50);
    }

    @Test
    @DisplayName("text/* 族命中，pdf 不命中")
    void supports() {
        assertThat(strategy.supports(withMime("text/plain"))).isTrue();
        assertThat(strategy.supports(withMime("text/csv"))).isTrue();
        assertThat(strategy.supports(withMime("text/markdown"))).isTrue();
        assertThat(strategy.supports(withMime("application/pdf"))).isFalse();
    }

    @Test
    @DisplayName("按空行切段：两段落 → 两个 section")
    void extractSplitsByParagraph() throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "para one\n\npara two", StandardCharsets.UTF_8);
        ExtractionCandidate candidate =
                ExtractionCandidate.basic(file, Files.size(file), "a.txt", "text/plain");

        ExtractionResult result = strategy.extract(candidate);

        assertThat(result.detectedFormat()).isEqualTo("text");
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.pageTexts()).containsExactly("para one", "para two");
        assertThat(result.checksum()).hasSize(64);
    }
}
