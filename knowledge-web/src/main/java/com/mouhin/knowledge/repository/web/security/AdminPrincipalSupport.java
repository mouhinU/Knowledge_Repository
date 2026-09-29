package com.mouhin.knowledge.repository.web.security;

import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 管理端认证主体读取与领域权限转换工具。
 *
 * <p>P0-T2 起 {@code roles} 由令牌 {@code roles} claim 直读，逗号拼接后传入 {@link Permission}； 旧令牌或缺失角色时 claim
 * 归一为空列表，转换结果与历史"恒 null"契约保持一致，避免下游权限判定 因空串与 null 分歧而漂移。
 *
 * @author mouhinU
 * @date 2026-09-24
 */
public final class AdminPrincipalSupport {

    private static final String ROLE_DELIMITER = ",";

    private AdminPrincipalSupport() {}

    /**
     * 从管理端过滤器写入的请求属性读取主体，并转换为管理员权限上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 已认证的管理员权限（userId + departmentId + roles 从令牌回读，admin 恒真）
     * @throws AdminAuthRequiredException 请求未携带有效管理员主体（映射 HTTP 401）
     */
    public static Permission toPermission(HttpServletRequest request) {
        Object attribute = request.getAttribute(AdminTokenAuthFilter.PRINCIPAL_ATTRIBUTE);
        if (!(attribute instanceof AdminPrincipalDTO principal)
                || principal.getUserKey() == null
                || principal.getUserKey().isBlank()
                || !Boolean.TRUE.equals(principal.getAdmin())) {
            throw new AdminAuthRequiredException("管理端身份不可用");
        }
        // 身份来源锁定为已验证主体：userId + departmentId + roles 从令牌回读；
        // admin 标志恒真——端点已被过滤器断言为管理员令牌。
        // 若未来引入受限管理员 persona 需要按角色过滤，在此读取 principal 调整即可，
        // 不再回退到请求体。
        return new Permission(
                principal.getUserKey(),
                principal.getDepartmentId(),
                joinRoles(principal.getRoles()),
                true);
    }

    /** 空列表归一为 null，保持与 Permission 旧契约一致（"null / 空 = 不过滤"）。 */
    private static String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return String.join(ROLE_DELIMITER, roles);
    }
}
