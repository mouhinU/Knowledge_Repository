package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.List;

/**
 * 视觉模型单轮对话请求（值对象）。
 *
 * <p>承载一段文本指令与若干待识别图像。图像以 base64 字符串 + MIME 承载（{@link String} 而非 {@code byte[]}，保持领域纯净化、 且 record
 * 默认 {@code equals/hashCode} 对 List 元素按值比较，无数组字段触发 java:S6218 之虞）。
 *
 * @param prompt 文本指令（如「逐字转录图中所有文字」）
 * @param images 图像列表，按调用顺序送入模型；可为空表示纯文本轮
 * @author mouhinU
 * @date 2026-09-23
 */
public record VisionChatRequest(String prompt, List<ImagePayload> images) {

    /**
     * 构造请求，{@code images} 为 null 时归一化为空列表。
     *
     * @param prompt 文本指令
     * @param images 图像列表（可空）
     * @return 视觉对话请求
     */
    public static VisionChatRequest of(String prompt, List<ImagePayload> images) {
        return new VisionChatRequest(prompt, images == null ? List.of() : List.copyOf(images));
    }

    /**
     * 送入视觉模型的单张图像载荷。
     *
     * @param base64Data 图像二进制的 base64 编码（不含 data 前缀）
     * @param mimeType 图像 MIME 类型（如 image/png、image/jpeg）
     */
    public record ImagePayload(String base64Data, String mimeType) {}
}
