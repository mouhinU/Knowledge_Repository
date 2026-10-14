package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.application.converter.TenantVoConverter;
import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 平台端列出租户执行器（app 层用例，只读，P3-T1）。
 *
 * <p>租户数量级 ≤ 千，本期不分页（与 {@link TenantGateway#listAll()} 契约一致）；上量后按需扩展分页 / 条件检索。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class TenantListQryExe {

    private final TenantGateway tenantGateway;

    public TenantListQryExe(TenantGateway tenantGateway) {
        this.tenantGateway = tenantGateway;
    }

    public List<TenantVO> execute() {
        return tenantGateway.listAll().stream().map(TenantVoConverter.INSTANCE::toVO).toList();
    }
}
