package com.mouhin.knowledge.repository.application.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 看图题配图 assetKey 解析工具（app 层共享）。
 *
 * <p>{@code kb_exam_question.images_json} 以 JSON 字符串数组形式存储教师在校对页绑定的图片句柄 （如 {@code
 * ["k1","k2"]}）。开考快照注入、错题本、成绩复核等多条链路都需把它解析为 assetKey 列表， 统一收敛到本工具，避免各处重复实现导致口径漂移。解析失败 /
 * 空白项一律安全降级为空列表或跳过， 绝不抛出异常阻断上层展示流程。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public final class ExamImages {

    private static final Logger logger = LoggerFactory.getLogger(ExamImages.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExamImages() {}

    /**
     * 解析 {@code images_json}（assetKey 字符串数组）为有序列表；逐个 trim 并跳过空白项。
     *
     * @param imagesJson 数据库 {@code images_json} 原文，可为 null / 空 / 脏数据
     * @return assetKey 列表；无有效项或解析失败返回空列表（不返回 null）
     */
    public static List<String> parseAssetKeys(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> raw =
                    OBJECT_MAPPER.readValue(imagesJson, new TypeReference<List<String>>() {});
            List<String> keys = new ArrayList<>();
            for (String k : raw) {
                if (k != null && !k.isBlank()) {
                    keys.add(k.trim());
                }
            }
            return keys;
        } catch (Exception e) {
            logger.warn("解析题目 images_json 失败，忽略配图: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析并「归一化」为可持久 / 可下发值：有效 assetKey 非空时返回列表，否则返回 {@code null} （便于配合
     * {@code @JsonInclude(NON_NULL)} 在无配图时省略字段，保持既有 JSON 载荷字节不变）。
     *
     * @param imagesJson 数据库 {@code images_json} 原文
     * @return 非空 assetKey 列表；无有效项时返回 null
     */
    public static List<String> parseOrNull(String imagesJson) {
        List<String> keys = parseAssetKeys(imagesJson);
        return keys.isEmpty() ? null : keys;
    }
}
