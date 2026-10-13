package com.mouhin.knowledge.repository.domain.context;

import java.util.function.Supplier;

/**
 * 租户上下文（P1-T2，SaaS 脊柱地基）。
 *
 * <p>以 {@link ThreadLocal} 持有"当前请求 / 当前任务"所属租户的业务标识（{@code tenant_key}）。放在 domain 层，零框架依赖，供 app /
 * infrastructure 共用同一口径，符合"领域层保持纯净"红线。
 *
 * <p><b>清理纪律（红线 #11 / 提案 §2.2）</b>：任何 {@code set} 之后必须在 {@code finally} 中 {@code clear}，否则线程池复用会把 A
 * 租户上下文泄漏到 B 请求。跨线程 / 异步用例（出卷、文章、索引的 {@code CompletableFuture}）不得隐式依赖本上下文，须用 {@link #capture()}
 * 显式携带快照后在目标线程 {@link Snapshot#restore()}（P1-T9 落地装饰型 executor）。
 *
 * <p><b>ScopedValue 预留</b>：JDK 21 正式提供 {@code ScopedValue}，未来若迁移到虚拟线程可将本类内部实现替换为 {@code
 * ScopedValue.where(...)}，对外 API（{@code getOrDefault/capture}）保持稳定；迁移须重跑 P1-T9 快照测试。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public final class TenantContext {

    /**
     * 单租户语义下的默认租户键（与 V23 种子 {@code sys_tenant.tenant_key='default'} 对齐）。
     */
    public static final String DEFAULT_TENANT_KEY = "default";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 绑定当前线程的租户。
     *
     * @param tenantKey 租户业务标识；{@code null} 或空白视为"不绑定"（等价于清除），非空则去除首尾空白后写入
     */
    public static void set(String tenantKey) {
        if (tenantKey == null || tenantKey.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantKey.trim());
        }
    }

    /**
     * 返回当前线程原始租户值，未设置时为 {@code null}（用于区分"未绑定"与"绑定为 default"）。
     */
    public static String get() {
        return CURRENT.get();
    }

    /**
     * 返回当前线程租户；未设置时回落到 {@link #DEFAULT_TENANT_KEY}。
     */
    public static String getOrDefault() {
        String value = CURRENT.get();
        return value == null ? DEFAULT_TENANT_KEY : value;
    }

    /**
     * 清除当前线程租户绑定。必须在 {@code finally} 中调用。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 捕获当前线程租户快照（含"未绑定"状态），供异步任务跨线程回放。
     */
    public static Snapshot capture() {
        return new Snapshot(CURRENT.get());
    }

    /**
     * 在指定租户下执行 {@code body}，无论正常返回或抛错都恢复到执行前的租户快照（支持嵌套）。
     *
     * @param tenantKey 目标租户；{@code null}/空白表示在该段内以"未绑定"运行
     * @param body      业务逻辑
     * @return {@code body} 的返回值
     */
    public static <T> T callWithTenant(String tenantKey, Supplier<T> body) {
        Snapshot previous = capture();
        try {
            set(tenantKey);
            return body.get();
        } finally {
            previous.restore();
        }
    }

    /**
     * 租户上下文快照：不可变的 {@code tenantKey} 载体（{@code null} 表示"未绑定"）。
     *
     * @param tenantKey 捕获时的租户键，可为 {@code null}
     */
    public record Snapshot(String tenantKey) {

        /**
         * 回放本快照到当前线程，并返回回放前的快照以便进一步嵌套 / 恢复。
         */
        public Snapshot restore() {
            Snapshot previous = capture();
            if (tenantKey == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(tenantKey);
            }
            return previous;
        }

        /**
         * 在异步执行前把捕获的租户写入当前线程。
         */
        public void apply() {
            if (tenantKey == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(tenantKey);
            }
        }
    }
}
