package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台端租户归档命令（client 层入参，P3-T5：S-4=方案 A 保留期起点）。
 *
 * <p>{@code tenantKey} 定位目标租户；{@code reason} 为可选归档原因（写入审计 detail）；{@code operatorId}/{@code
 * operatorName} 由平台令牌 claim 注入，不由前端请求体提供。归档把 ACTIVE / SUSPENDED 租户迁入 ARCHIVED 并打 {@code archivedAt}
 * 保留期起点戳； 保留期内数据仍在，可恢复；过保留期 + 双人复核后才由 reconciler 硬删。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantArchiveCmd {

    /**
     * 目标租户业务键。
     */
    private String tenantKey;

    /**
     * 归档原因（可空，写入审计 detail）。
     */
    private String reason;

    /**
     * 操作者标识（来自平台令牌 claim，非请求体）。
     */
    private String operatorId;

    /**
     * 操作者展示名（来自平台令牌 claim，非请求体）。
     */
    private String operatorName;
}
