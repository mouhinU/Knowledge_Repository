package com.mouhin.knowledge.repository.domain.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mouhin.knowledge.repository.domain.context.TenantContext.Snapshot;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantContext} 单元测试（P1-T2）。
 *
 * <p>锁定默认租户回落、空白绑定视为未绑定、{@code finally} 清理、{@code callWithTenant} 快照恢复与嵌套、 跨线程 {@link Snapshot}
 * 回放等不变式，为 SaaS 脊柱提供确定语义基线。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("TenantContext 租户上下文 (P1-T2)")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("set / get / clear")
    class BasicBinding {

        @Test
        @DisplayName("初始未绑定时 get 为 null、getOrDefault 回落到 default")
        void defaultsToDefaultTenant() {
            assertThat(TenantContext.get()).isNull();
            assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT_TENANT_KEY);
            assertThat(TenantContext.DEFAULT_TENANT_KEY).isEqualTo("default");
        }

        @Test
        @DisplayName("set 后 get 返回去除首尾空白的租户键")
        void setTrimsValue() {
            TenantContext.set("  acme  ");
            assertThat(TenantContext.get()).isEqualTo("acme");
            assertThat(TenantContext.getOrDefault()).isEqualTo("acme");
        }

        @Test
        @DisplayName("set(null)/set(空白) 视为不绑定")
        void blankBindingIsUnset() {
            TenantContext.set("acme");
            TenantContext.set(null);
            assertThat(TenantContext.get()).isNull();

            TenantContext.set("acme");
            TenantContext.set("   ");
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("clear 后回落到 default")
        void clearResetsToDefault() {
            TenantContext.set("acme");
            TenantContext.clear();
            assertThat(TenantContext.get()).isNull();
            assertThat(TenantContext.getOrDefault()).isEqualTo(TenantContext.DEFAULT_TENANT_KEY);
        }
    }

    @Nested
    @DisplayName("callWithTenant：作用域绑定与恢复")
    class ScopedExecution {

        @Test
        @DisplayName("块内使用目标租户，块后恢复到未绑定")
        void restoresUnsetAfterScope() {
            String inside = TenantContext.callWithTenant("beta", TenantContext::get);
            assertThat(inside).isEqualTo("beta");
            assertThat(TenantContext.get()).isNull();
        }

        @Test
        @DisplayName("块后恢复到外层原值（非默认）")
        void restoresPreviousValue() {
            TenantContext.set("alpha");
            String mid =
                    TenantContext.callWithTenant(
                            "beta", () -> TenantContext.get() + "/" + TenantContext.get());
            assertThat(mid).isEqualTo("beta/beta");
            assertThat(TenantContext.get()).isEqualTo("alpha");
        }

        @Test
        @DisplayName("支持嵌套")
        void nestedScopes() {
            TenantContext.set("alpha");
            TenantContext.callWithTenant(
                    "beta",
                    () -> {
                        assertThat(TenantContext.get()).isEqualTo("beta");
                        TenantContext.callWithTenant(
                                "gamma",
                                () -> {
                                    assertThat(TenantContext.get()).isEqualTo("gamma");
                                    return null;
                                });
                        assertThat(TenantContext.get()).isEqualTo("beta");
                        return null;
                    });
            assertThat(TenantContext.get()).isEqualTo("alpha");
        }

        @Test
        @DisplayName("body 抛异常也必须恢复原快照")
        void restoresOnException() {
            TenantContext.set("alpha");
            assertThatThrownBy(
                            () ->
                                    TenantContext.callWithTenant(
                                            "beta",
                                            () -> {
                                                throw new IllegalStateException("boom");
                                            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("boom");
            assertThat(TenantContext.get()).isEqualTo("alpha");
        }
    }

    @Nested
    @DisplayName("capture / restore：跨线程快照")
    class SnapshotPropagation {

        @Test
        @DisplayName("capture 捕获含未绑定状态，restore 返回回放前快照")
        void captureAndRestore() {
            TenantContext.set("alpha");
            Snapshot snapshot = TenantContext.capture();
            assertThat(snapshot.tenantKey()).isEqualTo("alpha");

            TenantContext.clear();
            assertThat(TenantContext.get()).isNull();

            Snapshot prev = snapshot.restore();
            assertThat(prev.tenantKey()).isNull(); // 回放前是未绑定
            assertThat(TenantContext.get()).isEqualTo("alpha");
        }

        @Test
        @DisplayName("快照可跨线程 apply 回放目标租户且线程间互不污染")
        void applyOnOtherThread() throws InterruptedException {
            TenantContext.set("alpha");
            Snapshot snapshot = TenantContext.capture();

            AtomicReference<String> seen = new AtomicReference<>("__unset__");
            Thread worker =
                    new Thread(
                            () -> {
                                // 新线程默认未绑定，apply 应回放 alpha
                                snapshot.apply();
                                seen.set(TenantContext.get());
                            });
            worker.start();
            worker.join();

            assertThat(seen.get()).isEqualTo("alpha"); // worker 收到快照租户
            assertThat(TenantContext.get()).isEqualTo("alpha"); // 主线程不受 worker 影响
        }

        @Test
        @DisplayName("null 快照 apply 视为清除")
        void nullSnapshotClears() {
            TenantContext.set("alpha");
            Snapshot unset = TenantContext.capture();
            // 手工构造未绑定快照（get 当前为 alpha，先清再捕获）
            TenantContext.clear();
            Snapshot blank = TenantContext.capture();
            assertThat(blank.tenantKey()).isNull();

            TenantContext.set("gamma");
            blank.apply();
            assertThat(TenantContext.get()).isNull();
            assertThat(unset.tenantKey()).isEqualTo("alpha");
        }
    }
}
