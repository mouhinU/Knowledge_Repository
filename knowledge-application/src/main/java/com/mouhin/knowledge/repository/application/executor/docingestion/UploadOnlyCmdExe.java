package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.application.converter.DocumentConverter;
import com.mouhin.knowledge.repository.client.dto.DocumentVO;
import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.gateway.DocumentExtractionGateway;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 仅上传并提取文本用例执行器（app 层，不入库）。
 * <p>
 * 逻辑原样迁移自 {@code DocumentIngestionApplicationService.uploadOnly}：空文件校验 → 临时落盘 →
 * 提取 → 去重 → 持久存储 → 建文档(UPLOADED) → 记录页数 → 发布创建事件 → 缓存提取结果。
 * 由适配层直接调用（出入参含 {@link MultipartFile}，传输耦合，不入对外契约）。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UploadOnlyCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(UploadOnlyCmdExe.class);

    private final DocumentIngestionSupport support;
    private final ExtractionCacheHolder extractionCache;
    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ApplicationEventPublisher eventPublisher;

    public UploadOnlyCmdExe(DocumentIngestionSupport support,
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

    @Transactional
    public DocumentVO execute(MultipartFile file, String ownerId, String departmentId,
                              DocumentVisibilityEnum visibility, String allowedRoles,
                              String tags, String category) {
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
            ExtractionResult result;
            try {
                result = documentExtractionService.extractText(
                        tempFile, file.getSize(), file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract text", e);
            }

            documentGateway.findByFileChecksum(result.checksum()).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Duplicate file detected. Existing document: " + existing.getDocumentKey());
            });

            Path permanentFile;
            try {
                permanentFile = support.copyToStorage(tempFile, file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to store uploaded file", e);
            }

            String documentKey = support.newDocumentKey();
            Document document = support.buildDocument(documentKey, file, permanentFile.toString(),
                    ownerId, departmentId, visibility, allowedRoles, tags, category,
                    ChunkingConfig.defaultConfig());
            document.validateForCreate();
            documentGateway.save(document);

            document.setTotalPages(result.totalPages());
            documentGateway.update(document);

            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    documentKey, file.getOriginalFilename(), ownerId, departmentId, LocalDateTime.now()));

            extractionCache.put(documentKey, result);

            logger.info("Document uploaded (pending index): {} -> {}", documentKey, document.getFileName());
            return DocumentConverter.toVO(document);

        } finally {
            support.deleteTempFile(tempFile);
        }
    }
}
