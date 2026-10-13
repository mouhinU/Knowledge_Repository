package com.mouhin.knowledge.repository.web.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.web.filter.TenantResolverFilter;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 管理端令牌鉴权过滤器单元测试。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("AdminTokenAuthFilter 集中鉴权")
class AdminTokenAuthFilterTest {

    private AdminAuthServiceI adminAuthService;
    private AdminTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        adminAuthService = mock(AdminAuthServiceI.class);
        filter = new AdminTokenAuthFilter(adminAuthService);
    }

    private MockHttpServletRequest protectedRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/admin/user");
        req.setMethod("GET");
        return req;
    }

    @Test
    @DisplayName("受保护端点缺少令牌：401 且不放行、不触发校验")
    void missingToken_unauthorized() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(req, resp);
        verify(adminAuthService, never()).validateToken(anyString());
    }

    @Test
    @DisplayName("受保护端点携带有效令牌：放行并回填身份属性")
    void validToken_proceeds() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        req.addHeader(AdminTokenAuthFilter.TOKEN_HEADER, "good-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(adminAuthService.validateToken("good-token"))
                .thenReturn(SingleResponse.of(new AdminPrincipalDTO("k1", "admin", true)));

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest(), "有效令牌应继续过滤器链");
        assertNotNull(req.getAttribute(AdminTokenAuthFilter.PRINCIPAL_ATTRIBUTE));
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("令牌无效（校验返回空身份）：401 且不放行")
    void invalidToken_unauthorized() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        req.addHeader(AdminTokenAuthFilter.TOKEN_HEADER, "bad-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(adminAuthService.validateToken("bad-token")).thenReturn(SingleResponse.of(null));

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("有效但非管理员令牌：仍拒绝访问管理端")
    void nonAdminToken_unauthorized() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        req.addHeader(AdminTokenAuthFilter.TOKEN_HEADER, "user-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(adminAuthService.validateToken("user-token"))
                .thenReturn(SingleResponse.of(new AdminPrincipalDTO("k1", "user", false)));

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    @DisplayName("SSE 端点通过 access_token 查询参数携带令牌：应被识别")
    void sseQueryParam_token() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/api/agent/exam/progress/sess-1");
        req.setMethod("GET");
        req.setParameter("access_token", "stream-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(adminAuthService.validateToken("stream-token"))
                .thenReturn(SingleResponse.of(new AdminPrincipalDTO("k1", "admin", true)));

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest());
        verify(adminAuthService).validateToken("stream-token");
    }

    @Test
    @DisplayName("考生侧 / 登录 / 健康 / Prometheus 探针 / 平台端路径放行，不做管理端校验")
    void publicPaths_skipped() throws Exception {
        assertSkipped("/api/student/auth/login");
        assertSkipped("/api/exam/submit");
        assertSkipped("/api/admin/auth/login");
        assertSkipped("/api/admin/auth/logout");
        assertSkipped("/actuator/health");
        assertSkipped("/actuator/prometheus");
        assertSkipped("/admin/index.html");
        // P3-T1 / T-4 方案 B：平台端点由独立 PlatformTokenAuthFilter 专属管辖，本过滤器必须放行（路径互斥）
        assertSkipped("/api/platform/tenants");
        assertSkipped("/api/platform/tenants/acme/suspend");
    }

    @Test
    @DisplayName("非白名单 actuator 端点（如 /actuator/metrics）仍需管理端令牌")
    void nonWhitelistedActuator_requiresAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/actuator/metrics");
        req.setMethod("GET");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(req, resp);
    }

    private void assertSkipped(String uri) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        req.setMethod("GET");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest(), uri + " 应直接放行");
        assertNull(req.getAttribute(AdminTokenAuthFilter.PRINCIPAL_ATTRIBUTE));
    }

    @Test
    @DisplayName("P1-T8：principal 携带非空 tenantKey 时写入 knowledge.tenant.claim 请求属性供下游过滤器读取")
    void validTokenWithTenant_populatesClaimAttribute() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        req.addHeader(AdminTokenAuthFilter.TOKEN_HEADER, "acme-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        AdminPrincipalDTO principal =
                new AdminPrincipalDTO("k1", "admin", true, null, List.of("ADMIN"), "acme");
        when(adminAuthService.validateToken("acme-token")).thenReturn(SingleResponse.of(principal));

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest());
        assertEquals("acme", req.getAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE));
    }

    @Test
    @DisplayName("P1-T8：旧 3 参 principal（tenantKey=null）不写 claim 属性，向后兼容窗口不打破")
    void validTokenWithoutTenant_skipsClaimAttribute() throws Exception {
        MockHttpServletRequest req = protectedRequest();
        req.addHeader(AdminTokenAuthFilter.TOKEN_HEADER, "legacy-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(adminAuthService.validateToken("legacy-token"))
                .thenReturn(SingleResponse.of(new AdminPrincipalDTO("k1", "admin", true)));

        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest());
        assertNull(req.getAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE));
    }
}
