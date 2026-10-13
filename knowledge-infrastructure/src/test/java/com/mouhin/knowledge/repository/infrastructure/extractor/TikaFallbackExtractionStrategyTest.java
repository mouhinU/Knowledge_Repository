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
 * {@link TikaFallbackExtractionStrategy} 测试：兜底谓词恒真 + 真实 Tika 解析。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@DisplayName("Tika 兜底解析策略")
class TikaFallbackExtractionStrategyTest {

    private final TikaFallbackExtractionStrategy strategy = new TikaFallbackExtractionStrategy();

    @TempDir Path tempDir;

    private static ExtractionCandidate withMime(String mime) {
        return ExtractionCandidate.basic(Path.of("dummy"), 10L, "dummy", mime);
    }

    @Test
    @DisplayName("name=TIKA_FALLBACK，priority=60")
    void identity() {
        assertThat(strategy.name()).isEqualTo("TIKA_FALLBACK");
        assertThat(strategy.priority()).isEqualTo(60);
    }

    @Test
    @DisplayName("supports 恒真（任意非空候选），null 除外")
    void supportsAny() {
        assertThat(strategy.supports(withMime("application/octet-stream"))).isTrue();
        assertThat(strategy.supports(withMime("application/pdf"))).isTrue();
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    @DisplayName("真实解析文本文件 → generic 结果非空")
    void extractViaTika() throws IOException {
        Path file = tempDir.resolve("fallback.txt");
        Files.writeString(file, "some generic content here", StandardCharsets.UTF_8);
        ExtractionCandidate candidate =
                ExtractionCandidate.basic(file, Files.size(file), "fallback.txt", "text/plain");

        ExtractionResult result = strategy.extract(candidate);

        assertThat(result.detectedFormat()).isEqualTo("generic");
        assertThat(result.pageTexts()).isNotEmpty();
        assertThat(String.join(" ", result.pageTexts())).contains("generic content");
    }
}
