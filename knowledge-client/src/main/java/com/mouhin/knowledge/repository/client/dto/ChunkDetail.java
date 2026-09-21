package com.mouhin.knowledge.repository.client.dto;

/**
 * 分块明细（解析预览，client 契约）
 *
 * <p>组件名与顺序与原 {@code DocumentIngestionApplicationService.ChunkDetail} 完全一致， 保证预览接口 JSON 不变。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public record ChunkDetail(
        int chunkIndex,
        int startPage,
        int endPage,
        int tokenCount,
        int charCount,
        String preview,
        String content) {}
