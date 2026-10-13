package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ExtractionResult} ⇄ JSON 编解码（Phase C 持久缓存专用）。
 *
 * <p>刻意逐字段显式序列化而非依赖 Jackson 对 record 的反射绑定，避免受编译参数（{@code -parameters}）与全局 ObjectMapper 配置影响， 保证
 * {@code pageTexts} / {@code warnings} 等字段跨版本稳定还原。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
public final class ExtractionResultCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtractionResultCodec() {}

    /**
     * 序列化为 JSON 文本。
     *
     * @param result 提取结果
     * @return JSON 字符串
     */
    public static String encode(ExtractionResult result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("totalPages", result.totalPages());
        node.put("likelyScanned", result.likelyScanned());
        node.put("checksum", result.checksum());
        node.put("detectedFormat", result.detectedFormat());
        node.put("encrypted", result.encrypted());
        node.put("title", result.title());
        node.put("author", result.author());
        writeStrings(node.putArray("pageTexts"), result.pageTexts());
        writeStrings(node.putArray("warnings"), result.warnings());
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("编码 ExtractionResult 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 JSON 文本反序列化。
     *
     * @param json JSON 字符串
     * @return 提取结果
     */
    public static ExtractionResult decode(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return new ExtractionResult(
                    readStrings(node.path("pageTexts")),
                    node.path("totalPages").asInt(0),
                    node.path("likelyScanned").asBoolean(false),
                    text(node, "checksum"),
                    text(node, "detectedFormat"),
                    readStrings(node.path("warnings")),
                    node.path("encrypted").asBoolean(false),
                    text(node, "title"),
                    text(node, "author"));
        } catch (Exception e) {
            throw new IllegalStateException("解码 ExtractionResult 失败: " + e.getMessage(), e);
        }
    }

    private static void writeStrings(ArrayNode array, List<String> values) {
        if (values != null) {
            values.forEach(v -> array.add(v == null ? "" : v));
        }
    }

    private static List<String> readStrings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(el -> out.add(el.asText("")));
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }
}
