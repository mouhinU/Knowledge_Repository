package com.mouhin.knowledge.repository.application.executor.knowledge;

import com.mouhin.knowledge.repository.application.support.AuthorizedSearchSupport;
import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库语义检索查询执行器（app 层用例）
 *
 * <p>编排语义检索 + <b>权限过滤</b> + 文档名补全，并组装为 {@link SearchResponseVO}。SEC-1 修复：权限过滤由 {@link
 * AuthorizedSearchSupport} 在检索出口通过领域 {@code PermissionDomainService.hasAccess} 逐条落地。分类过滤已在 Milvus
 * 层通过 {@code IsEqualTo} 谓词下推，应用层不再二次过滤。文档名补全走批量查询（单次 SQL IN），消除 N+1。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class KnowledgeSearchQryExe {

    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final double DEFAULT_MIN_SCORE = 0.65;

    /** 相似度分显示保留 4 位小数的放大倍数。 */
    private static final double SCORE_SCALE_4DP = 10000.0;

    private final AuthorizedSearchSupport authorizedSearch;
    private final PermissionDomainService permissionDomainService;
    private final DocumentGateway documentGateway;

    public KnowledgeSearchQryExe(
            AuthorizedSearchSupport authorizedSearch,
            PermissionDomainService permissionDomainService,
            DocumentGateway documentGateway) {
        this.authorizedSearch = authorizedSearch;
        this.permissionDomainService = permissionDomainService;
        this.documentGateway = documentGateway;
    }

    public SearchResponseVO execute(SearchCmd cmd) {
        String query = cmd.getQuery();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        Permission permission =
                new Permission(
                        cmd.getUserId(), cmd.getDepartmentId(), cmd.getRoles(), cmd.isAdmin());

        int max =
                cmd.getMaxResults() != null && cmd.getMaxResults() > 0
                        ? cmd.getMaxResults()
                        : DEFAULT_MAX_RESULTS;
        double min =
                cmd.getMinScore() != null && cmd.getMinScore() > 0
                        ? cmd.getMinScore()
                        : DEFAULT_MIN_SCORE;

        // filterExpr 保留兼容签名；真正生效的权限过滤在 AuthorizedSearchSupport 内部
        // 通过 hasAccess 后置逐条判定（Milvus 下推留待未来 buildFilter 支持 MetadataFilter 时启用）。
        String filterExpr = permissionDomainService.buildFilterExpression(permission);
        String category = cmd.getCategory();

        List<SearchResult> results =
                authorizedSearch.searchAuthorized(
                        query, max, min, filterExpr, category, permission);

        // 分类过滤已在 Milvus 层通过 IsEqualTo("category", category) 下推，此处不再二次过滤

        // 批量加载文档名（单次 SQL IN 查询），消除 N+1
        List<String> documentKeys =
                results.stream().map(SearchResult::getDocumentId).distinct().toList();
        Map<String, Document> docMap = documentGateway.findByDocumentKeys(documentKeys);

        List<SearchResponseVO.ItemVO> items = new ArrayList<>(results.size());
        for (SearchResult sr : results) {
            String docName =
                    docMap.containsKey(sr.getDocumentId())
                            ? docMap.get(sr.getDocumentId()).getFileName()
                            : "";
            SearchResponseVO.ItemVO item = new SearchResponseVO.ItemVO();
            item.setText(sr.getText());
            item.setDocumentKey(sr.getDocumentId());
            item.setDocumentName(docName);
            item.setPageNumber(sr.getPageNumber() != null ? sr.getPageNumber() : 0);
            item.setChunkIndex(sr.getChunkIndex() != null ? sr.getChunkIndex() : 0);
            item.setScore(Math.round(sr.getScore() * SCORE_SCALE_4DP) / SCORE_SCALE_4DP);
            item.setCategory(sr.getCategory() != null ? sr.getCategory() : "");
            items.add(item);
        }

        SearchResponseVO response = new SearchResponseVO();
        response.setQuery(query);
        response.setTotalResults(items.size());
        response.setResults(items);

        log.info("Search for user '{}' returned {} results", cmd.getUserId(), items.size());
        return response;
    }
}
