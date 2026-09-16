package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 文章生成进度存储与 SSE 推送
 * <p>
 * 管理 SSE 连接和事件缓冲。支持客户端先连接 SSE、再启动生成的场景：
 * 事件到达时若 SSE 已连接则直接推送，否则缓冲等待。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-13
 */
@Component
public class BlackboardProgressStore {

    private static final Logger logger = LoggerFactory.getLogger(BlackboardProgressStore.class);

    /**
     * SSE 超时时间：10 分钟
     */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    /**
     * 每个 session 的 SSE 连接列表
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 每个 session 的事件缓冲（SSE 连接前的早期事件）
     */
    private final Map<String, List<BlackboardProgressEvent>> eventBuffers = new ConcurrentHashMap<>();

    /**
     * 创建 SseEmitter 并关联到指定 session。
     * 回放已缓冲的事件，然后实时接收后续事件。
     *
     * @param sessionId 会话 ID
     * @return 新创建的 SseEmitter
     */
    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 回放已缓冲的事件
        List<BlackboardProgressEvent> buffered = eventBuffers.get(sessionId);
        if (buffered != null) {
            synchronized (buffered) {
                for (BlackboardProgressEvent event : buffered) {
                    sendToEmitter(emitter, event);
                }
            }
        }

        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(e -> removeEmitter(sessionId, emitter));

        logger.debug("SSE emitter 创建 [session={}, buffered={}]",
                sessionId, buffered != null ? buffered.size() : 0);
        return emitter;
    }

    /**
     * 推送事件到指定 session
     * <p>
     * 若 SSE 已连接则直接推送；否则缓冲等待。
     * </p>
     *
     * @param sessionId 会话 ID
     * @param event     进度事件
     */
    public void pushEvent(String sessionId, BlackboardProgressEvent event) {
        List<SseEmitter> sessionEmitters = emitters.get(sessionId);
        boolean hasActiveEmitter = sessionEmitters != null && !sessionEmitters.isEmpty();

        if (hasActiveEmitter) {
            // SSE 已连接，直接推送
            for (SseEmitter emitter : sessionEmitters) {
                sendToEmitter(emitter, event);
            }
        } else {
            // SSE 尚未连接，缓冲事件
            eventBuffers.computeIfAbsent(sessionId, k -> {
                synchronized (new Object()) {
                    return new CopyOnWriteArrayList<>();
                }
            }).add(event);
        }

        // 终态事件：延迟清理
        if ("COMPLETED".equals(event.getType()) || "ERROR".equals(event.getType())) {
            scheduleCleanup(sessionId);
        }
    }

    private void sendToEmitter(SseEmitter emitter, BlackboardProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(eventToMap(event)));
        } catch (IOException e) {
            logger.debug("SSE 推送失败: {}", e.getMessage());
        }
    }

    private void scheduleCleanup(String sessionId) {
        Thread.ofVirtual().name("progress-cleanup-" + sessionId).start(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            eventBuffers.remove(sessionId);
            List<SseEmitter> removed = emitters.remove(sessionId);
            if (removed != null) {
                for (SseEmitter emitter : removed) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // 忽略
                    }
                }
            }
            logger.debug("已清理 session 进度数据 [session={}]", sessionId);
        });
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        List<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters != null) {
            sessionEmitters.remove(emitter);
        }
    }

    /**
     * 将事件转换为 Map（用于 JSON 序列化）
     */
    private Map<String, Object> eventToMap(BlackboardProgressEvent event) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        putIfNotNull(map, "type", event.getType());
        if (event.getPhase() != null) {
            map.put("phase", event.getPhase().name());
        }
        putIfNotNull(map, "agentName", event.getAgentName());
        putIfNotNull(map, "agentStatus", event.getAgentStatus());
        putIfNotNull(map, "output", event.getOutput());
        putIfNotNull(map, "message", event.getMessage());
        putIfNotNull(map, "finalArticle", event.getFinalArticle());
        putIfNotNull(map, "keyFindings", event.getKeyFindings());
        putIfNotNull(map, "draftArticle", event.getDraftArticle());
        putIfNotNull(map, "reviewFeedback", event.getReviewFeedback());
        if (event.getQualityScore() > 0) {
            map.put("qualityScore", event.getQualityScore());
        }
        if (event.getRetrievedChunks() > 0) {
            map.put("retrievedChunks", event.getRetrievedChunks());
        }
        putIfNotNull(map, "errorMessage", event.getErrorMessage());
        putIfNotNull(map, "materials", event.getMaterials());
        putIfNotNull(map, "examPaper", event.getExamPaper());
        putIfNotNull(map, "answerKey", event.getAnswerKey());
        putIfNotNull(map, "examReviewFeedback", event.getExamReviewFeedback());
        putIfNotNull(map, "difficultyAssessment", event.getDifficultyAssessment());
        putIfNotNull(map, "deduplicationReport", event.getDeduplicationReport());
        return map;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
