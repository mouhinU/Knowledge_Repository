package com.mouhin.knowledge.repository.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 管理端令牌鉴权过滤器（adapter 层，集中校验）。
 *
 * <p>取代此前对管理端 API 强制的 HTTP Basic：所有受保护请求需携带 {@code X-Admin-Token}（或标准 {@code Authorization:
 * Bearer}）访问令牌，交由 {@link AdminAuthServiceI#validateToken} 做 签名 + 过期 + 账号激活态三重校验，任一不通过即以 401 JSON
 * 拒绝、不再向下传递。
 *
 * <p>放行边界（与鉴权链路配套）：健康探针、静态资源、以及考生侧 {@code /api/student/**}、 {@code /api/exam/**}（各自 app 层用学生 token
 * 校验）不参与本过滤器；管理端登录 / 登出端点 因获取令牌之前即需可达，也在放行之列。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Slf4j
public class AdminTokenAuthFilter extends OncePerRequestFilter {

    /** 管理端令牌请求头。 */
    public static final String TOKEN_HEADER = "X-Admin-Token";

    /** 登录后可从令牌身份还原的请求属性键，供下游（如审计）读取，避免重复解析。 */
    public static final String PRINCIPAL_ATTRIBUTE = "knowledge.admin.principal";

    private static final String BEARER_PREFIX = "Bearer ";

    /** SSE 进度流令牌查询参数（EventSource 不能自定义请求头）。 */
    private static final String ACCESS_TOKEN_PARAM = "access_token";

    /**
     * 运维探针类 actuator 端点，允许匿名可达（与业务令牌体系解耦）。
     *
     * <p>包含健康/就绪探针与 {@code /actuator/prometheus} 指标抓取端点——后者供 Prometheus 定期拉取， 无法携带会过期的管理端
     * JWT。仅暴露低基数运维指标（见 {@code ExtractionMetrics}：标签限于 strategy/mime/outcome/reason/model，不含
     * documentKey/fileName 等高基数字段）。<b>生产应收敛</b>： 建议改用独立 {@code management.server.port} 或在网关 /
     * 网络策略层按内网来源限制访问。
     */
    private static final List<String> PUBLIC_ACTUATOR_PATHS =
            List.of("/actuator/health", "/actuator/prometheus");

    /** 考生 / 学生侧与管理端登录登出等自带鉴权或须先可达的路径，本过滤器放行。 */
    private static final List<String> PUBLIC_PREFIXES =
            List.of(
                    "/api/student/",
                    "/api/exam/",
                    "/api/admin/auth/login",
                    "/api/admin/auth/logout");

    private final AdminAuthServiceI adminAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminTokenAuthFilter(AdminAuthServiceI adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!requiresAuth(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = resolveToken(request);
        AdminPrincipalDTO principal =
                token == null ? null : adminAuthService.validateToken(token).getData();
        if (principal == null || !Boolean.TRUE.equals(principal.getAdmin())) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        filterChain.doFilter(request, response);
    }

    /** 判定请求是否需要管理端令牌。仅拦截 {@code /api/**} 与 {@code /actuator/**}，且排除健康探针与放行前缀。 */
    private boolean requiresAuth(String path) {
        if (path == null) {
            return false;
        }
        boolean protectedArea = path.startsWith("/api/") || path.startsWith("/actuator/");
        if (!protectedArea) {
            return false;
        }
        for (String publicPath : PUBLIC_ACTUATOR_PATHS) {
            if (path.startsWith(publicPath)) {
                return false;
            }
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(TOKEN_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String value = authorization.substring(BEARER_PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        // SSE（EventSource）无法自定义请求头，进度流端点改由 access_token 查询参数携带令牌。
        String param = request.getParameter(ACCESS_TOKEN_PARAM);
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        log.debug("管理端鉴权失败：缺少或无效令牌");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of("error", "未认证或登录已过期", "code", HttpServletResponse.SC_UNAUTHORIZED));
    }
}
