package com.mouhin.knowledge.repository.domain.model.valueobject;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;

/**
 * 图片检索命中项（领域值对象）。
 * <p>
 * 承载一张已入库配图及其来源文档的可读名称，供校对页缩略图网格展示与选图。
 * {@code sourceDocumentName} 为来源文档文件名（检索时联表带出），无匹配时为 {@code null}。
 * </p>
 *
 * @param image              配图领域实体
 * @param sourceDocumentName 来源文档文件名（展示用，可为空）
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public record DocumentImageHit(DocumentImage image, String sourceDocumentName) {
}
