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
     * @return 已认证的管理员权限
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
        return new Permission(principal.getUserKey(), null, null, true);
    }
}
