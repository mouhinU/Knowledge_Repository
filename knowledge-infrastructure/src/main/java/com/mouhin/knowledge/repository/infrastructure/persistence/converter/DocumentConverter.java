package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentDO;

/**
 * 文档 DO ↔ 领域对象转换器
 *
 * @author mouhinU
 * @date 2026-09-02
 */
public final class DocumentConverter {

    private DocumentConverter() {}

    public static Document toDomain(DocumentDO doObj) {
        if (doObj == null) {
            return null;
        }

        Document domain = new Document();
        domain.setId(doObj.getId());
        domain.setDocumentKey(doObj.getDocumentKey());
        domain.setFileName(doObj.getFileName());
        domain.setFileType(doObj.getFileType());
        domain.setFileSize(doObj.getFileSize());
        domain.setStoragePath(doObj.getStoragePath());
        domain.setFileChecksum(doObj.getFileChecksum());
        domain.setTotalPages(doObj.getTotalPages());
        domain.setStatus(
                doObj.getStatus() != null ? DocumentStatusEnum.valueOf(doObj.getStatus()) : null);
        domain.setVisibility(
                doObj.getVisibility() != null
                        ? DocumentVisibilityEnum.valueOf(doObj.getVisibility())
                        : null);
        domain.setOwnerId(doObj.getOwnerId());
        domain.setDepartmentId(doObj.getDepartmentId());
        domain.setAllowedRoles(doObj.getAllowedRoles());
        domain.setSummary(doObj.getSummary());
        domain.setErrorMessage(doObj.getErrorMessage());
        domain.setTags(doObj.getTags());
        domain.setCategory(doObj.getCategory());
        domain.setCreatedTime(doObj.getCreateTime());
        domain.setUpdatedTime(doObj.getUpdateTime());

        if (doObj.getChunkMaxSize() != null) {
            ChunkingStrategyEnum strategy =
                    doObj.getChunkingStrategy() != null
                            ? ChunkingStrategyEnum.valueOf(doObj.getChunkingStrategy())
                            : ChunkingStrategyEnum.FIXED_SIZE;
            domain.setChunkingConfig(
                    new ChunkingConfig(
                            doObj.getChunkMaxSize(),
                            doObj.getChunkOverlap() != null ? doObj.getChunkOverlap() : 50,
                            strategy,
                            doObj.getRespectParagraph() != null
                                    ? doObj.getRespectParagraph()
                                    : true,
                            doObj.getRespectPage() != null ? doObj.getRespectPage() : true));
        }

        return domain;
    }

    public static DocumentDO toDO(Document domain) {
        if (domain == null) {
            return null;
        }

        DocumentDO doObj = new DocumentDO();
        doObj.setId(domain.getId());
        doObj.setDocumentKey(domain.getDocumentKey());
        doObj.setFileName(domain.getFileName());
        doObj.setFileType(domain.getFileType());
        doObj.setFileSize(domain.getFileSize());
        doObj.setStoragePath(domain.getStoragePath());
        doObj.setFileChecksum(domain.getFileChecksum());
        doObj.setTotalPages(domain.getTotalPages());
        doObj.setStatus(domain.getStatus() != null ? domain.getStatus().name() : null);
        doObj.setVisibility(domain.getVisibility() != null ? domain.getVisibility().name() : null);
        doObj.setOwnerId(domain.getOwnerId());
        doObj.setDepartmentId(domain.getDepartmentId());
        doObj.setAllowedRoles(domain.getAllowedRoles());
        doObj.setSummary(domain.getSummary());
        doObj.setErrorMessage(domain.getErrorMessage());
        doObj.setTags(domain.getTags());
        doObj.setCategory(domain.getCategory());
        doObj.setCreateTime(domain.getCreatedTime());
        doObj.setUpdateTime(domain.getUpdatedTime());

        ChunkingConfig config = domain.getChunkingConfig();
        if (config != null) {
            doObj.setChunkMaxSize(config.getMaxChunkSize());
            doObj.setChunkOverlap(config.getOverlapSize());
            doObj.setRespectParagraph(config.isRespectParagraphBoundary());
            doObj.setRespectPage(config.isRespectPageBoundary());
            doObj.setChunkingStrategy(
                    config.getStrategy() != null ? config.getStrategy().name() : "FIXED_SIZE");
        }

        return doObj;
    }
}
