package com.mouhin.knowledge.repository.client.dto;

/**
 * 页明细（解析预览，client 契约）
 *
 * <p>组件名与顺序与原 {@code DocumentIngestionApplicationService.PageDetail} 完全一致， 保证预览接口 JSON 不变。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
public record PageDetail(int pageNumber, int charCount, String preview) {}
