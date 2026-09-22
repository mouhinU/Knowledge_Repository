package com.mouhin.knowledge.repository.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.ExtractionCacheRepository;
import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCacheKey;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.infrastructure.config.HybridExtractionProperties;
import com.mouhin.knowledge.repository.infrastructure.config.LlmVisionProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** {@link PdfHybridExtractionStrategy} 契约测试（Phase C）。 */
@DisplayName("C PDF 混合策略")
class PdfHybridExtractionStrategyTest {

    private static final String LONG = "x".repeat(250);
    private static final String SHORT = "短";

    private PdfBoxExtractionStrategy pdfBox;
    private VisionChatGateway gateway;
    private PageRenderer pageRenderer;
    private ExtractionCacheRepository cacheRepository;
    private LlmVisionProperties visionProps;
    private HybridExtractionProperties props;
    private PdfHybridExtractionStrategy strategy;
    private Path realFile;

    @BeforeEach
    void setUp() throws IOException {
        pdfBox = Mockito.mock(PdfBoxExtractionStrategy.class);
        gateway = Mockito.mock(VisionChatGateway.class);
        pageRenderer = Mockito.mock(PageRenderer.class);
        cacheRepository = Mockito.mock(ExtractionCacheRepository.class);
        visionProps = new LlmVisionProperties();
        props = new HybridExtractionProperties();
        strategy =
                new PdfHybridExtractionStrategy(
                        pdfBox, gateway, pageRenderer, cacheRepository, visionProps, props);
        realFile = Files.createTempFile("hybrid", ".pdf");
        Files.write(realFile, new byte[] {1, 2, 3, 4});
    }

    private ExtractionResult base(List<String> texts, boolean scanned) {
        return new ExtractionResult(
                texts, texts.size(), scanned, "md5", "pdf", List.of(), false, null, null);
    }

    private ExtractionCandidate candidate(List<ExtractedImage> images) {
        return new ExtractionCandidate(
                realFile,
                4L,
                "scan.pdf",
                "application/pdf",
                Map.of(),
                images,
                ExtractionConfig.defaults());
    }

    private ExtractedImage img(int pageNo) {
        return new ExtractedImage(new byte[] {1, 2, 3}, "image/png", 10, 10, pageNo, 0);
    }

    @Test
    @DisplayName("标识与优先级")
    void identity() {
        assertThat(strategy.name()).isEqualTo("PDF_HYBRID");
        assertThat(strategy.priority()).isEqualTo(5);
    }

    @Nested
    @DisplayName("supports 路由")
    class Supports {

        @Test
        @DisplayName("默认关闭 → 不命中")
        void disabledNeverSupports() {
            props.setEnabled(false);
            assertThat(strategy.supports(candidate(List.of()))).isFalse();
        }

        @Test
        @DisplayName("启用 + PDF → 命中；非 PDF → 不命中")
        void enabledPdfOnly() {
            props.setEnabled(true);
            assertThat(strategy.supports(candidate(List.of()))).isTrue();
            ExtractionCandidate docx =
                    new ExtractionCandidate(
                            realFile,
                            1L,
                            "a.docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            Map.of(),
                            List.of(),
                            ExtractionConfig.defaults());
            assertThat(strategy.supports(docx)).isFalse();
        }
    }

    @Nested
    @DisplayName("extract 执行")
    class Extract {

        @Test
        @DisplayName("命中持久缓存 → 直接返回，跳过 PDFBox 与视觉")
        void cacheHitSkipsEverything() throws IOException {
            props.setEnabled(true);
            ExtractionResult cached = base(List.of("命中缓存"), true);
            when(cacheRepository.find(any(ExtractionCacheKey.class)))
                    .thenReturn(Optional.of(cached));
            ExtractionResult result = strategy.extract(candidate(List.of()));
            assertThat(result).isSameAs(cached);
            verify(pdfBox, never()).extract(any());
            verify(gateway, never()).chatWithImages(any());
            verify(cacheRepository, never()).save(any(), any());
        }

        @Test
        @DisplayName("缓存未命中 + 扫描低文本页 → 用嵌入图补全并回写缓存")
        void enhancesLowTextPageWithEmbedded() throws IOException {
            props.setEnabled(true);
            props.setTrigger("scanned_only");
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(SHORT, LONG), true));
            when(gateway.chatWithImages(any())).thenReturn("视觉文字");
            ExtractionResult result = strategy.extract(candidate(List.of(img(1))));
            assertThat(result.pageTexts()).containsExactly("视觉文字", LONG);
            assertThat(result.detectedFormat()).isEqualTo("pdf-hybrid");
            verify(pageRenderer, never()).renderPageToPng(any(), anyInt(), anyInt());
            verify(cacheRepository)
                    .save(any(ExtractionCacheKey.class), any(ExtractionResult.class));
        }

        @Test
        @DisplayName("无嵌入图 → 栅格化页面兜底")
        void fallsBackToRendering() throws IOException {
            props.setEnabled(true);
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(SHORT), true));
            when(pageRenderer.renderPageToPng(any(), anyInt(), anyInt()))
                    .thenReturn(new byte[] {9});
            when(gateway.chatWithImages(any())).thenReturn("补全文本");
            ExtractionResult result = strategy.extract(candidate(List.of()));
            assertThat(result.pageTexts()).containsExactly("补全文本");
            verify(pageRenderer, times(1)).renderPageToPng(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("全为高文本页 → 无待增强页，不调视觉")
        void noTargetsSkipsVision() throws IOException {
            props.setEnabled(true);
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(LONG, LONG), true));
            ExtractionResult result = strategy.extract(candidate(List.of()));
            assertThat(result.pageTexts()).containsExactly(LONG, LONG);
            verify(gateway, never()).chatWithImages(any());
        }

        @Test
        @DisplayName("trigger=always → 全页过视觉（即使扫描位为 false）")
        void alwaysTriggerEnhancesAll() throws IOException {
            props.setEnabled(true);
            props.setTrigger("always");
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(SHORT, SHORT), false));
            when(gateway.chatWithImages(any())).thenReturn("T");
            ExtractionResult result = strategy.extract(candidate(List.of(img(1), img(2))));
            assertThat(result.pageTexts()).containsExactly("T", "T");
            verify(gateway, times(2)).chatWithImages(any());
        }

        @Test
        @DisplayName("视觉异常 → 降级保留文本层并追加 warning，仍回写缓存")
        void degradesOnVisionFailure() throws IOException {
            props.setEnabled(true);
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(SHORT), true));
            when(gateway.chatWithImages(any())).thenThrow(new RuntimeException("timeout"));
            ExtractionResult result = strategy.extract(candidate(List.of(img(1))));
            assertThat(result.pageTexts()).containsExactly(SHORT);
            assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("部分页视觉识别为空/失败"));
            verify(cacheRepository)
                    .save(any(ExtractionCacheKey.class), any(ExtractionResult.class));
        }

        @Test
        @DisplayName("merge=append → 原文 + 视觉补全并存")
        void appendMergeKeepsOriginal() throws IOException {
            props.setEnabled(true);
            props.setTrigger("always");
            props.setMerge("append");
            when(cacheRepository.find(any(ExtractionCacheKey.class))).thenReturn(Optional.empty());
            when(pdfBox.extract(any())).thenReturn(base(List.of(LONG), false));
            when(gateway.chatWithImages(any())).thenReturn("补全");
            ExtractionResult result = strategy.extract(candidate(List.of(img(1))));
            assertThat(result.pageTexts().get(0)).startsWith(LONG).contains("补全");
        }
    }
}
