package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;

import java.util.List;
import java.util.Optional;

/**
 * 租户删除审批令牌持久化契约（P3-T5，双人复核载体）。
 *
 * <p>镜像 {@code UserInviteGateway} 的一次性码读写能力：签发落库、按 hash 精确查、按租户列 PENDING、消费 / 撤销回写。 实现位于
 * infrastructure 层（{@code sys_tenant_delete_approval} 表，其 {@code tenant_id} 是业务列，须进 IGNORE_TABLES
 * 白名单）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
public interface TenantDeleteApprovalGateway {

    /**
     * 按 token hash 精确查询审批令牌（第二操作者确认删除时用；不判定过期 / 状态，交由调用方按 {@code isUsable} 决策）。
     *
     * @param tokenHash SHA-256(raw token) hex
     * @return 命中的审批实体（可能为任意状态）；未命中返回 {@link Optional#empty()}
     */
    Optional<TenantDeleteApproval> findByTokenHash(String tokenHash);

    /**
     * 列出某租户当前 PENDING 的审批令牌（重签前吊销旧令牌用）。
     *
     * @param tenantId 租户主键
     * @return PENDING 审批列表；无则空列表
     */
    List<TenantDeleteApproval> findPendingByTenant(Long tenantId);

    /**
     * 插入新审批行；成功后回填 {@code approvalId} 与 {@code createTime/updateTime}。
     *
     * @param approval 审批实体，{@code approvalId} 应为 {@code null}
     */
    void insert(TenantDeleteApproval approval);

    /**
     * 更新已存在审批（消费 / 撤销 / 懒过期回写）。
     *
     * @param approval 审批实体，{@code approvalId} 必填
     */
    void update(TenantDeleteApproval approval);
}
