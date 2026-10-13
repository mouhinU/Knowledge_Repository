package com.mouhin.knowledge.repository.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.executor.platform.ArchiveTenantCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.ConfirmTenantDeletionCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.IssueDeleteApprovalCmdExe;
import com.mouhin.knowledge.repository.application.executor.platform.TenantDeletionPreviewQryExe;
import com.mouhin.knowledge.repository.application.service.TenantDeletionReconciler;
import com.mouhin.knowledge.repository.application.support.DeleteApprovalService;
import com.mouhin.knowledge.repository.application.support.SystemConfigService;
import com.mouhin.knowledge.repository.client.dto.*;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import com.mouhin.knowledge.repository.domain.gateway.ConfigInvalidationPublisher;
import com.mouhin.knowledge.repository.domain.gateway.VectorStoreGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.infrastructure.observability.DbPlatformAuditLog;
import com.mouhin.knowledge.repository.infrastructure.persistence.gateway.*;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * P3-T5 归档 → 保留期 → 双人复核 → 硬删（reconciler 续跑）全链路端到端集成测试（H2 MODE=MySQL，无需 Docker）。
 *
 * <p>在真实 H2（Flyway V1→V29）+ MyBatis-Plus + 真实网关（{@link TenantGatewayImpl} / {@link
 * TenantDeletionStepGatewayImpl} / {@link TenantDeleteApprovalGatewayImpl} / {@link
 * TenantDataPurgeGatewayImpl}） + 真实 {@link DbPlatformAuditLog}（审计落 {@code sys_audit_log}）+
 * 真实执行器上跑完整生命周期， 只把 Milvus 侧 {@link VectorStoreGateway} 以 mock 顶替（H2 环境无向量库）。验证：
 *
 * <ol>
 *   <li><b>归档</b>：{@link ArchiveTenantCmdExe} 把 ACTIVE 租户迁入 ARCHIVED 并打 {@code archivedAt}；
 *   <li><b>签发</b>：{@link IssueDeleteApprovalCmdExe} 为已归档租户返回一次性 raw token（仅一次可见）；
 *   <li><b>确认（双人复核）</b>：{@link ConfirmTenantDeletionCmdExe} 由<b>不同</b>操作者持 raw token 消费令牌、 迁入
 *       DELETING 并绑定 {@code deletionRequestId}；
 *   <li><b>硬删续跑</b>：{@link TenantDeletionReconciler#reconcile()} 逐步骤幂等清 DB 行 + 向量， 两步 SUCCESS →
 *       DELETED 终态；被清租户的业务行归零、步骤行落库、审计 SUCCESS；
 *   <li><b>幂等</b>：第二次 {@code reconcile()} 因该租户已非 DELETING 而是无副作用空操作（向量清除不再触发、步骤行不新增）；
 *   <li><b>预览</b>：{@link TenantDeletionPreviewQryExe} 只读统计各表行数与保留期是否已过，不删任何数据。
 * </ol>
 *
 * <p>门控与运行期语义对齐生产：{@code knowledge.saas.enabled=false}（不挂租户拦截器）、 {@code
 * knowledge.platform.enabled=true}（装配 reconciler bean）、{@code retention-days=0}（保留期即时满足， 便于测试；生产默认
 * 30）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("P3-T5 归档/双人复核/硬删续跑 全链路集成 (H2)")
@SpringBootTest(classes = TenantArchiveDeleteIntegrationTest.TestConfig.class)
class TenantArchiveDeleteIntegrationTest {

    @Autowired private TenantGatewayImpl tenantGateway;
    @Autowired private ArchiveTenantCmdExe archiveExe;
    @Autowired private IssueDeleteApprovalCmdExe issueApprovalExe;
    @Autowired private ConfirmTenantDeletionCmdExe confirmDeletionExe;
    @Autowired private TenantDeletionPreviewQryExe previewQryExe;
    @Autowired private TenantDeletionReconciler reconciler;
    @Autowired private VectorStoreGateway vectorStoreGateway;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:kr-archive-delete;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        // 双闸门：saas 关闭（不挂租户拦截器）+ platform 开启（装配 reconciler）
        registry.add("knowledge.saas.enabled", () -> "false");
        registry.add("knowledge.platform.enabled", () -> "true");
        // 保留期即时满足，便于测试（生产默认 30 天）
        registry.add("knowledge.saas.tenant-deletion.retention-days", () -> "0");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Tenant newActiveTenant(String key) {
        Tenant t = new Tenant();
        t.setTenantKey(key);
        t.setName("Archive IT " + key);
        t.setPlan("FREE");
        t.setStatus(Tenant.STATUS_ACTIVE);
        t.setDataRegion("cn");
        t.setProvisionRequestId(UUID.randomUUID().toString());
        tenantGateway.save(t);
        return t;
    }

    /** 为租户播种一条可被硬删的业务行（sys_config，唯一键 (tenant_id, config_key)），返回其 config_key。 */
    private String seedBusinessRow(long tenantId, String key) {
        String configKey = "it-del-" + key;
        jdbcTemplate.update(
                "INSERT INTO sys_config (config_key, config_value, value_type, tenant_id) VALUES (?,?,?,?)",
                configKey,
                "v",
                "STRING",
                tenantId);
        return configKey;
    }

    private TenantArchiveCmd archiveCmd(String key) {
        TenantArchiveCmd c = new TenantArchiveCmd();
        c.setTenantKey(key);
        c.setOperatorId("op-archive");
        c.setOperatorName("platform-admin");
        c.setReason("integration");
        return c;
    }

    private TenantDeleteApprovalCmd approvalCmd(String key, String approver) {
        TenantDeleteApprovalCmd c = new TenantDeleteApprovalCmd();
        c.setTenantKey(key);
        c.setOperatorId(approver);
        c.setOperatorName("project-lead");
        c.setTtlHours(24);
        return c;
    }

    private TenantDeleteCmd deleteCmd(String key, String token, String confirmer) {
        TenantDeleteCmd c = new TenantDeleteCmd();
        c.setTenantKey(key);
        c.setApprovalToken(token);
        c.setOperatorId(confirmer);
        c.setOperatorName("second-reviewer");
        return c;
    }

    private int countConfigByKey(String configKey) {
        Integer n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_config WHERE config_key = ?",
                        Integer.class,
                        configKey);
        return n == null ? 0 : n;
    }

    private int countSteps(long tenantId, String status) {
        Integer n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_tenant_deletion_step WHERE tenant_id = ? AND status = ?",
                        Integer.class,
                        tenantId,
                        status);
        return n == null ? 0 : n;
    }

    private int countAudit(String tenantKey, String action, String outcome) {
        Integer n =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM sys_audit_log WHERE tenant_key = ? AND action = ? AND outcome = ?",
                        Integer.class,
                        tenantKey,
                        action,
                        outcome);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName(
            "归档→签发→双人复核确认→reconcile 硬删：DELETED 终态 + 业务行归零 + 两步 SUCCESS + 审计落库；二次 reconcile 幂等空操作")
    void fullArchiveDeleteLifecycle() {
        String key = "it-del-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newActiveTenant(key);
        Long tenantId = tenant.getTenantId();
        String configKey = seedBusinessRow(tenantId, key);
        assertThat(countConfigByKey(configKey)).isEqualTo(1);

        // 1) 归档 ACTIVE → ARCHIVED
        TenantVO archived = archiveExe.execute(archiveCmd(key));
        assertThat(archived.getStatus()).isEqualTo(Tenant.STATUS_ARCHIVED);
        Tenant afterArchive = tenantGateway.findByTenantKey(key).orElseThrow();
        assertThat(afterArchive.isArchived()).isTrue();
        assertThat(afterArchive.getArchivedAt()).isNotNull();
        assertThat(countAudit(key, ArchiveTenantCmdExe.ACTION, "SUCCESS")).isEqualTo(1);

        // 2) 项目负责人签发一次性删除审批令牌（raw token 仅此一次可见）
        TenantDeleteApprovalVO approval =
                issueApprovalExe.execute(approvalCmd(key, "project-lead"));
        String rawToken = approval.getApprovalToken();
        assertThat(rawToken).isNotBlank();
        assertThat(approval.getStatus()).isEqualTo("PENDING");
        assertThat(countAudit(key, IssueDeleteApprovalCmdExe.ACTION, "SUCCESS")).isEqualTo(1);

        // 3) 第二操作者（≠ 签发者）持 raw token 确认 → 消费令牌 + DELETING + 绑定 deletionRequestId
        TenantVO deleting = confirmDeletionExe.execute(deleteCmd(key, rawToken, "second-reviewer"));
        assertThat(deleting.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
        Tenant afterConfirm = tenantGateway.findByTenantKey(key).orElseThrow();
        assertThat(afterConfirm.isDeleting()).isTrue();
        assertThat(afterConfirm.getDeletionRequestId()).isNotBlank();
        assertThat(countAudit(key, ConfirmTenantDeletionCmdExe.ACTION, "SUCCESS")).isEqualTo(1);

        // 4) reconciler 续跑硬删：清 DB 行 + 清向量 → DELETED 终态
        reconciler.reconcile();

        Tenant deleted = tenantGateway.findByTenantKey(key).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        // 业务行已被硬删归零
        assertThat(countConfigByKey(configKey)).isZero();
        // 两步（PURGE_DB_ROWS / PURGE_VECTORS）均落 SUCCESS 行，无 FAILED
        assertThat(countSteps(tenantId, "SUCCESS")).isEqualTo(2);
        assertThat(countSteps(tenantId, "FAILED")).isZero();
        // 向量侧按 tenantId 清除一次
        verify(vectorStoreGateway, times(1)).deleteByTenantId(eq(tenantId.longValue()));
        // 硬删审计 SUCCESS 落 sys_audit_log
        assertThat(countAudit(key, TenantDeletionReconciler.ACTION, "SUCCESS")).isEqualTo(1);

        // 5) 幂等：该租户已 DELETED，二次 reconcile 不再扫描到它 → 空操作（向量不再清、步骤行不新增）
        reconciler.reconcile();
        verify(vectorStoreGateway, times(1)).deleteByTenantId(eq(tenantId.longValue()));
        assertThat(countSteps(tenantId, "SUCCESS")).isEqualTo(2);
    }

    @Test
    @DisplayName("删除预览只读：统计各表行数 + 保留期是否已过，不删任何数据")
    void previewCountsRowsWithoutDeleting() {
        String key = "it-prev-" + UUID.randomUUID().toString().substring(0, 8);
        Tenant tenant = newActiveTenant(key);
        Long tenantId = tenant.getTenantId();
        String configKey = seedBusinessRow(tenantId, key);
        archiveExe.execute(archiveCmd(key));

        TenantDeletionPreviewVO preview = previewQryExe.execute(key);

        assertThat(preview.getStatus()).isEqualTo(Tenant.STATUS_ARCHIVED);
        // retention-days=0 → 保留期即时满足
        assertThat(preview.isRetentionElapsed()).isTrue();
        assertThat(preview.getEligibleAt()).isNotNull();
        // 至少含本用例播种的一行；不断言精确值——测试租户可能取到 tenant_id=1，与 V21 sys_config 种子行（tenant_id 默认 1）叠加
        assertThat(preview.getRowsByTable()).containsKey("sys_config");
        assertThat(preview.getRowsByTable().get("sys_config")).isGreaterThanOrEqualTo(1L);
        assertThat(preview.getTotalRows()).isGreaterThanOrEqualTo(1L);
        // 预览是只读操作：业务行仍在
        assertThat(countConfigByKey(configKey)).isEqualTo(1);
        assertThat(tenantGateway.findByTenantKey(key).orElseThrow().isArchived()).isTrue();
    }

    /**
     * 最小上下文：DataSource / Flyway / MyBatis-Plus / 真实网关 + 审计 + 执行器 + reconciler；Milvus 侧以 mock 顶替。
     */
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("com.mouhin.knowledge.repository.infrastructure.persistence.mapper")
    @Import({
        TenantGatewayImpl.class,
        TenantIdResolver.class,
        DbPlatformAuditLog.class,
        SystemConfigGatewayImpl.class,
        SystemConfigService.class,
        TenantDeletionStepGatewayImpl.class,
        TenantDeleteApprovalGatewayImpl.class,
        TenantDataPurgeGatewayImpl.class,
        DeleteApprovalService.class,
        ArchiveTenantCmdExe.class,
        IssueDeleteApprovalCmdExe.class,
        ConfirmTenantDeletionCmdExe.class,
        TenantDeletionPreviewQryExe.class,
        TenantDeletionReconciler.class
    })
    static class TestConfig {

        /** Milvus 向量网关以 mock 顶替：H2 测试环境无向量库，仅需验证 {@code deleteByTenantId} 调用契约。 */
        @Bean
        VectorStoreGateway vectorStoreGateway() {
            return mock(VectorStoreGateway.class);
        }

        /** P4-T2: ConfigInvalidationPublisher 以 mock 顶替（测试环境无 Redis）。 */
        @Bean
        ConfigInvalidationPublisher configInvalidationPublisher() {
            return mock(ConfigInvalidationPublisher.class);
        }
    }
}
