package com.mouhin.knowledge.repository.domain.constant;

/**
 * HTTP Authorization 方案前缀常量。
 *
 * <p>Bearer Token 是 OAuth2 / JWT 传递令牌的事实标准方案，web 侧过滤器与 infra 侧 LLM HTTP 网关都会拼 "Authorization:
 * Bearer &lt;token&gt;" 头，依 AGENTS.md 红线 #2 集中维护。
 */
public final class HttpAuthSchemes {

    private HttpAuthSchemes() {}

    /** Bearer 方案前缀（含尾随空格，用于 {@code BEARER_PREFIX + token} 拼接）。 */
    public static final String BEARER_PREFIX = "Bearer ";
}
