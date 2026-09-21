package com.mouhin.knowledge.repository.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

/**
 * 图片检索行数据对象（基础设施层内部投影）。
 *
 * <p>在全局图片检索联表查询中，除配图本体字段（继承 {@link DocumentImageDO}）外，附带来源文档的 可读文件名 {@code
 * sourceDocumentName}，供校对页缩略图网格展示与选图。仅用于 Mapper 结果映射， 不外泄到领域层。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@Getter
@Setter
public class DocumentImageSearchRowDO extends DocumentImageDO {

    /** 来源文档文件名（联表 kb_document 带出，可空） */
    private String sourceDocumentName;
}
