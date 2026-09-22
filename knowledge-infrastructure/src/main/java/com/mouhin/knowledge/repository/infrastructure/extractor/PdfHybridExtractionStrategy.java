package com.mouhin.knowledge.repository.infrastructure.extractor;

import com.mouhin.knowledge.repository.domain.gateway.ContentExtractor;
import com.mouhin.knowledge.repository.domain.gateway.ExtractionCacheRepository;
import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractedImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCacheKey;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionCandidate;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.VisionChatRequest;
import com.mouhin.knowledge.repository.infrastructure.config.HybridExtractionProperties;
import com.mouhin.knowledge.repository.infrastructure.config.LlmVisionProperties;
import com.mouhin.knowledge.repository.infrastructure.extraction.ExtractionSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PDF 混合解析策略（Phase C，默认关闭）：PDFBox 文本层为主，视觉模型按页补全疑似扫描 / 低文本页。
 *
 * <p>路由优先级 {@code 5}（先于 {@link PdfBoxExtractionStrategy} 的 10），但仅在配置显式把 {@code PDF_HYBRID} 放入
 * {@code application/pdf} 路由栈、且 {@link HybridExtractionProperties#isEnabled()} 为真时才命中——默认路由栈为
 * {@code [PDF_BOX]}，故默认不生效，零副作用。
 *
 * <p>执行流程：先跑 PDFBox 取文本层基线 → 按 {@code trigger} 选出待增强页 → 逐页（嵌入图优先，否则 {@link PageRenderer} 栅格化）调
 * {@link VisionChatGateway} → 按 {@code merge} 合并回文本层 → 以文档粒度写 {@link ExtractionCacheRepository}
 * 持久缓存（同文件二次解析直接命中，跳过全部视觉往返）。单页识别失败仅降级为保留文本层，不整篇失败；受单文档页数与总时长预算约束。视觉 HTTP 为长耗时外部 IO， 依红线 #8
 * 由异步增强执行器在专用线程池触发，不置于数据库事务内。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class PdfHybridExtractionStrategy implements ContentExtractor {

    /** 路由优先级：混合策略优先于纯 PDFBox，接管 PDF 解析入口。 */
    private static final int PRIORITY = 5;

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of("application/pdf");
    private static final String DEFAULT_IMAGE_MIME = "image/png";
    private static final int UNLIMITED_BYTES = 0;
    private static final String RENDER_MODE_EMBEDDED_PREF = "embedded-pref";
    private static final String RENDER_MODE_RASTER = "raster";

    private final PdfBoxExtractionStrategy pdfBox;
    private final VisionChatGateway visionChatGateway;
    private final PageRenderer pageRenderer;
    private final ExtractionCacheRepository cacheRepository;
    private final LlmVisionProperties visionProps;
    private final HybridExtractionProperties props;

    public PdfHybridExtractionStrategy(
            PdfBoxExtractionStrategy pdfBox,
            VisionChatGateway visionChatGateway,
            PageRenderer pageRenderer,
            ExtractionCacheRepository cacheRepository,
            LlmVisionProperties visionProps,
            HybridExtractionProperties props) {
        this.pdfBox = pdfBox;
        this.visionChatGateway = visionChatGateway;
        this.pageRenderer = pageRenderer;
        this.cacheRepository = cacheRepository;
        this.visionProps = visionProps;
        this.props = props;
    }

    @Override
    public String name() {
        return ExtractionStrategyEnum.PDF_HYBRID.name();
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(ExtractionCandidate candidate) {
        return props.isEnabled()
                && candidate != null
                && SUPPORTED_MIME_TYPES.contains(candidate.mimeType());
    }

    @Override
    public ExtractionResult extract(ExtractionCandidate candidate) throws IOException {
        ExtractionCacheKey key =
                buildKey(ExtractionSupport.calculateChecksum(candidate.filePath()));
        Optional<ExtractionResult> hit = cacheRepository.find(key);
        if (hit.isPresent()) {
            log.debug("pdf-hybrid cache hit: {}", key.checksum());
            return hit.get();
        }
        ExtractionResult base = pdfBox.extract(candidate);
        List<String> notes = new ArrayList<>();
        List<String> merged = enhance(candidate, base, notes);
        ExtractionResult result = assemble(base, merged, notes);
        cacheRepository.save(key, result);
        return result;
    }

    /** 依 trigger 选出待增强页，逐页识别并按 merge 合并回文本层；返回新的分页文本列表。 */
    private List<String> enhance(
            ExtractionCandidate candidate, ExtractionResult base, List<String> notes) {
        List<String> texts = new ArrayList<>(base.pageTexts());
        List<Integer> targets = selectPages(base, texts);
        if (targets.isEmpty()) {
            return texts;
        }
        long deadline =
                System.nanoTime()
                        + Duration.ofSeconds(Math.max(props.getMaxTotalSecondsPerDoc(), 1))
                                .toNanos();
        int degraded = 0;
        for (int page : targets) {
            if (System.nanoTime() > deadline) {
                notes.add("视觉预算耗尽，剩余页保留文本层: 已处理 " + (targets.size() - degraded));
                break;
            }
            String vision = recognizePage(candidate, page);
            if (vision == null || vision.isBlank()) {
                degraded++;
                continue;
            }
            texts.set(page, mergePage(texts.get(page), vision));
        }
        if (degraded > 0) {
            notes.add("部分页视觉识别为空/失败，保留文本层: " + degraded + "/" + targets.size());
        }
        return texts;
    }

    /** 选出需要视觉补全的 0 基页索引，受 max-pages-per-doc 上限约束。 */
    private List<Integer> selectPages(ExtractionResult base, List<String> texts) {
        String trigger = props.getTrigger();
        List<Integer> out = new ArrayList<>();
        int cap = Math.max(props.getMaxPagesPerDoc(), 0);
        for (int p = 0; p < texts.size() && out.size() < cap; p++) {
            if (needsVision(base, texts.get(p), trigger)) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean needsVision(ExtractionResult base, String text, String trigger) {
        if (HybridExtractionProperties.TRIGGER_ALWAYS.equals(trigger)) {
            return true;
        }
        boolean lowText = text == null || text.length() < props.getMinTextLenPerPage();
        if (HybridExtractionProperties.TRIGGER_IMAGE_HEAVY.equals(trigger)) {
            return lowText;
        }
        return base.likelyScanned() && lowText;
    }

    /** 单页识别：取图（嵌入优先否则栅格化）→ 调视觉网关；异常/超限降级返回 null，不抛出。 */
    private String recognizePage(ExtractionCandidate candidate, int pageIndex) {
        VisionChatRequest.ImagePayload image = imageFor(candidate, pageIndex);
        if (image == null) {
            return null;
        }
        try {
            return visionChatGateway.chatWithImages(
                    new VisionChatRequest(visionProps.getPrompt(), List.of(image)));
        } catch (RuntimeException e) {
            log.warn(
                    "pdf-hybrid vision page {} failed, keep text layer: {}",
                    pageIndex,
                    e.getMessage());
            return null;
        }
    }

    /** 解析单页图像源：优先命中该页嵌入图，否则栅格化整页；字节超限或不可得返回 null。 */
    private VisionChatRequest.ImagePayload imageFor(ExtractionCandidate candidate, int pageIndex) {
        if (props.isPreferEmbeddedImages()) {
            ExtractedImage embedded = embeddedForPage(candidate, pageIndex);
            if (embedded != null && withinLimit(embedded.bytes())) {
                return new VisionChatRequest.ImagePayload(
                        Base64.getEncoder().encodeToString(embedded.bytes()),
                        effectiveMime(embedded.mimeType()));
            }
        }
        try {
            byte[] png =
                    pageRenderer.renderPageToPng(
                            candidate.filePath(), pageIndex, props.getRenderFallbackDpi());
            if (png != null && withinLimit(png)) {
                return new VisionChatRequest.ImagePayload(
                        Base64.getEncoder().encodeToString(png), DEFAULT_IMAGE_MIME);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("pdf-hybrid render page {} failed: {}", pageIndex, e.getMessage());
        }
        return null;
    }

    private ExtractedImage embeddedForPage(ExtractionCandidate candidate, int pageIndex) {
        if (candidate.embeddedImages() == null) {
            return null;
        }
        int pageNo = pageIndex + 1;
        return candidate.embeddedImages().stream()
                .filter(img -> img != null && Integer.valueOf(pageNo).equals(img.pageOrSeq()))
                .findFirst()
                .orElse(null);
    }

    private boolean withinLimit(byte[] bytes) {
        int max = props.getMaxBytesPerImage();
        return bytes != null && bytes.length > 0 && (max == UNLIMITED_BYTES || bytes.length <= max);
    }

    private String effectiveMime(String mime) {
        return (mime == null || mime.isBlank()) ? DEFAULT_IMAGE_MIME : mime;
    }

    /** 按 merge 方式把视觉文本并入文本层：replace_all 覆盖 / append 追加 / replace_lowtext 仅补低文本页。 */
    private String mergePage(String baseText, String visionText) {
        String merge = props.getMerge();
        if (ExtractionConfigMerge.REPLACE_ALL.equals(merge)) {
            return visionText;
        }
        if (ExtractionConfigMerge.APPEND.equals(merge)) {
            return (baseText == null || baseText.isBlank())
                    ? visionText
                    : baseText + "\n\n[视觉补全]\n" + visionText;
        }
        boolean lowText = baseText == null || baseText.length() < props.getMinTextLenPerPage();
        return lowText ? visionText : baseText;
    }

    private ExtractionResult assemble(
            ExtractionResult base, List<String> merged, List<String> notes) {
        List<String> warnings = new ArrayList<>(base.warnings());
        warnings.addAll(notes);
        return new ExtractionResult(
                merged,
                base.totalPages(),
                base.likelyScanned(),
                base.checksum(),
                "pdf-hybrid",
                List.copyOf(warnings),
                base.encrypted(),
                base.title(),
                base.author());
    }

    private ExtractionCacheKey buildKey(String checksum) {
        return new ExtractionCacheKey(
                checksum,
                ExtractionStrategyEnum.PDF_HYBRID.name(),
                visionProps.getModelName(),
                promptHash(visionProps.getPrompt()),
                props.isPreferEmbeddedImages() ? RENDER_MODE_EMBEDDED_PREF : RENDER_MODE_RASTER);
    }

    private String promptHash(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 合并方式字面量常量（与 {@link com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionConfig}
     * 对齐）。
     */
    private static final class ExtractionConfigMerge {
        private ExtractionConfigMerge() {}

        private static final String APPEND = "append";
        private static final String REPLACE_ALL = "replace_all";
    }
}
