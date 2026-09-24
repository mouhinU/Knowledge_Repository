package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.client.dto.AdminChangePasswordCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginResultDTO;
import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.web.security.AdminTokenAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证控制器（adapter 层）。
 *
 * <p>提供登录 / 登出 / 当前身份 / 自助改密四个端点。{@code /login}、{@code /logout} 在过滤器放行清单内， 无需令牌即可达；{@code
 * /me}、{@code /change-password} 由 {@link AdminTokenAuthFilter} 先行校验令牌， 控制器从请求属性读取已认证身份，其中改密的 {@code
 * userKey} 一律以令牌身份为准，忽略请求体传入，防越权。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@RestController
@RequestMapping("/api/admin/auth")
@Slf4j
public class AdminAuthController {

    /** 统一响应 map 的 error / message 字段名（java:S1192 抽公共 key）。 */
    private static final String ERR_FIELD = "error";

    private static final String MSG_FIELD = "message";

    private final AdminAuthServiceI adminAuthService;

    public AdminAuthController(AdminAuthServiceI adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /** 管理端登录：用户名密码换取访问令牌。 */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AdminLoginCmd cmd) {
        try {
            AdminLoginResultDTO data = adminAuthService.login(cmd).getData();
            return ResponseEntity.ok(
                    Map.of(
                            MSG_FIELD,
                            "登录成功",
                            "token",
                            data.getToken(),
                            "userKey",
                            data.getUserKey(),
                            "username",
                            data.getUsername(),
                            "admin",
                            Boolean.TRUE.equals(data.getAdmin()),
                            "expiresAt",
                            data.getExpiresAt()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    ERR_FIELD,
                                    e.getMessage(),
                                    "code",
                                    HttpStatus.UNAUTHORIZED.value()));
        }
    }

    /** 管理端登出：JWT 无状态，服务端仅审计，前端丢弃本地令牌。 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = AdminTokenAuthFilter.TOKEN_HEADER, required = false)
                    String headerToken,
            @RequestBody(required = false) Map<String, String> body) {
        String token = headerToken;
        if (token == null && body != null) {
            token = body.get("token");
        }
        if (token != null) {
            adminAuthService.logout(token);
        }
        return ResponseEntity.ok(Map.of(MSG_FIELD, "已退出"));
    }

    /** 获取当前登录者身份（令牌已由过滤器校验，直接取回属性）。 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        AdminPrincipalDTO principal = currentPrincipal(request);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(ERR_FIELD, "未登录", "code", HttpStatus.UNAUTHORIZED.value()));
        }
        return ResponseEntity.ok(
                Map.of(
                        "userKey", principal.getUserKey(),
                        "username", principal.getUsername(),
                        "admin", Boolean.TRUE.equals(principal.getAdmin())));
    }

    /** 自助修改密码：以令牌身份为准，校验旧密码后设置新密码。 */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody AdminChangePasswordCmd cmd, HttpServletRequest request) {
        AdminPrincipalDTO principal = currentPrincipal(request);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(ERR_FIELD, "未登录", "code", HttpStatus.UNAUTHORIZED.value()));
        }
        cmd.setUserKey(principal.getUserKey());
        try {
            adminAuthService.changePassword(cmd);
            return ResponseEntity.ok(Map.of(MSG_FIELD, "密码修改成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERR_FIELD, "密码修改请求不合法"));
        }
    }

    private AdminPrincipalDTO currentPrincipal(HttpServletRequest request) {
        Object attribute = request.getAttribute(AdminTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        if (attribute instanceof AdminPrincipalDTO principal) {
            return principal;
        }
        log.debug("请求属性中无管理端身份，回退按令牌头校验");
        String headerToken = request.getHeader(AdminTokenAuthFilter.TOKEN_HEADER);
        return adminAuthService.validateToken(headerToken).getData();
    }
}
