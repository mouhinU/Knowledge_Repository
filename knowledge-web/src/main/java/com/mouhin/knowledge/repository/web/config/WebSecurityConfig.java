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
 * <p>
 * 管理端鉴权已从 HTTP Basic（内存单管理员）迁移为<b>用户名 / 密码登录 + 无状态 JWT</b>。此处不再声明
 * {@code InMemoryUserDetailsManager} 与 {@code httpBasic}：Spring Security 链对本服务所有请求放行，
 * 真正的鉴权由 {@link AdminTokenAuthFilter} 集中在链上完成——校验 {@code X-Admin-Token}（或 Bearer）
 * 令牌的签名、有效期与账号激活态，不通过即 401。
 * </p>
 * <p>
 * 保留无状态（STATELESS）与会话禁用、CSRF 关闭（纯令牌前后端分离）。健康探针
 * （{@code /actuator/health}）与静态资源天然匿名可达；考生侧 {@code /api/student/**}、{@code /api/exam/**}
 * 由 app 层校验学生 token，均在过滤器放行清单内，不经管理端令牌。
 * </p>
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AdminTokenAuthFilter adminTokenAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 鉴权职责移交集中过滤器，Security 链本身放行全部请求
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(adminTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
