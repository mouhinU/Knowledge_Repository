package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.ExamGradingApplicationService;
import com.mouhin.knowledge.repository.application.service.ExamTakingApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.web.dto.ReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    private final ExamGradingApplicationService gradingService;
    private final ExamTakingApplicationService examTakingService;

    public ExamReviewController(ExamGradingApplicationService gradingService,
                                ExamTakingApplicationService examTakingService) {
        this.gradingService = gradingService;
        this.examTakingService = examTakingService;
    }

    /**
     * 查询待 AI 评分的考试列表（SUBMITTED 状态）
     */
    @GetMapping("/pending-grading")
    public ResponseEntity<Map<String, Object>> listPendingGrading(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ExamSession> sessions = gradingService.listPendingGradingSessions(limit, offset);
        long total = gradingService.countPendingGrading();
        return ResponseEntity.ok(Map.of(
                "records", sessions,
                "total", total
        ));
    }

    /**
     * 手动触发单场考试的 AI 评分
     */
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
     * 批量触发所有待评分考试的 AI 评分
     */
    @PostMapping("/batch-trigger-grading")
    public ResponseEntity<Map<String, Object>> batchTriggerGrading() {
        try {
            int count = gradingService.batchTriggerGrading();
            return ResponseEntity.ok(Map.of(
                    "message", "批量评分完成",
                    "count", count
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询待复核的考试列表
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> listPending(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ExamSession> sessions = gradingService.listPendingReview(limit, offset);
        long total = gradingService.countPendingReview();
        return ResponseEntity.ok(Map.of(
                "records", sessions,
                "total", total
        ));
    }

    /**
     * 获取某场考试的答题详情（含 AI 评分）
     * <p>
     * 返回结构包含 session 级别信息和 answers 列表。
     * </p>
     */
    @GetMapping("/{sessionId}/answers")
    public ResponseEntity<Map<String, Object>> getAnswers(@PathVariable Long sessionId) {
        ExamSession session = gradingService.getSessionById(sessionId);
        List<ExamAnswer> answers = gradingService.listAnswersWithGrading(sessionId);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("topic", session.getTopic());
        result.put("studentId", session.getStudentId());
        result.put("difficulty", session.getDifficulty());
        result.put("status", session.getStatus());
        result.put("totalScore", session.getTotalScore());
        result.put("aiScore", session.getAiScore());
        result.put("finalScore", session.getFinalScore());
        result.put("durationMinutes", session.getDurationMinutes());
        result.put("startTime", session.getStartTime() != null ? session.getStartTime().toString() : null);
        result.put("submitTime", session.getSubmitTime() != null ? session.getSubmitTime().toString() : null);
        result.put("answers", answers);

        return ResponseEntity.ok(result);
    }

    /**
     * 复核单题
     */
    @PostMapping("/answer/{answerId}/review")
    public ResponseEntity<Map<String, String>> reviewAnswer(
            @PathVariable Long answerId,
            @RequestBody ReviewRequest request) {
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

    /**
     * 发布成绩
     */
    @PostMapping("/{sessionId}/publish")
    public ResponseEntity<Map<String, String>> publish(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "admin") String reviewer) {
        try {
            gradingService.publishScore(sessionId, reviewer);
            return ResponseEntity.ok(Map.of("message", "成绩已发布"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 管理员设置 / 覆盖考试时长
     */
    @PostMapping("/{sessionKey}/duration")
    public ResponseEntity<Map<String, String>> updateDuration(
            @PathVariable String sessionKey,
            @RequestBody Map<String, Integer> body) {
        try {
            Integer minutes = body.get("durationMinutes");
            examTakingService.updateDuration(sessionKey, minutes);
            return ResponseEntity.ok(Map.of("message", "考试时长已更新"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
