package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.client.dto.AdminLoginCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginResultDTO;
import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 管理端登录命令执行器（app 层用例）。
 *
 * <p>校验用户名 + 密码（BCrypt）+ 账号激活态，通过后经 {@link AdminJwtService} 签发无状态 JWT 访问令牌。 与考生登录不同：JWT
 * 自包含身份与过期时间，<b>不落库 session_token</b>，故无需事务边界。
 *
 * <p>安全考量：账号不存在与密码错误统一返回“用户名或密码错误”，避免用户名枚举；未设置密码 （passwordHash 为空）的账号同样按此提示拒绝。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Component
@Slf4j
public class AdminLoginCmdExe {

    private final UserGateway userGateway;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminJwtService adminJwtService;

    public AdminLoginCmdExe(
            UserGateway userGateway,
            BCryptPasswordEncoder passwordEncoder,
            AdminJwtService adminJwtService) {
        this.userGateway = userGateway;
        this.passwordEncoder = passwordEncoder;
        this.adminJwtService = adminJwtService;
    }

    public AdminLoginResultDTO execute(AdminLoginCmd cmd) {
        if (cmd.getUsername() == null || cmd.getPassword() == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        User user =
                userGateway
                        .findByUsername(cmd.getUsername())
                        .filter(u -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                        .filter(
                                u ->
                                        passwordEncoder.matches(
                                                cmd.getPassword(), u.getPasswordHash()))
                        .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (!Boolean.TRUE.equals(user.getAdmin())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        String token =
                adminJwtService.issue(
                        user.getUserKey(),
                        user.getUsername(),
                        Boolean.TRUE.equals(user.getAdmin()));
        AdminTokenPayload payload =
                adminJwtService
                        .verify(token)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Issued token failed verification"));

        AdminLoginResultDTO result = new AdminLoginResultDTO();
        result.setToken(token);
        result.setUserKey(user.getUserKey());
        result.setUsername(user.getUsername());
        result.setAdmin(Boolean.TRUE.equals(user.getAdmin()));
        result.setExpiresAt(
                Optional.ofNullable(payload.expiresAt()).map(Object::toString).orElse(""));

        log.info("管理端登录成功: username='{}', userKey={}", user.getUsername(), user.getUserKey());
        return result;
    }
}
