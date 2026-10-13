package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户删除审批令牌视图对象（client 层出参，P3-T5 双人复核签发结果）。
 *
 * <p>{@code approvalToken} 是<b>仅此一次可见</b>的 raw token：签发响应体返回一次后，服务端只保留其 SHA-256 hash， 不再可回显。
 * 其余字段（状态 / 过期时间 / 签发者）供平台运维核对。不含任何长期凭据。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantDeleteApprovalVO {

    private Long approvalId;
    private String tenantKey;

    /**
     * 签发者（项目负责人）operator id。
     */
    private String approverOperatorId;

    /**
     * 令牌状态（PENDING / USED / EXPIRED / REVOKED）。
     */
    private String status;

    private LocalDateTime expiresAt;

    /**
     * raw 审批令牌，<b>仅签发响应体一次性返回</b>；查询 / 复用场景为 {@code null}。
     */
    private String approvalToken;
}
