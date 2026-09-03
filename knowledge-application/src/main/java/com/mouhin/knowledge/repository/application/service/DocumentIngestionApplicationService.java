package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.event.DocumentCreatedEvent;
import com.mouhin.knowledge.repository.domain.event.DocumentProcessedEvent;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.repository.DocumentChunkRepository;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.domain.service.DocumentIngestionDomainService;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import com.mouhin.knowledge.repository.infrastructure.pdf.DocumentExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文档摄入应用服务
 * <p>
 * 编排文档上传 → 文本提取 → 分块 → 向量化 → 存储的完整流程。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentIngestionApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionApplicationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentExtractionService documentExtractionService;
    private final MilvusVectorStoreService vectorStoreService;
    private final DocumentIngestionDomainService ingestionDomainService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentIngestionApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentExtractionService documentExtractionService,
            MilvusVectorStoreService vectorStoreService,
            DocumentIngestionDomainService ingestionDomainService,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentExtractionService = documentExtractionService;
        this.vectorStoreService = vectorStoreService;
        this.ingestionDomainService = ingestionDomainService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 上传并处理文档
     *
     * @param file           文档文件
     * @param ownerId        所有者用户 ID
     * @param departmentId   所属部门 ID
     * @param visibility     可见性
     * @param allowedRoles   允许访问的角色（逗号分隔）
     * @param tags           标签（逗号分隔）
     * @param chunkingConfig 分块配置（null 使用默认）
     * @return 文档聚合根
     */
    @Transactional
    public Document uploadAndProcess(MultipartFile file,
                                     String ownerId,
                                     String departmentId,
                                     DocumentVisibilityEnum visibility,
                                     String allowedRoles,
                                     String tags,
                                     ChunkingConfig chunkingConfig) {
        // 1. 基本校验
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        // 2. 保存为临时文件
        Path tempFile;
        try {
            tempFile = saveToTemp(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save uploaded file", e);
        }

        try {
            // 3. 提取文本并检查去重
            DocumentExtractionService.ExtractionResult result;
            try {
                result = documentExtractionService.extractText(
                        tempFile, file.getSize(), file.getOriginalFilename());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract text from PDF", e);
            }

            documentRepository.findByFileChecksum(result.checksum()).ifPresent(existing -> {
                throw new IllegalArgumentException(
                        "Duplicate file detected. Existing document: " + existing.getDocumentKey());
            });

            // 4. 创建文档记录
            String documentKey = UUID.randomUUID().toString();
            Document document = buildDocument(documentKey, file, ownerId, departmentId,
                    visibility, allowedRoles, tags, chunkingConfig);
            document.validateForCreate();
            documentRepository.save(document);

            eventPublisher.publishEvent(new DocumentCreatedEvent(
                    documentKey, file.getOriginalFilename(), ownerId, departmentId, LocalDateTime.now()));

            // 5. 处理文档
            processDocument(document, result);

            return document;

        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * 处理文档：分块 → 向量化 → 存储
     */
    private void processDocument(Document document, DocumentExtractionService.ExtractionResult result) {
        try {
            document.markProcessing();
            documentRepository.update(document);

            if (result.likelyScanned()) {
                logger.warn("Document {} appears to be a scanned PDF. Text extraction may be incomplete.",
                        document.getDocumentKey());
            }

            // 分块
            List<DocumentChunk> chunks = ingestionDomainService.chunkDocument(
                    document, result.pageTexts(), document.getChunkingConfig());

            if (chunks.isEmpty()) {
                document.markFailed("No content extracted from PDF");
                documentRepository.update(document);
                return;
            }

            // 保存分块到关系数据库
            chunkRepository.saveBatch(chunks);

            // 向量化并存入 Milvus
            vectorStoreService.storeChunks(chunks);

            // 标记完成
            document.markIndexed(result.totalPages());
            document.setFileChecksum(result.checksum());
            documentRepository.update(document);

            eventPublisher.publishEvent(new DocumentProcessedEvent(
                    document.getDocumentKey(), document.getFileName(),
                    result.totalPages(), chunks.size(),
                    document.getOwnerId(), document.getDepartmentId(), LocalDateTime.now()));

            logger.info("Document {} processed successfully: {} pages, {} chunks",
                    document.getDocumentKey(), result.totalPages(), chunks.size());

        } catch (Exception e) {
            logger.error("Failed to process document {}: {}", document.getDocumentKey(), e.getMessage(), e);
            document.markFailed(e.getMessage());
            documentRepository.update(document);
        }
    }

    private Document buildDocument(String documentKey, MultipartFile file,
                                   String ownerId, String departmentId,
                                   DocumentVisibilityEnum visibility,
                                   String allowedRoles, String tags,
                                   ChunkingConfig chunkingConfig) {
        Document document = new Document();
        document.setDocumentKey(documentKey);
        document.setFileName(file.getOriginalFilename());
        document.setFileType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStatus(DocumentStatusEnum.UPLOADED);
        document.setVisibility(visibility != null ? visibility : DocumentVisibilityEnum.INTERNAL);
        document.setOwnerId(ownerId);
        document.setDepartmentId(departmentId);
        document.setAllowedRoles(allowedRoles);
        document.setTags(tags);
        document.setChunkingConfig(chunkingConfig != null ? chunkingConfig : ChunkingConfig.defaultConfig());
        return document;
    }

    private Path saveToTemp(MultipartFile file) throws IOException {
        Path tempDir = Files.createTempDirectory("knowledge-pdf-");
        String originalName = file.getOriginalFilename();
        String tempName = (originalName != null ? originalName : "upload") + ".tmp";
        Path tempFile = tempDir.resolve(tempName);
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
            if (tempFile.getParent() != null) {
                Files.deleteIfExists(tempFile.getParent());
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up temp file: {}", tempFile, e);
        }
    }
}
