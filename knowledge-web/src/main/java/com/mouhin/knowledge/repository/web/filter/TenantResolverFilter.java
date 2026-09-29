package com.mouhin.knowledge.repository.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.application.config.SaasProperties;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 租户解析过滤器（P1-T3，SaaS 脊柱请求入口）。
 *
 * <p>把"这次请求属于哪个租户"解析出来并写入 {@link TenantContext}，供下游 app / infra 读取。解析优先级三来源：
 *
 * <ol>
 *   <li>请求头 {@code X-Tenant-Id}（前端跨域 / 网关注入，X-T5）；
 *   <li>子域名 {@code {tenantKey}.{apexDomain}}（{@code knowledge.saas.apex-domain} 非空时启用）；
 *   <li>JWT claim {@code tenantId}（由 {@code AdminTokenAuthFilter} 在 P1-T8 落到请求属性 {@value
 *       #TENANT_CLAIM_ATTRIBUTE}）。
 * </ol>
 *
 * <p><b>冲突即拒</b>：任一来源给出不同的非空值视为跨租户伪造/歧义，直接 403（{@code TENANT_CONFLICT}）拒绝，绝不"取其一"。 三来源皆空时不绑定，下游
 * {@code getOrDefault} 落到 {@code default}（运行时单租户语义）。
 *
 * <p><b>回退闸门</b>：{@code knowledge.saas.enabled=false}（默认）时本过滤器整体短路—— 不解析、不绑定、直接放行，行为与单租户版本等价（提案
 * §9）。仅 {@code /api/**} 参与解析，静态页与其他路径直接放行。
 *
 * <p><b>清理纪律</b>（红线）：绑定后必须在 {@code finally} 中 {@link TenantContext#clear()}，避免线程池把租户上下文泄漏到下一请求。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Slf4j
public class TenantResolverFilter extends OncePerRequestFilter {

    /** 租户解析请求头。 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** JWT claim {@code tenantId} 落到请求属性的键（P1-T8 由鉴权过滤器填充）。 */
    public static final String TENANT_CLAIM_ATTRIBUTE = "knowledge.tenant.claim";

    /** 错误码：跨租户冲突。 */
    public static final String ERROR_CODE_CONFLICT = "TENANT_CONFLICT";

    /** 错误码：非法租户标识格式。 */
    public static final String ERROR_CODE_INVALID = "INVALID_TENANT_KEY";

    /** 合法租户键：1~64 位字母 / 数字 / 下划线 / 连字符（对齐 {@code sys_tenant.tenant_key} 语义）。 */
    private static final Pattern VALID_TENANT_KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 仅这些路径前缀参与租户解析。 */
    private static final String API_PREFIX = "/api/";

    private final SaasProperties saasProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TenantResolverFilter(SaasProperties saasProperties) {
        this.saasProperties = saasProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 关闭态直接放行（回退闸门），且非 /api/** 不参与解析。
        return !saasProperties.isEnabled() || path == null || !path.startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String fromHeader = trimToNull(request.getHeader(TENANT_HEADER));
        String fromSubdomain = resolveFromSubdomain(request);
        String fromClaim = trimToNull(stringAttribute(request, TENANT_CLAIM_ATTRIBUTE));

        for (String candidate : new String[] {fromHeader, fromSubdomain, fromClaim}) {
            if (candidate != null && !VALID_TENANT_KEY.matcher(candidate).matches()) {
                writeError(
                        response, HttpServletResponse.SC_BAD_REQUEST, ERROR_CODE_INVALID, "非法租户标识");
                return;
            }
        }

        String resolved;
        try {
            resolved = reconcile(fromHeader, fromSubdomain, fromClaim);
        } catch (TenantConflictException e) {
            log.debug("租户解析冲突：sources={}", e.candidates);
            writeError(
                    response, HttpServletResponse.SC_FORBIDDEN, ERROR_CODE_CONFLICT, "租户来源冲突，拒绝请求");
            return;
        }

        try {
            if (resolved != null) {
                TenantContext.set(resolved);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 汇总三个来源的非空候选并做一致性判定。
     *
     * @return 唯一候选值；三者皆空时返回 {@code null}
     * @throws TenantConflictException 存在两个及以上互不相同的值
     */
    private String reconcile(String header, String subdomain, String claim) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String candidate : new String[] {header, subdomain, claim}) {
            if (candidate != null) {
                distinct.add(candidate);
            }
        }
        if (distinct.size() > 1) {
            throw new TenantConflictException(distinct);
        }
        return distinct.isEmpty() ? null : distinct.iterator().next();
    }

    /** 从 Host 反解子域名租户键：仅当 {@code apexDomain} 非空、Host 恰为 {@code {key}.{apex}} 单级子域时返回 key。 */
    private String resolveFromSubdomain(HttpServletRequest request) {
        String apex = trimToNull(saasProperties.getApexDomain());
        if (apex == null) {
            return null;
        }
        apex = apex.toLowerCase();
        String host = request.getHeader("Host");
        if (host == null) {
            host = request.getServerName();
        }
        if (host == null) {
            return null;
        }
        host = host.toLowerCase();
        int portIdx = host.indexOf(':');
        if (portIdx >= 0) {
            host = host.substring(0, portIdx);
        }
        String suffix = "." + apex;
        if (!host.endsWith(suffix)) {
            return null;
        }
        String prefix = host.substring(0, host.length() - suffix.length());
        // 仅接受单级子域（{key}.apex）；多级或空视为非租户子域。
        if (prefix.isEmpty() || prefix.contains(".")) {
            return null;
        }
        return prefix;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String s ? s : null;
    }

    private void writeError(
            HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                java.util.Map.of("error", message, "errorCode", errorCode, "code", status));
    }

    /** 内部信号异常：三来源给出互不相同的租户值。 */
    private static final class TenantConflictException extends RuntimeException {
        private final Set<String> candidates;

        TenantConflictException(Set<String> candidates) {
            super("tenant source conflict");
            this.candidates = candidates;
        }
    }
}
