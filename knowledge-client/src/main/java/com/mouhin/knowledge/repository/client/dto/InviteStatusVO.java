package com.mouhin.knowledge.repository.client.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 邀请码状态视图对象（client 层出参，P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>前端邀请落地页（{@code /set-password.html?code=XXX}）加载时先调用本视图判定 raw invite code 的当前状态， 以便在提交前就区分"码错 /
 * 已使用 / 已过期 / 已吊销"四种业务态并给出对应引导文案（"请向平台运维重新申请"等）， 避免用户填完新密码才被 400 拒绝。
 *
 * <p><b>安全边界</b>（红线 #4 + 反枚举）：
 *
 * <ul>
 *   <li>{@code status=UNKNOWN}（raw code 未命中库中任何 hash 行）时<b>不</b>回填 {@code tenantKeyHint} / {@code
 *       usernameHint} / {@code expiresAt}——攻击者无法通过反复试投枚举有效邀请码。
 *   <li>命中场景下 {@code tenantKeyHint} / {@code usernameHint} 一律<b>脱敏</b>（前 3 字符 + {@code …}），
 *       仅提供"我这一条链接属于哪个租户 / 哪个账号"的核对锚点，不泄漏完整标识。
 *   <li>响应体<b>不</b>回显 raw code、hash、inviteId 或 userId——前端已持有 raw code，无需回显；inviteId 留待平台运维通过审计日志查询。
 * </ul>
 *
 * <p>{@code status} 取值与本类的语义四态一一对应，其中 {@code EXPIRED} 由 {@code UserInvite#isExpired} 惰性判定， 数据库列可能仍为
 * {@code PENDING}（不主动写库）；对外统一呈现"惰性过期"视图，简化前端逻辑。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class InviteStatusVO {

    /** 邀请码语义状态；取值见本类四个 {@code STATUS_*} 常量。 */
    private String status;

    /** 邀请过期时间；仅 {@code STATUS_PENDING} 场景非空，其它态为 {@code null} 防枚举。 */
    private LocalDateTime expiresAt;

    /** 目标租户业务键脱敏（前 3 字符 + {@code …}）；仅命中场景非空。 */
    private String tenantKeyHint;

    /** 目标账号用户名脱敏（首字符 + {@code ***}）；仅命中场景非空。 */
    private String usernameHint;

    /** 面向用户的建议性提示文案（如"请使用新密码登录"/"链接已过期，请联系平台运维重新申请"）。 */
    private String hint;

    /** raw code 未在任何 hash 行命中（无效 / 拼错 / 库中不存在）。 */
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    /** raw code 命中且邀请处于 {@code PENDING} 未过期，可继续设密。 */
    public static final String STATUS_PENDING = "PENDING";

    /** raw code 命中但邀请已被前次设密消费（{@code USED}）。 */
    public static final String STATUS_USED = "USED";

    /** raw code 命中但邀请已过期（{@code now >= expiresAt}，数据库列可能仍为 PENDING）。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** raw code 命中但邀请已被平台运维吊销或重签覆盖（{@code REVOKED}）。 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 未命中态工厂：仅返回 status 与通用 hint，不携带任何可被枚举的字段。 */
    public static InviteStatusVO unknown() {
        InviteStatusVO vo = new InviteStatusVO();
        vo.setStatus(STATUS_UNKNOWN);
        vo.setHint("邀请码无效，请向平台运维重新申请");
        return vo;
    }
}
