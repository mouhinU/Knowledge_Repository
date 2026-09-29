package com.mouhin.knowledge.repository.web.config;

import com.mouhin.knowledge.repository.web.filter.HtmlCacheBusterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 前端静态资源缓存击穿配置。
 *
 * <p>注册 {@link HtmlCacheBusterFilter}，作用范围精确限定到 HTML 入口页：
 *
 * <ul>
 *   <li>{@code /admin/}、{@code /admin} —— 管理端首页（index.html）。
 *   <li>{@code /admin/*.html} —— 11 个 admin 子页面。
 *   <li>{@code /admin.html} —— 旧 admin.html 重定向存根，同样禁缓存。
 *   <li>{@code /exam.html} —— 考生端 SPA 入口。
 * </ul>
 *
 * <p>Order 取 {@code HIGHEST_PRECEDENCE + 20}：晚于 Spring Security 过滤器链（默认 {@code
 * SecurityProperties.DEFAULT_FILTER_ORDER = -100}），以免改写 401 响应。
 *
 * <p>{@code app.version} 优先读环境变量 {@code APP_VERSION}（Docker 镜像层已 ENV），本地 dev profile 无值时回落为 {@code
 * dev}——版本 {@code dev} 不参与 URL 追加逻辑但响应头改写仍生效，避免开发环境噪音。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Configuration
public class WebStaticCacheConfig {

    /**
     * HTML 入口页路径白名单。
     *
     * <p>Servlet 规范只允许"精确匹配 / 前缀 {@code /*} / 扩展 {@code *.html}"三种形态，不支持 {@code /admin/*.html}
     * 这种前缀+扩展混写。这里采用 {@code /admin/*} 前缀 + 具体精确匹配的组合， 由 {@link HtmlCacheBusterFilter} 内部再按 URI 后缀 /
     * 目录索引页判断是否改写。
     */
    private static final String[] HTML_URL_PATTERNS = {
        "/admin", "/admin/", "/admin/*", "/admin.html", "/exam.html",
    };

    @Bean
    public FilterRegistrationBean<HtmlCacheBusterFilter> htmlCacheBusterFilterRegistration(
            @Value("${app.version:${APP_VERSION:dev}}") String appVersion) {
        FilterRegistrationBean<HtmlCacheBusterFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new HtmlCacheBusterFilter(appVersion));
        reg.addUrlPatterns(HTML_URL_PATTERNS);
        reg.setName("htmlCacheBusterFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return reg;
    }
}
