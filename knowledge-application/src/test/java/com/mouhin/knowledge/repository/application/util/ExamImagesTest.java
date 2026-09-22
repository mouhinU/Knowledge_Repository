package com.mouhin.knowledge.repository.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 看图题配图 assetKey 解析工具单测（阶段 4）。
 *
 * <p>锁定跨链路共享的解析口径：正常数组按序保留、逐项 trim 且跳过空白、null / 空 / 脏 JSON 安全降级为 空列表（不抛异常），以及 {@code parseOrNull}
 * 把「无有效配图」归一化为 null 以便 {@code @JsonInclude} 省略字段。 纯函数测试，确定且离线。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@DisplayName("看图题配图 assetKey 解析 (ExamImages)")
class ExamImagesTest {

    @Test
    @DisplayName("正常数组按序保留")
    void parsesOrderedArray() {
        assertEquals(List.of("k1", "k2"), ExamImages.parseAssetKeys("[\"k1\",\"k2\"]"));
    }

    @Test
    @DisplayName("逐项 trim 且跳过空白项")
    void trimsAndSkipsBlank() {
        assertEquals(
                List.of("k1", "k2"), ExamImages.parseAssetKeys("[\" k1 \", \"\", null, \"k2\"]"));
    }

    @Test
    @DisplayName("null / 空串 / 空数组 → 空列表")
    void emptyInputsReturnEmpty() {
        assertTrue(ExamImages.parseAssetKeys(null).isEmpty());
        assertTrue(ExamImages.parseAssetKeys("   ").isEmpty());
        assertTrue(ExamImages.parseAssetKeys("[]").isEmpty());
    }

    @Test
    @DisplayName("脏 JSON 安全降级为空列表，不抛异常")
    void malformedReturnsEmpty() {
        assertTrue(ExamImages.parseAssetKeys("not-json").isEmpty());
        assertTrue(ExamImages.parseAssetKeys("{\"a\":1}").isEmpty());
    }

    @Test
    @DisplayName("parseOrNull：有效项返回列表、无有效项返回 null")
    void parseOrNullNormalizesEmpty() {
        assertEquals(List.of("k1"), ExamImages.parseOrNull("[\"k1\"]"));
        assertNull(ExamImages.parseOrNull("[]"));
        assertNull(ExamImages.parseOrNull(null));
        assertNull(ExamImages.parseOrNull("bad"));
    }
}
