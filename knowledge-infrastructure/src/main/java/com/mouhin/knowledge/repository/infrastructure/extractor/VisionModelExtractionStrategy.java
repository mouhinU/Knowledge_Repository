package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.VisionChatRequest;
import com.mouhin.knowledge.repository.infrastructure.config.LlmVisionProperties;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视觉模型解析策略（Phase B，默认关闭）。
 *
 * <p>仅当 {@link LlmVisionProperties#isEnabled()} 为真、且候选为图片或「文本层稀疏（{@code ocrRecommended}）的 PDF」时
 * {@link #supports(ExtractionCandidate)} 才命中——因此默认不参与 {@code CompositeExtractionService} 路由，零副作用。
 * 命中后：优先用文档嵌入图，否则由 {@link PageRenderer} 栅格化页面为 PNG；逐页调用 {@link VisionChatGateway} 识别，
 * 单页异常/空返回按「降级不抛、仅 warning」处理，受单文档页数与总时长预算约束。视觉 HTTP 调用为长耗时外部 IO， 依红线 #8 不得置于数据库事务内（异步增强编排在 Phase C
 * 落地）。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class VisionModelExtractionStrategy implements ContentExtractor {

    /** 路由优先级：视觉是增强型兜底，排在原生文本族（10~60）之后。 */
    private static final int PRIORITY = 70;

    private static final String PDF_MIME = "application/pdf";
    private static final String IMAGE_MIME_PREFIX = "image/";
    private static final String DEFAULT_IMAGE_MIME = "image/png";
    private static final String HINT_OCR_RECOMMENDED = "ocrRecommended";
    private static final int UNLIMITED_BYTES = 0;

    private final VisionChatGateway visionChatGateway;
    private final PageRenderer pageRenderer;
    private final LlmVisionProperties props;

    public VisionModelExtractionStrategy(
            VisionChatGateway visionChatGateway,
            PageRenderer pageRenderer,
            LlmVisionProperties props) {
        this.visionChatGateway = visionChatGateway;
        this.pageRenderer = pageRenderer;
        this.props = props;
    }

    @Override
    public String name() {
        return ExtractionStrategyEnum.VISION.name();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return props.isEnabled() && isImageOrScannedPdf(candidate);
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        List<VisionChatRequest.ImagePayload> images = resolveImageSources(candidate);
        if (images.isEmpty()) {
            log.warn("vision: no usable image source for {}", candidate.fileName());
            return ExtractionResult.empty();
        }
        List<String> texts =
                invokeVisionPerPage(images, candidate.config().maxTotalSecondsPerDoc());
        return assembleResult(candidate, texts, images.size());
    }

    private boolean isImageOrScannedPdf(ExtractionCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String mime = candidate.mimeType();
        if (mime == null) {
            return false;
        }
        if (mime.startsWith(IMAGE_MIME_PREFIX)) {
            return true;
        }
        return PDF_MIME.equals(mime) && hasOcrRecommendedHint(candidate);
    }

    private boolean hasOcrRecommendedHint(ExtractionCandidate candidate) {
        return candidate.nativeHints() != null
                && Boolean.TRUE.equals(candidate.nativeHints().get(HINT_OCR_RECOMMENDED));
    }

    private List<VisionChatRequest.ImagePayload> resolveImageSources(ExtractionCandidate candidate)
            throws IOException {
        int maxBytes = candidate.config().maxBytesPerImage();
        List<VisionChatRequest.ImagePayload> out = new ArrayList<>();
        String mime = candidate.mimeType();
        if (mime != null && mime.startsWith(IMAGE_MIME_PREFIX)) {
            addImage(out, Files.readAllBytes(candidate.filePath()), mime, maxBytes);
            return out;
        }
        if (props.isPreferEmbeddedImages() && hasEmbeddedImages(candidate)) {
            collectEmbedded(candidate, out, maxBytes);
            return out;
        }
        collectRenderedPages(candidate, out, maxBytes);
        return out;
    }

    private boolean hasEmbeddedImages(ExtractionCandidate candidate) {
        return candidate.embeddedImages() != null && !candidate.embeddedImages().isEmpty();
    }

    private void collectEmbedded(
            ExtractionCandidate candidate, List<VisionChatRequest.ImagePayload> out, int maxBytes) {
        int cap = Math.min(candidate.config().maxPagesPerDoc(), candidate.embeddedImages().size());
        for (int i = 0; i < cap; i++) {
            ExtractedImage img = candidate.embeddedImages().get(i);
            addImage(out, img.bytes(), img.mimeType(), maxBytes);
        }
    }

    private void collectRenderedPages(
            ExtractionCandidate candidate, List<VisionChatRequest.ImagePayload> out, int maxBytes)
            throws IOException {
        Path pdfPath = candidate.filePath();
        int pages = Math.min(candidate.config().maxPagesPerDoc(), pageRenderer.pageCount(pdfPath));
        int dpi = candidate.config().renderFallbackDpi();
        for (int p = 0; p < pages; p++) {
            byte[] png = pageRenderer.renderPageToPng(pdfPath, p, dpi);
            addImage(out, png, DEFAULT_IMAGE_MIME, maxBytes);
        }
    }

    private void addImage(
            List<VisionChatRequest.ImagePayload> out, byte[] bytes, String mime, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        if (maxBytes != UNLIMITED_BYTES && bytes.length > maxBytes) {
            log.warn("vision: skip oversized image {}bytes > limit {}", bytes.length, maxBytes);
            return;
        }
        String effectiveMime = (mime == null || mime.isBlank()) ? DEFAULT_IMAGE_MIME : mime;
        out.add(
                new VisionChatRequest.ImagePayload(
                        Base64.getEncoder().encodeToString(bytes), effectiveMime));
    }

    private List<String> invokeVisionPerPage(
            List<VisionChatRequest.ImagePayload> images, int budgetSeconds) {
        List<String> texts = new ArrayList<>();
        long deadline =
                System.nanoTime() + Duration.ofSeconds(Math.max(budgetSeconds, 1)).toNanos();
        AtomicInteger failures = new AtomicInteger();
        for (VisionChatRequest.ImagePayload image : images) {
            if (System.nanoTime() > deadline) {
                log.warn(
                        "vision budget exhausted, processed {}/{} pages",
                        texts.size(),
                        images.size());
                break;
            }
            try {
                String text =
                        visionChatGateway.chatWithImages(
                                new VisionChatRequest(props.getPrompt(), List.of(image)));
                if (text != null && !text.isBlank()) {
                    texts.add(text.trim());
                }
            } catch (RuntimeException e) {
                failures.incrementAndGet();
                log.warn("vision call failed, skip page: {}", e.getMessage());
            }
        }
        return texts;
    }

    private ExtractionResult assembleResult(
            ExtractionCandidate candidate, List<String> texts, int attempted) throws IOException {
        if (texts.isEmpty()) {
            return ExtractionResult.empty();
        }
        boolean degraded = texts.size() < attempted;
        List<String> warnings =
                degraded ? List.of("部分页视觉识别为空或失败: " + texts.size() + "/" + attempted) : List.of();
        String format = isImageMime(candidate.mimeType()) ? "image" : "vision";
        return new ExtractionResult(
                texts,
                texts.size(),
                true,
                ExtractionSupport.calculateChecksum(candidate.filePath()),
                format,
                warnings,
                false,
                null,
                null);
    }

    private boolean isImageMime(String mime) {
        return mime != null && mime.startsWith(IMAGE_MIME_PREFIX);
    }
}
