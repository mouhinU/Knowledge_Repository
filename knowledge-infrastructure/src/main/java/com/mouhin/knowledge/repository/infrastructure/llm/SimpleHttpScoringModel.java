package com.mouhin.knowledge.repository.infrastructure.llm;

import dev.langchain4j.model.scoring.ScoringModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 简单 HTTP ScoringModel 实现，调用 OpenAI 兼容的 reranker 端点。
 *
 * <p>请求格式（OpenAI 兼容）：
 *
 * <pre>{@code
 * POST /score
 * {
 *   "model": "bge-reranker-v2-m3",
 *   "query": "...",
 *   "documents": ["...", "..."]
 * }
 * }</pre>
 *
 * <p>响应格式：
 *
 * <pre>{@code
 * {
 *   "results": [
 *     {"index": 0, "relevance_score": 0.95},
 *     {"index": 1, "relevance_score": 0.72}
 *   ]
 * }
 * }</pre>
 *
 * <p>支持 Xenova/bge-reranker、Infinity、TEI 等 OpenAI 兼容的 reranker 服务。
 *
 * @author mouhinU
 * @date 2026-09-25
 */
@Slf4j
public class SimpleHttpScoringModel implements ScoringModel {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final HttpClient httpClient;

    public SimpleHttpScoringModel(
            String baseUrl, String apiKey, String modelName, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @Override
    public dev.langchain4j.model.output.Response<List<Double>> scoreAll(
            List<dev.langchain4j.data.segment.TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return dev.langchain4j.model.output.Response.from(List.of());
        }

        // 从 TextSegment 提取文本
        List<String> texts =
                segments.stream().map(dev.langchain4j.data.segment.TextSegment::text).toList();

        // 构建请求 JSON
        String json = buildRequestJson(query, texts);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/score"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error(
                        "Reranker HTTP error: status={}, body={}",
                        response.statusCode(),
                        response.body());
                throw new RuntimeException("Reranker HTTP error: " + response.statusCode());
            }

            return dev.langchain4j.model.output.Response.from(
                    parseResponse(response.body(), texts.size()));

        } catch (IOException | InterruptedException e) {
            log.error("Reranker call failed: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reranker call failed", e);
        }
    }

    private String buildRequestJson(String query, List<String> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escapeJson(modelName)).append("\",");
        sb.append("\"query\":\"").append(escapeJson(query)).append("\",");
        sb.append("\"documents\":[");
        for (int i = 0; i < documents.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(documents.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private List<Double> parseResponse(String responseBody, int expectedCount) {
        // 简单 JSON 解析：提取 results 数组中的 relevance_score
        // 格式: {"results":[{"index":0,"relevance_score":0.95},...]}
        List<Double> scores = new ArrayList<>(expectedCount);

        int resultsStart = responseBody.indexOf("\"results\"");
        if (resultsStart < 0) {
            log.warn("Reranker response missing 'results': {}", responseBody);
            // 兜底：返回均匀分数，避免 pipeline 崩溃
            for (int i = 0; i < expectedCount; i++) {
                scores.add(0.5);
            }
            return scores;
        }

        int arrayStart = responseBody.indexOf("[", resultsStart);
        int arrayEnd = responseBody.indexOf("]", arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            log.warn("Reranker response malformed: {}", responseBody);
            for (int i = 0; i < expectedCount; i++) {
                scores.add(0.5);
            }
            return scores;
        }

        String arrayContent = responseBody.substring(arrayStart + 1, arrayEnd);
        // 按 },{ 分割每个 result 对象
        String[] items = arrayContent.split("\\},\\{");
        for (String item : items) {
            int scoreIdx = item.indexOf("\"relevance_score\"");
            if (scoreIdx >= 0) {
                int colonIdx = item.indexOf(":", scoreIdx);
                int endIdx = item.indexOf(",", colonIdx);
                if (endIdx < 0) endIdx = item.indexOf("}", colonIdx);
                if (endIdx < 0) endIdx = item.length();
                String scoreStr = item.substring(colonIdx + 1, endIdx).trim();
                try {
                    scores.add(Double.parseDouble(scoreStr));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse reranker score: {}", scoreStr);
                    scores.add(0.5);
                }
            } else {
                scores.add(0.5);
            }
        }

        // 确保返回数量与输入一致
        while (scores.size() < expectedCount) {
            scores.add(0.5);
        }
        if (scores.size() > expectedCount) {
            scores = scores.subList(0, expectedCount);
        }

        return scores;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
