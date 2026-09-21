package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 考试评分进度存储与 SSE 推送
 *
 * <p>由 {@link ExamReviewController} 与评分流程桥接：Web 层建立 SSE 连接， 通过 {@link #createCallback(String)}
 * 得到回调注入到应用层，评分过程按题上报。
 *
 * <p>事件名（{@code event.name}）：
 *
 * <ul>
 *   <li>START — 单题开始，携带 questionIndex / questionType / input
 *   <li>DONE — 单题结束，携带 rawOutput / score / maxScore / feedback / elapsed
 *   <li>ERROR — 单题异常，携带 questionIndex / error
 *   <li>COMPLETE — 整场结束，携带 totalQuestions / totalAiScore
 *   <li>FATAL — 整场启动失败，携带 error
 * </ul>
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
@Component
public class ExamGradingProgressStore {

    private static final Logger logger = LoggerFactory.getLogger(ExamGradingProgressStore.class);

    /** SSE 超时：10 分钟 */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    /** 每场考试的 SSE 连接（同一 streamId 只允许一个客户端） */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 建立 SSE 通道，返回 emitter。若同 streamId 已有连接则替换（旧连接立即 complete）。
     *
     * @param streamId 前端生成的流 ID
     * @return SSE emitter
     */
    public SseEmitter createEmitter(String streamId) {
        SseEmitter previous = emitters.remove(streamId);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception ignored) {
                // 忽略
            }
        }
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(streamId, emitter);
        emitter.onCompletion(() -> emitters.remove(streamId, emitter));
        emitter.onTimeout(() -> emitters.remove(streamId, emitter));
        emitter.onError(e -> emitters.remove(streamId, emitter));
        logger.debug("评分 SSE 连接建立 [streamId={}]", streamId);
        return emitter;
    }

    /**
     * 构造应用层回调，把每题输入 / 原始输出 / 得分事件推给对应 SSE 通道。
     *
     * @param streamId SSE 通道 ID
     * @return 评分进度回调
     */
    public ExamGradingProgressCallback createCallback(String streamId) {
        return createCallback(streamId, null);
    }

    /**
     * 构造带 sessionId 标签的评分回调。
     *
     * <p>批量评分时同一 SSE 通道会承载多个场次的进度事件，前端按 sessionId 分派到对应卡片。
     *
     * @param streamId SSE 通道 ID
     * @param sessionId 若不为 null，事件 payload 中会附带 sessionId
     * @return 评分进度回调
     */
    public ExamGradingProgressCallback createCallback(String streamId, Long sessionId) {
        return new ExamGradingProgressCallback() {
            @Override
            public void onQuestionStart(int questionIndex, String questionType, String aiInput) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("questionIndex", questionIndex);
                payload.put("questionType", questionType == null ? "" : questionType);
                payload.put("input", aiInput == null ? "" : aiInput);
                send(streamId, "START", payload);
            }

            @Override
            public void onQuestionToken(int questionIndex, String kind, String delta) {
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("questionIndex", questionIndex);
                payload.put("kind", kind == null ? "output" : kind);
                payload.put("delta", delta);
                send(streamId, "TOKEN", payload);
            }

            @Override
            public void onQuestionDone(
                    int questionIndex,
                    String aiRawOutput,
                    int aiScore,
                    int maxScore,
                    String aiFeedback,
                    long elapsedMs) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("questionIndex", questionIndex);
                payload.put("rawOutput", aiRawOutput == null ? "" : aiRawOutput);
                payload.put("score", aiScore);
                payload.put("maxScore", maxScore);
                payload.put("feedback", aiFeedback == null ? "" : aiFeedback);
                payload.put("elapsed", elapsedMs);
                send(streamId, "DONE", payload);
            }

            @Override
            public void onQuestionError(int questionIndex, String errorMessage) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("questionIndex", questionIndex);
                payload.put("error", errorMessage == null ? "unknown" : errorMessage);
                send(streamId, "ERROR", payload);
            }

            @Override
            public void onComplete(int totalQuestions, int totalAiScore) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("totalQuestions", totalQuestions);
                payload.put("totalAiScore", totalAiScore);
                send(streamId, "COMPLETE", payload);
                // 单场模式：立即关闭；批量模式：由 Controller 归零后统一 BATCH_COMPLETE + close
                if (sessionId == null) {
                    closeStream(streamId);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                if (sessionId != null) {
                    payload.put("sessionId", sessionId);
                }
                payload.put("error", errorMessage == null ? "unknown" : errorMessage);
                send(streamId, "FATAL", payload);
                if (sessionId == null) {
                    closeStream(streamId);
                }
            }
        };
    }

    /**
     * 主动关闭 SSE 通道（Controller 在批量任务全部完成时调用）
     *
     * @param streamId SSE 通道 ID
     */
    public void close(String streamId) {
        closeStream(streamId);
    }

    /**
     * 推送批量收尾事件并关闭 SSE 通道
     *
     * @param streamId SSE 通道 ID
     * @param sessionCount 本次批量评分覆盖的场次数
     */
    public void emitBatchComplete(String streamId, int sessionCount) {
        send(streamId, "BATCH_COMPLETE", Map.of("sessionCount", sessionCount));
        closeStream(streamId);
    }

    private void send(String streamId, String name, Map<String, Object> payload) {
        SseEmitter emitter = emitters.get(streamId);
        if (emitter == null) {
            logger.debug("评分 SSE 未连接，丢弃事件 [streamId={}, event={}]", streamId, name);
            return;
        }
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(name).data(payload));
            }
        } catch (IOException e) {
            logger.debug("评分 SSE 推送失败 [streamId={}, event={}]: {}", streamId, name, e.getMessage());
            emitters.remove(streamId, emitter);
        } catch (Exception e) {
            logger.warn("评分 SSE 推送异常 [streamId={}, event={}]", streamId, name, e);
        }
    }

    private void closeStream(String streamId) {
        SseEmitter emitter = emitters.remove(streamId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 忽略
            }
        }
    }
}
