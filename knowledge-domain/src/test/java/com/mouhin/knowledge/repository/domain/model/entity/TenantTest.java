package com.mouhin.knowledge.repository.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Tenant} 领域实体状态流转单测（P3-T1）。
 *
 * <p>锁定 suspend / resume 的合法迁移与非法迁移拒绝语义：仅 ACTIVE→SUSPENDED、SUSPENDED→ACTIVE 允许， 其余（含幂等重复、归档 /
 * 删除态）一律抛 {@link IllegalStateException}，状态流转规则内聚于实体。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("Tenant 实体状态流转 (P3-T1)")
class TenantTest {

    private Tenant withStatus(String status) {
        Tenant t = new Tenant();
        t.setTenantId(1L);
        t.setTenantKey("acme");
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("ACTIVE → suspend() → SUSPENDED")
    void suspendFromActive() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        t.suspend();
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_SUSPENDED);
        assertThat(t.isSuspended()).isTrue();
        assertThat(t.isActive()).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED → resume() → ACTIVE")
    void resumeFromSuspended() {
        Tenant t = withStatus(Tenant.STATUS_SUSPENDED);
        t.resume();
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_ACTIVE);
        assertThat(t.isActive()).isTrue();
    }

    @Test
    @DisplayName("非 ACTIVE 态 suspend() 抛 IllegalStateException")
    void suspendFromNonActiveRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_SUSPENDED).suspend())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot suspend");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ARCHIVED).suspend())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_DELETED).suspend())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("非 SUSPENDED 态 resume() 抛 IllegalStateException")
    void resumeFromNonSuspendedRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).resume())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resume");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ARCHIVED).resume())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("equals / hashCode 以 tenantId 为准")
    void equalityByTenantId() {
        Tenant a = withStatus(Tenant.STATUS_ACTIVE);
        Tenant b = withStatus(Tenant.STATUS_SUSPENDED);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // ==================== P3-T2 开通状态机流转 ====================

    @Test
    @DisplayName("PENDING → beginProvisioning(reqId) → PROVISIONING 且回填 requestId")
    void beginProvisioningFromPending() {
        Tenant t = withStatus(Tenant.STATUS_PENDING);
        t.beginProvisioning("req-uuid-1");
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_PROVISIONING);
        assertThat(t.isProvisioning()).isTrue();
        assertThat(t.getProvisionRequestId()).isEqualTo("req-uuid-1");
    }

    @Test
    @DisplayName("FAILED → beginProvisioning(reqId) → PROVISIONING（重放允许）")
    void beginProvisioningFromFailedReplay() {
        Tenant t = withStatus(Tenant.STATUS_FAILED);
        t.beginProvisioning("req-uuid-2");
        assertThat(t.isProvisioning()).isTrue();
        assertThat(t.getProvisionRequestId()).isEqualTo("req-uuid-2");
    }

    @Test
    @DisplayName("PROVISIONING → completeProvisioning() → ACTIVE")
    void completeProvisioning() {
        Tenant t = withStatus(Tenant.STATUS_PROVISIONING);
        t.completeProvisioning();
        assertThat(t.isActive()).isTrue();
    }

    @Test
    @DisplayName("PROVISIONING → failProvisioning() → FAILED")
    void failProvisioning() {
        Tenant t = withStatus(Tenant.STATUS_PROVISIONING);
        t.failProvisioning();
        assertThat(t.isFailed()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 态 beginProvisioning() 抛 IllegalStateException（已开通不可重开）")
    void beginProvisioningFromActiveRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).beginProvisioning("req"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot begin provisioning");
    }

    @Test
    @DisplayName("beginProvisioning 空白 requestId 抛 IllegalArgumentException")
    void beginProvisioningBlankRequestIdRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_PENDING).beginProvisioning("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provisionRequestId");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_PENDING).beginProvisioning(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName(
            "非 PROVISIONING 态 completeProvisioning()/failProvisioning() 抛 IllegalStateException")
    void completeOrFailFromNonProvisioningRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_PENDING).completeProvisioning())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete provisioning");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).failProvisioning())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot fail provisioning");
    }

    // ==================== P3-T5 归档 / 硬删生命周期 ====================

    @Test
    @DisplayName("ACTIVE → archive(ts) → ARCHIVED 且打 archivedAt 保留期起点戳")
    void archiveFromActive() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        LocalDateTime ts = LocalDateTime.of(2026, 10, 9, 10, 0);
        t.archive(ts);
        assertThat(t.isArchived()).isTrue();
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_ARCHIVED);
        assertThat(t.getArchivedAt()).isEqualTo(ts);
    }

    @Test
    @DisplayName("SUSPENDED → archive(ts) → ARCHIVED（软停态亦可归档）")
    void archiveFromSuspended() {
        Tenant t = withStatus(Tenant.STATUS_SUSPENDED);
        LocalDateTime ts = LocalDateTime.of(2026, 10, 9, 11, 0);
        t.archive(ts);
        assertThat(t.isArchived()).isTrue();
        assertThat(t.getArchivedAt()).isEqualTo(ts);
    }

    @Test
    @DisplayName("非 ACTIVE/SUSPENDED 态 archive() 抛 IllegalStateException")
    void archiveFromNonArchivableRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_DELETED).archive(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot archive");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_PENDING).archive(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ARCHIVED).archive(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("archive(null) 抛 IllegalArgumentException")
    void archiveNullTimestampRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).archive(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archivedAt");
    }

    @Test
    @DisplayName("ARCHIVED → restore() → ACTIVE 且清空 archivedAt（保留期内撤销归档）")
    void restoreFromArchived() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        t.archive(LocalDateTime.of(2026, 10, 9, 12, 0));
        t.restore();
        assertThat(t.isActive()).isTrue();
        assertThat(t.getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("非 ARCHIVED 态 restore() 抛 IllegalStateException")
    void restoreFromNonArchivedRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).restore())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot restore");
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_DELETED).restore())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ARCHIVED → markDeleting(reqId) → DELETING 且绑定 deletionRequestId")
    void markDeletingFromArchived() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        t.archive(LocalDateTime.of(2026, 10, 9, 12, 0));
        t.markDeleting("del-uuid-1");
        assertThat(t.isDeleting()).isTrue();
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_DELETING);
        assertThat(t.getDeletionRequestId()).isEqualTo("del-uuid-1");
    }

    @Test
    @DisplayName("非 ARCHIVED 态 markDeleting() 抛 IllegalStateException")
    void markDeletingFromNonArchivedRejected() {
        assertThatThrownBy(() -> withStatus(Tenant.STATUS_ACTIVE).markDeleting("del"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot begin deletion");
    }

    @Test
    @DisplayName("markDeleting 空白 requestId 抛 IllegalArgumentException")
    void markDeletingBlankRequestIdRejected() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        t.archive(LocalDateTime.of(2026, 10, 9, 12, 0));
        assertThatThrownBy(() -> t.markDeleting("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deletionRequestId");
        assertThatThrownBy(() -> t.markDeleting(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DELETING → markDeleted(ts) → DELETED 终态且打 deletedAt 戳")
    void markDeletedFromDeleting() {
        Tenant t = withStatus(Tenant.STATUS_ACTIVE);
        t.archive(LocalDateTime.of(2026, 10, 9, 12, 0));
        t.markDeleting("del-uuid-1");
        LocalDateTime done = LocalDateTime.of(2026, 10, 9, 13, 0);
        t.markDeleted(done);
        assertThat(t.isDeleted()).isTrue();
        assertThat(t.getStatus()).isEqualTo(Tenant.STATUS_DELETED);
        assertThat(t.getDeletedAt()).isEqualTo(done);
    }

    @Test
    @DisplayName(
            "非 DELETING 态 markDeleted() 抛 IllegalStateException；null 戳抛 IllegalArgumentException")
    void markDeletedFromNonDeletingRejected() {
        assertThatThrownBy(
                        () -> withStatus(Tenant.STATUS_ARCHIVED).markDeleted(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot complete deletion");
        Tenant deleting = withStatus(Tenant.STATUS_ACTIVE);
        deleting.archive(LocalDateTime.of(2026, 10, 9, 12, 0));
        deleting.markDeleting("del-uuid-1");
        assertThatThrownBy(() -> deleting.markDeleted(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deletedAt");
    }
}
