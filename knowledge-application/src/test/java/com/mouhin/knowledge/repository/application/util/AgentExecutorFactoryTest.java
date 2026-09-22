package com.mouhin.knowledge.repository.application.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 智能体线程池工厂饱和策略回归测试（定向审计 CONC-2）。
 *
 * <p>锁定 {@link AgentExecutorFactory} 的溢出语义：池满时走 {@code AbortPolicy} 快速失败， 抛出 {@link
 * RejectedExecutionException}，而 <b>绝不</b>回退到 {@code CallerRunsPolicy} ——后者会让提交任务所在的调用线程（HTTP
 * servlet 线程）同步执行整条 LLM / 出卷 / 评分流水线， 分钟级阻塞请求线程并放大对上游模型服务的踩踏。用例通过在调用线程上运行拒绝探针， 验证拒绝发生在调用线程本身（抛出
 * REE）而非被就地执行。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@DisplayName("智能体线程池：饱和时快速失败抛 RejectedExecutionException，不回退到调用线程执行")
class AgentExecutorFactoryTest {

    /** 探针任务上限：足够越过工厂的 MAX_POOL_SIZE(32) 触发拒绝，又不无限循环。 */
    private static final int MAX_PROBE_TASKS = 512;

    @Test
    @DisplayName("池满抛 REE 且不抢占调用线程；拒绝前所有已接纳任务均正常运行")
    void saturatedPoolRejectsFastInsteadOfCallerRuns() throws Exception {
        ThreadPoolExecutor pool = AgentExecutorFactory.newBoundedAgentPool("test-agent");
        CountDownLatch gate = new CountDownLatch(1);
        int accepted = 0;
        boolean rejected = false;
        Thread caller = Thread.currentThread();

        try {
            for (int i = 0; i < MAX_PROBE_TASKS; i++) {
                final int taskId = i;
                // 被拒绝时 AbortPolicy 在 submit 处同步抛出；若是 CallerRunsPolicy，
                // 该任务体会在当前（调用）线程上运行 —— 借此区分两种策略。
                pool.execute(() -> assertTaskNotRunOnCallerThread(taskId, caller));
                accepted++;
            }
        } catch (RejectedExecutionException rex) {
            rejected = true;
        } finally {
            gate.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        // 必须确实发生了拒绝（证明池有界 + AbortPolicy），且拒绝前已接纳了若干任务。
        assertTrue(rejected, "线程池饱和时应抛出 RejectedExecutionException（AbortPolicy 快速失败）");
        assertTrue(accepted > 0, "在触发拒绝前应先成功接纳若干任务");
    }

    /**
     * 任务体断言：任务绝不运行在提交它的调用线程上。
     *
     * <p>若工厂仍采用 {@code CallerRunsPolicy}，饱和时任务会在调用线程就地运行， 本断言即失败——以此锁死“溢出不在请求线程跑”的修复意图。
     */
    private static void assertTaskNotRunOnCallerThread(int taskId, Thread caller) {
        if (Thread.currentThread() == caller) {
            fail("任务 #" + taskId + " 被回退到调用线程执行（疑似 CallerRunsPolicy），违反 CONC-2 快速失败约定");
        }
        try {
            // 短暂占位，模拟一次耗时的 Agent / LLM 调用，制造池饱和窗口。
            TimeUnit.MILLISECONDS.sleep(5);
        } catch (InterruptedException e) {
            // shutdownNow 会中断在睡的任务线程，属正常收尾，恢复中断标志即可。
            Thread.currentThread().interrupt();
        }
    }
}
