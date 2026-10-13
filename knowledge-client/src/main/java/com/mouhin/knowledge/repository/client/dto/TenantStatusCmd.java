package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端租户状态流转命令（client 层入参，P3-T1：suspend / resume 共用）。
 *
 * <p>{@code tenantKey} 定位目标租户；{@code reason} 为可选的流转原因（写入审计日志 detail，便于合规追溯）； {@code
 * operatorId}/{@code operatorName} 由平台令牌 claim 注入，不由前端请求体提供。
 *
 * <p>归档 / 删除（archive / delete）本期仅落软状态流转骨架，完整编排属 P3-T5，届时另立命令对象承载保留期 / 双人复核等参数。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantStatusCmd {

    /** 目标租户业务键。 */
    private String tenantKey;

    /** 流转原因（可空，写入审计 detail）。 */
    private String reason;

    /** 操作者标识（来自平台令牌 claim，非请求体）。 */
    private String operatorId;

    /** 操作者展示名（来自平台令牌 claim，非请求体）。 */
    private String operatorName;
}
