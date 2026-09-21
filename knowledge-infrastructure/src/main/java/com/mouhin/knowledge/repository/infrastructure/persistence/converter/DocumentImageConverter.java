package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentImageDO;

/**
 * 文档图片 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public final class DocumentImageConverter {

    private DocumentImageConverter() {}

    public static DocumentImage toDomain(DocumentImageDO doObj) {
        if (doObj == null) {
            return null;
        }
        DocumentImage domain = new DocumentImage();
        domain.setId(doObj.getId());
        domain.setAssetKey(doObj.getAssetKey());
        domain.setDocumentId(doObj.getDocumentId());
        domain.setDocumentKey(doObj.getDocumentKey());
        domain.setStoragePath(doObj.getStoragePath());
        domain.setSha256(doObj.getSha256());
        domain.setMimeType(doObj.getMimeType());
        domain.setPageNo(doObj.getPageNo());
        domain.setSeqOnPage(doObj.getSeqOnPage());
        domain.setWidth(doObj.getWidth());
        domain.setHeight(doObj.getHeight());
        domain.setByteSize(doObj.getByteSize());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static DocumentImageDO toDO(DocumentImage domain) {
        if (domain == null) {
            return null;
        }
        DocumentImageDO doObj = new DocumentImageDO();
        doObj.setId(domain.getId());
        doObj.setAssetKey(domain.getAssetKey());
        doObj.setDocumentId(domain.getDocumentId());
        doObj.setDocumentKey(domain.getDocumentKey());
        doObj.setStoragePath(domain.getStoragePath());
        doObj.setSha256(domain.getSha256());
        doObj.setMimeType(domain.getMimeType());
        doObj.setPageNo(domain.getPageNo());
        doObj.setSeqOnPage(domain.getSeqOnPage());
        doObj.setWidth(domain.getWidth());
        doObj.setHeight(domain.getHeight());
        doObj.setByteSize(domain.getByteSize());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
