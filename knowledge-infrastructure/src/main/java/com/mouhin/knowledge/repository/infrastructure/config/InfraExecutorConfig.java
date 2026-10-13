package com.mouhin.knowledge.repository.infrastructure.config;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施层异步执行器装配（Phase B 起）。
 *
 * <p>提供<b>独立有界</b>的 {@code visionExecutor}，供 Phase C 的视觉异步增强（{@code CompletableFuture.runAsync(...,
 * visionExecutor)}）使用：核心 2、最大 4、队列 64、{@link ThreadPoolExecutor.CallerRunsPolicy} 兜底，与出卷 / Agent 的
 * 线程池物理隔离，避免视觉长任务饿死对话线程。池内线程在首次提交任务时才惰性创建，默认视觉关闭时该 Bean 空载无害。
 *
 * <p>P1-T9：池外再套一层 {@link TenantContext} 快照传播。视觉增强经 {@code visionExecutor} 落进 worker 线程时若不带
 * 快照，MyBatis-Plus {@code TenantLineInnerInterceptor} 在 {@code saas.enabled=true} 路径下会读到线程复用留下的
 * 上一位用户的租户，属跨租户读写红线。装饰实现内联在本类，与 {@code
 * com.mouhin.knowledge.repository.application.util.TenantContextPropagatingExecutor} 语义一致—— 后者在 app
 * 模块，infra 因 COLA 依赖方向（infra→domain，不可反向依赖 app）无法直接复用，此处以等价匿名类兜住。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Configuration
@Slf4j
public class InfraExecutorConfig {

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 64;

    /** 视觉异步增强专用有界线程池（已包一层 {@link TenantContext} 快照传播）。 */
    @Bean("visionExecutor")
    public Executor visionExecutor() {
        log.info(
                "Vision executor initialized: core={} max={} queue={} (tenant-aware wrapper on)",
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY);
        ThreadPoolExecutor pool =
                new ThreadPoolExecutor(
                        CORE_POOL_SIZE,
                        MAX_POOL_SIZE,
                        KEEP_ALIVE_SECONDS,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                        new ThreadPoolExecutor.CallerRunsPolicy());
        return new TenantPropagatingExecutorView(pool);
    }

    /**
     * {@link Executor} 视图：每次 {@code execute} 前在提交线程捕获租户快照，在 worker 线程 {@code apply()} 后执行任务，{@code
     * finally} 里 {@code restore()} 前置状态。与 app 层的 {@code TenantContextPropagatingExecutor} 语义等价，仅因
     * COLA 依赖方向就地复制一份最小实现。
     */
    private static final class TenantPropagatingExecutorView implements Executor {

        private final ThreadPoolExecutor delegate;

        private TenantPropagatingExecutorView(ThreadPoolExecutor delegate) {
            if (delegate == null) {
                throw new RejectedExecutionException("delegate must not be null");
            }
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            if (command == null) {
                throw new NullPointerException("command");
            }
            TenantContext.Snapshot captured = TenantContext.capture();
            delegate.execute(
                    () -> {
                        TenantContext.Snapshot previous = captured.restore();
                        try {
                            command.run();
                        } finally {
                            previous.restore();
                        }
                    });
        }
    }
}
