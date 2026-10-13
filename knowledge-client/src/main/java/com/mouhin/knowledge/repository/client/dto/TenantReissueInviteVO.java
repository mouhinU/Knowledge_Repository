package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台端重签邀请视图对象（client 层出参，P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>承载一次 {@code TenantReissueInviteCmd} 的签发结果：新 raw code 与其前端落地页相对 URL，加上目标 (tenantKey, username)
 * 与过期时间，供平台运维核对与转交。
 *
 * <p><b>安全</b>：{@code code} 是 raw invite code，<b>仅本次响应体一次性可见</b>（同 {@code
 * TenantProvisionVO.InviteVO.code}），服务端只保留其 SHA-256 hash，此后不可回显。运维须通过安全渠道（加密邮件 / IM
 * 私聊）转交租户管理员，禁止贴工单 / 群聊 / 日志。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantReissueInviteVO {

    /**
     * 目标租户业务键。
     */
    private String tenantKey;

    /**
     * 目标管理员用户名（租户内唯一，与 tenantKey 一起定位邀请归属）。
     */
    private String username;

    /**
     * raw invite code（Base64URL 无填充，43 字符）；<b>仅本次响应可见</b>。
     */
    private String code;

    /**
     * 前端设密页相对 URL：{@code /set-password.html?code=<code>}。
     */
    private String url;

    /**
     * 邀请过期时间（签发时刻 + TTL）。
     */
    private LocalDateTime expiresAt;

    /**
     * 本次签发吊销的旧 PENDING 邀请数（通常为 1，首次补签为 0）。供运维核对是否重签成功。
     */
    private int revokedPrevious;
}
