package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;

/**
 * 文档 领域聚合根 → 视图对象 转换器（app 层）
 *
 * <p>映射与原 {@code DocumentAdminController.buildDocumentResponse} 一致： fileSize/totalPages
 * null→0，status/visibility 取枚举 name，tags/createdTime null→""。
 *
 * <p>例外登记（红线 #6「优先用 MapStruct」的例外）：VO 十字段中 {@code fileSize}/{@code totalPages}/{@code
 * visibility}/{@code tags}/{@code createdTime} 需 null 缺省、{@code status}/{@code visibility} 需枚举
 * {@code .name()}（{@code createdTime} 还需 {@code toString}），MapStruct {@code defaultValue} 无法落原语
 * {@code 0} 或调用方法，全靠 {@code expression} 会失去自动映射收益。故保留手写。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public final class DocumentConverter {

    private DocumentConverter() {}

    public static DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setDocumentKey(doc.getDocumentKey());
        vo.setFileName(doc.getFileName());
        vo.setFileSize(doc.getFileSize() != null ? doc.getFileSize() : 0L);
        vo.setTotalPages(doc.getTotalPages() != null ? doc.getTotalPages() : 0);
        vo.setStatus(doc.getStatus().name());
        vo.setVisibility(doc.getVisibility() != null ? doc.getVisibility().name() : "");
        vo.setOwnerId(doc.getOwnerId());
        vo.setDepartmentId(doc.getDepartmentId());
        vo.setTags(doc.getTags() != null ? doc.getTags() : "");
        vo.setCreatedTime(doc.getCreatedTime() != null ? doc.getCreatedTime().toString() : "");
        return vo;
    }
}
