package com.mouhin.knowledge.repository.client.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

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

    /** 当前 provision 请求 UUID v4（P3-T2，S-5=方案 A 幂等键）。 */
    private String provisionRequestId;

    private String dataRegion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
