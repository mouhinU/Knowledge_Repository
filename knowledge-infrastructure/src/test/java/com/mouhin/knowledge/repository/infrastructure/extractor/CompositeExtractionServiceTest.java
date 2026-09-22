package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.config.ExtractorRoutingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link CompositeExtractionService} 路由逻辑单测：内置映射、配置覆盖、顺序回退、启用白名单、空结果与文件校验。
 *
 * <p>用桩 {@link ContentExtractor} 验证组合器决策，不触碰真实 POI / PDFBox 解析体；文本族以临时 {@code .txt} 让 Tika 稳定探测为
 * {@code text/plain}，从而走通 MIME 检测 → 路由全链路。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
class CompositeExtractionServiceTest {

    @TempDir Path tempDir;

    /** 可配置桩策略：命中 supports 谓词后按注入行为返回哨兵结果或抛异常。 */
    private static final class StubExtractor implements ContentExtractor {
        private final String name;
        private final int priority;
        private final Predicate<ExtractionCandidate> supports;
        private final Stub behavior;

        StubExtractor(
                String name, int priority, Predicate<ExtractionCandidate> supports, Stub behavior) {
            this.name = name;
            this.priority = priority;
            this.supports = supports;
            this.behavior = behavior;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(ExtractionCandidate candidate) {
            return supports.test(candidate);
        }

        @Override
        public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
            return behavior.apply(name);
        }
    }

    @FunctionalInterface
    private interface Stub {
        ExtractionResult apply(String strategyName) throws IOException;
    }

    private static Stub returns() {
        return name -> new ExtractionResult(List.of("from:" + name), 1, false, "ck-" + name, name);
    }

    private static Stub throwsIo(String message) {
        return name -> {
            throw new IOException(message + " (" + name + ")");
        };
    }

    private Path writeTextFile(String content) throws IOException {
        Path file = tempDir.resolve("doc.txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Nested
    @DisplayName("内置 MIME 映射")
    class BuiltinRouting {

        @Test
        @DisplayName("text/plain 命中内置 PLAIN_TEXT 策略")
        void routesPlainTextByBuiltinMap() throws IOException {
            Path file = writeTextFile("hello world");
            StubExtractor plainTextStub = new StubExtractor("PLAIN_TEXT", 50, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(
                            List.of(plainTextStub), new ExtractorRoutingProperties());

            ExtractionResult result = composite.extractText(file, Files.size(file), "doc.txt");

            assertThat(result.detectedFormat()).isEqualTo("PLAIN_TEXT");
            assertThat(result.pageTexts()).containsExactly("from:PLAIN_TEXT");
        }
    }

    @Nested
    @DisplayName("强制策略栈（reparse）")
    class ForcedStack {

        @Test
        @DisplayName("forcedStack 命中未内置注册的策略")
        void forcedStackRunsCustom() throws IOException {
            Path file = writeTextFile("content");
            StubExtractor customB = new StubExtractor("CUSTOM_B", 1, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(
                            List.of(customB), new ExtractorRoutingProperties());

            ExtractionResult result =
                    composite.extractText(file, Files.size(file), "doc.txt", List.of("CUSTOM_B"));

            assertThat(result.detectedFormat()).isEqualTo("CUSTOM_B");
            assertThat(result.pageTexts()).containsExactly("from:CUSTOM_B");
        }

        @Test
        @DisplayName("空 forcedStack → 回退配置默认，未注册策略不被选中 → 空结果")
        void emptyForcedStackFallsBackToConfig() throws IOException {
            Path file = writeTextFile("content");
            StubExtractor customB = new StubExtractor("CUSTOM_B", 1, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(
                            List.of(customB), new ExtractorRoutingProperties());

            ExtractionResult result =
                    composite.extractText(file, Files.size(file), "doc.txt", List.of());

            assertThat(result.detectedFormat()).isEqualTo("empty");
        }
    }

    @Nested
    @DisplayName("配置覆盖与回退")
    class ConfigAndFallback {

        @Test
        @DisplayName("routing 配置覆盖内置映射")
        void configRoutingOverridesBuiltin() throws IOException {
            Path file = writeTextFile("content");
            ExtractorRoutingProperties props = new ExtractorRoutingProperties();
            props.setRouting(Map.of("text/plain", List.of("CUSTOM_A")));
            StubExtractor customA = new StubExtractor("CUSTOM_A", 1, c -> true, returns());
            StubExtractor plainTextStub = new StubExtractor("PLAIN_TEXT", 50, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(customA, plainTextStub), props);

            ExtractionResult result = composite.extractText(file, Files.size(file), "doc.txt");

            assertThat(result.detectedFormat()).isEqualTo("CUSTOM_A");
        }

        @Test
        @DisplayName("首个策略抛 IOException 时回退到栈内下一策略")
        void fallbackOnIoException() throws IOException {
            Path file = writeTextFile("content");
            ExtractorRoutingProperties props = new ExtractorRoutingProperties();
            props.setRouting(Map.of("text/plain", List.of("BROKEN", "CUSTOM_B")));
            StubExtractor broken = new StubExtractor("BROKEN", 1, c -> true, throwsIo("boom"));
            StubExtractor good = new StubExtractor("CUSTOM_B", 2, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(broken, good), props);

            ExtractionResult result = composite.extractText(file, Files.size(file), "doc.txt");

            assertThat(result.detectedFormat()).isEqualTo("CUSTOM_B");
        }

        @Test
        @DisplayName("栈内全部失败则冒泡最后一个 IOException")
        void rethrowsLastErrorWhenAllFail() throws IOException {
            Path file = writeTextFile("content");
            ExtractorRoutingProperties props = new ExtractorRoutingProperties();
            props.setRouting(Map.of("text/plain", List.of("FAIL_A", "FAIL_B")));
            props.setDefaultStack(List.of("NOT_IN_STACK"));
            StubExtractor a = new StubExtractor("FAIL_A", 1, c -> true, throwsIo("A"));
            StubExtractor b = new StubExtractor("FAIL_B", 2, c -> true, throwsIo("B"));
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(a, b), props);

            assertThatThrownBy(() -> composite.extractText(file, Files.size(file), "doc.txt"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("B");
        }

        @Test
        @DisplayName("enabled-strategies 白名单命中不到时回退 default-stack")
        void disabledStrategyFallsBackToDefaultStack() throws IOException {
            Path file = writeTextFile("content");
            ExtractorRoutingProperties props = new ExtractorRoutingProperties();
            // 不配 routing → 内置 text/plain → [PLAIN_TEXT]；但 PLAIN_TEXT 被禁用
            props.setEnabledStrategies(List.of("TIKA_FALLBACK"));
            props.setDefaultStack(List.of("TIKA_FALLBACK"));
            StubExtractor plainTextStub = new StubExtractor("PLAIN_TEXT", 50, c -> true, returns());
            StubExtractor tikaStub = new StubExtractor("TIKA_FALLBACK", 60, c -> true, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(plainTextStub, tikaStub), props);

            ExtractionResult result = composite.extractText(file, Files.size(file), "doc.txt");

            assertThat(result.detectedFormat()).isEqualTo("TIKA_FALLBACK");
        }

        @Test
        @DisplayName("无策略 supports 命中 → 返回 empty()，不抛异常")
        void noSupportReturnsEmpty() throws IOException {
            Path file = writeTextFile("content");
            StubExtractor neverSupports =
                    new StubExtractor("PLAIN_TEXT", 50, c -> false, returns());
            CompositeExtractionService composite =
                    new CompositeExtractionService(
                            List.of(neverSupports), new ExtractorRoutingProperties());

            ExtractionResult result = composite.extractText(file, Files.size(file), "doc.txt");

            assertThat(result.totalPages()).isZero();
            assertThat(result.pageTexts()).isEmpty();
            assertThat(result.detectedFormat()).isEqualTo("empty");
        }
    }

    @Nested
    @DisplayName("validateFile 与 checksum")
    class ValidationAndChecksum {

        @Test
        @DisplayName("路径不存在抛 IllegalArgumentException")
        void missingFileRejected() {
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(), new ExtractorRoutingProperties());
            assertThatThrownBy(
                            () ->
                                    composite.validateFile(
                                            tempDir.resolve("nope.txt"), 10, "nope.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("空文件（size<=0）抛 IllegalArgumentException")
        void emptyFileRejected() throws IOException {
            Path file = writeTextFile("x");
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(), new ExtractorRoutingProperties());
            assertThatThrownBy(() -> composite.validateFile(file, 0, "doc.txt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("不支持的扩展名抛 IllegalArgumentException")
        void unsupportedExtensionRejected() throws IOException {
            Path file = tempDir.resolve("archive.zip");
            Files.writeString(file, "PK");
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(), new ExtractorRoutingProperties());
            assertThatThrownBy(() -> composite.validateFile(file, 2, "archive.zip"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }

        @Test
        @DisplayName("calculateChecksum 返回文件内容的 MD5（32 位小写十六进制）")
        void checksumMatchesMd5() throws Exception {
            byte[] content = "hello checksum".getBytes(StandardCharsets.UTF_8);
            Path file = tempDir.resolve("ck.txt");
            Files.write(file, content);
            CompositeExtractionService composite =
                    new CompositeExtractionService(List.of(), new ExtractorRoutingProperties());

            String actual = composite.calculateChecksum(file);

            MessageDigest md = MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(content)) {
                sb.append(String.format("%02x", b));
            }
            assertThat(actual).isEqualTo(sb.toString()).hasSize(32);
        }
    }
}
