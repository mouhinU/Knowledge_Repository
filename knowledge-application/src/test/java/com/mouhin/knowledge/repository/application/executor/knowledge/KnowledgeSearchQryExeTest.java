package com.mouhin.knowledge.repository.application.executor.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.support.AuthorizedSearchSupport;
import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;
import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 知识库语义检索查询执行器单测：锁定空查询短路、默认参数归一（max=10 / minScore=0.5）、应用层分类二次过滤、 文档名补全与 null 归一 / score 4 位小数取整的
 * VO 组装语义。
 *
 * @author mouhinU
 * @date 2026-09-24 18:20:00
 */
@DisplayName("知识库语义检索查询执行器 (KnowledgeSearchQryExe)")
class KnowledgeSearchQryExeTest {

    private final AuthorizedSearchSupport authorizedSearch = mock(AuthorizedSearchSupport.class);
    private final PermissionDomainService permissionDomainService =
            mock(PermissionDomainService.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final KnowledgeSearchQryExe exe =
            new KnowledgeSearchQryExe(authorizedSearch, permissionDomainService, documentGateway);

    private SearchCmd cmd(String query) {
        SearchCmd c = new SearchCmd();
        c.setQuery(query);
        c.setUserId("u-1");
        c.setDepartmentId("d-1");
        c.setRoles("TEACHER");
        return c;
    }

    @Test
    @DisplayName("query 为 null / 空白 → 'Query must not be blank'，不触发检索")
    void blankQueryRejected() {
        assertThatThrownBy(() -> exe.execute(cmd(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Query must not be blank");
        assertThatThrownBy(() -> exe.execute(cmd("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Query must not be blank");
        verify(authorizedSearch, never())
                .searchAuthorized(any(), anyInt(), anyDouble(), any(), any(), any());
    }

    @Test
    @DisplayName("maxResults / minScore 缺省 → 归一为 10 / 0.5，Permission 由 cmd 组装透传")
    void defaultsNormalizedAndPermissionPassed() {
        when(permissionDomainService.buildFilterExpression(any())).thenReturn("filter-expr");
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());

        exe.execute(cmd("向量检索"));

        ArgumentCaptor<Permission> cap = ArgumentCaptor.forClass(Permission.class);
        verify(authorizedSearch)
                .searchAuthorized(
                        eq("向量检索"), eq(10), eq(0.5), eq("filter-expr"), eq(null), cap.capture());
        Permission permission = cap.getValue();
        assertThat(permission.getUserId()).isEqualTo("u-1");
        assertThat(permission.getDepartmentId()).isEqualTo("d-1");
        assertThat(permission.getRoles()).isEqualTo("TEACHER");
        assertThat(permission.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("显式 maxResults / minScore 透传生效（>0 才覆盖默认）")
    void explicitParamsWin() {
        when(permissionDomainService.buildFilterExpression(any())).thenReturn(null);
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(List.of());
        SearchCmd c = cmd("q");
        c.setMaxResults(3);
        c.setMinScore(0.8);

        exe.execute(c);

        verify(authorizedSearch)
                .searchAuthorized(eq("q"), eq(3), eq(0.8), eq(null), eq(null), any());
    }

    @Test
    @DisplayName("分类二次过滤：检索层漏网的异类结果在执行器出口被剔除")
    void categorySecondFilterApplied() {
        when(permissionDomainService.buildFilterExpression(any())).thenReturn(null);
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new SearchResult("a", "k1", "A.pdf", 1, 0, 0.9, "数学"),
                                new SearchResult("b", "k2", "B.pdf", 1, 0, 0.8, "物理")));
        SearchCmd c = cmd("q");
        c.setCategory("数学");

        SearchResponseVO vo = exe.execute(c);

        assertThat(vo.getTotalResults()).isEqualTo(1);
        assertThat(vo.getResults())
                .singleElement()
                .extracting(SearchResponseVO.ItemVO::getCategory)
                .isEqualTo("数学");
    }

    @Test
    @DisplayName("VO 组装：文档名补全（缺失回空串）、页码/分块号 null→0、score 取 4 位小数、category null→空串")
    void voAssemblyNormalizesNulls() {
        when(permissionDomainService.buildFilterExpression(any())).thenReturn(null);
        when(authorizedSearch.searchAuthorized(
                        anyString(), anyInt(), anyDouble(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new SearchResult("正文", "key-1", null, null, null, 0.123456789),
                                new SearchResult("越权文档", "ghost", null, 2, 1, 0.5)));
        Document doc = new Document();
        doc.setDocumentKey("key-1");
        doc.setFileName("教材.pdf");
        when(documentGateway.findByDocumentKey("key-1")).thenReturn(Optional.of(doc));
        when(documentGateway.findByDocumentKey("ghost")).thenReturn(Optional.empty());

        SearchResponseVO vo = exe.execute(cmd("q"));

        assertThat(vo.getQuery()).isEqualTo("q");
        assertThat(vo.getTotalResults()).isEqualTo(2);
        SearchResponseVO.ItemVO first = vo.getResults().get(0);
        assertThat(first.getDocumentName()).isEqualTo("教材.pdf");
        assertThat(first.getPageNumber()).isZero();
        assertThat(first.getChunkIndex()).isZero();
        assertThat(first.getScore()).isEqualTo(0.1235);
        assertThat(first.getCategory()).isEmpty();
        // TODO(行为可疑): 文档名补全失败仅回空串、不剔除该条——检索出口若含已删文档的脏向量残留，
        //   前端会看到无名条目；建议在 VO 组装处过滤或在向量侧同步清理。
        assertThat(vo.getResults().get(1).getDocumentName()).isEmpty();
    }
}
