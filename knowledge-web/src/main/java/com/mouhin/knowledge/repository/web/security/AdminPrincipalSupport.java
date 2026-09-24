package com.mouhin.knowledge.repository.web.security;

import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理端认证主体读取与领域权限转换工具。
 *
 * @author mouhinU
 * @date 2026-09-24
 */
public final class AdminPrincipalSupport {

    private AdminPrincipalSupport() {}

    /**
     * 从管理端过滤器写入的请求属性读取主体，并转换为管理员权限上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 已认证的管理员权限（userId + departmentId 从 principal 回读，admin 恒真）
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
        // 身份来源锁定为已验证主体：userId + departmentId 从令牌回库读取；roles 由 sys_user_role
        // 关系维护（当前未接入，传 null 与旧契约保持一致）；admin 标志恒真——端点已被过滤器断言
        // 为管理员令牌。若未来引入受限管理员 persona 需要按角色过滤，在此读取 principal 调整即可，
        // 不再回退到请求体。
        return new Permission(principal.getUserKey(), principal.getDepartmentId(), null, true);
    }
}
