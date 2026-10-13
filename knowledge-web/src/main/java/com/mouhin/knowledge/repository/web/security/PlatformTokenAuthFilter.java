package com.mouhin.knowledge.repository.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.gateway.PlatformIdpTokenService;
import com.mouhin.knowledge.repository.domain.gateway.PlatformJwtService;
import com.mouhin.knowledge.repository.domain.model.valueobject.PlatformTokenPayload;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 平台端令牌鉴权过滤器（adapter 层，P3-T1 / T-4 方案 B）。
 *
 * <p>与 {@code AdminTokenAuthFilter}（租户管理员令牌）<b>完全独立</b>：只拦 {@code /api/platform/**}， 只认独立 {@code
 * knowledge.platform.jwt.secret} 签发的平台令牌（请求头 {@value #TOKEN_HEADER} 或标准 {@code Authorization:
 * Bearer}），校验签名 + 过期 + {@code scope=platform} 三重合法性，任一不通过即 401 JSON 拒绝。
 *
 * <p><b>路径互斥</b>：{@code AdminTokenAuthFilter} 的放行前缀已加入 {@code /api/platform/}，故平台端点不经租户管理员令牌体系；
 * 本过滤器也不触碰 {@code /api/platform/**} 之外的任何路径（{@link #shouldNotFilter}）。两套令牌互不可用，
 * 即便租户管理员令牌泄漏也无法触达平台生命周期端点（T-4 方案 B 的核心安全边界）。
 *
 * <p><b>企业 IdP 并存（P3-T7 / C-3=方案 B）</b>：本地平台 JWT 校验不命中时，若已装配 {@link PlatformIdpTokenService}（{@code
 * knowledge.platform.oidc.enabled=true}）则追加一次企业 IdP 令牌（RS256/JWKS）校验， 两套令牌并存、任一通过即放行；{@code
 * oidc.enabled=false} 时校验器缺席，行为与 P3-T1 完全一致（本地 JWT 单路径回退闸门）。
 *
 * <p>鉴权成功后把 {@link PlatformTokenPayload} 落到请求属性 {@value #PRINCIPAL_ATTRIBUTE}，供 {@code
 * PlatformTenantController} 读取操作者身份注入审计，避免重复解析令牌。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Slf4j
public class PlatformTokenAuthFilter extends OncePerRequestFilter {

    /** 平台端令牌请求头。 */
    public static final String TOKEN_HEADER = "X-Platform-Token";

    /** 校验成功后平台操作者载荷落到请求属性的键，供 controller 读取注入审计。 */
    public static final String PRINCIPAL_ATTRIBUTE = "knowledge.platform.principal";

    /** 本过滤器唯一拦截的路径前缀。 */
    public static final String PLATFORM_PREFIX = "/api/platform/";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ERROR_CODE = "PLATFORM_UNAUTHORIZED";

    private final PlatformJwtService platformJwtService;

    /** 企业 IdP 令牌校验器（P3-T7，可空）：{@code oidc.enabled=false} 或未装配时缺席，平台端仅走本地 JWT。 */
    private final PlatformIdpTokenService idpTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformTokenAuthFilter(PlatformJwtService platformJwtService) {
        this(platformJwtService, null);
    }

    public PlatformTokenAuthFilter(
            PlatformJwtService platformJwtService, PlatformIdpTokenService idpTokenService) {
        this.platformJwtService = platformJwtService;
        this.idpTokenService = idpTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(PLATFORM_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        Optional<PlatformTokenPayload> payload =
                token == null ? Optional.empty() : platformJwtService.verify(token);
        // P3-T7 本地平台 JWT 不命中且已接入企业 IdP → 追加 IdP 令牌校验分支（C-3=方案 B），两套令牌并存互不影响。
        if (payload.isEmpty() && token != null && idpTokenService != null) {
            payload = idpTokenService.verify(token);
        }
        if (payload.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, payload.get());
        filterChain.doFilter(request, response);
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
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        log.debug("平台端鉴权失败：缺少或无效平台令牌");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "error",
                        "未认证或平台令牌无效",
                        "errorCode",
                        ERROR_CODE,
                        "code",
                        HttpServletResponse.SC_UNAUTHORIZED));
    }
}
