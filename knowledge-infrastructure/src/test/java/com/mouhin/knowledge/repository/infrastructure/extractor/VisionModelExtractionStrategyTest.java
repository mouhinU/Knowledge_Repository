package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.config.LlmVisionProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** {@link VisionModelExtractionStrategy} 契约测试（Phase B）。 */
@DisplayName("B 视觉策略")
class VisionModelExtractionStrategyTest {

    private VisionChatGateway gateway;
    private PageRenderer pageRenderer;
    private LlmVisionProperties props;
    private VisionModelExtractionStrategy strategy;
    private Path realFile;

    @BeforeEach
    void setUp() throws IOException {
        gateway = Mockito.mock(VisionChatGateway.class);
        pageRenderer = Mockito.mock(PageRenderer.class);
        props = new LlmVisionProperties();
        strategy = new VisionModelExtractionStrategy(gateway, pageRenderer, props);
        realFile = Files.createTempFile("scan", ".pdf");
        Files.write(realFile, new byte[] {1, 2, 3, 4});
    }

    private ExtractionCandidate pdfScannedWithEmbedded(List<ExtractedImage> images) {
        return new ExtractionCandidate(
                realFile,
                4L,
                "scan.pdf",
                "application/pdf",
                Map.of("ocrRecommended", true),
                images,
                ExtractionConfig.defaults());
    }

    @Test
    @DisplayName("标识与优先级")
    void identity() {
        assertThat(strategy.name()).isEqualTo("VISION");
        assertThat(strategy.priority()).isEqualTo(70);
    }

    @Nested
    @DisplayName("supports 路由")
    class Supports {

        @Test
        @DisplayName("默认关闭：任何候选都不命中")
        void disabledNeverSupports() {
            props.setEnabled(false);
            assertThat(strategy.supports(candidateOf("image/png"))).isFalse();
            assertThat(strategy.supports(pdfScannedWithEmbedded(List.of()))).isFalse();
        }

        @Test
        @DisplayName("启用 + 图片 → 命中")
        void enabledImage() {
            props.setEnabled(true);
            assertThat(strategy.supports(candidateOf("image/png"))).isTrue();
        }

        @Test
        @DisplayName("启用 + 扫描 PDF（ocrRecommended）→ 命中；非扫描/Office → 不命中")
        void enabledPdfScannedOnly() {
            props.setEnabled(true);
            assertThat(strategy.supports(pdfScannedWithEmbedded(List.of()))).isTrue();
            ExtractionCandidate pdfText =
                    new ExtractionCandidate(
                            Path.of("/tmp/t.pdf"),
                            1L,
                            "t.pdf",
                            "application/pdf",
                            Map.of(),
                            List.of(),
                            ExtractionConfig.defaults());
            assertThat(strategy.supports(pdfText)).isFalse();
            assertThat(strategy.supports(candidateOf("application/vnd.ms-excel"))).isFalse();
        }
    }

    @Nested
    @DisplayName("extract 执行")
    class Extract {

        @TempDir Path dir;

        @Test
        @DisplayName("优先用嵌入图，逐页调视觉并汇总文本")
        void usesEmbeddedImages() throws IOException {
            props.setEnabled(true);
            when(gateway.chatWithImages(any())).thenReturn("  第1页文字  ");
            ExtractedImage img =
                    new ExtractedImage(new byte[] {1, 2, 3}, "image/png", 10, 10, 1, 0);
            ExtractionResult result = strategy.extract(pdfScannedWithEmbedded(List.of(img, img)));
            assertThat(result.pageTexts()).containsExactly("第1页文字", "第1页文字");
            assertThat(result.likelyScanned()).isTrue();
            assertThat(result.detectedFormat()).isEqualTo("vision");
            verify(pageRenderer, never()).renderPageToPng(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("无嵌入图则回退渲染页面")
        void fallsBackToRendering() throws IOException {
            props.setEnabled(true);
            when(pageRenderer.pageCount(any())).thenReturn(2);
            when(pageRenderer.renderPageToPng(any(), anyInt(), anyInt()))
                    .thenReturn(new byte[] {9, 9});
            when(gateway.chatWithImages(any())).thenReturn("页文本");
            ExtractionResult result = strategy.extract(pdfScannedWithEmbedded(List.of()));
            assertThat(result.pageTexts()).containsExactly("页文本", "页文本");
            verify(pageRenderer, Mockito.times(2)).renderPageToPng(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("视觉调用异常/空返回 → 降级不抛，返回空结果")
        void degradesOnFailures() throws IOException {
            props.setEnabled(true);
            when(gateway.chatWithImages(any())).thenThrow(new RuntimeException("timeout"));
            ExtractedImage img = new ExtractedImage(new byte[] {1}, "image/png", 1, 1, 1, 0);
            ExtractionResult result = strategy.extract(pdfScannedWithEmbedded(List.of(img)));
            assertThat(result.pageTexts()).isEmpty();
            assertThat(result.detectedFormat()).isEqualTo("empty");
        }

        @Test
        @DisplayName("图片文件：读文件字节为单图送入")
        void extractsImageFile() throws IOException {
            props.setEnabled(true);
            Path png = dir.resolve("a.png");
            Files.write(png, new byte[] {5, 6, 7});
            when(gateway.chatWithImages(any())).thenReturn("图中文字");
            ExtractionCandidate candidate =
                    new ExtractionCandidate(
                            png,
                            Files.size(png),
                            "a.png",
                            "image/png",
                            Map.of(),
                            List.of(),
                            ExtractionConfig.defaults());
            ExtractionResult result = strategy.extract(candidate);
            assertThat(result.pageTexts()).containsExactly("图中文字");
            assertThat(result.detectedFormat()).isEqualTo("image");
        }

        @Test
        @DisplayName("超过字节上限的图被跳过 → 无源返回空")
        void skipsOversizedImage() throws IOException {
            props.setEnabled(true);
            ExtractionConfig tight =
                    new ExtractionConfig(20, 200, 2, 150, ExtractionConfig.MERGE_APPEND, 60, 300);
            ExtractedImage big =
                    new ExtractedImage(new byte[] {1, 2, 3, 4}, "image/png", 1, 1, 1, 0);
            ExtractionCandidate candidate =
                    new ExtractionCandidate(
                            Path.of("/tmp/scan.pdf"),
                            1L,
                            "scan.pdf",
                            "application/pdf",
                            Map.of("ocrRecommended", true),
                            List.of(big),
                            tight);
            ExtractionResult result = strategy.extract(candidate);
            assertThat(result.detectedFormat()).isEqualTo("empty");
            verify(gateway, never()).chatWithImages(any());
        }
    }

    private ExtractionCandidate candidateOf(String mime) {
        return new ExtractionCandidate(
                Path.of("/tmp/x"), 1L, "x", mime, Map.of(), List.of(), ExtractionConfig.defaults());
    }
}
