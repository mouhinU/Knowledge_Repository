package com.mouhin.knowledge.repository.tenant;

import com.mouhin.knowledge.repository.application.service.ProvisioningOutcome;
import com.mouhin.knowledge.repository.application.service.ProvisioningStateMachine;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.TenantProvisionStepGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningContext;
import com.mouhin.knowledge.repository.domain.provisioning.ProvisioningStep;
import com.mouhin.knowledge.repository.infrastructure.observability.Slf4jPlatformAuditLog;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.TenantGatewayImpl;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.TenantProvisionStepGatewayImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-T2 租户开通状态机端到端集成测试（H2 MODE=MySQL，无需 Docker）。
 *
 * <p>在真实 H2（Flyway V1→V27 全量迁移）+ MyBatis-Plus + 真实 {@link TenantGatewayImpl} / {@link
 * TenantProvisionStepGatewayImpl} 上跑 {@link ProvisioningStateMachine}，验证清单 §7 item 4 的三条集成口径：
 *
 * <ol>
 *   <li><b>步骤级幂等</b>：同一 {@code provisionRequestId} 重放时，前次已 SUCCESS 的步骤不再执行、不新增行；
 *   <li><b>FAILED 可查询原因</b>：失败步骤的 {@code error_detail} 落库，可经 {@code listByRequest} 查回；
 *   <li><b>中断后重试不重复</b>：失败重放时 {@code attemptNo} 递增，{@code (tenant_id, request_id, step_name,
 *       attempt_no)} 唯一约束不被破坏。
 * </ol>
 *
 * <p>刻意用 H2 而非 Testcontainers MySQL：本用例只验编排 + 幂等语义（schema 方言兼容已由 {@code FlywayMigrationSmokeTest}
 * 覆盖），H2 可在纯单元 CI 通道确定运行、不依赖 Docker。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("P3-T2 租户开通状态机集成 (H2)")
@SpringBootTest(classes = TenantProvisioningIntegrationTest.TestConfig.class)
class TenantProvisioningIntegrationTest {

    @Autowired
    private TenantGateway tenantGateway;
    @Autowired
    private TenantProvisionStepGateway stepGateway;
    @Autowired
    private PlatformAuditLog auditLog;
    @Autowired
    private CountingStep countingStep;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-provision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        // 关闭 SaaS 拦截器：平台元数据表本就在 ignore 白名单，本用例只验编排，不需租户过滤
        registry.add("knowledge.saas.enabled", () -> "false");
    }

    private Tenant newPendingTenant(String key) {
        Tenant t = new Tenant();
        t.setTenantKey(key);
        t.setName("Provision IT " + key);
        t.setPlan("FREE");
        t.setStatus(Tenant.STATUS_PENDING);
        t.setDataRegion("cn");
        t.setProvisionRequestId(UUID.randomUUID().toString());
        tenantGateway.save(t);
        return t;
    }

    /**
     * 构造一个只含指定步骤的状态机（复用真实网关 + 审计）。
     */
    private ProvisioningStateMachine machineWith(ProvisioningStep... steps) {
        return new ProvisioningStateMachine(tenantGateway, stepGateway, auditLog, List.of(steps));
    }

    @Test
    @DisplayName("首次开通成功：PENDING → ACTIVE，步骤行落库为 SUCCESS")
    void provisionsToActive() {
        Tenant tenant =
                newPendingTenant("it-active-" + UUID.randomUUID().toString().substring(0, 8));
        String reqId = tenant.getProvisionRequestId();

        ProvisioningOutcome outcome =
                machineWith(countingStep).provision(tenant, reqId, "op-it", "IT Admin");

        assertThat(outcome.isSuccess()).isTrue();
        Tenant reloaded = tenantGateway.findByTenantKey(tenant.getTenantKey()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getProvisionRequestId()).isEqualTo(reqId);

        List<TenantProvisionStep> steps = stepGateway.listByRequest(reloaded.getTenantId(), reqId);
        assertThat(steps)
                .anySatisfy(s -> assertThat(s.getStepName()).isEqualTo("COUNTING_STEP"))
                .allSatisfy(
                        s ->
                                assertThat(s.getStatus())
                                        .isEqualTo(TenantProvisionStep.STATUS_SUCCESS));
    }

    @Test
    @DisplayName("步骤级幂等：同 requestId 重放，已成功步骤不再执行、不新增行")
    void replayIsIdempotent() {
        Tenant tenant = newPendingTenant("it-idem-" + UUID.randomUUID().toString().substring(0, 8));
        String reqId = tenant.getProvisionRequestId();
        countingStep.executions.set(0);

        machineWith(countingStep).provision(tenant, reqId, "op-it", "IT Admin");
        int afterFirst = countingStep.executions.get();
        long rowsAfterFirst =
                stepGateway.listByRequest(tenant.getTenantId(), reqId).stream()
                        .filter(s -> "COUNTING_STEP".equals(s.getStepName()))
                        .count();

        // ACTIVE 后重放：状态机空跑；即便强制回 PENDING 再放同 requestId，已成功步骤也应跳过
        Tenant replay = tenantGateway.findByTenantKey(tenant.getTenantKey()).orElseThrow();
        replay.setStatus(Tenant.STATUS_PENDING); // 模拟"未完成"以驱动步骤遍历
        tenantGateway.update(replay);
        machineWith(countingStep).provision(replay, reqId, "op-it", "IT Admin");

        assertThat(countingStep.executions.get()).as("重放不应再次执行已 SUCCESS 的步骤").isEqualTo(afterFirst);
        long rowsAfterReplay =
                stepGateway.listByRequest(tenant.getTenantId(), reqId).stream()
                        .filter(s -> "COUNTING_STEP".equals(s.getStepName()))
                        .count();
        assertThat(rowsAfterReplay).isEqualTo(rowsAfterFirst);
    }

    @Test
    @DisplayName("FAILED 可查询原因 + 中断后重试：失败落 error_detail，重放 attemptNo 递增后成功")
    void failedThenRetriedSucceeds() {
        Tenant tenant =
                newPendingTenant("it-flaky-" + UUID.randomUUID().toString().substring(0, 8));
        String reqId = tenant.getProvisionRequestId();
        FlakyStep flaky = new FlakyStep();

        // 第一次：FLAKY_STEP 抛异常 → FAILED
        ProvisioningOutcome first =
                machineWith(countingStep, flaky).provision(tenant, reqId, "op-it", "IT Admin");
        assertThat(first.isSuccess()).isFalse();
        assertThat(first.finalStatus()).isEqualTo(Tenant.STATUS_FAILED);

        Tenant afterFail = tenantGateway.findByTenantKey(tenant.getTenantKey()).orElseThrow();
        assertThat(afterFail.isFailed()).isTrue();
        List<TenantProvisionStep> failedSteps =
                stepGateway.listByRequest(afterFail.getTenantId(), reqId);
        TenantProvisionStep failedFlaky =
                failedSteps.stream()
                        .filter(s -> "FLAKY_STEP".equals(s.getStepName()))
                        .findFirst()
                        .orElseThrow();
        assertThat(failedFlaky.getStatus()).isEqualTo(TenantProvisionStep.STATUS_FAILED);
        assertThat(failedFlaky.getErrorDetail()).contains("simulated transient failure");
        assertThat(failedFlaky.getAttemptNo()).isEqualTo(1);

        // 重放：FLAKY_STEP 第二次成功；COUNTING_STEP 已 SUCCESS 应跳过；attemptNo 递增到 2
        ProvisioningOutcome retry =
                machineWith(countingStep, flaky).provision(afterFail, reqId, "op-it", "IT Admin");
        assertThat(retry.isSuccess()).isTrue();

        Tenant afterRetry = tenantGateway.findByTenantKey(tenant.getTenantKey()).orElseThrow();
        assertThat(afterRetry.isActive()).isTrue();

        List<TenantProvisionStep> allSteps =
                stepGateway.listByRequest(afterRetry.getTenantId(), reqId);
        TenantProvisionStep succeededFlaky =
                allSteps.stream()
                        .filter(s -> "FLAKY_STEP".equals(s.getStepName()) && s.getAttemptNo() == 2)
                        .findFirst()
                        .orElseThrow();
        assertThat(succeededFlaky.getStatus()).isEqualTo(TenantProvisionStep.STATUS_SUCCESS);
        // COUNTING_STEP 只应有一条 SUCCESS（重放跳过，未新增 attempt）
        long countingRows =
                allSteps.stream().filter(s -> "COUNTING_STEP".equals(s.getStepName())).count();
        assertThat(countingRows).isEqualTo(1);
    }

    /**
     * 最小 Spring 上下文：DataSource / Flyway / MyBatis-Plus / 两个真实 Gateway / 状态机 / 审计。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({
            TenantGatewayImpl.class,
            TenantProvisionStepGatewayImpl.class,
            Slf4jPlatformAuditLog.class,
            ProvisioningStateMachine.class
    })
    static class TestConfig {

        /**
         * 计数步骤：记录执行次数，验证幂等跳过。
         */
        @Bean
        CountingStep countingStep() {
            return new CountingStep();
        }
    }

    /**
     * 可计数的成功步骤（order=150，排在 ValidateTenantStep 之后）。
     */
    static class CountingStep implements ProvisioningStep {
        final AtomicInteger executions = new AtomicInteger();

        @Override
        public String name() {
            return "COUNTING_STEP";
        }

        @Override
        public int order() {
            return 150;
        }

        @Override
        public void execute(ProvisioningContext context) {
            executions.incrementAndGet();
        }
    }

    /**
     * 首次失败、之后成功的步骤，用于模拟"中断后重试"。
     */
    static class FlakyStep implements ProvisioningStep {
        final AtomicInteger executions = new AtomicInteger();

        @Override
        public String name() {
            return "FLAKY_STEP";
        }

        @Override
        public int order() {
            return 300;
        }

        @Override
        public void execute(ProvisioningContext context) {
            if (executions.incrementAndGet() == 1) {
                throw new IllegalStateException("simulated transient failure");
            }
        }
    }
}
