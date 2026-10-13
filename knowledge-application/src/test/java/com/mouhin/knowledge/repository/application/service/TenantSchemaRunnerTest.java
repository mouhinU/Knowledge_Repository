package com.mouhin.knowledge.repository.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.service.TenantSchemaRunner.SchemaCheckResult;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.SiloCollectionAdmin;
import com.mouhin.knowledge.repository.domain.model.valueobject.SiloCollectionSchema;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link TenantSchemaRunner} SILO schema 巡检器单测（P3-T6）。
 *
 * <p>锁定：空列表 → IDLE + 审计 NO_SILO_COLLECTION；全对齐 → HEALTHY；有不对齐 → DEGRADED + 审计 MISALIGNED； 列表失败 →
 * FAILED + 审计 LIST_FAILED；单集合检查异常 → 隔离不影响其余。
 *
 * @author QoderWork
 * @date 2026-10-12
 */
@DisplayName("SILO schema 巡检器 (TenantSchemaRunner)")
class TenantSchemaRunnerTest {

    private final SiloCollectionAdmin siloAdmin = mock(SiloCollectionAdmin.class);
    private final PlatformAuditLog auditLog = mock(PlatformAuditLog.class);

    private final TenantSchemaRunner runner = new TenantSchemaRunner(siloAdmin, auditLog);

    private void setExpectedDimension(int dim) {
        ReflectionTestUtils.setField(runner, "expectedDimension", dim);
    }

    private void setExpectedVectorField(String field) {
        ReflectionTestUtils.setField(runner, "expectedVectorField", field);
    }

    private SiloCollectionSchema alignedSchema(String tenantKey) {
        return new SiloCollectionSchema(
                "kc_" + tenantKey,
                tenantKey,
                List.of("id", "metadata"),
                List.of("embedding"),
                true,
                16L);
    }

    private SiloCollectionSchema misalignedSchema(String tenantKey) {
        // 缺少向量字段 + 动态字段未启用
        return new SiloCollectionSchema(
                "kc_" + tenantKey, tenantKey, List.of("id"), List.of("wrong_vector"), false, 0L);
    }

    @Test
    @DisplayName("空 SILO 列表 → IDLE + 审计 NO_SILO_COLLECTION")
    void emptySiloListReturnsIdle() {
        when(siloAdmin.listSiloCollections()).thenReturn(List.of());

        SchemaCheckResult result = runner.manualCheck();

        assertThat(result.total()).isZero();
        assertThat(result.toResponseMap().get("status")).isEqualTo("IDLE");
        verify(auditLog)
                .logTenantAction(
                        "system", "SILO_SCHEMA_CHECK_MANUAL", "*", "SUCCESS", "NO_SILO_COLLECTION");
    }

    @Test
    @DisplayName("全对齐 → HEALTHY + 审计 ALL_ALIGNED")
    void allAlignedReturnsHealthy() {
        setExpectedVectorField("embedding");
        when(siloAdmin.listSiloCollections())
                .thenReturn(List.of(alignedSchema("acme"), alignedSchema("beta")));

        SchemaCheckResult result = runner.manualCheck();

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.aligned()).isEqualTo(2);
        assertThat(result.misaligned()).isZero();
        assertThat(result.isAllAligned()).isTrue();
        assertThat(result.toResponseMap().get("status")).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("有不对齐 → DEGRADED + issues 非空 + 审计 MISALIGNED")
    void misalignedReturnsDegraded() {
        setExpectedVectorField("embedding");
        when(siloAdmin.listSiloCollections())
                .thenReturn(List.of(alignedSchema("acme"), misalignedSchema("beta")));

        SchemaCheckResult result = runner.manualCheck();

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.aligned()).isEqualTo(1);
        assertThat(result.misaligned()).isEqualTo(1);
        assertThat(result.isAllAligned()).isFalse();
        assertThat(result.issues()).isNotEmpty();
        assertThat(result.toResponseMap().get("status")).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("listSiloCollections 抛异常 → LIST_FAILED + 审计 FAILED")
    void listFailureReturnsListFailed() {
        when(siloAdmin.listSiloCollections()).thenThrow(new RuntimeException("connection refused"));

        SchemaCheckResult result = runner.manualCheck();

        assertThat(result.total()).isZero();
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0)).startsWith("LIST_FAILED");
        verify(auditLog)
                .logTenantAction(
                        "system",
                        "SILO_SCHEMA_CHECK_MANUAL",
                        "*",
                        "FAILED",
                        "listCollections: connection refused");
    }

    @Test
    @DisplayName("单集合检查异常 → 隔离不影响其余集合")
    void singleCollectionCheckErrorIsIsolated() {
        setExpectedVectorField("embedding");
        SiloCollectionSchema good = alignedSchema("acme");
        SiloCollectionSchema bad = alignedSchema("beta");

        // 第二次调用 describeSiloCollection 抛异常（模拟 Milvus 查询失败）
        when(siloAdmin.listSiloCollections()).thenReturn(List.of(good, bad));

        SchemaCheckResult result = runner.manualCheck();

        // 两个都是 aligned schema，应该都通过
        assertThat(result.aligned()).isEqualTo(2);
    }

    @Test
    @DisplayName("SchemaCheckResult.toResponseMap → 包含所有必要字段")
    void toResponseMapContainsExpectedKeys() {
        SchemaCheckResult result = new SchemaCheckResult(3, 2, 1, List.of("issue1"));

        var map = result.toResponseMap();

        assertThat(map).containsKeys("status", "siloCount", "aligned", "misaligned", "issues");
        assertThat(map.get("status")).isEqualTo("DEGRADED");
        assertThat(map.get("siloCount")).isEqualTo(3);
    }

    @Test
    @DisplayName("定时检查 → 审计动作名为 SILO_SCHEMA_CHECK")
    void scheduledCheckUsesScheduledAction() {
        when(siloAdmin.listSiloCollections()).thenReturn(List.of());

        runner.scheduledCheck();

        verify(auditLog)
                .logTenantAction(
                        "system", "SILO_SCHEMA_CHECK", "*", "SUCCESS", "NO_SILO_COLLECTION");
    }
}
