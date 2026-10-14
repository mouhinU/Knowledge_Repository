package com.mouhin.knowledge.repository.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.constant.CohereProtocol;
import com.mouhin.knowledge.repository.domain.constant.HttpAuthSchemes;
import com.mouhin.knowledge.repository.domain.constant.HttpHeaderNames;
import com.mouhin.knowledge.repository.domain.constant.MimeTypes;
import dev.langchain4j.model.output.Response;
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
 * 简单 HTTP ScoringModel 实现，调用 Cohere 兼容的 reranker 端点。
 *
 * <p>请求格式（Cohere 兼容）：
 *
 * <pre>{@code
 * POST /v1/rerank
 * {
 *   "model": "bge-reranker-v2-m3",
 *   "query": "...",
 *   "documents": ["...", "..."],
 *   "top_n": 10
 * }
 * }</pre>
 *
 * <p>响应格式：
 *
 * <pre>{@code
 * {
 *   "results": [
 *     {"index": 0, "relevance_score": 0.95, "document": {"text": "..."}},
 *     {"index": 1, "relevance_score": 0.72, "document": {"text": "..."}}
 *   ]
 * }
 * }</pre>
 *
 * <p>使用 Jackson 进行 JSON 序列化/反序列化，避免手工拼接导致的边界问题。 支持 wkao/bge-reranker-v2-m3 等 Cohere 兼容的 reranker
 * 服务。
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
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;

    public SimpleHttpScoringModel(
            String baseUrl, String apiKey, String modelName, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.requestTimeout = timeout;
        this.objectMapper = new ObjectMapper();
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    private static List<Double> fillWithDefault(int count, double defaultValue) {
        List<Double> scores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            scores.add(defaultValue);
        }
        return scores;
    }

    @Override
    public Response<List<Double>> scoreAll(
            List<dev.langchain4j.data.segment.TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(List.of());
        }

        List<String> texts =
                segments.stream().map(dev.langchain4j.data.segment.TextSegment::text).toList();

        String json = buildRequestJson(query, texts);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/rerank"))
                        .header(HttpHeaderNames.CONTENT_TYPE, MimeTypes.APPLICATION_JSON)
                        .header(
                                HttpHeaderNames.AUTHORIZATION,
                                HttpAuthSchemes.BEARER_PREFIX + apiKey)
                        .timeout(requestTimeout)
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

            return Response.from(parseResponse(response.body(), texts.size()));

        } catch (IOException | InterruptedException e) {
            log.error("Reranker call failed: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Reranker call failed", e);
        }
    }

    private String buildRequestJson(String query, List<String> documents) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put(CohereProtocol.KEY_MODEL, modelName);
            root.put(CohereProtocol.KEY_QUERY, query);
            root.put(CohereProtocol.KEY_TOP_N, documents.size());
            ArrayNode docsArray = root.putArray(CohereProtocol.KEY_DOCUMENTS);
            for (String doc : documents) {
                docsArray.add(doc);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to build reranker request JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build reranker request", e);
        }
    }

    private List<Double> parseResponse(String responseBody, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode resultsNode = root.get(CohereProtocol.KEY_RESULTS);
            if (resultsNode == null || !resultsNode.isArray()) {
                log.warn("Reranker response missing 'results' array: {}", responseBody);
                return fillWithDefault(expectedCount, 0.5);
            }

            // 按 index 排序确保顺序正确（reranker 可能按分数降序返回）
            List<JsonNode> results = new ArrayList<>();
            resultsNode.forEach(results::add);
            results.sort(
                    (a, b) -> {
                        int idxA =
                                a.has(CohereProtocol.KEY_INDEX)
                                        ? a.get(CohereProtocol.KEY_INDEX).asInt()
                                        : 0;
                        int idxB =
                                b.has(CohereProtocol.KEY_INDEX)
                                        ? b.get(CohereProtocol.KEY_INDEX).asInt()
                                        : 0;
                        return Integer.compare(idxA, idxB);
                    });

            List<Double> scores = new ArrayList<>(expectedCount);
            for (JsonNode result : results) {
                JsonNode scoreNode = result.get(CohereProtocol.KEY_RELEVANCE_SCORE);
                if (scoreNode != null && scoreNode.isNumber()) {
                    scores.add(scoreNode.asDouble());
                } else {
                    log.warn("Missing or invalid relevance_score in reranker result");
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
        } catch (Exception e) {
            log.error("Failed to parse reranker response: {}", e.getMessage());
            return fillWithDefault(expectedCount, 0.5);
        }
    }
}
