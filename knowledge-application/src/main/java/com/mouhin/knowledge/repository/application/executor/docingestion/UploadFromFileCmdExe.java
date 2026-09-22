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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 从已组装文件创建文档用例执行器（app 层，分片上传完成后调用，不做文本提取）。
 *
 * <p>上传流程只做文件保存：计算文件 MD5 去重 → 建文档(UPLOADED) → 抽取图片元数据 → 发布创建事件。文本提取延迟到用户点击「解析预览」时由 {@link
 * PreviewFromDocumentQryExe} 触发。
 *
 * <p>出入参为 {@link Path} + 元数据，无 HTTP 传输类型，但当前由适配层分片上传完成流程直接调用。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class UploadFromFileCmdExe {

    private final DocumentIngestionSupport support;
    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentImageSupport documentImageSupport;

    public UploadFromFileCmdExe(
            DocumentIngestionSupport support,
            DocumentGateway documentGateway,
            DocumentExtractionGateway documentExtractionService,
            ApplicationEventPublisher eventPublisher,
            DocumentImageSupport documentImageSupport) {
        this.support = support;
        this.documentGateway = documentGateway;
        this.documentExtractionService = documentExtractionService;
        this.eventPublisher = eventPublisher;
        this.documentImageSupport = documentImageSupport;
    }

    public DocumentVO execute(
            Path assembledFile,
            String fileName,
            String ownerId,
            String departmentId,
            DocumentVisibilityEnum visibility,
            String allowedRoles,
            String tags,
            String category) {
        long fileSize;
        try {
            fileSize = Files.size(assembledFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get file size", e);
        }

        String checksum;
        try {
            checksum = documentExtractionService.calculateChecksum(assembledFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to calculate file checksum", e);
        }

        documentGateway
                .findByFileChecksum(checksum)
                .ifPresent(
                        existing -> {
                            throw new IllegalArgumentException(
                                    "Duplicate file detected. Existing document: "
                                            + existing.getDocumentKey());
                        });

        String documentKey = support.newDocumentKey();
        Document document = new Document();
        document.setDocumentKey(documentKey);
        document.setFileName(fileName);
        document.setFileType(support.detectFileType(assembledFile));
        document.setFileSize(fileSize);
        document.setStoragePath(assembledFile.toString());
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setVisibility(visibility != null ? visibility : DocumentVisibilityEnum.INTERNAL);
        document.setOwnerId(ownerId);
        document.setDepartmentId(departmentId);
        document.setAllowedRoles(allowedRoles);
        document.setTags(tags);
        document.setCategory(category);
        document.setChunkingConfig(ChunkingConfig.defaultConfig());
        document.setFileChecksum(checksum);
        document.validateForCreate();

        documentGateway.save(document);

        try {
            documentImageSupport.extractAndPersist(document, assembledFile);
        } catch (Exception imgEx) {
            log.warn("图片抽取失败，忽略以保上传主流程 [documentKey={}]: {}", documentKey, imgEx.getMessage());
        }

        eventPublisher.publishEvent(
                new DocumentCreatedEvent(
                        documentKey, fileName, ownerId, departmentId, LocalDateTime.now()));

        log.info(
                "Document uploaded from assembled file (pending preview & index): {} -> {}",
                documentKey,
                fileName);
        return DocumentConverter.toVO(document);
    }
}
