package com.mouhin.knowledge.repository.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.gateway.VisionChatGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.VisionChatRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容端点的视觉对话网关实现（Phase B）。
 *
 * <p>以多模态 {@code messages[].content} 数组（{@code text} + 若干 {@code image_url} data-URI）请求 {@code
 * /chat/completions}，从 {@code choices[0].message.content} 取回识别文本。paddleocr-vl / qwen-vl / GLM-4V 等
 * 均为 OpenAI 兼容端点，故共用本实现，仅 base-url、模型名与鉴权不同。
 *
 * <p>沿用 {@link OpenAiCompatibleStreamingChatGateway} 的直连 JDK {@link HttpClient} 风格：由 {@code
 * VisionModelConfig} 注入<b>视觉角色专属</b>客户端以实现连接隔离；日志与异常<b>严禁</b>回显 base64 图像内容（红线 #4），
 * 失败仅记录状态码与截断后的响应体。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Slf4j
public class OpenAiCompatibleVisionChatGateway implements VisionChatGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";

    private final String chatCompletionsUrl;
    private final String apiKey;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param baseUrl OpenAI 兼容 base-url（已含 /v1 等前缀）
     * @param apiKey 访问密钥（本地端点可留空）
     * @param modelName 模型名
     * @param temperature 采样温度
     * @param maxTokens 最大生成 token 数
     * @param timeout 单次请求超时
     * @param httpClient 视觉角色专属 HTTP 客户端（连接隔离，由配置装配）
     */
    public OpenAiCompatibleVisionChatGateway(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            int maxTokens,
            Duration timeout,
            HttpClient httpClient) {
        this.chatCompletionsUrl = trimTrailingSlash(baseUrl) + "/chat/completions";
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.requestTimeout = timeout;
        this.httpClient = httpClient;
        log.info(
                "Initializing vision chat gateway: {} at {} (images-capable)",
                modelName,
                chatCompletionsUrl);
    }

    @Override
    public String chatWithImages(VisionChatRequest request) {
        String requestBody = buildRequestBody(request);
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(chatCompletionsUrl))
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpRequest httpRequest =
                builder.POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody, StandardCharsets.UTF_8))
                        .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            httpRequest,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException(
                        "视觉识别请求失败 HTTP " + status + ": " + abbreviate(response.body()));
            }
            String text = extractContent(response.body());
            if (text == null || text.isBlank()) {
                log.warn("Vision model returned empty content for model={}", modelName);
                return "";
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("视觉识别被中断: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("视觉识别调用异常: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(VisionChatRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);
        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", request.prompt() == null ? "" : request.prompt());
        for (VisionChatRequest.ImagePayload image : request.images()) {
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = imagePart.putObject("image_url");
            imageUrl.put("url", toDataUrl(image));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构造视觉请求体失败: " + e.getMessage(), e);
        }
    }

    private static String toDataUrl(VisionChatRequest.ImagePayload image) {
        String mime =
                (image.mimeType() == null || image.mimeType().isBlank())
                        ? "image/png"
                        : image.mimeType();
        return DATA_URL_PREFIX + mime + BASE64_MARKER + image.base64Data();
    }

    private String extractContent(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root.hasNonNull("error")) {
                String msg =
                        root.path("error").path("message").asText(root.path("error").toString());
                throw new RuntimeException("视觉模型返回错误: " + msg);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return "";
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "解析视觉响应失败: " + e.getMessage() + " body=" + abbreviate(body), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
