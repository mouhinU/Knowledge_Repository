package com.mouhin.knowledge.repository.domain.provisioning;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户开通上下文（P3-T2 引入，P3-T3 扩展 attributes 通道）。
 *
 * <p>在 {@link ProvisioningStep} 之间传递的载体：承载目标租户、本次请求的 UUID v4 幂等键、操作者身份， 以及一个可写的 {@link
 * #attributes()} 映射（P3-T3 起）。步骤实现从中读取所需信息；如需把结果向后续步骤 / 状态机出参传递（如首位 admin 的邀请 code），写入 attributes
 * 而非修改租户实体。
 *
 * <p><b>线程安全</b>：{@link #attributes()} 是 {@link ConcurrentHashMap}；状态机当前实现按步骤串行执行，
 * 但为将来"独立步骤并行化"预留安全语义。
 *
 * <p><b>属性键命名约定</b>：{@code provisioning.<name>}（如 {@link #ATTR_INVITE_INFO}），避免与将来其他用途冲突。
 *
 * @param tenant             目标租户聚合根（含 tenantId / tenantKey / status）
 * @param provisionRequestId 本次开通请求 UUID v4（S-5=方案 A 幂等键）
 * @param operatorId         操作者标识（平台端令牌携带）
 * @param operatorName       操作者显示名
 * @param attributes         步骤间共享的可写属性映射；由状态机在创建上下文时注入 {@link ConcurrentHashMap}
 * @author mouhinU
 * @date 2026-10-08
 */
public record ProvisioningContext(
        Tenant tenant,
        String provisionRequestId,
        String operatorId,
        String operatorName,
        Map<String, Object> attributes) {

    /**
     * 属性键：首位 admin 邀请信息（P3-T3）。
     *
     * <p>值为 {@code ProvisioningOutcome.InviteInfo}（app 层记录类型）；由 {@code CreateFirstAdminStep} 写入，
     * 状态机在所有步骤成功后读出并附到 {@code ProvisioningOutcome}。domain 层不感知具体类型， 保持依赖方向纯净（红线 #7）。
     */
    public static final String ATTR_INVITE_INFO = "provisioning.invite.info";

    /**
     * 便捷构造：为 attributes 注入默认 {@link ConcurrentHashMap}。
     *
     * @param tenant             目标租户
     * @param provisionRequestId 幂等键
     * @param operatorId         操作者 id
     * @param operatorName       操作者名
     */
    public ProvisioningContext(
            Tenant tenant, String provisionRequestId, String operatorId, String operatorName) {
        this(tenant, provisionRequestId, operatorId, operatorName, new ConcurrentHashMap<>());
    }

    /**
     * 便捷取租户主键。
     */
    public Long tenantId() {
        return tenant == null ? null : tenant.getTenantId();
    }

    /**
     * 便捷取租户业务键。
     */
    public String tenantKey() {
        return tenant == null ? null : tenant.getTenantKey();
    }
}
