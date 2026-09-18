package com.mouhin.knowledge.repository.application.executor.knowledge;

import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库语义检索查询执行器（app 层用例）
 *
 * <p>编排语义检索 + 权限过滤 + 应用层分类二次过滤 + 文档名补全，并组装为
 * {@link SearchResponseVO}。默认参数、score 4 位小数取整与空值归一逻辑与原
 * {@code KnowledgeQueryApplicationService} + 控制器 Map 组装完全一致。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class KnowledgeSearchQryExe {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeSearchQryExe.class);

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final double DEFAULT_MIN_SCORE = 0.5;

    private final VectorStoreGateway vectorStoreGateway;
    private final PermissionDomainService permissionDomainService;
    private final DocumentGateway documentGateway;

    public KnowledgeSearchQryExe(VectorStoreGateway vectorStoreGateway,
                                 PermissionDomainService permissionDomainService,
                                 DocumentGateway documentGateway) {
        this.vectorStoreGateway = vectorStoreGateway;
        this.permissionDomainService = permissionDomainService;
        this.documentGateway = documentGateway;
    }

    public SearchResponseVO execute(SearchCmd cmd) {
        String query = cmd.getQuery();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        Permission permission = new Permission(
                cmd.getUserId(), cmd.getDepartmentId(), cmd.getRoles(), cmd.isAdmin());

        int max = cmd.getMaxResults() != null && cmd.getMaxResults() > 0
                ? cmd.getMaxResults() : DEFAULT_MAX_RESULTS;
        double min = cmd.getMinScore() != null && cmd.getMinScore() > 0
                ? cmd.getMinScore() : DEFAULT_MIN_SCORE;

        String filterExpr = permissionDomainService.buildFilterExpression(permission);
        String category = cmd.getCategory();

        List<SearchResult> results = vectorStoreGateway.search(query, max, min, filterExpr, category);

        if (category != null && !category.isBlank()) {
            results = results.stream()
                    .filter(sr -> category.equals(sr.getCategory()))
                    .toList();
        }

        List<SearchResponseVO.ItemVO> items = new ArrayList<>(results.size());
        for (SearchResult sr : results) {
            String docName = documentGateway.findByDocumentKey(sr.getDocumentId())
                    .map(Document::getFileName)
                    .orElse("");
            SearchResponseVO.ItemVO item = new SearchResponseVO.ItemVO();
            item.setText(sr.getText());
            item.setDocumentKey(sr.getDocumentId());
            item.setDocumentName(docName);
            item.setPageNumber(sr.getPageNumber() != null ? sr.getPageNumber() : 0);
            item.setChunkIndex(sr.getChunkIndex() != null ? sr.getChunkIndex() : 0);
            item.setScore(Math.round(sr.getScore() * 10000.0) / 10000.0);
            item.setCategory(sr.getCategory() != null ? sr.getCategory() : "");
            items.add(item);
        }

        SearchResponseVO response = new SearchResponseVO();
        response.setQuery(query);
        response.setTotalResults(items.size());
        response.setResults(items);

        logger.info("Search for user '{}' returned {} results", cmd.getUserId(), items.size());
        return response;
    }
}
