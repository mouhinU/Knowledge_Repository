package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 仅上传文件用例执行器（app 层，不入库，不做文本提取）。
 *
 * <p>上传流程只做文件保存：空文件校验 → 临时落盘 → 计算文件 MD5 去重 → 持久存储 → 建文档(UPLOADED) → 抽取图片元数据 →
 * 发布创建事件。文本提取延迟到用户点击「解析预览」时由 {@link PreviewFromDocumentQryExe} 触发。
 *
 * <p>由适配层直接调用（出入参含 {@link MultipartFile}，传输耦合，不入对外契约）。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class UploadOnlyCmdExe {

    private final DocumentIngestionSupport support;
    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentImageSupport documentImageSupport;

    public UploadOnlyCmdExe(
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
            MultipartFile file,
            String ownerId,
            String departmentId,
            DocumentVisibilityEnum visibility,
            String allowedRoles,
            String tags,
            String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        Path tempFile;
        try {
            tempFile = support.saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            String checksum;
            try {
                checksum = documentExtractionService.calculateChecksum(tempFile);
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

            Path permanentFile;
            try {
                permanentFile = support.copyToStorage(tempFile, file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to store uploaded file", e);
            }

            String documentKey = support.newDocumentKey();
            Document document =
                    support.buildDocument(
                            documentKey,
                            file,
                            permanentFile.toString(),
                            ownerId,
                            departmentId,
                            visibility,
                            allowedRoles,
                            tags,
                            category,
                            ChunkingConfig.defaultConfig());
            document.setFileChecksum(checksum);
            document.validateForCreate();
            documentGateway.save(document);

            try {
                documentImageSupport.extractAndPersist(document, permanentFile);
            } catch (Exception imgEx) {
                log.warn("图片抽取失败，忽略以保上传主流程 [documentKey={}]: {}", documentKey, imgEx.getMessage());
            }

            eventPublisher.publishEvent(
                    new DocumentCreatedEvent(
                            documentKey,
                            file.getOriginalFilename(),
                            ownerId,
                            departmentId,
                            LocalDateTime.now()));

            log.info(
                    "Document uploaded (pending preview & index): {} -> {}",
                    documentKey,
                    document.getFileName());
            return DocumentConverter.toVO(document);

        } finally {
            support.deleteTempFile(tempFile);
        }
    }
}
