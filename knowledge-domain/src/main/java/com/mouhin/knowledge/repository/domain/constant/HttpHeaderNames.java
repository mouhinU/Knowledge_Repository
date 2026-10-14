package com.mouhin.knowledge.repository.domain.constant;

/**
 * HTTP 请求/响应头名常量。
 *
 * <p>infra 层基于 JDK {@code java.net.http.HttpClient} 直连外部 LLM 端点时使用（该模块不依赖 spring-web，无法引用 {@code
 * org.springframework.http.HttpHeaders}）。web 层若已在 Spring 上下文 中，仍建议优先用 {@code
 * org.springframework.http.HttpHeaders.*}。此处仅集中维护跨模块共享的 header 名，依 AGENTS.md 红线 #2 避免各处硬编码。
 */
public final class HttpHeaderNames {

    private HttpHeaderNames() {}

    /** Content-Type 头名。 */
    public static final String CONTENT_TYPE = "Content-Type";

    /** Authorization 头名。 */
    public static final String AUTHORIZATION = "Authorization";

    /** Accept 头名。 */
    public static final String ACCEPT = "Accept";

    /** Cache-Control 头名。 */
    public static final String CACHE_CONTROL = "Cache-Control";
}
