package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.Objects;

/**
 * 解析结果持久缓存的复合主键（值对象）。
 *
 * <p>缓存粒度精确到「同一文件 + 同一策略 + 同一模型 + 同一提示词 + 同一渲染模式」：任一维度变化都应重算， 避免把旧模型 / 旧提示词的识别结果误命中到新配置。{@code
 * promptHash} 为提示词的摘要，避免把长文本存入索引列。
 *
 * @param checksum 文件内容摘要（MD5）
 * @param strategy 解析策略名（如 VISION / PDF_HYBRID）
 * @param modelName 使用的模型名（纯文本策略可为空串）
 * @param promptHash 提示词摘要（无提示词时为空串）
 * @param renderMode 页面渲染方式（embedded | raster | none）
 * @author mouhinU
 * @date 2026-09-23
 */
public record ExtractionCacheKey(
        String checksum, String strategy, String modelName, String promptHash, String renderMode) {

    public ExtractionCacheKey {
        checksum = require(checksum, "checksum");
        strategy = require(strategy, "strategy");
        modelName = nullToEmpty(modelName);
        promptHash = nullToEmpty(promptHash);
        renderMode = nullToEmpty(renderMode);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "ExtractionCacheKey." + field + " must not be blank");
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
