package com.mouhin.knowledge.repository.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.gateway.PlatformIdpTokenService;
import com.mouhin.knowledge.repository.domain.gateway.PlatformJwtService;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link PlatformTokenAuthFilter} 平台端令牌鉴权过滤器单测（P3-T1 / T-4 方案 B）。
 *
 * <p>锁定：仅拦 {@code /api/platform/**}（其余路径 shouldNotFilter 放行）、合法令牌落 principal attr 并放行、 缺令牌 / 无效令牌
 * 401 + {@code PLATFORM_UNAUTHORIZED}、支持 {@code X-Platform-Token} 与 {@code Authorization: Bearer}
 * 两种载体。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("PlatformTokenAuthFilter 平台端鉴权 (P3-T1)")
class PlatformTokenAuthFilterTest {

    private PlatformJwtService jwtService;
    private PlatformTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = mock(PlatformJwtService.class);
        filter = new PlatformTokenAuthFilter(jwtService);
    }

    private MockHttpServletRequest platformRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/tenants");
        request.setRequestURI("/api/platform/tenants");
        return request;
    }

    private boolean runChain(MockHttpServletRequest request, MockHttpServletResponse response) {
        boolean[] ran = {false};
        FilterChain chain = (rq, rs) -> ran[0] = true;
        try {
            filter.doFilter(request, response, chain);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ran[0];
    }

    @Test
    @DisplayName("非 /api/platform/** 路径 shouldNotFilter 放行（不触碰租户管理员端点）")
    void skipsNonPlatformPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        request.setRequestURI("/api/documents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ran = runChain(request, response);

        assertThat(ran).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("合法 X-Platform-Token → 放行且落 principal attr")
    void validHeaderTokenPasses() {
        when(jwtService.verify("good-token"))
                .thenReturn(
                        Optional.of(
                                new PlatformTokenPayload(
                                        "op-1",
                                        "platform-admin",
                                        Instant.now().plusSeconds(3600))));
        MockHttpServletRequest request = platformRequest();
        request.addHeader(PlatformTokenAuthFilter.TOKEN_HEADER, "good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ran = runChain(request, response);

        assertThat(ran).isTrue();
        Object attr = request.getAttribute(PlatformTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(attr).isInstanceOf(PlatformTokenPayload.class);
        assertThat(((PlatformTokenPayload) attr).operatorId()).isEqualTo("op-1");
    }

    @Test
    @DisplayName("合法 Authorization: Bearer → 放行")
    void validBearerTokenPasses() {
        when(jwtService.verify("bearer-token"))
                .thenReturn(
                        Optional.of(
                                new PlatformTokenPayload(
                                        "op-2", "admin2", Instant.now().plusSeconds(3600))));
        MockHttpServletRequest request = platformRequest();
        request.addHeader("Authorization", "Bearer bearer-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(runChain(request, response)).isTrue();
    }

    @Test
    @DisplayName("缺令牌 → 401 PLATFORM_UNAUTHORIZED，不放行")
    void missingTokenRejected() throws Exception {
        MockHttpServletRequest request = platformRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ran = runChain(request, response);

        assertThat(ran).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("PLATFORM_UNAUTHORIZED");
    }

    @Test
    @DisplayName("无效令牌（verify 返回 empty）→ 401，不放行")
    void invalidTokenRejected() {
        when(jwtService.verify(anyString())).thenReturn(Optional.empty());
        MockHttpServletRequest request = platformRequest();
        request.addHeader(PlatformTokenAuthFilter.TOKEN_HEADER, "bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ran = runChain(request, response);

        assertThat(ran).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("本地 JWT 不命中但企业 IdP 校验通过 → 放行且落 IdP 归一载荷（P3-T7）")
    void idpFallbackPasses() {
        PlatformIdpTokenService idpTokenService = mock(PlatformIdpTokenService.class);
        PlatformTokenAuthFilter idpFilter =
                new PlatformTokenAuthFilter(jwtService, idpTokenService);
        when(jwtService.verify("idp-token")).thenReturn(Optional.empty());
        when(idpTokenService.verify("idp-token"))
                .thenReturn(
                        Optional.of(
                                new PlatformTokenPayload(
                                        "idp-sub-9", "ops.li", Instant.now().plusSeconds(3600))));

        MockHttpServletRequest request = platformRequest();
        request.addHeader(PlatformTokenAuthFilter.TOKEN_HEADER, "idp-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean[] ran = {false};
        try {
            idpFilter.doFilter(request, response, (rq, rs) -> ran[0] = true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThat(ran[0]).isTrue();
        Object attr = request.getAttribute(PlatformTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(attr).isInstanceOf(PlatformTokenPayload.class);
        assertThat(((PlatformTokenPayload) attr).operatorId()).isEqualTo("idp-sub-9");
    }

    @Test
    @DisplayName("本地 JWT 与企业 IdP 均不命中 → 401 拒绝（P3-T7）")
    void bothPathsReject() {
        PlatformIdpTokenService idpTokenService = mock(PlatformIdpTokenService.class);
        PlatformTokenAuthFilter idpFilter =
                new PlatformTokenAuthFilter(jwtService, idpTokenService);
        when(jwtService.verify(anyString())).thenReturn(Optional.empty());
        when(idpTokenService.verify(anyString())).thenReturn(Optional.empty());

        MockHttpServletRequest request = platformRequest();
        request.addHeader(PlatformTokenAuthFilter.TOKEN_HEADER, "nope");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ran = runChainWith(idpFilter, request, response);

        assertThat(ran).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private boolean runChainWith(
            PlatformTokenAuthFilter targetFilter,
            MockHttpServletRequest request,
            MockHttpServletResponse response) {
        boolean[] ran = {false};
        FilterChain chain = (rq, rs) -> ran[0] = true;
        try {
            targetFilter.doFilter(request, response, chain);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ran[0];
    }
}
