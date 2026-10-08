package com.mouhin.knowledge.repository.client.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户开通结果视图对象（client 层出参，P3-T2）。
 *
 * <p>承载一次 {@code ProvisioningStateMachine} 编排的终态与各步骤明细，供平台端 {@code POST
 * /api/platform/tenants/{tenantKey}/provision} 响应体使用。FAILED 时 {@code steps} 内含失败步骤的 {@code
 * errorDetail}，满足 S-5=方案 A "FAILED 可查询原因"。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Getter
@Setter
public class TenantProvisionVO {

    /** 目标租户业务键。 */
    private String tenantKey;

    /** 本次请求 UUID v4（幂等键）。 */
    private String provisionRequestId;

    /** 租户终态：{@code ACTIVE} / {@code FAILED}。 */
    private String finalStatus;

    /** 是否开通成功。 */
    private boolean success;

    /** 各步骤执行结果，按执行顺序。 */
    private List<StepVO> steps;

    /**
     * 单步骤执行结果。
     *
     * @author mouhinU
     * @date 2026-10-08
     */
    @Getter
    @Setter
    public static class StepVO {

        /** 步骤名。 */
        private String stepName;

        /** {@code SUCCESS} / {@code FAILED} / {@code SKIPPED}。 */
        private String status;

        /** 本次尝试序号。 */
        private int attemptNo;

        /** 失败原因（成功 / 跳过为 {@code null}）。 */
        private String errorDetail;
    }
}
