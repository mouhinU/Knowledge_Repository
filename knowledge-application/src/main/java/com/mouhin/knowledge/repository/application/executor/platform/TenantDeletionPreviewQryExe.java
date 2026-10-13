package com.mouhin.knowledge.repository.application.executor.platform;

import com.mouhin.knowledge.repository.client.dto.TenantDeletionPreviewVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantDataPurgeGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.valueobject.TenantKey;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 平台端租户删除预览查询执行器（app 层用例，只读 dry-run，P3-T5）。
 *
 * <p>供签发 / 确认删除前核对：目标租户状态、归档时间、保留期是否已过、可删最早时点，以及各业务表待删行数与合计。 <b>不执行任何删除</b>——只经 {@link
 * TenantDataPurgeGateway#countTenantRows(long)} 统计。文件层（MinIO）清理受 P5-T1 约束延后，本预览只覆盖 DB 行。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
public class TenantDeletionPreviewQryExe {

    private final TenantGateway tenantGateway;
    private final TenantDataPurgeGateway tenantDataPurgeGateway;

    /** S-4=方案 A 保留期天数（默认 30），与 {@code ConfirmTenantDeletionCmdExe} 同源配置。 */
    @Value("${knowledge.saas.tenant-deletion.retention-days:30}")
    private int retentionDays;

    public TenantDeletionPreviewQryExe(
            TenantGateway tenantGateway, TenantDataPurgeGateway tenantDataPurgeGateway) {
        this.tenantGateway = tenantGateway;
        this.tenantDataPurgeGateway = tenantDataPurgeGateway;
    }

    public TenantDeletionPreviewVO execute(String rawTenantKey) {
        String tenantKey = TenantKey.normalize(rawTenantKey);
        Tenant tenant =
                tenantGateway
                        .findByTenantKey(tenantKey)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Tenant not found: " + tenantKey));

        LocalDateTime now = LocalDateTime.now();
        TenantDeletionPreviewVO vo = new TenantDeletionPreviewVO();
        vo.setTenantKey(tenantKey);
        vo.setStatus(tenant.getStatus());
        vo.setArchivedAt(tenant.getArchivedAt());
        vo.setRetentionDays(retentionDays);

        LocalDateTime eligibleAt =
                tenant.getArchivedAt() == null
                        ? null
                        : tenant.getArchivedAt().plusDays(Math.max(0, retentionDays));
        vo.setEligibleAt(eligibleAt);
        vo.setRetentionElapsed(eligibleAt != null && !now.isBefore(eligibleAt));

        Map<String, Long> rows =
                tenant.getTenantId() == null
                        ? new LinkedHashMap<>()
                        : tenantDataPurgeGateway.countTenantRows(tenant.getTenantId());
        vo.setRowsByTable(rows);
        vo.setTotalRows(rows.values().stream().mapToLong(Long::longValue).sum());
        return vo;
    }
}
