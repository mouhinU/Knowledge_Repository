package com.mouhin.knowledge.repository.application.service;

import java.util.List;

/**
 * 一次租户开通编排的结果快照（P3-T2）。
 *
 * <p>由 {@link ProvisioningStateMachine#provision} 返回，供 {@code TenantProvisionCmdExe} 转 VO。
 * 不落库，仅进程内传递。
 *
 * @param provisionRequestId 本次请求 UUID v4（S-5=方案 A 幂等键）
 * @param finalStatus 租户终态（{@code ACTIVE} / {@code FAILED}）
 * @param steps 各步骤执行结果，按执行顺序
 * @author mouhinU
 * @date 2026-10-08
 */
public record ProvisioningOutcome(
        String provisionRequestId, String finalStatus, List<StepOutcome> steps) {

    /** 是否开通成功（终态 ACTIVE）。 */
    public boolean isSuccess() {
        return "ACTIVE".equals(finalStatus);
    }

    /**
     * 单步骤执行结果。
     *
     * @param stepName 步骤名
     * @param status {@code SUCCESS} / {@code FAILED} / {@code SKIPPED}（重放时前次已成功）
     * @param attemptNo 本次尝试序号
     * @param errorDetail 失败原因（成功 / 跳过为 {@code null}）
     */
    public record StepOutcome(String stepName, String status, int attemptNo, String errorDetail) {}
}
