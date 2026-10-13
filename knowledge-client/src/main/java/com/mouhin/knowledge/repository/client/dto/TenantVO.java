package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户视图对象（client 层出参，P3-T1 平台端管理视图）。
 *
 * <p>承载 {@code sys_tenant} 的可对外字段，供 {@code PlatformTenantController} 的 get / list / create /
 * suspend / resume 响应体使用。不含任何敏感信息（无密钥、无令牌）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantVO {

    private Long tenantId;
    private String tenantKey;
    private String name;
    private String plan;
    private String status;

    /**
     * 当前 provision 请求 UUID v4（P3-T2，S-5=方案 A 幂等键）。
     */
    private String provisionRequestId;

    /**
     * 当前删除请求 UUID v4（P3-T5，删除幂等键）；未进入删除流程为 {@code null}。
     */
    private String deletionRequestId;

    /**
     * 归档时间（P3-T5，S-4 保留期起点）；非归档态为 {@code null}。
     */
    private LocalDateTime archivedAt;

    /**
     * 硬删完成时间（P3-T5，DELETED 终态戳）；未完成为 {@code null}。
     */
    private LocalDateTime deletedAt;

    private String dataRegion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
