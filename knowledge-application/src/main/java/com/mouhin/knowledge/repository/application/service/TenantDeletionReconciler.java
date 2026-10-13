package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.domain.constant.AgentNames;
import com.mouhin.knowledge.repository.domain.gateway.*;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 租户删除对账器（app 层，P3-T5；S-4=方案 A 硬删执行引擎）。
 *
 * <p>把 {@code ConfirmTenantDeletionCmdExe} 认领为 {@link Tenant#STATUS_DELETING} 的租户，异步、可续跑、逐步骤幂等地硬删：
 *
 * <ol>
 *   <li>{@code PURGE_DB_ROWS}：经 {@link TenantDataPurgeGateway#purgeTenantRows(long)} 按 {@code
 *       tenant_id} 清空全部业务表；
 *   <li>{@code PURGE_VECTORS}：经 {@link VectorStoreGateway#deleteByTenantId(long)} 清空 Milvus 该租户向量；
 *   <li>两步皆 SUCCESS → {@link Tenant#markDeleted(LocalDateTime)} 迁入 DELETED 终态 + 失效目录缓存 + 审计
 *       SUCCESS。
 * </ol>
 *
 * <p><b>步骤级幂等 / 续跑</b>：镜像 {@code ProvisioningStateMachine} —— 每步先查 {@code findSuccess}，命中记 SKIPPED
 * 跳过； 否则 {@code attemptNo = findMaxAttemptNo + 1} 插入 RUNNING 行、执行、回填 SUCCESS（含 {@code
 * rowsAffected}）/ FAILED。 任一步失败即审计 FAILED 并<b>保留 DELETING 态</b>，下轮定时扫描自动续跑（{@code attemptNo+1}），
 * 前次已 SUCCESS 的步骤跳过。清除动作本身幂等（重删返回 0 行 / removeAll 空操作），故续跑安全。
 *
 * <p><b>事务边界</b>：本类<b>不</b>包裹单个大事务——每步的 {@code insert/update}、清除、状态迁移各自独立提交，这样"中断后续跑"才能看到前次已
 * SUCCESS 的步骤（红线 #8：破坏性长耗时 IO 不入请求事务）。
 *
 * <p><b>门控</b>：仅当 {@code knowledge.platform.enabled=true} 才装配本 bean（{@link ConditionalOnProperty}）；
 * 默认关闭态下不被 Spring 加载，{@code @Scheduled} 不注册，绝不触碰任何数据（红线 #11 双闸门）。
 *
 * <p><b>范围</b>：只清 DB 行 + Milvus 向量两处；文件层（MinIO）物理清理受 P5-T1 约束延后。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "knowledge.platform.enabled", havingValue = "true")
public class TenantDeletionReconciler {

    /** 审计动作名。 */
    public static final String ACTION = "TENANT_DELETE";

    /** 步骤名：清空 DB 业务行。 */
    public static final String STEP_PURGE_DB_ROWS = "PURGE_DB_ROWS";

    /** 步骤名：清空 Milvus 向量。 */
    public static final String STEP_PURGE_VECTORS = "PURGE_VECTORS";

    /** 单轮扫描的 DELETING 租户上限。 */
    private static final int DEFAULT_BATCH_SIZE = 20;

    private final TenantGateway tenantGateway;
    private final TenantDeletionStepGateway stepGateway;
    private final TenantDataPurgeGateway purgeGateway;
    private final VectorStoreGateway vectorStoreGateway;
    private final PlatformAuditLog auditLog;
    private final TenantDirectory tenantDirectory;

    @Value("${knowledge.saas.tenant-deletion.batch-size:20}")
    private int batchSize = DEFAULT_BATCH_SIZE;

    public TenantDeletionReconciler(
            TenantGateway tenantGateway,
            TenantDeletionStepGateway stepGateway,
            TenantDataPurgeGateway purgeGateway,
            VectorStoreGateway vectorStoreGateway,
            PlatformAuditLog auditLog,
            TenantDirectory tenantDirectory) {
        this.tenantGateway = tenantGateway;
        this.stepGateway = stepGateway;
        this.purgeGateway = purgeGateway;
        this.vectorStoreGateway = vectorStoreGateway;
        this.auditLog = auditLog;
        this.tenantDirectory = tenantDirectory;
    }

    /**
     * 定时扫描 DELETING 租户并续跑硬删。默认每 5 分钟一轮，可经 {@code
     * knowledge.saas.tenant-deletion.reconcile-interval-ms} 调整。
     *
     * <p>单个租户失败不影响其余租户（逐项 try/catch），失败租户保留 DELETING 态待下轮续跑。
     */
    @Scheduled(fixedDelayString = "${knowledge.saas.tenant-deletion.reconcile-interval-ms:300000}")
    public void reconcile() {
        List<Tenant> deleting =
                tenantGateway.listByStatus(Tenant.STATUS_DELETING, effectiveBatchSize());
        if (deleting.isEmpty()) {
            return;
        }
        log.info("Tenant deletion reconcile: {} tenant(s) in DELETING", deleting.size());
        for (Tenant tenant : deleting) {
            try {
                runDeletion(tenant);
            } catch (RuntimeException ex) {
                log.error(
                        "Tenant deletion reconcile failed [tenant={}, requestId={}]",
                        tenant.getTenantKey(),
                        tenant.getDeletionRequestId(),
                        ex);
            }
        }
    }

    /** 对单个 DELETING 租户执行两步硬删 + 终态迁移（步骤级幂等，可续跑）。 */
    void runDeletion(Tenant tenant) {
        String requestId = tenant.getDeletionRequestId();
        if (requestId == null || requestId.isBlank()) {
            log.warn(
                    "Tenant '{}' is DELETING without deletionRequestId, skip (data fix required)",
                    tenant.getTenantKey());
            return;
        }
        Long tenantId = tenant.getTenantId();

        StepOutcome dbOutcome =
                runStep(
                        tenant,
                        requestId,
                        STEP_PURGE_DB_ROWS,
                        () -> {
                            Map<String, Long> rows = purgeGateway.purgeTenantRows(tenantId);
                            return rows.values().stream().mapToLong(Long::longValue).sum();
                        });
        if (TenantDeletionStep.STATUS_FAILED.equals(dbOutcome.status())) {
            auditFailure(tenant, STEP_PURGE_DB_ROWS, dbOutcome.errorDetail());
            return;
        }

        StepOutcome vectorOutcome =
                runStep(
                        tenant,
                        requestId,
                        STEP_PURGE_VECTORS,
                        () -> {
                            vectorStoreGateway.deleteByTenantId(tenantId);
                            return null; // removeAll 不返回计数
                        });
        if (TenantDeletionStep.STATUS_FAILED.equals(vectorOutcome.status())) {
            auditFailure(tenant, STEP_PURGE_VECTORS, vectorOutcome.errorDetail());
            return;
        }

        // 两步皆成功（含 SKIPPED 续跑）→ 迁入 DELETED 终态
        tenant.markDeleted(LocalDateTime.now());
        tenantGateway.update(tenant);
        tenantDirectory.invalidateByKey(tenant.getTenantKey());
        auditLog.logTenantAction(
                AgentNames.ACTOR_SYSTEM,
                ACTION,
                tenant.getTenantKey(),
                "SUCCESS",
                "DELETING→DELETED dbRows=" + dbOutcome.rowsAffected() + " requestId=" + requestId);
        log.info(
                "Tenant hard-deleted (DELETING→DELETED): {} dbRows={} requestId={}",
                tenant.getTenantKey(),
                dbOutcome.rowsAffected(),
                requestId);
    }

    /**
     * 执行单步骤（含跳过判定与 RUNNING/SUCCESS/FAILED 行落库），镜像 {@code ProvisioningStateMachine#runStep}。
     *
     * @param action 步骤副作用，返回影响行数（无可计数时返回 {@code null}）
     * @return 步骤结果，永不为 {@code null}
     */
    private StepOutcome runStep(
            Tenant tenant, String requestId, String stepName, DeletionAction action) {
        Long tenantId = tenant.getTenantId();

        // 幂等：前次已 SUCCESS 则跳过，不新增行
        if (stepGateway.findSuccess(tenantId, requestId, stepName).isPresent()) {
            int skippedAttempt = stepGateway.findMaxAttemptNo(tenantId, requestId, stepName);
            log.debug("Deletion step '{}' already SUCCESS for tenant {}, skip", stepName, tenantId);
            return new StepOutcome(TenantDeletionStep.STATUS_SKIPPED, null, null, skippedAttempt);
        }

        int attemptNo = stepGateway.findMaxAttemptNo(tenantId, requestId, stepName) + 1;
        TenantDeletionStep record = new TenantDeletionStep();
        record.setTenantId(tenantId);
        record.setDeletionRequestId(requestId);
        record.setStepName(stepName);
        record.setAttemptNo(attemptNo);
        record.setStatus(TenantDeletionStep.STATUS_RUNNING);
        record.setStartedAt(LocalDateTime.now());
        stepGateway.insert(record);

        try {
            Long rows = action.run();
            record.setStatus(TenantDeletionStep.STATUS_SUCCESS);
            record.setRowsAffected(rows);
            record.setFinishedAt(LocalDateTime.now());
            stepGateway.update(record);
            return new StepOutcome(TenantDeletionStep.STATUS_SUCCESS, rows, null, attemptNo);
        } catch (RuntimeException ex) {
            String detail = TenantDeletionStep.truncateErrorDetail(describe(ex));
            record.setStatus(TenantDeletionStep.STATUS_FAILED);
            record.setErrorDetail(detail);
            record.setFinishedAt(LocalDateTime.now());
            stepGateway.update(record);
            log.warn(
                    "Deletion step '{}' FAILED for tenant {} (attempt {}): {}",
                    stepName,
                    tenant.getTenantKey(),
                    attemptNo,
                    detail);
            return new StepOutcome(TenantDeletionStep.STATUS_FAILED, null, detail, attemptNo);
        }
    }

    private void auditFailure(Tenant tenant, String stepName, String errorDetail) {
        auditLog.logTenantAction(
                AgentNames.ACTOR_SYSTEM,
                ACTION,
                tenant.getTenantKey(),
                "FAILED",
                "step=" + stepName + " error=" + errorDetail);
    }

    /** 组装异常摘要：类型 + message，避免 NPE 时无信息。 */
    private String describe(RuntimeException ex) {
        String msg = ex.getMessage();
        return ex.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private int effectiveBatchSize() {
        return batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
    }

    /** 删除步骤副作用；返回影响行数（无可计数时 {@code null}）。 */
    @FunctionalInterface
    interface DeletionAction {
        Long run();
    }

    /**
     * 步骤结果快照。
     *
     * @param status SUCCESS / FAILED / SKIPPED
     * @param rowsAffected 影响行数（可空）
     * @param errorDetail 失败原因（仅 FAILED 非空）
     * @param attemptNo 本次尝试序号
     */
    record StepOutcome(String status, Long rowsAffected, String errorDetail, int attemptNo) {}
}
