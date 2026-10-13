package com.mouhin.knowledge.repository.client.api;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.client.dto.*;

/**
 * 管理端认证应用服务契约（client 层）。
 *
 * <p>以用户名 / 密码登录换取无状态 JWT 访问令牌，替代原先的 HTTP Basic（内存单管理员）。返回值统一用 COLA 契约类型：登录成功 {@code data} 为 {@link
 * AdminLoginResultDTO}；账号不存在 / 密码错误 / 已禁用 抛 {@link IllegalArgumentException}，由适配层转为 401 /
 * 400。令牌校验（{@link #validateToken}）在签名 无效、过期或账号已禁用时返回 {@code data} 为 null 的成功响应，由适配层 / 过滤器映射为 401。
 *
 * <p><b>P3-T3 扩展</b>：{@link #setPasswordByInvite} 用于新租户首位 admin 通过一次性邀请码完成首次设密， 无需登录令牌（凭 raw invite
 * code 换设密权限），端点在 {@code AdminTokenAuthFilter.PUBLIC_PREFIXES} 白名单里。
 *
 * <p><b>P3-T7 邀请式设密<b>回访</b>扩展</b>：{@link #queryInviteStatus} 供前端邀请落地页在提交新密码前 判定 raw code
 * 的当前语义状态（{@code PENDING / USED / EXPIRED / REVOKED / UNKNOWN}），以给出"码错 / 已过期 /
 * 已被使用"的差异化引导，避免用户填完新密码才被 400 拒绝。本方法亦在 PUBLIC_PREFIXES 白名单内，凭 raw code 换查询权限， 响应体做严格脱敏（详见 {@link
 * InviteStatusVO}）。
 *
 * @author mouhinU
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

    /**
     * 邀请式首次设密（P3-T3）：raw invite code + 新密码 → 落库 password_hash + must_change_password=0 + 邀请状态
     * USED。<b>不</b>签发任何令牌；管理员随后走 {@link #login} 换正式 admin token。
     *
     * @param cmd 邀请码 + 新密码
     * @return 结果 VO（含 username / tenantKey / 提示消息）
     * @throws IllegalArgumentException 邀请码无效 / 已过期 / 已使用 / 新密码长度越界
     */
    SingleResponse<SetPasswordByInviteVO> setPasswordByInvite(SetPasswordByInviteCmd cmd);

    /**
     * 邀请码状态查询（P3-T7 回访）：raw invite code → 语义状态视图（脱敏 hint，反枚举）。
     *
     * <p>只读、不写审计、不修改邀请状态。用于前端落地页在提交新密码前的预检；亦可用作运维脚本 / 集成测试的验证锚点。
     *
     * @param rawCode 原始邀请码（前端 URL query 值原样传入）
     * @return 状态视图；{@code rawCode} 为空或未命中时 {@code status=UNKNOWN}，不携带任何 hint 字段
     */
    SingleResponse<InviteStatusVO> queryInviteStatus(String rawCode);
}
