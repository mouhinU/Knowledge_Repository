package com.mouhin.knowledge.repository.application.util;

import com.mouhin.knowledge.repository.domain.context.TenantContext;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 租户上下文跨线程传播装饰器（P1-T9，SaaS 异步脊柱）。
 *
 * <p>问题：{@link TenantContext} 是 {@link ThreadLocal}，出卷 / 评分 / 文章三条流水线的 fan-out 均通过 {@code
 * CompletableFuture.runAsync(task, agentExecutor)} 提交到独立线程池；worker 线程从池里复用时，
 * 前一次任务的租户绑定会残留（甚至串到下一位用户）。若不做快照传播，MyBatis-Plus {@code TenantLineInnerInterceptor}
 * 在异步路径下读到的将是"上次谁留下什么就是什么"，直接踩红线（跨租户数据泄漏 / 默认落到 {@code default}）。
 *
 * <p>解决：本装饰器在<b>提交侧</b>（调用线程）{@code capture()} 当前租户快照，在<b>执行侧</b>（worker 线程）先 {@code apply()}
 * 快照、执行任务、再 {@code restore()} 前置状态——即便线程池复用 worker 也不会串租户，且嵌套调用 / 异常路径都保证回到提交前的 ThreadLocal 值。零
 * Spring 依赖、纯 JDK，放 app/util 与 {@code AgentExecutorFactory} 同层。
 *
 * <p>覆盖范围：{@link #execute(Runnable)} 是 {@code CompletableFuture.runAsync / supplyAsync} 的最终落点，也是
 * {@link java.util.concurrent.ExecutorService#submit} 系列的骨架，装饰它就等价于覆盖全部异步入口。 其余 {@code
 * ExecutorService} API 全部原样转发到 {@code delegate}，不吞语义。
 *
 * <p>未做而留给 Phase 3 的增强：批量任务的父/子租户分离（例如某任务临时切换到 {@code default} 做全局索引）、 ScopedValue 迁移（{@link
 * TenantContext} 内部实现换掉时本类无须改动，API 已对齐）。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public final class TenantContextPropagatingExecutor implements ExecutorService {

    private final ExecutorService delegate;

    public TenantContextPropagatingExecutor(ExecutorService delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate executor must not be null");
        }
        this.delegate = delegate;
    }

    /**
     * 就地包装：在<b>调用线程</b>捕获租户快照，返回一个会在执行时应用快照、{@code finally} 恢复前置状态的 {@link Runnable}。
     *
     * <p>用途（P1-T9 收尾）：文档入库三条异步链路（{@code Index / Reindex / IndexCustomChunks AsyncCmdExe}）历史上把
     * {@code CompletableFuture.runAsync(task)}（<b>无 executor 参数</b>，落 {@link
     * java.util.concurrent.ForkJoinPool#commonPool()}）作为脱离请求线程的兜底手段。给它们换专属线程池会改变既有拒绝 /
     * 并发语义（超出本任务"补租户传播"的边界）， 故只在这一处包一层快照：提交线程 {@code capture()}，commonPool worker 执行前 {@code
     * restore()}、执行后 {@code finally} 复位——commonPool 线程随即回到"未绑定"，绝不把 A 租户带到下一次复用。
     *
     * @param task 原始任务，非空
     * @return 带租户快照传播的包装任务
     */
    public static Runnable wrapCurrent(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return wrap(task, TenantContext.capture());
    }

    /**
     * {@link Runnable} 版的有返回值对应物，供 {@code CompletableFuture.supplyAsync} 落点使用（P1-T9 同语义）。
     *
     * @param task 原始供应任务，非空
     * @param <T>  返回类型
     * @return 带租户快照传播的包装供应任务
     */
    public static <T> Supplier<T> wrapCurrent(Supplier<T> task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        TenantContext.Snapshot captured = TenantContext.capture();
        return () -> {
            TenantContext.Snapshot previous = captured.restore();
            try {
                return task.get();
            } finally {
                previous.restore();
            }
        };
    }

    private static Runnable wrap(Runnable task, TenantContext.Snapshot captured) {
        return () -> {
            TenantContext.Snapshot previous = captured.restore();
            try {
                task.run();
            } finally {
                previous.restore();
            }
        };
    }

    /**
     * 提交点捕获快照，执行点应用+恢复。任务体若自行抛错由 {@code delegate} 后续 Future / UncaughtHandler 处理； 本装饰只保证 {@code
     * finally} 一定把 worker 线程恢复到进入前的租户。
     */
    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        TenantContext.Snapshot captured = TenantContext.capture();
        delegate.execute(wrap(command, captured));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(tagged(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(tagged(task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(tagged(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return delegate.invokeAll(taggedAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(taggedAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(taggedAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(taggedAll(tasks), timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private Runnable tagged(Runnable task) {
        if (task == null) {
            throw new RejectedExecutionException("task must not be null");
        }
        return wrap(task, TenantContext.capture());
    }

    private <T> Callable<T> tagged(Callable<T> task) {
        if (task == null) {
            throw new RejectedExecutionException("task must not be null");
        }
        TenantContext.Snapshot captured = TenantContext.capture();
        return () -> {
            TenantContext.Snapshot previous = captured.restore();
            try {
                return task.call();
            } finally {
                previous.restore();
            }
        };
    }

    private <T> Collection<? extends Callable<T>> taggedAll(
            Collection<? extends Callable<T>> tasks) {
        return tasks.stream().map(this::tagged).toList();
    }
}
