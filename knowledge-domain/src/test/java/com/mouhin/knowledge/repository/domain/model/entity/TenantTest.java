package com.mouhin.knowledge.repository.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
