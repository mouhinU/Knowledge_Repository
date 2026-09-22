package com.mouhin.knowledge.repository.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.DocumentGateway;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.domain.model.valueobject.SearchResult;
import com.mouhin.knowledge.repository.domain.service.PermissionDomainService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AuthorizedSearchSupport} 单元测试（SEC-1：检索出口权限后置过滤）。
 *
 * <p>仅 mock 两个网关（{@link VectorStoreGateway} / {@link DocumentGateway}）， 使用真实 {@link
 * PermissionDomainService} 走完整 ACL 判定，验证：超管短路、 INTERNAL/RESTRICTED/PRIVATE
 * 越权剔除、元数据缺失按最小权限拒绝、over-fetch 拉取 与截断、以及 {@code overFetchSize} 边界。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("AuthorizedSearchSupport 检索权限过滤")
class AuthorizedSearchSupportTest {

    private final VectorStoreGateway vectorStoreGateway = mock(VectorStoreGateway.class);
    private final DocumentGateway documentGateway = mock(DocumentGateway.class);
    private final PermissionDomainService permissionDomainService = new PermissionDomainService();

    private final AuthorizedSearchSupport support =
            new AuthorizedSearchSupport(
                    vectorStoreGateway, documentGateway, permissionDomainService);

    private static SearchResult result(String documentKey) {
        return new SearchResult(
                "chunk-" + documentKey, documentKey, "file-" + documentKey, 1, 0, 0.9);
    }

    private static Document doc(
            DocumentVisibilityEnum visibility,
            String ownerId,
            String departmentId,
            String allowedRoles) {
        Document d = new Document();
        d.setVisibility(visibility);
        d.setOwnerId(ownerId);
        d.setDepartmentId(departmentId);
        d.setAllowedRoles(allowedRoles);
        return d;
    }

    @Test
    @DisplayName("空白查询抛 IllegalArgumentException")
    void blankQueryThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        support.searchAuthorized(
                                "  ", 5, 0.3, null, null, new Permission("u1", "d", "R", false)));
    }

    @Test
    @DisplayName("permission 为 null 时按最小权限拒绝全部结果")
    void nullPermissionDeniesAll() {
        List<SearchResult> kept = support.filterAccessible(List.of(result("k1")), null);
        assertEquals(0, kept.size());
    }

    @Test
    @DisplayName("超级管理员短路放行，且不查文档元数据")
    void adminBypassesWithoutLookup() {
        Permission admin = new Permission("u-admin", "dept-x", "ADMIN", true);
        List<SearchResult> kept =
                support.filterAccessible(List.of(result("k1"), result("k2")), admin);
        assertEquals(2, kept.size());
        verify(documentGateway, never()).findByDocumentKey(anyString());
    }

    @Nested
    @DisplayName("非超管按 ACL 逐条过滤")
    class AccessControl {

        @Test
        @DisplayName("PUBLIC 放行、跨部门 INTERNAL 剔除、越权 PRIVATE 剔除")
        void mixedVisibility() {
            Permission u = new Permission("u1", "dept-a", "STUDENT", false);
            when(documentGateway.findByDocumentKey("public-doc"))
                    .thenReturn(Optional.of(doc(DocumentVisibilityEnum.PUBLIC, "o", "d", null)));
            when(documentGateway.findByDocumentKey("internal-other"))
                    .thenReturn(
                            Optional.of(doc(DocumentVisibilityEnum.INTERNAL, "o", "dept-b", null)));
            when(documentGateway.findByDocumentKey("private-other"))
                    .thenReturn(
                            Optional.of(
                                    doc(
                                            DocumentVisibilityEnum.PRIVATE,
                                            "someone-else",
                                            "dept-a",
                                            null)));

            List<SearchResult> kept =
                    support.filterAccessible(
                            List.of(
                                    result("public-doc"),
                                    result("internal-other"),
                                    result("private-other")),
                            u);

            assertEquals(1, kept.size());
            assertEquals("public-doc", kept.get(0).getDocumentId());
        }

        @Test
        @DisplayName("RESTRICTED 角色命中放行、未命中剔除")
        void restrictedRoleGate() {
            Permission u = new Permission("u1", "dept-a", "TEACHER", false);
            when(documentGateway.findByDocumentKey("r-allow"))
                    .thenReturn(
                            Optional.of(
                                    doc(
                                            DocumentVisibilityEnum.RESTRICTED,
                                            "o",
                                            "d",
                                            "ADMIN,TEACHER")));
            when(documentGateway.findByDocumentKey("r-deny"))
                    .thenReturn(
                            Optional.of(
                                    doc(DocumentVisibilityEnum.RESTRICTED, "o", "d", "MANAGER")));

            List<SearchResult> kept =
                    support.filterAccessible(List.of(result("r-allow"), result("r-deny")), u);

            assertEquals(1, kept.size());
            assertEquals("r-allow", kept.get(0).getDocumentId());
        }

        @Test
        @DisplayName("文档元数据缺失按不可访问剔除（宁漏不越权）")
        void missingDocumentDenied() {
            Permission u = new Permission("u1", "dept-a", "STUDENT", false);
            when(documentGateway.findByDocumentKey("ghost")).thenReturn(Optional.empty());

            List<SearchResult> kept = support.filterAccessible(List.of(result("ghost")), u);

            assertEquals(0, kept.size());
        }
    }

    @Test
    @DisplayName("searchAuthorized 走 over-fetch 拉取并截断至 maxResults")
    void overFetchThenTrim() {
        Permission admin = new Permission("u-admin", "dept-x", "ADMIN", true);
        int max = 2;
        int expectedFetch = AuthorizedSearchSupport.overFetchSize(max);
        List<SearchResult> raw = new ArrayList<>();
        for (int i = 0; i < expectedFetch; i++) {
            raw.add(result("k" + i));
        }
        when(vectorStoreGateway.search(anyString(), eq(expectedFetch), anyDouble(), any(), any()))
                .thenReturn(raw);

        List<SearchResult> out = support.searchAuthorized("q", max, 0.3, null, null, admin);

        assertEquals(max, out.size());
        verify(vectorStoreGateway).search(eq("q"), eq(expectedFetch), eq(0.3), any(), any());
    }

    @Nested
    @DisplayName("overFetchSize 边界")
    class OverFetchBoundary {

        @Test
        @DisplayName("小结果集：max*3+10")
        void smallMax() {
            assertEquals(13, AuthorizedSearchSupport.overFetchSize(1));
            assertEquals(40, AuthorizedSearchSupport.overFetchSize(10));
        }

        @Test
        @DisplayName("命中硬上限 500")
        void hitsCap() {
            assertEquals(500, AuthorizedSearchSupport.overFetchSize(200));
        }

        @Test
        @DisplayName("非法/零 max 归一下限且不低于 max")
        void clampLow() {
            assertEquals(13, AuthorizedSearchSupport.overFetchSize(0));
            assertEquals(13, AuthorizedSearchSupport.overFetchSize(-5));
        }

        @Test
        @DisplayName("超上限输入被夹到 500")
        void aboveCap() {
            assertEquals(500, AuthorizedSearchSupport.overFetchSize(1000));
        }
    }
}
