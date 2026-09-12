package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentStatusEnum;
import com.mouhin.knowledge.repository.domain.repository.DocumentChunkRepository;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档管理应用服务
 * <p>
 * 提供文档的查询、归档、删除等管理操作。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class DocumentManagementApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentManagementApplicationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final MilvusVectorStoreService vectorStoreService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentManagementApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            MilvusVectorStoreService vectorStoreService,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStoreService = vectorStoreService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 根据 documentKey 获取文档
     */
    public Document getByKey(String documentKey) {
        return documentRepository.findByDocumentKey(documentKey)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentKey));
    }

    /**
     * 按所有者分页查询文档
     */
    public List<Document> listByOwner(String ownerId, int page, int size) {
        return documentRepository.listByOwnerId(ownerId, page, size);
    }

    /**
     * 按部门分页查询文档
     */
    public List<Document> listByDepartment(String departmentId, int page, int size) {
        return documentRepository.listByDepartmentId(departmentId, page, size);
    }

    /**
     * 按状态查询文档
     */
    public List<Document> listByStatus(DocumentStatusEnum status) {
        return documentRepository.listByStatus(status);
    }

    /**
     * 查询全部文档
     */
    public List<Document> listAll() {
        return documentRepository.listAll();
    }

    /**
     * 获取知识库统计信息
     */
    public record KnowledgeStats(
            long totalDocuments,
            long indexedDocuments,
            long processingDocuments,
            long failedDocuments
    ) {
    }

    public KnowledgeStats getStats() {
        return new KnowledgeStats(
                documentRepository.countByStatus(DocumentStatusEnum.INDEXED)
                        + documentRepository.countByStatus(DocumentStatusEnum.PROCESSING)
                        + documentRepository.countByStatus(DocumentStatusEnum.FAILED)
                        + documentRepository.countByStatus(DocumentStatusEnum.UPLOADED),
                documentRepository.countByStatus(DocumentStatusEnum.INDEXED),
                documentRepository.countByStatus(DocumentStatusEnum.PROCESSING),
                documentRepository.countByStatus(DocumentStatusEnum.FAILED)
        );
    }

    /**
     * 归档文档（不再参与检索）
     */
    @Transactional
    public void archive(String documentKey) {
        Document document = getByKey(documentKey);
        document.archive();
        documentRepository.update(document);

        // 从 Milvus 删除向量（归档文档不参与检索）
        vectorStoreService.deleteByDocumentKey(documentKey);

        logger.info("Document {} archived", documentKey);
    }

    /**
     * 删除文档（级联清理所有关联数据）
     */
    @Transactional
    public void delete(String documentKey) {
        Document document = getByKey(documentKey);

        // 1. 删除 Milvus 向量
        vectorStoreService.deleteByDocumentKey(documentKey);

        // 2. 删除分块记录
        chunkRepository.deleteByDocumentId(document.getId());

        // 3. 删除文档记录
        documentRepository.deleteById(document.getId());

        logger.info("Document {} deleted", documentKey);
    }
}
