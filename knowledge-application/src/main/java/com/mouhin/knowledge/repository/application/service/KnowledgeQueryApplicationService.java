package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.repository.DocumentRepository;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import com.mouhin.knowledge.repository.infrastructure.milvus.MilvusVectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库查询应用服务
 * <p>
 * 编排语义检索 + 权限过滤 + 结果补全。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@Service
public class KnowledgeQueryApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeQueryApplicationService.class);

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final double DEFAULT_MIN_SCORE = 0.5;

    private final MilvusVectorStoreService vectorStoreService;
    private final PermissionDomainService permissionDomainService;
    private final DocumentRepository documentRepository;

    public KnowledgeQueryApplicationService(
            MilvusVectorStoreService vectorStoreService,
            PermissionDomainService permissionDomainService,
            DocumentRepository documentRepository) {
        this.vectorStoreService = vectorStoreService;
        this.permissionDomainService = permissionDomainService;
        this.documentRepository = documentRepository;
    }

    /**
     * 语义检索知识库
     *
     * @param query      查询文本
     * @param permission 用户权限上下文
     * @param maxResults 最大返回数量（null 使用默认 10）
     * @param minScore   最低相似度（null 使用默认 0.5）
     * @return 检索结果列表
     */
    public List<SearchResult> search(String query,
                                     Permission permission,
                                     Integer maxResults,
                                     Double minScore) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }

        int max = maxResults != null && maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS;
        double min = minScore != null && minScore > 0 ? minScore : DEFAULT_MIN_SCORE;

        // 构建权限过滤表达式
        String filterExpr = permissionDomainService.buildFilterExpression(permission);

        // 执行语义检索
        List<SearchResult> results = vectorStoreService.search(query, max, min, filterExpr);

        // 补全文档名称
        List<SearchResult> enrichedResults = new java.util.ArrayList<>(results.size());
        for (SearchResult sr : results) {
            String docName = documentRepository.findByDocumentKey(sr.getDocumentId())
                    .map(doc -> doc.getFileName())
                    .orElse("");
            enrichedResults.add(new SearchResult(
                    sr.getText(),
                    sr.getDocumentId(),
                    docName,
                    sr.getPageNumber(),
                    sr.getChunkIndex(),
                    sr.getScore()
            ));
        }

        logger.info("Search for user '{}' returned {} results", permission.getUserId(), enrichedResults.size());
        return enrichedResults;
    }
}
