package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视觉角色模型配置（{@code knowledge.llm.vision.*}）—— Phase B，默认关闭。
 *
 * <p>与 chat / embedding 同为三段式命名空间下的一等角色，独立 base-url / 密钥 / 超时，走 OpenAI 兼容多模态协议。默认 {@code
 * enabled=false}：{@link
 * com.mouhin.knowledge.repository.infrastructure.extractor.VisionModelExtractionStrategy} 在关闭时
 * {@code supports()} 恒为 false，不参与路由，零副作用；仅在配置本地 PaddleOCR-CPU / 云端视觉端点后置 {@code true} 才启用视觉增强。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.llm.vision")
public class LlmVisionProperties {

    /** 是否启用视觉模型策略（默认关，Phase B 交付后即插即用但不开启）。 */
    private boolean enabled = false;

    /** 部署模式：local-cpu | cloud-api | local-gpu。 */
    private String mode = "local-cpu";

    /** 供应商协议标识（当前仅 openai-compatible）。 */
    private String provider = "openai-compatible";

    /** OpenAI 兼容 base-url（已含 /v1 等前缀）。 */
    private String baseUrl = "http://localhost:8080/v1";

    /** 访问密钥（本地 ollama/paddle 可留空或占位）。 */
    private String apiKey = "";

    /** 模型名。 */
    private String modelName = "paddleocr-vl";

    /** 采样温度（OCR 类识别建议 0）。 */
    private double temperature = 0.0D;

    /** 单次识别最大生成 token。 */
    private int maxTokens = 4096;

    /** 连接超时（秒）。 */
    private int connectTimeoutSeconds = 3;

    /** 读超时（秒）。 */
    private int readTimeoutSeconds = 30;

    /** 整调用超时（秒）。 */
    private int callTimeoutSeconds = 60;

    /** 送入模型的默认文本指令（提示词）。 */
    private String prompt = "请逐字转录图片中的所有文字，保持原有段落与顺序，仅输出文本内容本身，不要解释。";

    /** 优先使用文档嵌入图（否则由 PageRenderer 栅格化页面）。 */
    private boolean preferEmbeddedImages = true;
}
