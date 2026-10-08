package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import java.util.List;
import java.util.Optional;

/**
 * 租户开通步骤持久化契约（P3-T2）。
 *
 * <p>为 {@code ProvisioningStateMachine} 提供步骤级读写能力，是 S-5=方案 A "步骤级幂等 + FAILED 可查 {@code
 * error_detail}" 的存储抽象。实现位于 infrastructure 层。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface TenantProvisionStepGateway {

    /**
     * 查询某租户某请求下某步骤的成功记录（用于跳过判定）。
     *
     * @param tenantId 租户主键
     * @param provisionRequestId 开通请求 UUID v4
     * @param stepName 步骤名
     * @return 已 SUCCESS 的记录；不存在返回 {@link Optional#empty()}
     */
    Optional<TenantProvisionStep> findSuccess(
            Long tenantId, String provisionRequestId, String stepName);

    /**
     * 查询某租户某请求下某步骤的最大 {@code attemptNo}。
     *
     * @param tenantId 租户主键
     * @param provisionRequestId 开通请求 UUID v4
     * @param stepName 步骤名
     * @return 最大 attemptNo；不存在返回 0（首次尝试应为 1）
     */
    int findMaxAttemptNo(Long tenantId, String provisionRequestId, String stepName);

    /**
     * 列出某租户某请求下的全部步骤，按 {@code stepId} 升序（FAILED 排查用）。
     *
     * @param tenantId 租户主键
     * @param provisionRequestId 开通请求 UUID v4
     * @return 步骤列表；无记录返回空列表
     */
    List<TenantProvisionStep> listByRequest(Long tenantId, String provisionRequestId);

    /**
     * 插入新步骤行；成功后回填 {@code stepId} 与 {@code createTime/updateTime}。
     *
     * @param step 步骤实体，{@code stepId} 应为 {@code null}
     */
    void insert(TenantProvisionStep step);

    /**
     * 更新已存在步骤（typically RUNNING → SUCCESS/FAILED，回填 {@code finishedAt} / {@code errorDetail}）。
     *
     * @param step 步骤实体，{@code stepId} 必填
     */
    void update(TenantProvisionStep step);
}
