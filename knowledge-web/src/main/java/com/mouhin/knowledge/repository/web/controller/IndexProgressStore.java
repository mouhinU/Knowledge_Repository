package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.domain.service.IndexProgressCallback;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 索引进度 SSE 推送管理
 *
 * <p>管理每个文档索引过程的 SSE 连接，将进度事件实时推送到前端。 支持事件缓冲，防止 SSE 连接建立前的进度事件丢失。
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Component
@Slf4j
public class IndexProgressStore {

    /** SSE 超时：5 分钟 */
    private static final long SSE_TIMEOUT = 300_000L;

    /** 每个文档的 SSE 发射器 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 发射器
     *
     * @param documentKey 文档标识
     * @return SseEmitter 实例
     */
    public SseEmitter createEmitter(String documentKey) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(documentKey, emitter);

        emitter.onCompletion(() -> emitters.remove(documentKey));
        emitter.onTimeout(() -> emitters.remove(documentKey));
        emitter.onError(e -> emitters.remove(documentKey));

        return emitter;
    }

    /**
     * 创建进度回调，将进度事件通过 SSE 推送
     *
     * @param documentKey 文档标识
     * @return IndexProgressCallback 实例
     */
    public IndexProgressCallback createCallback(String documentKey) {
        return new IndexProgressCallback() {
            @Override
            public void onProgress(int completedChunks, int totalChunks) {
                pushEvent(
                        documentKey,
                        Map.of(
                                "type",
                                "PROGRESS",
                                "completedChunks",
                                completedChunks,
                                "totalChunks",
                                totalChunks,
                                "percent",
                                totalChunks > 0 ? (completedChunks * 100 / totalChunks) : 0));
            }

            @Override
            public void onComplete() {
                pushEvent(documentKey, Map.of("type", "COMPLETED"));
                cleanup(documentKey);
            }

            @Override
            public void onError(String errorMessage) {
                pushEvent(
                        documentKey,
                        Map.of(
                                "type",
                                "ERROR",
                                "message",
                                errorMessage != null ? errorMessage : "Unknown error"));
                cleanup(documentKey);
            }
        };
    }

    private void pushEvent(String documentKey, Map<String, Object> data) {
        SseEmitter emitter = emitters.get(documentKey);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE send failed for document {}: {}", documentKey, e.getMessage());
            emitters.remove(documentKey);
        }
    }

    private void cleanup(String documentKey) {
        SseEmitter emitter = emitters.remove(documentKey);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete failed: {}", e.getMessage());
            }
        }
    }
}
