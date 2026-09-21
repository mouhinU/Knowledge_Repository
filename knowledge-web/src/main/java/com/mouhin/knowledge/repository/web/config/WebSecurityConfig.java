package com.mouhin.knowledge.repository.web.config;

import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.web.security.AdminTokenAuthFilter;
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
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final AdminAuthServiceI adminAuthService;

    public WebSecurityConfig(AdminAuthServiceI adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Bean
    public AdminTokenAuthFilter adminTokenAuthFilter() {
        return new AdminTokenAuthFilter(adminAuthService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, AdminTokenAuthFilter adminTokenAuthFilter) throws Exception {
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
                .addFilterBefore(adminTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
