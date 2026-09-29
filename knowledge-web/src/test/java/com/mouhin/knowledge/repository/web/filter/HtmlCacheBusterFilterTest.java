package com.mouhin.knowledge.repository.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link HtmlCacheBusterFilter} 单元测试。
 *
 * <p>覆盖：URL 改写、query 追加、幂等、非 HTML 透传、字符集与响应头改写。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("HtmlCacheBusterFilter 前端缓存击穿")
class HtmlCacheBusterFilterTest {

    private static final String VERSION = "V2026092903";

    private HtmlCacheBusterFilter newFilter() {
        return new HtmlCacheBusterFilter(VERSION);
    }

    private MockHttpServletRequest req(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI(uri);
        r.setMethod("GET");
        return r;
    }

    /** 用给定的响应 content-type 与 body 走一次 filter，返回响应快照。 */
    private MockHttpServletResponse run(
            String uri, String contentType, String body, HtmlCacheBusterFilter filter)
            throws ServletException, IOException {
        MockHttpServletRequest request = req(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(contentType);
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(ServletRequest sreq, ServletResponse sres)
                            throws IOException {
                        sres.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                        sres.flushBuffer();
                    }
                };
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletResponse runHtml(String uri, String body)
            throws ServletException, IOException {
        return run(uri, "text/html;charset=UTF-8", body, newFilter());
    }

    @Test
    @DisplayName("CSS/JS 裸 URL 被追加 ?v=APP_VERSION")
    void rewriteAssets_appendsVersion() throws Exception {
        String html =
                "<html><head>"
                        + "<link href=\"assets/css/common.css\" rel=\"stylesheet\">"
                        + "</head><body>"
                        + "<script src=\"assets/js/ai-exam.js\"></script>"
                        + "<script src=\"assets/js/common.js\"></script>"
                        + "</body></html>";
        String out = runHtml("/admin/ai-exam.html", html).getContentAsString();
        assertTrue(out.contains("assets/css/common.css?v=" + VERSION), "CSS 应带版本位：actual=" + out);
        assertTrue(
                out.contains("assets/js/ai-exam.js?v=" + VERSION),
                "ai-exam.js 应带版本位：actual=" + out);
        assertTrue(
                out.contains("assets/js/common.js?v=" + VERSION), "common.js 应带版本位：actual=" + out);
    }

    @Test
    @DisplayName("已带 query 的引用以 &v= 追加，不破坏原参数")
    void rewriteAssets_preservesExistingQuery() throws Exception {
        String html = "<script src=\"assets/js/common.js?lang=zh\"></script>";
        String out = runHtml("/admin/index.html", html).getContentAsString();
        assertTrue(
                out.contains("assets/js/common.js?lang=zh&v=" + VERSION),
                "应保留原 query 并追加 &v=：actual=" + out);
    }

    @Test
    @DisplayName("幂等：已注入 ?v=当前版本 的引用不会被二次改写")
    void rewriteAssets_idempotent() throws Exception {
        String html = "<script src=\"assets/js/ai-exam.js\"></script>";
        String once = runHtml("/admin/ai-exam.html", html).getContentAsString();
        String twice = runHtml("/admin/ai-exam.html", once).getContentAsString();
        assertFalse(
                twice.contains("v=" + VERSION + "?v=") || twice.contains("v=" + VERSION + "&v="),
                "不应重复注入版本：actual=" + twice);
        int idx = twice.indexOf("?v=");
        int next = twice.indexOf("?v=", idx + 1);
        assertTrue(idx >= 0 && next < 0, "应只出现一次 ?v=：actual=" + twice);
    }

    @Test
    @DisplayName("HTML 响应头被改严为 no-store/no-cache/must-revalidate + Pragma + Expires")
    void rewriteHeaders_htmlNoStore() throws Exception {
        MockHttpServletResponse resp = runHtml("/admin/index.html", "<html><body>hi</body></html>");
        String cc = resp.getHeader("Cache-Control");
        assertNotNull(cc, "Cache-Control 必须被显式设置");
        assertTrue(cc.contains("no-store"), "应包含 no-store：actual=" + cc);
        assertTrue(cc.contains("no-cache"), "应包含 no-cache：actual=" + cc);
        assertTrue(cc.contains("must-revalidate"), "应包含 must-revalidate：actual=" + cc);
        assertEquals("no-cache", resp.getHeader("Pragma"));
        assertNotNull(resp.getHeader("Expires"), "Expires 应被设置");
    }

    @Test
    @DisplayName("下游 handler 试图覆盖 Cache-Control/Expires 被锁定拦截，仍保持 no-store")
    void downstreamCannotOverrideLockedHeaders() throws Exception {
        MockHttpServletRequest request = req("/admin/ai-exam.html");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        resp.setContentType("text/html;charset=UTF-8");
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(ServletRequest sreq, ServletResponse sres)
                            throws IOException {
                        HttpServletResponse hr = (HttpServletResponse) sres;
                        // 模拟 Spring ResourceHttpRequestHandler 应用 application.yml 里的 no-cache 配置
                        hr.setHeader("Cache-Control", "no-cache, must-revalidate");
                        hr.addHeader("Cache-Control", "public, max-age=3600");
                        hr.setDateHeader("Expires", System.currentTimeMillis() + 86400_000L);
                        hr.getOutputStream()
                                .write(
                                        "<html><body>ok</body></html>"
                                                .getBytes(StandardCharsets.UTF_8));
                    }
                };
        newFilter().doFilter(request, resp, chain);
        String cc = resp.getHeader("Cache-Control");
        assertNotNull(cc);
        assertTrue(cc.contains("no-store"), "锁定后仍应保留 no-store：actual=" + cc);
        assertFalse(cc.contains("max-age=3600"), "下游 addHeader 应被吞掉：actual=" + cc);
        assertEquals("no-cache", resp.getHeader("Pragma"), "Pragma 也应锁定为 no-cache");
    }

    @Test
    @DisplayName("非 HTML 响应不被改写：JSON 透传 & 不强推 no-store")
    void nonHtml_passThrough() throws Exception {
        String json = "{\"data\":\"assets/js/x.js not-a-real-ref\"}";
        MockHttpServletResponse resp =
                run("/api/admin/user", "application/json", json, newFilter());
        assertEquals(json, resp.getContentAsString());
        String cc = resp.getHeader("Cache-Control");
        assertTrue(cc == null || !cc.contains("no-store"), "JSON 不应被强推 no-store：actual=" + cc);
    }

    @Test
    @DisplayName("app.version 空：不改写 URL，但仍收紧 HTML 响应头")
    void emptyVersion_skipsUrlRewrite() throws Exception {
        String html = "<script src=\"assets/js/ai-exam.js\"></script>";
        MockHttpServletResponse resp =
                run(
                        "/admin/ai-exam.html",
                        "text/html;charset=UTF-8",
                        html,
                        new HtmlCacheBusterFilter(""));
        assertEquals(html, resp.getContentAsString(), "无版本时 URL 保持原样");
        assertNotNull(resp.getHeader("Cache-Control"), "响应头仍应被改严");
        assertTrue(resp.getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    @DisplayName("考试端 exam.html 也命中改写与响应头")
    void examHtml_alsoRewritten() throws Exception {
        String html = "<link href=\"assets/css/common.css\">";
        MockHttpServletResponse resp = runHtml("/exam.html", html);
        assertTrue(
                resp.getContentAsString().contains("assets/css/common.css?v=" + VERSION),
                "exam.html 应被改写：actual=" + resp.getContentAsString());
        assertTrue(resp.getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    @DisplayName("Content-Length 与响应体实际字节一致（正则扩展后不留 stale 长度）")
    void contentLengthMatchesBody() throws Exception {
        String html = "<script src=\"assets/js/ai-exam.js\"></script>";
        MockHttpServletResponse resp = runHtml("/admin/ai-exam.html", html);
        byte[] body = resp.getContentAsByteArray();
        assertEquals(body.length, resp.getContentLength(), "长度应同步到改写后的 body");
        assertTrue(new String(body, StandardCharsets.UTF_8).contains("?v=" + VERSION));
    }

    @Test
    @DisplayName("引号变体：src='assets/js/x.js' 单引号形式同样被追加")
    void rewriteAssets_singleQuoteVariant() throws Exception {
        String html = "<script src='assets/js/common.js'></script>";
        String out = runHtml("/admin/users.html", html).getContentAsString();
        assertTrue(out.contains("assets/js/common.js?v=" + VERSION), "单引号变体应命中：actual=" + out);
    }
}
