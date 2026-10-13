package com.mouhin.knowledge.repository.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chat 角色 LLM 配置（{@code knowledge.llm.chat.*}）—— 三段式多模型治理之「对话」。
 *
 * <p>承载单一对话模型端点（OpenAI 兼容）与其独立超时预算。connect/read 作用于注入的 per-role HttpClient， call 为整次请求超时；{@code
 * streaming} 子块单独放宽 token 预算与超时，供黑板 Agent / 出卷逐 token 推送使用， 避免推理型模型把预算先耗在思考链上导致正式内容为空。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "knowledge.llm.chat")
public class LlmChatProperties {

    /** 供应商标识（仅用于状态展示与日志，不再驱动 Bean 切换）。 */
    private String provider = "deepseek";

    /** OpenAI 兼容 base-url。 */
    private String baseUrl = "https://api.deepseek.com";

    /** 访问密钥。 */
    private String apiKey = "";

    /** 模型名。 */
    private String modelName = "deepseek-chat";

    /** 采样温度。 */
    private double temperature = 0.7D;

    /** 最大生成 token 数（非流式）。 */
    private int maxTokens = 4096;

    /** TCP 建连超时（秒）。 */
    private int connectTimeoutSeconds = 5;

    /** 单次读超时（秒）。 */
    private int readTimeoutSeconds = 120;

    /** 整次调用超时（秒）。 */
    private int callTimeoutSeconds = 180;

    /** 流式对话子配置。 */
    private Streaming streaming = new Streaming();

    /** 流式对话独立预算（token 上限 + 超时）。 */
    @Getter
    @Setter
    public static class Streaming {

        /** 流式最大生成 token 数（需明显大于非流式 max-tokens）。 */
        private int maxTokens = 16384;

        /** 流式请求超时（秒）。 */
        private int timeoutSeconds = 300;
    }
}
