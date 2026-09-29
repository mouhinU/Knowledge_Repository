package com.mouhin.knowledge.repository.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 前端静态资源缓存击穿过滤器（Cache-Busting）。
 *
 * <p>项目此前只依赖 {@code Cache-Control: no-cache}，浏览器在 <b>同 URL</b> 命中磁盘缓存时可能仍跳过条件 GET；加上 {@code
 * assets/js/ai-exam.js} 这类裸 URL 不带版本位，导致发布新版本后必须 cmd+shift+r 强刷才能看到 新代码。此过滤器针对 HTML 入口页做两件事：
 *
 * <ol>
 *   <li>把响应体中所有 {@code assets/(css|js)/xxx.(css|js)} 引用追加 {@code ?v=${APP_VERSION}}，让 <b>URL
 *       本身随版本变化</b>——只要版本变，就绕过任何浏览器/中间代理缓存；对已带 query 的引用以 {@code &v=} 追加，幂等。
 *   <li>把 HTML 响应头锁定为 {@code no-store, no-cache, must-revalidate}（附带 HTTP/1.0 的 {@code Pragma:
 *       no-cache} 与 {@code Expires: 0}），并通过 {@link CacheLockingWrapper} 拦截下游 {@code
 *       ResourceHttpRequestHandler} 对这三个头的 {@code setHeader/addHeader}，防止它们反过来把我们设的值覆盖掉。
 * </ol>
 *
 * <p>不干预 JSON/图片/字体等非 HTML 响应；不改变静态资源本身的响应头（仍走 {@code
 * spring.web.resources.cache.cachecontrol}），依赖版本化 URL 完成击穿。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Slf4j
public class HtmlCacheBusterFilter extends OncePerRequestFilter {

    /**
     * 匹配 HTML 中的本地静态资源引用，形如 {@code "assets/css/x.css"} / {@code 'assets/js/y.js?lang=zh'}。 捕获组：
     *
     * <ul>
     *   <li>quote —— 起始引号（单/双），必须在替换时回填，避免破坏 HTML 结构。
     *   <li>path —— 不含 query 的完整资源路径（例如 {@code assets/js/ai-exam.js}），支持可选前导斜杠。
     *   <li>query —— 可选的现有查询串（以 {@code ?} 开头），保留并追加 {@code &v=}。
     * </ul>
     *
     * <p>lookahead {@code (?=\k<quote>)} 确保只替换 URL 本身，不吞掉引号；末尾的引号在下次 find 之前保留。
     */
    private static final Pattern ASSET_PATTERN =
            Pattern.compile(
                    "(?<quote>[\"'])"
                            + "(?<path>/?assets/(?<kind>css|js)/(?<name>[A-Za-z0-9_.\\-]+)\\.(?<ext>css|js))"
                            + "(?<query>\\?[^\"'\\s>]*)?"
                            + "(?=\\k<quote>)");

    /** HTML 严格禁缓存响应头。 */
    private static final String HTML_CACHE_CONTROL =
            "no-store, no-cache, must-revalidate, max-age=0";

    /** 内容类型前缀判定。 */
    private static final String HTML_CONTENT_TYPE = "text/html";

    /** 本过滤器独占写入的响应头名（大小写无关）；下游 setHeader/addHeader 将被静默忽略。 */
    private static final Set<String> LOCKED_HEADERS = Set.of("cache-control", "pragma", "expires");

    /** 版本变量；空/null 时不做 URL 注入（仅锁定响应头），避免把 {@code ?v=null} 写进生产。 */
    private final String appVersion;

    public HtmlCacheBusterFilter(String appVersion) {
        this.appVersion = appVersion;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // url-pattern '/admin/*' 同时命中 JS/CSS 等资源；对非 HTML URI 直接透传，避免包一层缓存浪费内存
        if (!isHtmlEntryPoint(request)) {
            chain.doFilter(request, response);
            return;
        }
        // 关键：先在 underlying 上落头；下游再拿到 wrapped 时无法覆盖锁定项。
        // 只在真正命中 HTML 入口时锁定，避免影响 404/重定向。
        applyNoStoreHeaders(response);
        CacheLockingWrapper wrapped =
                new CacheLockingWrapper(new ContentCachingResponseWrapper(response));
        try {
            chain.doFilter(request, wrapped);
        } finally {
            ContentCachingResponseWrapper cache = wrapped.getCache();
            byte[] body = cache.getContentAsByteArray();
            String contentType = wrapped.getContentType();
            boolean htmlLike =
                    contentType != null
                            && contentType.toLowerCase().startsWith(HTML_CONTENT_TYPE)
                            && body.length > 0;
            if (htmlLike) {
                byte[] rewritten = rewriteHtmlBody(body, wrapped.getCharacterEncoding());
                if (rewritten != body) {
                    // ContentCachingResponseWrapper 把 chain 写入的原始 body 缓存在同一 stream 里；
                    // 若直接再 write(rewritten)，copyBodyToResponse 会输出"原始 + 改写"两段。
                    // 必须先 resetBuffer 清空 cache，再灌入改写版本。
                    cache.resetBuffer();
                    cache.setContentLength(rewritten.length);
                    cache.getOutputStream().write(rewritten);
                }
                // 若未改写（版本缺失/无匹配），cache 内容原样正确，交给 copyBodyToResponse 即可
            }
            cache.copyBodyToResponse();
        }
    }

    /**
     * 判定请求 URI 是否为 HTML 入口页：以 {@code .html} 结尾，或是 {@code /admin}/{@code /admin/} 目录索引。 其他 URI 即使被
     * url-pattern 匹配到（例如 {@code /admin/assets/js/x.js}）也直接透传。
     */
    private boolean isHtmlEntryPoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.endsWith(".html")) {
            return true;
        }
        return uri.equals("/admin") || uri.equals("/admin/");
    }

    /** 覆写 HTML 响应头。 */
    private void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", HTML_CACHE_CONTROL);
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
    }

    /**
     * 用 UTF-8（或响应自身字符集）解析 HTML 文本、正则替换资源引用、再编码回字节。
     *
     * @return 若版本号缺失或没有命中，返回原数组（同一引用）；否则返回新数组。
     */
    private byte[] rewriteHtmlBody(byte[] body, String encoding) {
        if (!StringUtils.hasText(appVersion)) {
            return body;
        }
        String charset = StringUtils.hasText(encoding) ? encoding : StandardCharsets.UTF_8.name();
        String html;
        try {
            html = new String(body, charset);
        } catch (Exception ex) {
            log.debug("HTML 字符集解码失败，退回 UTF-8：charset={} err={}", charset, ex.getMessage());
            html = new String(body, StandardCharsets.UTF_8);
            charset = StandardCharsets.UTF_8.name();
        }
        Matcher m = ASSET_PATTERN.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 64);
        boolean changed = false;
        while (m.find()) {
            String quote = m.group("quote");
            String path = m.group("path");
            String query = m.group("query");
            String replacement;
            if (query == null || query.isEmpty()) {
                replacement = quote + path + "?v=" + appVersion;
                changed = true;
            } else if (query.contains("v=" + appVersion)) {
                // 幂等：已带当前版本则原样保留
                replacement = quote + path + query;
            } else {
                replacement = quote + path + query + "&v=" + appVersion;
                changed = true;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        if (!changed) {
            return body;
        }
        try {
            return out.toString().getBytes(charset);
        } catch (Exception ex) {
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 拦截 {@code setHeader/addHeader/setDateHeader/setIntHeader} 对 {@link #LOCKED_HEADERS} 中头名的写入， 让
     * {@link HtmlCacheBusterFilter} 预先设置在 underlying 上的 no-store/no-cache 值不被下游 {@code
     * ResourceHttpRequestHandler} 覆盖。其他头（{@code Content-Type}/{@code Last-Modified}/{@code
     * Accept-Ranges}/{@code Content-Length} 等）完全透传。
     *
     * <p>{@code ContentCachingResponseWrapper} 自身已负责 body 缓存与 {@code copyBodyToResponse}；本包装只做头名锁定，
     * 通过 {@link HttpServletResponseWrapper} 继承链把 body/write 操作委派给内层缓存包装。
     */
    private static final class CacheLockingWrapper extends HttpServletResponseWrapper {

        private final ContentCachingResponseWrapper cache;

        CacheLockingWrapper(ContentCachingResponseWrapper cache) {
            super(cache);
            this.cache = cache;
        }

        ContentCachingResponseWrapper getCache() {
            return cache;
        }

        @Override
        public void setHeader(String name, String value) {
            if (isLocked(name)) {
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (isLocked(name)) {
                return;
            }
            super.addHeader(name, value);
        }

        @Override
        public void setDateHeader(String name, long date) {
            if (isLocked(name)) {
                return;
            }
            super.setDateHeader(name, date);
        }

        @Override
        public void setIntHeader(String name, int val) {
            if (isLocked(name)) {
                return;
            }
            super.setIntHeader(name, val);
        }

        private boolean isLocked(String name) {
            return name != null && LOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }
    }
}
