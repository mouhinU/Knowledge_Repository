package com.mouhin.knowledge.repository.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

    /** 视觉异步增强专用有界线程池。 */
    @Bean("visionExecutor")
    public Executor visionExecutor() {
        log.info(
                "Vision executor initialized: core={} max={} queue={}",
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                QUEUE_CAPACITY);
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
