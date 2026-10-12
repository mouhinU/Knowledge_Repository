package com.mouhin.knowledge.repository.application.service;

import com.mouhin.knowledge.repository.client.api.ExamGradingServiceI;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.DistributedLockGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamAlertGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 考试评分定时任务（P5-T5 多副本租户感知改造）。
 *
 * <p>每 5 分钟执行一次：先回收超时卡在 GRADING 的场次（进程崩溃兜底）， 再扫描 SUBMITTED 状态且交卷时间超过延迟阈值的考试自动触发 AI 评分。
 * 真正的并发去重由评分入口的 {@code SUBMITTED→GRADING} CAS 认领保证。
 *
 * <p><b>P5-T5 改造</b>：多副本部署下通过 {@link DistributedLockGateway} 全局锁保证同一时刻只有一个副本执行扫描；
 * 扫描逻辑按租户游标分页遍历（{@link TenantGateway#listActiveByCursor}），每个租户在 {@link TenantContext} 作用域内处理， 单租户异常
 * try/catch 隔离不阻塞下一个租户。
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Component
@Slf4j
public class ExamGradingScheduler {

    /** 分布式锁 key：全局评分调度锁 */
    private static final String LOCK_KEY = "kr:lock:exam-grading";

    /** 分布式锁 TTL（秒）：须大于任务最大执行时间 + buffer。单轮扫描上限 50 租户 × 50 场次 = 2500 场次，预留 5 分钟 */
    private static final int LOCK_TTL_SECONDS = 600;

    /** 租户游标单轮批量上限 */
    private static final int TENANT_BATCH_SIZE = 50;

    /** 每轮扫描的待评分场次上限（单租户） */
    private static final int PENDING_BATCH_SIZE = 50;

    /** 超时回收单轮扫描上限（单租户） */
    private static final int RECOVER_BATCH_SIZE = 100;

    /** 场次状态：评分中（并发认领态） */
    private static final String STATUS_GRADING = "GRADING";

    private final ExamSessionGateway examSessionGateway;
    private final ExamGradingServiceI gradingService;
    private final ExamAlertGateway examAlertGateway;
    private final TenantGateway tenantGateway;
    private final DistributedLockGateway distributedLockGateway;

    /** 实例唯一标识，用于分布式锁 ownerId */
    private final String instanceId =
            UUID.randomUUID().toString().replace("-", "").substring(0, 16);

    @Value("${knowledge.exam.grading-delay-minutes:30}")
    private int gradingDelayMinutes;

    @Value("${knowledge.exam.grading-timeout-minutes:15}")
    private int gradingTimeoutMinutes;

    public ExamGradingScheduler(
            ExamSessionGateway examSessionGateway,
            ExamGradingServiceI gradingService,
            ExamAlertGateway examAlertGateway,
            TenantGateway tenantGateway,
            DistributedLockGateway distributedLockGateway) {
        this.examSessionGateway = examSessionGateway;
        this.gradingService = gradingService;
        this.examAlertGateway = examAlertGateway;
        this.tenantGateway = tenantGateway;
        this.distributedLockGateway = distributedLockGateway;
    }

    /**
     * 定时扫描待评分考试并自动触发（P5-T5 多副本租户感知）。
     *
     * <p>每 5 分钟执行一次。先尝试获取全局分布式锁，获锁成功后按租户游标分页遍历 ACTIVE 租户， 每个租户在 {@link TenantContext} 作用域内执行超时回收 +
     * 待评分扫描。 单租户异常 try/catch 隔离，不阻塞下一个租户。
     */
    @Scheduled(fixedRate = 300000)
    public void scheduleAutoGrading() {
        // P5-T5：全局分布式锁，多副本下同一时刻只有一个实例执行扫描
        if (!distributedLockGateway.tryLock(LOCK_KEY, instanceId, LOCK_TTL_SECONDS)) {
            log.debug("[ExamGrading] 未获锁，跳过本轮扫描（其他副本正在执行）");
            return;
        }

        try {
            scanAllTenants();
        } finally {
            distributedLockGateway.unlock(LOCK_KEY, instanceId);
        }
    }

    /** 按租户游标分页遍历全部 ACTIVE 租户，逐租户执行评分扫描。 */
    private void scanAllTenants() {
        long lastId = 0;
        int tenantsScanned = 0;
        int totalTriggered = 0;

        while (true) {
            List<Tenant> batch = tenantGateway.listActiveByCursor(lastId, TENANT_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            for (Tenant tenant : batch) {
                try {
                    int triggered = scanSingleTenant(tenant);
                    totalTriggered += triggered;
                    tenantsScanned++;
                } catch (Exception e) {
                    log.error(
                            "[ExamGrading] 租户扫描异常，跳过 [tenantKey={}]: {}",
                            tenant.getTenantKey(),
                            e.getMessage(),
                            e);
                }
            }

            // 更新游标
            lastId = batch.get(batch.size() - 1).getTenantId();

            // 本批不足 batchSize，说明已扫完
            if (batch.size() < TENANT_BATCH_SIZE) {
                break;
            }
        }

        if (totalTriggered > 0) {
            log.info(
                    "[ExamGrading] 本轮扫描完成：租户数={}, 触发评分={}, 延迟阈值={}分钟",
                    tenantsScanned,
                    totalTriggered,
                    gradingDelayMinutes);
        }
    }

    /**
     * 单租户评分扫描：先回收超时卡住的场次，再扫描待评分场次。
     *
     * @return 本轮触发的评分场次数
     */
    private int scanSingleTenant(Tenant tenant) {
        String tenantKey = tenant.getTenantKey();
        return TenantContext.callWithTenant(
                tenantKey,
                () -> {
                    // 阶段 1：超时回收
                    recoverStuckGrading(tenantKey);

                    // 阶段 2：待评分扫描
                    List<ExamSession> pending =
                            examSessionGateway.listPendingGrading(PENDING_BATCH_SIZE);
                    if (pending.isEmpty()) {
                        return 0;
                    }

                    LocalDateTime threshold = LocalDateTime.now().minusMinutes(gradingDelayMinutes);
                    int triggered = 0;

                    for (ExamSession session : pending) {
                        if (session.getSubmitTime() != null
                                && session.getSubmitTime().isBefore(threshold)) {
                            try {
                                gradingService.triggerGrading(session.getId());
                                triggered++;
                            } catch (Exception e) {
                                log.error(
                                        "[ExamGrading] 定时评分失败 [tenantKey={}, session={}]: {}",
                                        tenantKey,
                                        session.getId(),
                                        e.getMessage());
                            }
                        }
                    }
                    return triggered;
                });
    }

    /**
     * 回收超时卡在 GRADING 的场次。
     *
     * <p>评分中途异常的回退已在异步链路即时处理，本方法专门兜底进程崩溃 / 实例重启等无法回退的场景： 将 {@code update_time} 早于超时阈值仍处于 GRADING
     * 的场次以 CAS 回退为 SUBMITTED，使其重新参与调度。
     */
    private void recoverStuckGrading(String tenantKey) {
        try {
            List<ExamSession> grading =
                    examSessionGateway.listByStatus(STATUS_GRADING, RECOVER_BATCH_SIZE, 0);
            if (grading.isEmpty()) {
                return;
            }
            LocalDateTime deadline = LocalDateTime.now().minusMinutes(gradingTimeoutMinutes);
            int recovered = 0;
            for (ExamSession session : grading) {
                // CONC-1：单条原子回收——SQL 内判定 status==GRADING 且 update_time<deadline，
                // 回退 SUBMITTED 同时清空围栏令牌，使在途旧评分者心跳/终态立即失配退出。
                // 仅当确实回收了本场次（affected=1）才告警，避免与刚完成评分的行竞争误报。
                if (examSessionGateway.reclaimStuckGrading(session.getId(), deadline)) {
                    recovered++;
                    log.warn(
                            "[ExamGrading] 回收超时评分场次 [tenantKey={}, session={}, lastUpdate={}]",
                            tenantKey,
                            session.getId(),
                            session.getUpdateTime());
                    examAlertGateway.gradingTimeout(session.getId());
                }
            }
            if (recovered > 0) {
                log.info(
                        "[ExamGrading] 超时评分回收 [tenantKey={}, 本轮回退={}, 超时阈值={}分钟]",
                        tenantKey,
                        recovered,
                        gradingTimeoutMinutes);
            }
        } catch (Exception e) {
            log.error("[ExamGrading] 超时评分回收任务异常 [tenantKey={}]: {}", tenantKey, e.getMessage(), e);
        }
    }
}
