package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.valueobject.AdminTokenPayload;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 管理端令牌校验查询执行器（app 层用例）。
 *
 * <p>双重判定：先经 {@link AdminJwtService} 校验令牌签名与有效期，再回库确认账号仍处激活态——这样 令牌在被吊销 / 账号被禁用后立即失效，弥补 JWT
 * 无状态无法主动失效的短板。任一环节不通过即返回 {@code null}（由调用方映射为 401）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class AdminValidateTokenQryExe {

    private final AdminJwtService adminJwtService;
    private final UserGateway userGateway;

    public AdminValidateTokenQryExe(AdminJwtService adminJwtService, UserGateway userGateway) {
        this.adminJwtService = adminJwtService;
        this.userGateway = userGateway;
    }

    public AdminPrincipalDTO execute(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Optional<AdminTokenPayload> verified = adminJwtService.verify(token);
        if (verified.isEmpty()) {
            return null;
        }
        AdminTokenPayload payload = verified.get();
        User user = userGateway.findByUserKey(payload.userKey()).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        return new AdminPrincipalDTO(
                user.getUserKey(), user.getUsername(), Boolean.TRUE.equals(user.getAdmin()));
    }
}
