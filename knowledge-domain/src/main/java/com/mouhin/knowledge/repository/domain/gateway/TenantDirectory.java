package com.mouhin.knowledge.repository.domain.gateway;

import java.util.Optional;

/**
 * 租户目录端口（domain 定义，infrastructure 以 {@code TenantIdResolver} 实现）。
 *
 * <p>把"租户键 ↔ 租户 id 映射 + 租户当前状态"这层<b>带缓存的只读目录</b>抽象为 domain 端口，使：
 *
 * <ul>
 *   <li><b>app 层</b>生命周期用例（{@code TenantSuspendCmdExe}/{@code TenantResumeCmdExe}）能在状态变更后失效缓存，
 *       而无需依赖 infrastructure 具体类（COLA 红线 #6/#7：app 只依赖 domain 接口）；
 *   <li><b>adapter 层</b>请求入口（{@code TenantResolverFilter}）能按租户状态做软拦（P3-T4：SUSPENDED → 403 {@code
 *       TENANT_SUSPENDED}），读到的是缓存加速后的状态，避免每请求回源 DB。
 * </ul>
 *
 * <p>缓存一致性由"写路径显式失效"保证：任何改变租户状态的操作（suspend/resume/provision）都应在落库后调用 {@link
 * #invalidateByKey(String)}（或 {@link #invalidateById(long)}），下次读取回源 DB 重建， 从而"suspend 后即时拒、resume
 * 后即时恢复"。当前阶段（Phase 3）单节点部署，不做 TTL；多节点场景需引入外部失效广播（Phase 4+）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
public interface TenantDirectory {

    /**
     * 查询租户当前状态（缓存加速；miss 时回源 DB）。
     *
     * <p><b>不抛异常</b>——未知 / 空白 key 返回 {@link Optional#empty()}，由调用方决定放行或拒绝。 请求路径软拦只关心"是否
     * SUSPENDED"，未知租户交由下游 {@code resolve} 严格校验。
     *
     * @param tenantKey 租户业务键（内部做 trim + 小写归一）
     * @return 状态字符串（取值同 {@code Tenant.STATUS_*}）；未知租户 {@link Optional#empty()}
     */
    Optional<String> findStatusByKey(String tenantKey);

    /**
     * 失效某个租户键的全部缓存（键↔id 双向映射 + 状态）。状态变更（suspend/resume）后调用，使下次读取回源。
     *
     * @param tenantKey 租户业务键；{@code null} 视为空操作
     */
    void invalidateByKey(String tenantKey);

    /**
     * 失效某个租户 id 的全部缓存（键↔id 双向映射 + 状态）。
     *
     * @param tenantId 租户数值主键
     */
    void invalidateById(long tenantId);

    /**
     * 全量清空缓存（配置热更 / 测试隔离使用）。
     */
    void invalidateAll();
}
