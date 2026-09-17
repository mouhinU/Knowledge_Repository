package com.mouhin.knowledge.repository.client.dto;

/**
 * 自定义分块输入（前端提交，client 契约）
 *
 * <p>组件名与顺序与原 {@code DocumentIngestionApplicationService.CustomChunkInput} 完全一致，
 * 保证 {@code /api/admin/document/{documentKey}/index-custom} 请求体反序列化不变。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public record CustomChunkInput(int chunkIndex, int startPage, int endPage, String content) {
}
