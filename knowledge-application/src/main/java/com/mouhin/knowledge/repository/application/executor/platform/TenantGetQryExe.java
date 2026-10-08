package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import org.springframework.stereotype.Component;

/**
 * 平台端查询单个租户执行器（app 层用例，只读，P3-T1）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class TenantGetQryExe {

    private final TenantGateway tenantGateway;

    public TenantGetQryExe(TenantGateway tenantGateway) {
        this.tenantGateway = tenantGateway;
    }

    /**
     * 按业务键查询租户。
     *
     * @param tenantKey 租户业务键（归一化后查询）
     * @return 租户 VO
     * @throws IllegalArgumentException 租户不存在
     */
    public TenantVO execute(String tenantKey) {
        String normalized = TenantKey.normalize(tenantKey);
        return tenantGateway
                .findByTenantKey(normalized)
                .map(TenantVoConverter::toVO)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + normalized));
    }
}
