package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;

import java.util.List;
import java.util.Optional;

/**
 * 租户删除步骤持久化契约（P3-T5）。
 *
 * <p>为 {@code TenantDeletionReconciler} 提供步骤级读写能力，是"步骤级幂等 + FAILED 可查 {@code error_detail} + 续跑"
 * 的存储抽象；镜像 {@link TenantProvisionStepGateway}。实现位于 infrastructure 层。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public interface TenantDeletionStepGateway {

    /**
     * 查询某租户某请求下某步骤的成功记录（用于续跑跳过判定）。
     *
     * @param tenantId          租户主键
     * @param deletionRequestId 删除请求 UUID v4
     * @param stepName          步骤名
     * @return 已 SUCCESS 的记录；不存在返回 {@link Optional#empty()}
     */
    Optional<TenantDeletionStep> findSuccess(
            Long tenantId, String deletionRequestId, String stepName);

    /**
     * 查询某租户某请求下某步骤的最大 {@code attemptNo}。
     *
     * @param tenantId          租户主键
     * @param deletionRequestId 删除请求 UUID v4
     * @param stepName          步骤名
     * @return 最大 attemptNo；不存在返回 0（首次尝试应为 1）
     */
    int findMaxAttemptNo(Long tenantId, String deletionRequestId, String stepName);

    /**
     * 列出某租户某请求下的全部步骤，按 {@code stepId} 升序（FAILED 排查用）。
     *
     * @param tenantId          租户主键
     * @param deletionRequestId 删除请求 UUID v4
     * @return 步骤列表；无记录返回空列表
     */
    List<TenantDeletionStep> listByRequest(Long tenantId, String deletionRequestId);

    /**
     * 插入新步骤行；成功后回填 {@code stepId} 与 {@code createTime/updateTime}。
     *
     * @param step 步骤实体，{@code stepId} 应为 {@code null}
     */
    void insert(TenantDeletionStep step);

    /**
     * 更新已存在步骤（typically RUNNING → SUCCESS/FAILED，回填 {@code finishedAt} / {@code rowsAffected} /
     * {@code errorDetail}）。
     *
     * @param step 步骤实体，{@code stepId} 必填
     */
    void update(TenantDeletionStep step);
}
