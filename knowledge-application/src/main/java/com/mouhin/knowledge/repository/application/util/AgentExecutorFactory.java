package com.mouhin.knowledge.repository.application.util;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能体 / 评分异步任务线程池工厂。
 *
 * <p>统一收口应用层异步任务的线程资源，替代原先散落的 {@code Executors.newVirtualThreadPerTaskExecutor()}：后者无界创建任务、且从不关闭，
 * 一旦出卷扇出或批量评分瞬时涌入过多任务，会放大对 Ollama / 向量库 / 连接池的并发压力， 容器优雅停机时也会遗留在途任务。按《Java 开发手册》与项目 AGENTS.md 第六章，
 * 线程池必须经 {@link ThreadPoolExecutor} 显式构造，此处给出合规且有界的默认构造。
 *
 * <p>线程数与队列显式命名，避免魔法值散落。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
public final class AgentExecutorFactory {

    /** 空闲核心线程数：任务全部完成后不保留常驻线程。 */
    private static final int CORE_POOL_SIZE = 0;

    /** 最大并发线程数上限：约束同时在途的 LLM / 出卷 / 评分任务数量， 防止瞬时扇出压垮模型服务与数据库连接池。 */
    private static final int MAX_POOL_SIZE = 32;

    /** 空闲线程存活时间（秒）：超过上限的空闲线程在此时长后回收。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private AgentExecutorFactory() {}

    /**
     * 构建一个有界、空闲回收、溢出走 {@code AbortPolicy}（快速失败）的线程池。
     *
     * <p>采用 {@link SynchronousQueue}：任务到达时若无空闲线程则立即扩容至上限。达到上限后 <b>不再</b>由调用线程自行执行（旧 {@code
     * CallerRunsPolicy} 会让承载 HTTP 请求的 servlet 线程同步跑完整条 LLM / 出卷 / 评分流水线，分钟级阻塞请求线程、违背异步语义，
     * 并放大对上游模型服务的踩踏），而是抛出 {@link java.util.concurrent.RejectedExecutionException}， 由上层调用点捕获并向客户端返回
     * 429（请求过多），实现背压下的快速失败。
     *
     * @param namePrefix 线程名前缀，便于日志与线程栈定位
     * @return 显式构造的有界线程池
     */
    public static ThreadPoolExecutor newBoundedAgentPool(String namePrefix) {
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new NamedDaemonThreadFactory(namePrefix),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 带序号的守护线程工厂：线程名可读、设为守护线程，并兜底记录未捕获异常。
     *
     * @author mouhinU
     * @date 2026-09-19
     */
    private static final class NamedDaemonThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedDaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(
                    (thread, ex) ->
                            System.err.println(
                                    "[AgentExecutor] 线程 " + thread.getName() + " 未捕获异常: " + ex));
            return t;
        }
    }
}
