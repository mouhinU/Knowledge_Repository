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

    /** JSON 字段名：总页数。 */
    private static final String KEY_TOTAL_PAGES = "totalPages";

    /** JSON 字段名：是否疑似扫描件。 */
    private static final String KEY_LIKELY_SCANNED = "likelyScanned";

    /** JSON 字段名：内容校验和。 */
    private static final String KEY_CHECKSUM = "checksum";

    /** JSON 字段名：检测到的格式。 */
    private static final String KEY_DETECTED_FORMAT = "detectedFormat";

    /** JSON 字段名：是否加密。 */
    private static final String KEY_ENCRYPTED = "encrypted";

    /** JSON 字段名：标题。 */
    private static final String KEY_TITLE = "title";

    /** JSON 字段名：作者。 */
    private static final String KEY_AUTHOR = "author";

    /** JSON 字段名：逐页文本数组。 */
    private static final String KEY_PAGE_TEXTS = "pageTexts";

    /** JSON 字段名：告警信息数组。 */
    private static final String KEY_WARNINGS = "warnings";

    private ExtractionResultCodec() {}

    /**
     * 序列化为 JSON 文本。
     *
     * @param result 提取结果
     * @return JSON 字符串
     */
    public static String encode(ExtractionResult result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(KEY_TOTAL_PAGES, result.totalPages());
        node.put(KEY_LIKELY_SCANNED, result.likelyScanned());
        node.put(KEY_CHECKSUM, result.checksum());
        node.put(KEY_DETECTED_FORMAT, result.detectedFormat());
        node.put(KEY_ENCRYPTED, result.encrypted());
        node.put(KEY_TITLE, result.title());
        node.put(KEY_AUTHOR, result.author());
        writeStrings(node.putArray(KEY_PAGE_TEXTS), result.pageTexts());
        writeStrings(node.putArray(KEY_WARNINGS), result.warnings());
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
                    readStrings(node.path(KEY_PAGE_TEXTS)),
                    node.path(KEY_TOTAL_PAGES).asInt(0),
                    node.path(KEY_LIKELY_SCANNED).asBoolean(false),
                    text(node, KEY_CHECKSUM),
                    text(node, KEY_DETECTED_FORMAT),
                    readStrings(node.path(KEY_WARNINGS)),
                    node.path(KEY_ENCRYPTED).asBoolean(false),
                    text(node, KEY_TITLE),
                    text(node, KEY_AUTHOR));
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
