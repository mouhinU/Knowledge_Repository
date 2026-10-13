package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 租户删除预览视图对象（client 层出参，P3-T5 dry-run，不执行任何删除）。
 *
 * <p>供平台运维在签发 / 确认删除前核对：目标租户当前状态、归档时间、保留期是否已过（{@code retentionElapsed}）、 可删除的最早时点（{@code
 * eligibleAt}），以及各业务表待删行数（{@code rowsByTable}）与合计（{@code totalRows}）。 文件层（MinIO）清理受 P5-T1
 * 约束延后，本预览只覆盖 DB 行。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantDeletionPreviewVO {

    private String tenantKey;
    private String status;
    private LocalDateTime archivedAt;

    /**
     * 保留期截止点（{@code archivedAt + retentionDays}）；未归档为 {@code null}。
     */
    private LocalDateTime eligibleAt;

    /**
     * 保留期是否已过（{@code eligibleAt} 非空且不晚于当前时间）。
     */
    private boolean retentionElapsed;

    /**
     * 保留期天数（S-4=方案 A，默认 30）。
     */
    private int retentionDays;

    /**
     * 各业务表待删行数（表名 → 行数，稳定序）；未归档或不可预览时为空 Map。
     */
    private Map<String, Long> rowsByTable;

    /**
     * 待删行数合计。
     */
    private long totalRows;
}
