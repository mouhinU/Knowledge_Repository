package com.mouhin.knowledge.repository.domain.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * 租户删除步骤记录（P3-T5，映射 {@code sys_tenant_deletion_step}）。
 *
 * <p>镜像 {@link TenantProvisionStep} 的步骤级幂等载体：一次 {@code deletionRequestId} 下每个步骤每次尝试对应一行； {@code
 * (tenantId, deletionRequestId, stepName, attemptNo)} 四元组唯一，保证 reconciler 续跑幂等—— 已 {@code SUCCESS}
 * 的步骤在续跑时跳过（不新增行），失败的步骤 {@code attemptNo+1} 后重跑。
 *
 * <p>相较开通步骤多一个 {@code rowsAffected}：硬删是破坏性操作，逐步骤落删除行数便于事后审计与对账（S-4=方案 A 审计诉求）。
 *
 * <p>本实体位于 domain 层，纯字段承载 + 简单状态判定；步骤执行编排在 app 层 {@code TenantDeletionReconciler}。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Getter
@Setter
public class TenantDeletionStep {

    /** 步骤状态：执行中。 */
    public static final String STATUS_RUNNING = "RUNNING";

    /** 步骤状态：成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 步骤状态：失败（可通过 {@code attemptNo+1} 续跑）。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 步骤状态：跳过（本次续跑前次已 SUCCESS）。 */
    public static final String STATUS_SKIPPED = "SKIPPED";

    /** {@code errorDetail} 截断上限，与 DDL VARCHAR(2000) 对齐。 */
    public static final int ERROR_DETAIL_MAX_LENGTH = 2000;

    private Long stepId;

    private Long tenantId;

    /** 删除请求 UUID v4（删除幂等键）。 */
    private String deletionRequestId;

    /** 步骤名（如 {@code PURGE_DB_ROWS} / {@code PURGE_VECTORS}）。 */
    private String stepName;

    /** 第几次尝试，从 1 起；FAILED 续跑时 +1。 */
    private Integer attemptNo;

    /** 步骤状态，见本类 {@code STATUS_*} 常量。 */
    private String status;

    /** 本步影响行数（硬删审计用；成功时回填，失败/跳过可为 {@code null}）。 */
    private Long rowsAffected;

    /** 失败原因，截断至 {@link #ERROR_DETAIL_MAX_LENGTH}。 */
    private String errorDetail;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 将 {@code errorDetail} 截断至 {@link #ERROR_DETAIL_MAX_LENGTH}，避免超出 DDL 列宽导致写入失败。
     *
     * @param raw 原始错误文本，可为 {@code null}
     * @return 截断后的字符串；{@code null} 输入返回 {@code null}
     */
    public static String truncateErrorDetail(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.length() <= ERROR_DETAIL_MAX_LENGTH
                ? raw
                : raw.substring(0, ERROR_DETAIL_MAX_LENGTH);
    }

    /** 是否已成功（续跑时的跳过判定）。 */
    public boolean isSuccess() {
        return STATUS_SUCCESS.equals(status);
    }

    /** 是否已失败。 */
    public boolean isFailed() {
        return STATUS_FAILED.equals(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TenantDeletionStep that = (TenantDeletionStep) o;
        return Objects.equals(stepId, that.stepId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stepId);
    }

    @Override
    public String toString() {
        return "TenantDeletionStep{"
                + "stepId="
                + stepId
                + ", tenantId="
                + tenantId
                + ", stepName='"
                + stepName
                + '\''
                + ", attemptNo="
                + attemptNo
                + ", status='"
                + status
                + '\''
                + ", rowsAffected="
                + rowsAffected
                + '}';
    }
}
