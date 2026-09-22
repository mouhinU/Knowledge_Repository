package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PDF 混合解析（文本层 + 视觉补全）行为配置（{@code knowledge.extractor.vision.*}）—— Phase C，默认关闭。
 *
 * <p>与 {@link LlmVisionProperties}（{@code knowledge.llm.vision.*}，负责"连哪个模型、用什么密钥"）分工不同：
 * 本类负责"哪些页要过视觉、如何与文本层合并、花多少预算"。 {@code PdfHybridExtractionStrategy} 在 {@code enabled=false} 时
 * {@code supports()} 恒为 false， 且默认路由栈（{@code application/pdf → [PDF_BOX]}）不含 {@code
 * PDF_HYBRID}，双重保证默认不参与解析，零副作用。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.extractor.vision")
public class HybridExtractionProperties {

    /** 是否启用 PDF 混合策略（默认关，仅在配置路由栈显式引入 PDF_HYBRID 且本开关置 true 时生效）。 */
    private boolean enabled = false;

    /** 视觉增强触发条件：scanned_only（仅疑似扫描页）| image_heavy（低文本页）| always（全页）。 */
    private String trigger = "scanned_only";

    /** 优先使用文档嵌入图（否则由 PageRenderer 栅格化页面）。 */
    private boolean preferEmbeddedImages = true;

    /** 无嵌入图时页面栅格化分辨率（DPI）。 */
    private int renderFallbackDpi = 150;

    /** 判定"文本层稀疏页"（可能需要视觉补全）的每页最小字符数。 */
    private int minTextLenPerPage = 200;

    /** 单文档最多补全的页数，防止超大文档耗尽视觉预算。 */
    private int maxPagesPerDoc = 20;

    /** 单张图片送入视觉模型的字节上限（默认 4MB）。 */
    private int maxBytesPerImage = 4 * 1024 * 1024;

    /** 视觉结果与文本层合并方式：replace_lowtext | append | replace_all。 */
    private String merge = "replace_lowtext";

    /** 单次视觉调用超时（秒）。 */
    private int timeoutSeconds = 60;

    /** 单文档视觉增强总时长上限（秒）。 */
    private int maxTotalSecondsPerDoc = 300;

    /** 触发条件常量：仅疑似扫描页。 */
    public static final String TRIGGER_SCANNED_ONLY = "scanned_only";

    /** 触发条件常量：低文本页（图片占比高）。 */
    public static final String TRIGGER_IMAGE_HEAVY = "image_heavy";

    /** 触发条件常量：全页过视觉。 */
    public static final String TRIGGER_ALWAYS = "always";
}
