package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.application.config.SaasProperties;
import com.mouhin.knowledge.repository.domain.context.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link TenantContextHolder} 单元测试（P1-T2）。
 *
 * <p>核心验证"回退闸门"语义：{@code saas.enabled=false} 时即便线程上残留租户绑定， 对外也恒返回 {@code default}；开启时才透传 {@link
 * TenantContext}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantContextHolder 开关门控 (P1-T2)")
class TenantContextHolderTest {

    @Mock
    private SaasProperties saasProperties;

    private TenantContextHolder holder;

    @BeforeEach
    void setUp() {
        holder = new TenantContextHolder(saasProperties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("isEnabled 委托给配置")
    void isEnabledDelegates() {
        when(saasProperties.isEnabled()).thenReturn(true);
        assertThat(holder.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("clear 无视开关，始终清除线程绑定")
    void clearAlwaysWorks() {
        TenantContext.set("acme");
        holder.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Nested
    @DisplayName("saas.enabled=false（默认，单租户回退闸门）")
    class Disabled {

        @Test
        @DisplayName("即便线程已绑定租户，仍恒返回 default")
        void ignoresThreadBinding() {
            when(saasProperties.isEnabled()).thenReturn(false);
            TenantContext.set("acme");
            assertThat(holder.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_KEY);
        }

        @Test
        @DisplayName("bind 为空操作，不写入上下文")
        void bindIsNoOp() {
            when(saasProperties.isEnabled()).thenReturn(false);
            holder.bind("acme");
            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    @DisplayName("saas.enabled=true（多租户）")
    class Enabled {

        @Test
        @DisplayName("透传上下文中的租户")
        void returnsContextTenant() {
            when(saasProperties.isEnabled()).thenReturn(true);
            holder.bind("acme");
            assertThat(holder.getTenantId()).isEqualTo("acme");
        }

        @Test
        @DisplayName("未绑定时回落 default")
        void fallsBackToDefault() {
            when(saasProperties.isEnabled()).thenReturn(true);
            assertThat(holder.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_KEY);
        }
    }
}
