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
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传并立即处理文档用例执行器（app 层，提取 → 分块 → 向量化 → 存储）。
 *
 * <p>逻辑原样迁移自 {@code DocumentIngestionApplicationService.uploadAndProcess}。当前无适配层调用点， 作为完整用例保留。
 *
 * <p>CONC-3 / OPS-2：不再标注 {@code @Transactional}。本用例含文件解析与 {@link
 * DocumentIngestionSupport#processDocument} 的耗时向量化 IO，若被方法级事务包裹会在整个流程期间占用 HikariCP 连接。文档落库为单条原子写、
 * 创建事件亦无 {@code @TransactionalEventListener} 消费方，去掉事务不损失任何一致性。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UploadAndProcessCmdExe {

    private final DocumentIngestionSupport support;
    private final DocumentGateway documentGateway;
    private final DocumentExtractionGateway documentExtractionService;
    private final ApplicationEventPublisher eventPublisher;

    public UploadAndProcessCmdExe(
            DocumentIngestionSupport support,
            DocumentGateway documentGateway,
            DocumentExtractionGateway documentExtractionService,
            ApplicationEventPublisher eventPublisher) {
        this.support = support;
        this.documentGateway = documentGateway;
        this.documentExtractionService = documentExtractionService;
        this.eventPublisher = eventPublisher;
    }

    public DocumentVO execute(
            MultipartFile file,
            String ownerId,
            String departmentId,
            DocumentVisibilityEnum visibility,
            String allowedRoles,
            String tags,
            ChunkingConfig chunkingConfig) {
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
                result =
                        documentExtractionService.extractText(
                                tempFile, file.getSize(), file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract text from PDF", e);
            }

            documentGateway
                    .findByFileChecksum(result.checksum())
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
                            null,
                            chunkingConfig);
            document.validateForCreate();
            documentGateway.save(document);

            eventPublisher.publishEvent(
                    new DocumentCreatedEvent(
                            documentKey,
                            file.getOriginalFilename(),
                            ownerId,
                            departmentId,
                            LocalDateTime.now()));

            support.processDocument(document, result);

            return DocumentConverter.toVO(document);

        } finally {
            support.deleteTempFile(tempFile);
        }
    }
}
