package com.mouhin.knowledge.repository.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mouhin.knowledge.repository.application.config.SaasProperties;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link TenantResolverFilter} 单元测试（P1-T3）。
 *
 * <p>覆盖三级解析来源（header / 子域名 / JWT claim）、来源冲突 403、非法格式 400、关闭态与 {@code /api/**} 之外的放行，
 * 以及"绑定后必须在请求结束清理"的线程泄漏防护。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("TenantResolverFilter 租户解析 (P1-T3)")
class TenantResolverFilterTest {

    private SaasProperties saasProperties;
    private TenantResolverFilter filter;

    @BeforeEach
    void setUp() {
        saasProperties = new SaasProperties();
        filter = new TenantResolverFilter(saasProperties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 记录链执行时上下文里生效的租户（用于断言"确实在下游可见"）。 */
    private String runCapturing(MockHttpServletRequest request, MockHttpServletResponse response) {
        String[] captured = {SENTINEL};
        FilterChain recording = (rq, rs) -> captured[0] = String.valueOf(TenantContext.get());
        try {
            filter.doFilter(request, response, recording);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return captured[0];
    }

    private static final String SENTINEL = "<<chain-not-run>>";

    private MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
        request.setRequestURI("/api/documents");
        return request;
    }

    @Nested
    @DisplayName("saas.enabled=false（回退闸门）")
    class Disabled {

        @Test
        @DisplayName("请求放行且不绑定任何租户")
        void passesThroughWithoutBinding() {
            saasProperties.setEnabled(false);
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            MockHttpServletResponse response = new MockHttpServletResponse();

            String captured = runCapturing(request, response);

            assertThat(captured).isEqualTo("null"); // 链执行了，但上下文未绑定
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("冲突来源在关闭态不触发 403（整体短路）")
        void conflictIgnoredWhenDisabled() {
            saasProperties.setEnabled(false);
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            request.setAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE, "beta");
            MockHttpServletResponse response = new MockHttpServletResponse();

            runCapturing(request, response);

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("saas.enabled=true：三级来源解析")
    class EnabledResolution {

        @Test
        @DisplayName("来源一：X-Tenant-Id 请求头")
        void resolvesFromHeader() {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(runCapturing(request, response)).isEqualTo("acme");
            // 请求结束后必须清理，避免线程池泄漏
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("来源二：{key}.{apexDomain} 子域名")
        void resolvesFromSubdomain() {
            saasProperties.setEnabled(true);
            saasProperties.setApexDomain("example.com");
            MockHttpServletRequest request = apiRequest();
            request.addHeader("Host", "acme.example.com");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(runCapturing(request, response)).isEqualTo("acme");
        }

        @Test
        @DisplayName("子域名去端口 + 非子域(apex 本身)不解析")
        void subdomainEdgeCases() {
            saasProperties.setEnabled(true);
            saasProperties.setApexDomain("example.com");

            MockHttpServletRequest withPort = apiRequest();
            withPort.addHeader("Host", "tenant-1.example.com:8443");
            assertThat(runCapturing(withPort, new MockHttpServletResponse())).isEqualTo("tenant-1");

            MockHttpServletRequest apexItself = apiRequest();
            apexItself.addHeader("Host", "example.com");
            assertThat(runCapturing(apexItself, new MockHttpServletResponse()))
                    .isEqualTo("null"); // 无子域标签 → 不绑定

            MockHttpServletRequest multiLevel = apiRequest();
            multiLevel.addHeader("Host", "www.acme.example.com");
            assertThat(runCapturing(multiLevel, new MockHttpServletResponse()))
                    .isEqualTo("null"); // 多级子域视为歧义，不解析
        }

        @Test
        @DisplayName("来源三：JWT claim（P1-T8 落到请求属性）")
        void resolvesFromClaim() {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = apiRequest();
            request.setAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE, "tenantX");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(runCapturing(request, response)).isEqualTo("tenantX");
        }

        @Test
        @DisplayName("三来源皆空 → 不绑定，下游落到 default 语义")
        void emptyMeansUnbound() {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = apiRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(runCapturing(request, response)).isEqualTo("null");
            assertThat(TenantContext.getOrDefault()).isEqualTo("default");
        }
    }

    @Nested
    @DisplayName("一致性与拒绝路径")
    class Reconciliation {

        @Test
        @DisplayName("多来源同值不冲突")
        void sameValueAcrossSourcesOk() {
            saasProperties.setEnabled(true);
            saasProperties.setApexDomain("example.com");
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            request.addHeader("Host", "acme.example.com");
            request.setAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE, "acme");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(runCapturing(request, response)).isEqualTo("acme");
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("header 与 claim 不同值 → 403 TENANT_CONFLICT，链不执行")
        void conflictRejected() throws Exception {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            request.setAttribute(TenantResolverFilter.TENANT_CLAIM_ATTRIBUTE, "beta");
            MockHttpServletResponse response = new MockHttpServletResponse();

            String captured = runCapturing(request, response);

            assertThat(captured).isEqualTo(SENTINEL); // 被拒，链未执行
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("TENANT_CONFLICT");
        }

        @Test
        @DisplayName("非法租户格式（含 ../ 或空格）→ 400 INVALID_TENANT_KEY")
        void invalidFormatRejected() throws Exception {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = apiRequest();
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme/../etc");
            MockHttpServletResponse response = new MockHttpServletResponse();

            String captured = runCapturing(request, response);

            assertThat(captured).isEqualTo(SENTINEL);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("INVALID_TENANT_KEY");
        }
    }

    @Nested
    @DisplayName("路径范围")
    class PathScope {

        @Test
        @DisplayName("非 /api/** 路径即便开启也不解析")
        void nonApiPathSkipped() {
            saasProperties.setEnabled(true);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/index.html");
            request.setRequestURI("/admin/index.html");
            request.addHeader(TenantResolverFilter.TENANT_HEADER, "acme");
            MockHttpServletResponse response = new MockHttpServletResponse();

            String captured = runCapturing(request, response);

            // shouldNotFilter 命中 → 直接进入下游链，上下文未绑定
            assertThat(captured).isEqualTo("null");
        }
    }
}
