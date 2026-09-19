package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.AdminChangePasswordCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginResultDTO;
import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;

/**
 * 管理端认证应用服务契约（client 层）。
 *
 * <p>以用户名 / 密码登录换取无状态 JWT 访问令牌，替代原先的 HTTP Basic（内存单管理员）。返回值统一用
 * COLA 契约类型：登录成功 {@code data} 为 {@link AdminLoginResultDTO}；账号不存在 / 密码错误 / 已禁用
 * 抛 {@link IllegalArgumentException}，由适配层转为 401 / 400。令牌校验（{@link #validateToken}）在签名
 * 无效、过期或账号已禁用时返回 {@code data} 为 null 的成功响应，由适配层 / 过滤器映射为 401。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
public interface AdminAuthServiceI {

    /** 用户名密码登录，签发访问令牌。 */
    SingleResponse<AdminLoginResultDTO> login(AdminLoginCmd cmd);

    /** 退出登录。JWT 无状态，服务端仅记录审计、令牌由前端丢弃。 */
    Response logout(String token);

    /** 校验令牌并还原当前登录者身份（签名 / 过期 / 账号激活态三重判定）。 */
    SingleResponse<AdminPrincipalDTO> validateToken(String token);

    /** 自助修改密码：校验旧密码后设置新密码。 */
    Response changePassword(AdminChangePasswordCmd cmd);
}
