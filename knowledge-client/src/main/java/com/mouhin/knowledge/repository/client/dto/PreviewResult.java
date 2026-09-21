package com.mouhin.knowledge.repository.client.dto;

import java.util.List;

/**
 * 解析预览结果（client 契约）
 *
 * <p>组件名与顺序与原 {@code DocumentIngestionApplicationService.PreviewResult} 完全一致， 保证 {@code
 * /api/admin/document/{documentKey}/preview} 返回 JSON 不变。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public record PreviewResult(
        String fileName,
        String format,
        int totalPages,
        int extractedSections,
        boolean likelyScanned,
        String checksum,
        int totalChunks,
        List<PageDetail> pages,
        List<ChunkDetail> chunks) {}
