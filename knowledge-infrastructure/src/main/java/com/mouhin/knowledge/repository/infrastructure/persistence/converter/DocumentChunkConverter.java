package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentChunkDO;

/**
 * 文档分块 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public final class DocumentChunkConverter {

    private DocumentChunkConverter() {
    }

    public static DocumentChunk toDomain(DocumentChunkDO doObj) {
        if (doObj == null) {
            return null;
        }

        DocumentChunk domain = new DocumentChunk();
        domain.setId(doObj.getId());
        domain.setChunkKey(doObj.getChunkKey());
        domain.setDocumentId(doObj.getDocumentId());
        domain.setDocumentKey(doObj.getDocumentKey());
        domain.setChunkIndex(doObj.getChunkIndex());
        domain.setStartPage(doObj.getStartPage());
        domain.setEndPage(doObj.getEndPage());
        domain.setContent(doObj.getContent());
        domain.setTokenCount(doObj.getTokenCount());
        domain.setVectorId(doObj.getVectorId());
        domain.setDepartmentId(doObj.getDepartmentId());
        domain.setVisibility(doObj.getVisibility());
        domain.setAllowedRoles(doObj.getAllowedRoles());
        domain.setOwnerId(doObj.getOwnerId());
        domain.setCategory(doObj.getCategory());
        domain.setCreatedTime(doObj.getCreateTime());

        return domain;
    }

    public static DocumentChunkDO toDO(DocumentChunk domain) {
        if (domain == null) {
            return null;
        }

        DocumentChunkDO doObj = new DocumentChunkDO();
        doObj.setId(domain.getId());
        doObj.setChunkKey(domain.getChunkKey());
        doObj.setDocumentId(domain.getDocumentId());
        doObj.setDocumentKey(domain.getDocumentKey());
        doObj.setChunkIndex(domain.getChunkIndex());
        doObj.setStartPage(domain.getStartPage());
        doObj.setEndPage(domain.getEndPage());
        doObj.setContent(domain.getContent());
        doObj.setTokenCount(domain.getTokenCount());
        doObj.setVectorId(domain.getVectorId());
        doObj.setDepartmentId(domain.getDepartmentId());
        doObj.setVisibility(domain.getVisibility());
        doObj.setAllowedRoles(domain.getAllowedRoles());
        doObj.setOwnerId(domain.getOwnerId());
        doObj.setCategory(domain.getCategory());
        doObj.setCreateTime(domain.getCreatedTime());

        return doObj;
    }
}
