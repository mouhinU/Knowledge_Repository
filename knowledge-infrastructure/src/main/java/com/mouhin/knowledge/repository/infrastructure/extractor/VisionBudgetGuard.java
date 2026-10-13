package com.mouhin.knowledge.repository.infrastructure.extractor;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 视觉成本护栏（Phase D）。
 *
 * <p>以「全局每日视觉处理页数」为闸，跨文档累计当日已用页数并按自然日滚动复位。{@code enforcement=log-only}（默认）时 仅告警、绝不拦截，保证零副作用；{@code
 * enforce} 时超限的请求页被拒绝（调用方据此跳过视觉、保留文本层）。 因异步增强路径尚未透传调用主体，暂为全局粒度，主体接入后可平滑细化为 per-user。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Component
@Slf4j
public class VisionBudgetGuard {

    private final AtomicLong usedPagesToday = new AtomicLong();
    private final AtomicLong windowEpochDay = new AtomicLong(LocalDate.now().toEpochDay());

    /**
     * 尝试消费 {@code pages} 页视觉预算。
     *
     * @param pages 本次拟处理的页数
     * @param dailyLimit 全局每日页数上限
     * @param enforce true=超限拦截；false=仅告警不拦截
     * @return 允许处理返回 {@code true}；{@code enforce} 且超限返回 {@code false}
     */
    public synchronized boolean tryConsume(int pages, int dailyLimit, boolean enforce) {
        rollWindowIfNeeded();
        long used = usedPagesToday.get();
        if (used + pages > dailyLimit) {
            if (enforce) {
                log.warn("vision budget exceeded, skip pages: used={} limit={}", used, dailyLimit);
                return false;
            }
            log.warn(
                    "vision budget exceeded (log-only, not blocking): used={} limit={}",
                    used,
                    dailyLimit);
        }
        usedPagesToday.addAndGet(pages);
        return true;
    }

    /** 当日已消费页数（供观测/测试）。 */
    public long usedToday() {
        rollWindowIfNeeded();
        return usedPagesToday.get();
    }

    private void rollWindowIfNeeded() {
        long today = LocalDate.now().toEpochDay();
        if (windowEpochDay.get() != today) {
            windowEpochDay.set(today);
            usedPagesToday.set(0);
        }
    }
}
