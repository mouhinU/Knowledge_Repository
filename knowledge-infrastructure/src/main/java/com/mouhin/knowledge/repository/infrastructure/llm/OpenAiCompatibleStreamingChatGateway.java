package com.mouhin.knowledge.repository.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.service.StreamingChatGateway;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI 兼容端点的流式对话网关实现。
 *
 * <p>直接以 {@code stream=true} 请求 {@code /chat/completions} 并逐行解析 SSE， 从而同时拿到 {@code
 * delta.content}（正常输出）与 {@code delta.reasoning_content / delta.reasoning}（思考链，取决于模型是否支持）。 dashscope
 * / deepseek / ollama 均为 OpenAI 兼容端点，故共用本实现，仅 base-url、 模型名与鉴权不同。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Slf4j
public class OpenAiCompatibleStreamingChatGateway implements StreamingChatGateway {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String chatCompletionsUrl;
    private final String apiKey;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param baseUrl OpenAI 兼容 base-url（已含 /v1 等前缀）
     * @param apiKey 访问密钥（ollama 可传占位符）
     * @param modelName 模型名
     * @param temperature 采样温度
     * @param maxTokens 最大生成 token 数
     * @param timeout 单次请求超时
     */
    public OpenAiCompatibleStreamingChatGateway(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature,
            int maxTokens,
            Duration timeout) {
        this.chatCompletionsUrl = trimTrailingSlash(baseUrl) + "/chat/completions";
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.requestTimeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        log.info("Initializing streaming chat gateway: {} at {}", modelName, chatCompletionsUrl);
    }

    @Override
    public String streamCompletion(
            String systemPrompt, String userPrompt, StreamDeltaHandler handler) {
        String requestBody = buildRequestBody(systemPrompt, userPrompt);
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(chatCompletionsUrl))
                        .timeout(requestTimeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        requestBody, StandardCharsets.UTF_8))
                        .build();

        StringBuilder output = new StringBuilder();
        String[] finishReason = {null};
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String errBody = readAll(response.body());
                throw new IOException("流式对话请求失败 HTTP " + status + ": " + abbreviate(errBody));
            }
            consumeStream(response.body(), handler, output, finishReason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式对话被中断: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("流式对话调用异常: " + e.getMessage(), e);
        }
        if (output.length() == 0) {
            String reason = finishReason[0];
            if ("length".equals(reason)) {
                throw new RuntimeException(
                        "模型思考链耗尽 token 预算（finish_reason=length），未产出正式内容，"
                                + "请调大 knowledge.llm.streaming.max-tokens 或改用非推理模型");
            }
            throw new RuntimeException(
                    "模型返回空内容（finish_reason=" + (reason == null ? "null" : reason) + "）");
        }
        return output.toString();
    }

    /** 逐行读取 SSE，解析增量并回调。 */
    private void consumeStream(
            InputStream body,
            StreamDeltaHandler handler,
            StringBuilder output,
            String[] finishReason)
            throws IOException {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    // 跳过心跳注释行（":" 开头）与事件分隔空行
                    continue;
                }
                String payload = trimmed.substring("data:".length()).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                processPayload(payload, handler, output, finishReason);
            }
        }
    }

    private void processPayload(
            String payload,
            StreamDeltaHandler handler,
            StringBuilder output,
            String[] finishReason) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (root.hasNonNull("error")) {
                String msg =
                        root.path("error").path("message").asText(root.path("error").toString());
                throw new RuntimeException("模型返回错误: " + msg);
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode first = choices.get(0);
            JsonNode finish = first.path("finish_reason");
            if (finish.isTextual() && !finish.asText().isEmpty()) {
                finishReason[0] = finish.asText();
            }
            JsonNode delta = first.path("delta");
            String content = delta.path("content").asText("");
            String reasoning =
                    firstNonEmpty(
                            delta.path("reasoning_content").asText(""),
                            delta.path("reasoning").asText(""));
            if (!reasoning.isEmpty() && handler != null) {
                handler.onDelta(KIND_THINKING, reasoning);
            }
            if (!content.isEmpty()) {
                output.append(content);
                if (handler != null) {
                    handler.onDelta(KIND_OUTPUT, content);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析流式增量失败，跳过该行: {}", abbreviate(payload));
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);
        root.put("stream", true);
        ArrayNode messages = root.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt == null ? "" : userPrompt);
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构造流式请求体失败: " + e.getMessage(), e);
        }
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
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

    private static String readAll(InputStream in) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
