package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.examgrading.GradeExamAsyncCmdExe;
import com.mouhin.knowledge.repository.application.executor.examgrading.TriggerGradingAsyncCmdExe;
import com.mouhin.knowledge.repository.client.api.ExamGradingServiceI;
import com.mouhin.knowledge.repository.client.api.ExamTakingServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.client.dto.ReviewRequest;
import com.mouhin.knowledge.repository.domain.service.ExamGradingProgressCallback;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 成绩复核控制器（管理端）
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/admin/exam-review")
public class ExamReviewController {

    private static final Logger logger = LoggerFactory.getLogger(ExamReviewController.class);

    private final ExamGradingServiceI gradingService;
    private final ExamTakingServiceI examTakingService;
    private final ExamGradingProgressStore gradingProgressStore;
    private final GradeExamAsyncCmdExe gradeExamAsyncCmdExe;
    private final TriggerGradingAsyncCmdExe triggerGradingAsyncCmdExe;

    public ExamReviewController(
            ExamGradingServiceI gradingService,
            ExamTakingServiceI examTakingService,
            ExamGradingProgressStore gradingProgressStore,
            GradeExamAsyncCmdExe gradeExamAsyncCmdExe,
            TriggerGradingAsyncCmdExe triggerGradingAsyncCmdExe) {
        this.gradingService = gradingService;
        this.examTakingService = examTakingService;
        this.gradingProgressStore = gradingProgressStore;
        this.gradeExamAsyncCmdExe = gradeExamAsyncCmdExe;
        this.triggerGradingAsyncCmdExe = triggerGradingAsyncCmdExe;
    }

    /** 查询待 AI 评分的考试列表（SUBMITTED 状态） */
    @GetMapping("/pending-grading")
    public ResponseEntity<Map<String, Object>> listPendingGrading(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ExamSessionDTO> sessions =
                gradingService.listPendingGradingSessions(limit, offset).getData();
        long total = gradingService.countPendingGrading().getData();
        return ResponseEntity.ok(
                Map.of(
                        "records", sessions,
                        "total", total));
    }

    /** 手动触发单场考试的 AI 评分（同步，向后兼容） */
    @PostMapping("/{sessionId}/trigger-grading")
    public ResponseEntity<Map<String, String>> triggerGrading(@PathVariable Long sessionId) {
        try {
            gradingService.triggerGrading(sessionId);
            return ResponseEntity.ok(Map.of("message", "AI 评分完成"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 异步触发单场考试的 AI 评分（前端 EventSource 通道）
     *
     * <p>立即返回 sessionId，实际评分在虚拟线程中执行，每题通过 SSE 通道 {@code streamId} 上报。
     *
     * @param sessionId 考试场次 ID
     * @param streamId 前端生成的 SSE 通道 ID（与 GET /grading-stream 一致）
     */
    @PostMapping("/{sessionId}/trigger-grading-async")
    public ResponseEntity<Map<String, Object>> triggerGradingAsync(
            @PathVariable Long sessionId, @RequestParam String streamId) {
        try {
            ExamGradingProgressCallback callback = gradingProgressStore.createCallback(streamId);
            triggerGradingAsyncCmdExe.execute(sessionId, callback);
            return ResponseEntity.ok(
                    Map.of(
                            "message", "AI 评分已启动",
                            "sessionId", sessionId,
                            "streamId", streamId));
        } catch (RejectedExecutionException rex) {
            // 评分线程池已达并发上限：任务未启动（SSE 已上报繁忙），返回 429 供前端退避重试。
            logger.warn("评分请求被限流（并发已达上限）[session={}]", sessionId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "系统繁忙，评分并发已达上限，请稍后重试"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量异步触发所有待评分考试的 AI 评分（前端 EventSource 通道）
     *
     * <p>所有场次共用同一 SSE 通道 {@code streamId}，事件 payload 中携带 sessionId 供前端分派。
     *
     * @param streamId 前端生成的 SSE 通道 ID
     */
    @PostMapping("/batch-trigger-grading-async")
    public ResponseEntity<Map<String, Object>> batchTriggerGradingAsync(
            @RequestParam String streamId) {
        List<Long> pending = gradingService.listPendingGradingSessionIds().getData();
        if (pending.isEmpty()) {
            gradingProgressStore.emitBatchComplete(streamId, 0);
            return ResponseEntity.ok(Map.of("message", "无待评分考试", "count", 0));
        }
        final int total = pending.size();
        AtomicInteger remaining = new AtomicInteger(total);
        int accepted = 0;
        int rejected = 0;
        for (Long id : pending) {
            ExamGradingProgressCallback inner = gradingProgressStore.createCallback(streamId, id);
            ExamGradingProgressCallback wrapped =
                    wrapWithCounter(inner, remaining, streamId, total);
            try {
                gradeExamAsyncCmdExe.execute(id, wrapped);
                accepted++;
            } catch (RejectedExecutionException rex) {
                // 线程池饱和：本场次任务未启动。繁忙提示已由 support 经 wrapped.onError 上报，
                // 该 onError 已对 remaining 递减一次，此处绝不可再次递减，否则会破坏批量完成计数。
                rejected++;
                logger.warn("批量评分中某场次被限流（并发已达上限）[session={}]", id);
            }
        }
        if (accepted == 0) {
            // 全部被拒：无任何场次回调会收尾，SSE 会挂起，主动补发 BATCH_COMPLETE 关闭通道并返回 429。
            gradingProgressStore.emitBatchComplete(streamId, 0);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(
                            Map.of(
                                    "message", "系统繁忙，评分并发已达上限，请稍后重试",
                                    "count", total,
                                    "accepted", accepted,
                                    "rejected", rejected));
        }
        return ResponseEntity.ok(
                Map.of(
                        "message", "批量评分已启动",
                        "count", total,
                        "accepted", accepted,
                        "rejected", rejected,
                        "streamId", streamId));
    }

    /**
     * 评分进度 SSE 通道
     *
     * <p>事件类型：START / DONE / ERROR / COMPLETE / FATAL / BATCH_COMPLETE。 前端应先建立此 EventSource 再触发
     * async 端点，避免早期事件丢失。
     */
    @GetMapping(value = "/grading-stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter gradingStream(@PathVariable String streamId) {
        logger.debug("评分 SSE 建立 [streamId={}]", streamId);
        return gradingProgressStore.createEmitter(streamId);
    }

    /** 包装回调：整场 COMPLETE / FATAL 后递减剩余场次；归零时统一推送 BATCH_COMPLETE 并关闭 SSE。 */
    private ExamGradingProgressCallback wrapWithCounter(
            ExamGradingProgressCallback inner,
            AtomicInteger remaining,
            String streamId,
            int totalSessions) {
        return new ExamGradingProgressCallback() {
            @Override
            public void onQuestionStart(int questionIndex, String questionType, String aiInput) {
                inner.onQuestionStart(questionIndex, questionType, aiInput);
            }

            @Override
            public void onQuestionToken(int questionIndex, String kind, String delta) {
                inner.onQuestionToken(questionIndex, kind, delta);
            }

            @Override
            public void onQuestionDone(
                    int questionIndex,
                    String aiRawOutput,
                    int aiScore,
                    int maxScore,
                    String aiFeedback,
                    long elapsedMs) {
                inner.onQuestionDone(
                        questionIndex, aiRawOutput, aiScore, maxScore, aiFeedback, elapsedMs);
            }

            @Override
            public void onQuestionError(int questionIndex, String errorMessage) {
                inner.onQuestionError(questionIndex, errorMessage);
            }

            @Override
            public void onComplete(int totalQuestions, int totalAiScore) {
                inner.onComplete(totalQuestions, totalAiScore);
                if (remaining.decrementAndGet() == 0) {
                    gradingProgressStore.emitBatchComplete(streamId, totalSessions);
                }
            }

            @Override
            public void onError(String errorMessage) {
                inner.onError(errorMessage);
                if (remaining.decrementAndGet() == 0) {
                    gradingProgressStore.emitBatchComplete(streamId, totalSessions);
                }
            }
        };
    }

    /** 查询待复核的考试列表 */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> listPending(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ExamSessionDTO> sessions = gradingService.listPendingReview(limit, offset).getData();
        long total = gradingService.countPendingReview().getData();
        return ResponseEntity.ok(
                Map.of(
                        "records", sessions,
                        "total", total));
    }

    /**
     * 获取某场考试的答题详情（含 AI 评分）
     *
     * <p>返回结构包含 session 级别信息和 answers 列表。
     */
    @GetMapping("/{sessionId}/answers")
    public ResponseEntity<Map<String, Object>> getAnswers(@PathVariable Long sessionId) {
        ExamSessionDTO session = gradingService.getSessionById(sessionId).getData();
        List<ExamAnswerDTO> answers = gradingService.listAnswersWithGrading(sessionId).getData();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("topic", session.getTopic());
        result.put("studentId", session.getStudentId());
        result.put("difficulty", session.getDifficulty());
        result.put("status", session.getStatus());
        result.put("totalScore", session.getTotalScore());
        result.put("aiScore", session.getAiScore());
        result.put("finalScore", session.getFinalScore());
        result.put("durationMinutes", session.getDurationMinutes());
        result.put(
                "startTime",
                session.getStartTime() != null ? session.getStartTime().toString() : null);
        result.put(
                "submitTime",
                session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
        result.put("answers", answers);

        return ResponseEntity.ok(result);
    }

    /** 复核单题 */
    @PostMapping("/answer/{answerId}/review")
    public ResponseEntity<Map<String, String>> reviewAnswer(
            @PathVariable Long answerId, @RequestBody ReviewRequest request) {
        try {
            gradingService.reviewAnswer(
                    answerId,
                    request.getReviewScore(),
                    request.getReviewFeedback(),
                    request.getReviewer() != null ? request.getReviewer() : "admin");
            return ResponseEntity.ok(Map.of("message", "复核完成"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 发布成绩 */
    @PostMapping("/{sessionId}/publish")
    public ResponseEntity<Map<String, String>> publish(
            @PathVariable Long sessionId, @RequestParam(defaultValue = "admin") String reviewer) {
        try {
            gradingService.publishScore(sessionId, reviewer);
            return ResponseEntity.ok(Map.of("message", "成绩已发布"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 管理员设置 / 覆盖考试时长 */
    @PostMapping("/{sessionKey}/duration")
    public ResponseEntity<Map<String, String>> updateDuration(
            @PathVariable String sessionKey, @RequestBody Map<String, Integer> body) {
        try {
            Integer minutes = body.get("durationMinutes");
            examTakingService.updateDuration(sessionKey, minutes);
            return ResponseEntity.ok(Map.of("message", "考试时长已更新"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
