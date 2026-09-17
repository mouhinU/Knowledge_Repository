package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 从已组装文件创建文档用例执行器（app 层，分片上传完成后调用，不入库）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.uploadFromFile}。出入参为
 * {@link Path} + 元数据，无 HTTP 传输类型，但当前由适配层分片上传完成流程直接调用。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UploadFromFileCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(UploadFromFileCmdExe.class);

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ApplicationEventPublisher eventPublisher;

    public UploadFromFileCmdExe(DocumentIngestionSupport support,
                                ExtractionCacheHolder extractionCache,
                                DocumentGateway documentGateway,
                                DocumentExtractionGateway documentExtractionService,
                                ApplicationEventPublisher eventPublisher) {
        this.support = support;
        this.extractionCache = extractionCache;
        this.documentGateway = documentGateway;
        this.documentExtractionService = documentExtractionService;
        this.eventPublisher = eventPublisher;
    }

    public DocumentVO execute(Path assembledFile, String fileName, String ownerId,
                              String departmentId, DocumentVisibilityEnum visibility,
                              String allowedRoles, String tags, String category) {
        ExtractionResult result;
        try {
            long fileSize = Files.size(assembledFile);
            result = documentExtractionService.extractText(assembledFile, fileSize, fileName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text from assembled file", e);
        }

        documentGateway.findByFileChecksum(result.checksum()).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Duplicate file detected. Existing document: " + existing.getDocumentKey());
        });

        String documentKey = support.newDocumentKey();
        Document document = new Document();
        document.setDocumentKey(documentKey);
        document.setFileName(fileName);
        document.setFileType(support.detectFileType(assembledFile));
        document.setFileSize(result.pageTexts().stream().mapToLong(String::length).sum());
        document.setStoragePath(assembledFile.toString());
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setVisibility(visibility != null ? visibility : DocumentVisibilityEnum.INTERNAL);
        document.setOwnerId(ownerId);
        document.setDepartmentId(departmentId);
        document.setAllowedRoles(allowedRoles);
        document.setTags(tags);
        document.setCategory(category);
        document.setChunkingConfig(ChunkingConfig.defaultConfig());
        document.setTotalPages(result.totalPages());
        document.setFileChecksum(result.checksum());
        document.validateForCreate();

        documentGateway.save(document);

        eventPublisher.publishEvent(new DocumentCreatedEvent(
                documentKey, fileName, ownerId, departmentId, LocalDateTime.now()));

        extractionCache.put(documentKey, result);

        logger.info("Document uploaded from assembled file (pending index): {} -> {}", documentKey, fileName);
        return DocumentConverter.toVO(document);
    }
}
