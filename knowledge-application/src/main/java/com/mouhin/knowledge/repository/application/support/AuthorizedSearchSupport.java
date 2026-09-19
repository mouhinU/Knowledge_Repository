package com.mouhin.knowledge.repository.application.support;

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
import java.util.Optional;

/**
 * 授权检索支持组件（app 层，SEC-1 修复：把权限过滤真正落到检索出口）。
 * <p>
 * 项目此前依赖 {@link PermissionDomainService#buildFilterExpression(Permission)} 生成 Milvus
 * 过滤串再由 {@code MilvusVectorStoreService.buildFilter} 下推——但 buildFilter 当前直接
 * {@code return null}，表达式被完全丢弃；应用层此前也仅按 category 二次过滤、从不按 ACL 过滤，
 * 导致 INTERNAL/RESTRICTED/PRIVATE 文档在语义检索里对任意调用者泄露。
 * </p>
 * <p>
 * 本组件复用领域内已存在的 {@link PermissionDomainService#hasAccess} 判定，在向量检索返回后逐条
 * 按调用者 {@link Permission} 与 {@link Document} 的可见性/角色/所有者/部门过滤，实现 RBAC +
 * 文档级 ACL 在检索期的落地。超管短路放行。缺元数据（Document 不存在）按最小权限拒绝，与
 * {@code hasAccess} 语义保持一致。
 * </p>
 * <p>
 * 召回补偿：向量层先 over-fetch（{@code max × factor + extra}，硬上限 {@link #MAX_OVERFETCH}），
 * 过滤后再截断至 max，避免受限文档占据 top-N 名额导致最终结果不足。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class AuthorizedSearchSupport {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizedSearchSupport.class);

    /** over-fetch 倍率：受限文档密集时给足过滤后仍能凑够 maxResults 的空间。 */
    private static final int OVERFETCH_FACTOR = 3;
    /** over-fetch 绝对加量：小结果集下避免被倍率拉得太紧。 */
    private static final int OVERFETCH_EXTRA = 10;
    /** over-fetch 硬上限：保护 Milvus 侧 IO 与内存。 */
    private static final int MAX_OVERFETCH = 500;

    private final VectorStoreGateway vectorStoreGateway;
    private final DocumentGateway documentGateway;
    private final PermissionDomainService permissionDomainService;

    public AuthorizedSearchSupport(VectorStoreGateway vectorStoreGateway,
                                   DocumentGateway documentGateway,
                                   PermissionDomainService permissionDomainService) {
        this.vectorStoreGateway = vectorStoreGateway;
        this.documentGateway = documentGateway;
        this.permissionDomainService = permissionDomainService;
    }

    /**
     * 带权限过滤的语义检索。app 层所有检索入口统一走此方法。
     *
     * @param query      查询串
     * @param maxResults 最终返回上限（过滤后截断）
     * @param minScore   最小相似度阈值
     * @param filterExpr 领域层构造的 Milvus 过滤表达式（当前 buildFilter 未下推，保留兼容签名）
     * @param category   分类过滤（应用层二次过滤，与本组件正交）
     * @param permission 调用者权限上下文
     * @return 已按 ACL 过滤且不超过 maxResults 的结果列表
     */
    public List<SearchResult> searchAuthorized(String query, int maxResults, double minScore,
                                               String filterExpr, String category, Permission permission) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        int safeMax = Math.max(1, maxResults);
        int fetch = overFetchSize(safeMax);
        List<SearchResult> raw = vectorStoreGateway.search(query, fetch, minScore, filterExpr, category);
        List<SearchResult> kept = filterAccessible(raw, permission);
        if (kept.size() > safeMax) {
            kept = new ArrayList<>(kept.subList(0, safeMax));
        }
        return kept;
    }

    /**
     * 对已有检索结果做后置权限过滤（当调用方需要更细粒度控制 fetch/trim 时可单独使用）。
     */
    public List<SearchResult> filterAccessible(List<SearchResult> raw, Permission permission) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (permission == null) {
            logger.warn("检索权限过滤：permission 为 null，按最小权限拒绝全部结果");
            return List.of();
        }
        if (permission.isAdmin()) {
            return new ArrayList<>(raw);
        }
        List<SearchResult> kept = new ArrayList<>(raw.size());
        for (SearchResult sr : raw) {
            Optional<Document> docOpt = documentGateway.findByDocumentKey(sr.getDocumentId());
            if (docOpt.isEmpty()) {
                // 反向兜底：Document 元数据缺失时按不可访问处理，宁漏不越权。
                logger.debug("检索权限过滤：Document 元数据缺失，剔除 [documentKey={}]", sr.getDocumentId());
                continue;
            }
            Document doc = docOpt.get();
            boolean allowed = permissionDomainService.hasAccess(
                    permission,
                    doc.getVisibility(),
                    doc.getOwnerId(),
                    doc.getDepartmentId(),
                    doc.getAllowedRoles());
            if (allowed) {
                kept.add(sr);
            }
        }
        return kept;
    }

    /**
     * over-fetch 计算：{@code min(max × factor + extra, MAX_OVERFETCH)}，且不低于 max。
     */
    static int overFetchSize(int maxResults) {
        int w = Math.max(1, maxResults);
        long raw = (long) w * OVERFETCH_FACTOR + OVERFETCH_EXTRA;
        return (int) Math.min(Math.max(raw, w), MAX_OVERFETCH);
    }
}
