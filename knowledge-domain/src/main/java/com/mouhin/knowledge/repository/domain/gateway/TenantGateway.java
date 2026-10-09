package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 租户仓储接口（domain 定义，infrastructure 以 {@code TenantGatewayImpl} 实现）。
 *
 * <p>面向 {@code sys_tenant} 平台级主表的读写契约。遵循 COLA 红线 #6/#7：app 层 Executor 只依赖本接口， 不直连 {@code
 * TenantMapper} 或 {@code TenantDO}；DO ⇄ Entity 转换收敛在 infrastructure 边界。
 *
 * <p>P3-T1 交付平台端生命周期管理所需的最小读集（按 key / id 查、列表）与写集（新增、状态更新）。 分页 / 条件检索在租户数量上量后（Phase 4）再按需扩展，避免过早抽象。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface TenantGateway {

    /** 按数值主键查询租户。 */
    Optional<Tenant> findById(Long tenantId);

    /** 按业务键（小写归一后）查询租户。 */
    Optional<Tenant> findByTenantKey(String tenantKey);

    /** 列出全部租户（平台端管理视图，租户数量级 ≤ 千，暂不分页）。 */
    List<Tenant> listAll();

    /**
     * 按生命周期状态列出租户（P3-T5 reconciler 扫描 {@code DELETING} 认领态用），按 {@code tenantId} 升序取前 {@code limit}
     * 条。
     *
     * @param status 状态取值，见 {@code Tenant.STATUS_*}
     * @param limit 单轮批量上限（&le;0 视为不分页上限，实现方须自我保护）
     * @return 命中租户列表；无命中返回空列表
     */
    List<Tenant> listByStatus(String status, int limit);

    /**
     * 列出归档时间早于保留期截止点的 ARCHIVED 租户（S-4=方案 A 保留期到期扫描），按 {@code archivedAt} 升序、{@code tenantId} 升序取前
     * {@code limit} 条。
     *
     * @param cutoff 保留期截止点（{@code archivedAt < cutoff} 视为已到期）
     * @param limit 单轮批量上限
     * @return 到期归档租户列表；无命中返回空列表
     */
    List<Tenant> listArchivedBefore(LocalDateTime cutoff, int limit);

    /** 新增租户，回填自增主键到入参实体。 */
    void save(Tenant tenant);

    /** 更新租户（含状态流转后的落库）。 */
    void update(Tenant tenant);
}
