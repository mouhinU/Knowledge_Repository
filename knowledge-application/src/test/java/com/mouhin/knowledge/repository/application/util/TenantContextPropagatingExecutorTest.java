package com.mouhin.knowledge.repository.application.util;

import static org.junit.jupiter.api.Assertions.*;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

/**
 * {@link TenantContextPropagatingExecutor} 契约单测（P1-T9，SaaS 异步脊柱）。
 *
 * <p>验证核心不变式：worker 线程执行任务体时 {@link TenantContext#get()} 等于<b>提交时刻</b>调用线程的租户； 任务结束后 worker 线程的
 * ThreadLocal 回到进入前的状态（支持池复用不泄漏、嵌套不串）。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("TenantContextPropagatingExecutor 快照跨线程传播 (P1-T9)")
class TenantContextPropagatingExecutorTest {

    private ThreadPoolExecutor rawPool;
    private ExecutorService tenantAware;

    @BeforeEach
    void setUp() {
        rawPool =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        Executors.defaultThreadFactory(),
                        new ThreadPoolExecutor.AbortPolicy());
        tenantAware = new TenantContextPropagatingExecutor(rawPool);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        rawPool.shutdownNow();
        TenantContext.clear();
    }

    @Nested
    @DisplayName("execute：CompletableFuture.runAsync 落点")
    class Execute {

        @Test
        @DisplayName("调用线程已绑 acme：worker 任务体读到 acme")
        void propagatesCallerTenant() throws Exception {
            TenantContext.set("acme");
            AtomicReference<String> observed = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);

            tenantAware.execute(
                    () -> {
                        observed.set(TenantContext.get());
                        done.countDown();
                    });

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertEquals("acme", observed.get());
        }

        @Test
        @DisplayName("调用线程未绑定：worker 任务体读到 null（不假填 default）")
        void propagatesUnboundState() throws Exception {
            TenantContext.clear();
            AtomicReference<String> observed = new AtomicReference<>("sentinel");
            CountDownLatch done = new CountDownLatch(1);

            tenantAware.execute(
                    () -> {
                        observed.set(TenantContext.get());
                        done.countDown();
                    });

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertNull(observed.get(), "未绑定时应传 null，让下游 getOrDefault 决定回落语义");
        }

        @Test
        @DisplayName("worker 线程复用时：前后两次不同租户提交互不污染（第二次读到 acme2）")
        void noLeakAcrossPoolReuse() throws Exception {
            TenantContext.set("acme1");
            CountDownLatch firstDone = new CountDownLatch(1);
            tenantAware.execute(firstDone::countDown);
            assertTrue(firstDone.await(2, TimeUnit.SECONDS));

            TenantContext.set("acme2");
            AtomicReference<String> observed = new AtomicReference<>();
            CountDownLatch secondDone = new CountDownLatch(1);
            tenantAware.execute(
                    () -> {
                        observed.set(TenantContext.get());
                        secondDone.countDown();
                    });
            assertTrue(secondDone.await(2, TimeUnit.SECONDS));

            assertEquals("acme2", observed.get(), "第二次提交应看到调用线程当前租户，非池残留");
        }

        @Test
        @DisplayName("任务抛异常也须恢复 worker 线程前置状态（不污染后续复用）")
        void restoresOnTaskFailure() throws Exception {
            // 预热 worker，让它先带一个"上次遗留"（若无装饰会泄漏）
            TenantContext.set("legacy");
            CountDownLatch warm = new CountDownLatch(1);
            tenantAware.execute(warm::countDown);
            assertTrue(warm.await(2, TimeUnit.SECONDS));

            // 提交一个必然抛错的任务，观察 finally 是否清理
            TenantContext.set("boom-tenant");
            CountDownLatch rejected = new CountDownLatch(1);
            tenantAware.execute(
                    () -> {
                        try {
                            throw new IllegalStateException("boom");
                        } finally {
                            rejected.countDown();
                        }
                    });
            assertTrue(rejected.await(2, TimeUnit.SECONDS));

            // 提交一个正常任务，观察 worker 是否回到"干净"（未绑定）状态
            TenantContext.clear();
            AtomicReference<String> observed = new AtomicReference<>("sentinel");
            CountDownLatch clean = new CountDownLatch(1);
            tenantAware.execute(
                    () -> {
                        observed.set(TenantContext.get());
                        clean.countDown();
                    });
            assertTrue(clean.await(2, TimeUnit.SECONDS));
            assertNull(observed.get(), "异常路径后 worker 线程应恢复调用前的 null 绑定，不携带 boom-tenant");
        }
    }

    @Nested
    @DisplayName("submit / invokeAll：Future 语义等价保留")
    class FutureApis {

        @Test
        @DisplayName("submit(Callable) 返回值不受影响且任务体读到调用侧租户")
        void submitCallablePropagatesAndReturns() throws Exception {
            TenantContext.set("beta");
            Callable<String> task = () -> TenantContext.get() + ":" + "ok";
            Future<String> future = tenantAware.submit(task);

            assertEquals("beta:ok", future.get(2, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("submit(Runnable) 完成后 result 位仍返回原对象")
        void submitRunnableWithResult() throws Exception {
            TenantContext.set("gamma");
            Object sentinel = new Object();
            AtomicReference<String> observed = new AtomicReference<>();
            Future<Object> future =
                    tenantAware.submit(() -> observed.set(TenantContext.get()), sentinel);

            assertEquals(sentinel, future.get(2, TimeUnit.SECONDS));
            assertEquals("gamma", observed.get());
        }

        @Test
        @DisplayName("invokeAll：每个 Callable 独立捕获提交侧快照")
        void invokeAllPropagatesForEach() throws Exception {
            TenantContext.set("omega");
            List<Callable<String>> tasks =
                    List.of(TenantContext::get, TenantContext::get, TenantContext::get);

            List<Future<String>> futures = tenantAware.invokeAll(tasks, 2, TimeUnit.SECONDS);
            for (Future<String> f : futures) {
                assertEquals("omega", f.get());
            }
        }
    }

    @Nested
    @DisplayName("参数与生命周期")
    class Contract {

        @Test
        @DisplayName("delegate=null 拒绝构造")
        void rejectsNullDelegate() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TenantContextPropagatingExecutor(null));
        }

        @Test
        @DisplayName("execute(null) NPE；submit(null) 拒绝执行")
        void rejectsNullCommand() {
            assertThrows(NullPointerException.class, () -> tenantAware.execute(null));
            assertThrows(
                    java.util.concurrent.RejectedExecutionException.class,
                    () -> tenantAware.submit((Runnable) null));
            assertThrows(
                    java.util.concurrent.RejectedExecutionException.class,
                    () -> tenantAware.submit((Callable<String>) null));
        }

        @Test
        @DisplayName("shutdown / isShutdown / awaitTermination 透传 delegate")
        void delegatesLifecycle() throws Exception {
            tenantAware.shutdown();
            assertTrue(tenantAware.isShutdown());
            assertTrue(tenantAware.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(tenantAware.isTerminated());
        }
    }

    @Nested
    @DisplayName("wrapCurrent：commonPool 落点就地快照 (P1-T9 收尾)")
    class WrapCurrent {

        @Test
        @DisplayName("Runnable：wrap 时刻捕获调用线程租户，之后改租户/换线程执行仍读原捕获值")
        void runnableCapturesAtWrapTime() throws Exception {
            TenantContext.set("acme");
            AtomicReference<String> observed = new AtomicReference<>();
            Runnable probe =
                    TenantContextPropagatingExecutor.wrapCurrent(
                            () -> observed.set(TenantContext.get()));

            // wrap 之后立刻改掉当前线程租户：证明捕获发生在 wrap 调用点，而非执行点
            TenantContext.set("changed-after-wrap");

            // 在另一个线程执行 probe（模拟 commonPool worker，其进入前状态为 null）
            Thread worker = new Thread(probe);
            worker.start();
            worker.join(2000);

            assertEquals("acme", observed.get(), "应读到 wrap 时刻的 acme，而非执行时刻的值");
        }

        @Test
        @DisplayName("Runnable：worker 线程执行完后恢复其进入前的绑定，不残留租户")
        void runnableRestoresWorkerPriorState() throws Exception {
            TenantContext.set("task-tenant");
            Runnable wrapped = TenantContextPropagatingExecutor.wrapCurrent(() -> {});

            AtomicReference<String> afterRun = new AtomicReference<>("sentinel");
            Thread worker =
                    new Thread(
                            () -> {
                                TenantContext.set("worker-preexisting");
                                wrapped.run();
                                afterRun.set(TenantContext.get());
                            });
            worker.start();
            worker.join(2000);

            assertEquals(
                    "worker-preexisting",
                    afterRun.get(),
                    "执行完应回到 worker 线程进入前的绑定，而非 task-tenant 或 null");
        }

        @Test
        @DisplayName("Supplier：有返回值链路同样携带快照并透传结果")
        void supplierPropagatesAndReturns() {
            TenantContext.set("omega");
            java.util.function.Supplier<String> wrapped =
                    TenantContextPropagatingExecutor.wrapCurrent(
                            () -> TenantContext.get() + "|value");

            assertEquals("omega|value", wrapped.get());
        }

        @Test
        @DisplayName("wrapCurrent(null) 抛 NPE")
        void rejectsNull() {
            assertThrows(
                    NullPointerException.class,
                    () -> TenantContextPropagatingExecutor.wrapCurrent((Runnable) null));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            TenantContextPropagatingExecutor.wrapCurrent(
                                    (java.util.function.Supplier<String>) null));
        }
    }
}
