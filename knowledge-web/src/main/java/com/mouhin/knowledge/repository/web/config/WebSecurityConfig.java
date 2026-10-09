package com.mouhin.knowledge.repository.web.config;

import com.mouhin.knowledge.repository.application.config.SaasProperties;
import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.domain.gateway.PlatformJwtService;
import com.mouhin.knowledge.repository.domain.gateway.TenantDirectory;
import com.mouhin.knowledge.repository.web.filter.TenantResolverFilter;
import com.mouhin.knowledge.repository.web.security.AdminTokenAuthFilter;
import com.mouhin.knowledge.repository.web.security.PlatformTokenAuthFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Web 安全配置
 *
 * <p>管理端鉴权已从 HTTP Basic（内存单管理员）迁移为<b>用户名 / 密码登录 + 无状态 JWT</b>。此处不再声明 {@code
 * InMemoryUserDetailsManager} 与 {@code httpBasic}：Spring Security 链对本服务所有请求放行， 真正的鉴权由 {@link
 * AdminTokenAuthFilter} 集中在链上完成——校验 {@code X-Admin-Token}（或 Bearer） 令牌的签名、有效期与账号激活态，不通过即 401。
 *
 * <p>保留无状态（STATELESS）与会话禁用、CSRF 关闭（纯令牌前后端分离）。健康探针 （{@code /actuator/health}）与静态资源天然匿名可达；考生侧 {@code
 * /api/student/**}、{@code /api/exam/**} 由 app 层校验学生 token，均在过滤器放行清单内，不经管理端令牌。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final AdminAuthServiceI adminAuthService;
    private final SaasProperties saasProperties;
    private final TenantDirectory tenantDirectory;

    public WebSecurityConfig(
            AdminAuthServiceI adminAuthService,
            SaasProperties saasProperties,
            TenantDirectory tenantDirectory) {
        this.adminAuthService = adminAuthService;
        this.saasProperties = saasProperties;
        this.tenantDirectory = tenantDirectory;
    }

    @Bean
    public AdminTokenAuthFilter adminTokenAuthFilter() {
        return new AdminTokenAuthFilter(adminAuthService);
    }

    /** 租户解析过滤器（P1-T3）：紧随鉴权过滤器，以便读取其落到请求属性的 JWT 租户 claim；P3-T4 起兼做软停租户 403 软拦。 */
    @Bean
    public TenantResolverFilter tenantResolverFilter() {
        return new TenantResolverFilter(saasProperties, tenantDirectory);
    }

    /**
     * 平台端令牌鉴权过滤器（P3-T1 / T-4 方案 B）：仅当 {@code knowledge.platform.enabled=true} 时才注册（{@link
     * org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}，与 {@code
     * PlatformJwtGatewayImpl}、{@code PlatformTenantController} 同门控）。默认关闭态下本 bean 缺席，{@code
     * /api/platform/**} 不注册任何平台鉴权， 且控制器本身也不注册，端点整体不可达。
     */
    @Bean
    @ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
    public PlatformTokenAuthFilter platformTokenAuthFilter(PlatformJwtService platformJwtService) {
        return new PlatformTokenAuthFilter(platformJwtService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AdminTokenAuthFilter adminTokenAuthFilter,
            TenantResolverFilter tenantResolverFilter,
            ObjectProvider<PlatformTokenAuthFilter> platformTokenAuthFilterProvider)
            throws Exception {
        // CSRF 关闭合理性（java:S4502 例外）：本服务纯前后端分离 + 无状态令牌鉴权——
        //   1) SessionCreationPolicy.STATELESS，服务端不建任何 HttpSession，浏览器 Cookie 里没有 JSESSIONID；
        //   2) 管理端凭证走自定义头 X-Admin-Token / Authorization: Bearer，浏览器同源策略禁止跨站脚本读取或复用；
        //   3) httpBasic / formLogin 均显式 disable，不存在依赖 Cookie 的自动认证通道。
        // 结论：无 ambient credential 可被跨站伪造携带，关闭 CSRF 安全。若未来引入 Cookie 会话需同步启用。
        http.csrf(AbstractHttpConfigurer::disable) // NOSONAR java:S4502 无 Cookie 会话，前后端分离令牌鉴权
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 鉴权职责移交集中过滤器，Security 链本身放行全部请求
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(adminTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 租户解析（P1-T3）：晚于鉴权过滤器，确保其可读 AdminTokenAuthFilter 落下的租户 claim；
                // saas.enabled=false 时该过滤器 shouldNotFilter 直接放行，等价单租户。
                .addFilterAfter(tenantResolverFilter, AdminTokenAuthFilter.class);
        // 平台端鉴权（P3-T1 / T-4 方案 B）：仅当平台开关开启（filter bean 存在）才挂链，只拦 /api/platform/**；
        // 与 AdminTokenAuthFilter 路径互斥（后者 PUBLIC_PREFIXES 已放行 /api/platform/），两套令牌互不可用。
        PlatformTokenAuthFilter platformFilter = platformTokenAuthFilterProvider.getIfAvailable();
        if (platformFilter != null) {
            http.addFilterAfter(platformFilter, AdminTokenAuthFilter.class);
        }
        return http.build();
    }
}
