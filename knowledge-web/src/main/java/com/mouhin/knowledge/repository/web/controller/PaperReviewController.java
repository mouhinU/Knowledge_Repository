package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ExamQuestionSplitSupport;
import com.mouhin.knowledge.repository.application.executor.examreview.ApprovePaperCmdExe;
import com.mouhin.knowledge.repository.application.executor.examreview.GetPaperQuestionsQryExe;
import com.mouhin.knowledge.repository.application.executor.examreview.ListReviewPendingQryExe;
import com.mouhin.knowledge.repository.application.executor.examreview.PaperQuestionsView;
import com.mouhin.knowledge.repository.application.executor.examreview.ResplitPaperCmdExe;
import com.mouhin.knowledge.repository.application.executor.examreview.UpdatePaperQuestionCmdExe;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 试卷校对控制器（管理端，阶段 1-D 校对关口）
 * <p>
 * 提供待校对试卷列表、逐题结构化校对视图、就地编辑回写 {@code kb_exam_question}、
 * 校对通过发布（契约校验不过不可发布）、以及按原文重新切分回灌等接口。
 * 与面向考生答题的 {@code /api/admin/exam-review}（成绩复核）区分，此处针对「试卷本身」的发布前把关。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@RestController
@RequestMapping("/api/admin/paper-review")
public class PaperReviewController {

    private static final Logger logger = LoggerFactory.getLogger(PaperReviewController.class);

    private final ListReviewPendingQryExe listReviewPendingQryExe;
    private final GetPaperQuestionsQryExe getPaperQuestionsQryExe;
    private final UpdatePaperQuestionCmdExe updatePaperQuestionCmdExe;
    private final ApprovePaperCmdExe approvePaperCmdExe;
    private final ResplitPaperCmdExe resplitPaperCmdExe;

    public PaperReviewController(ListReviewPendingQryExe listReviewPendingQryExe,
                                 GetPaperQuestionsQryExe getPaperQuestionsQryExe,
                                 UpdatePaperQuestionCmdExe updatePaperQuestionCmdExe,
                                 ApprovePaperCmdExe approvePaperCmdExe,
                                 ResplitPaperCmdExe resplitPaperCmdExe) {
        this.listReviewPendingQryExe = listReviewPendingQryExe;
        this.getPaperQuestionsQryExe = getPaperQuestionsQryExe;
        this.updatePaperQuestionCmdExe = updatePaperQuestionCmdExe;
        this.approvePaperCmdExe = approvePaperCmdExe;
        this.resplitPaperCmdExe = resplitPaperCmdExe;
    }

    /**
     * 待校对试卷列表（REVIEWABLE / VALIDATION_FAILED）
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> listPending(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ExamHistoryDTO> history = listReviewPendingQryExe.execute(limit, offset);
        List<Map<String, Object>> records = new ArrayList<>();
        for (ExamHistoryDTO h : history) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", h.getSessionId());
            row.put("topic", h.getTopic());
            row.put("difficulty", h.getDifficulty());
            row.put("category", h.getCategory());
            row.put("status", h.getStatus());
            row.put("qualityScore", h.getQualityScore());
            row.put("durationMinutes", h.getDurationMinutes());
            row.put("createTime", h.getCreateTime() != null ? h.getCreateTime().toString() : null);
            records.add(row);
        }
        long total = listReviewPendingQryExe.count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        return ResponseEntity.ok(result);
    }

    /**
     * 某份试卷的结构化逐题校对视图（含实时契约校验结果与发布门槛）
     */
    @GetMapping("/{sessionId}/questions")
    public ResponseEntity<Map<String, Object>> getQuestions(@PathVariable String sessionId) {
        PaperQuestionsView view = getPaperQuestionsQryExe.execute(sessionId);
        ExamHistory history = view.history();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", history.getSessionId());
        result.put("topic", history.getTopic());
        result.put("difficulty", history.getDifficulty());
        result.put("status", history.getStatus());
        result.put("qualityScore", history.getQualityScore());
        result.put("durationMinutes", history.getDurationMinutes());
        result.put("examPlan", history.getExamPlan());
        result.put("reviewedBy", history.getReviewedBy());
        result.put("reviewedTime", history.getReviewedTime() != null ? history.getReviewedTime().toString() : null);
        result.put("reviewRequired", view.reviewRequired());
        result.put("pass", view.validation().pass());
        result.put("issues", view.validation().issues());
        result.put("questions", toQuestionMaps(view.questions()));
        return ResponseEntity.ok(result);
    }

    /**
     * 就地编辑单题（标准答案 / 解析 / 分值），回写后返回最新校验结果
     */
    @PutMapping("/{sessionId}/question/{questionNumber}")
    public ResponseEntity<Map<String, Object>> updateQuestion(
            @PathVariable String sessionId,
            @PathVariable Integer questionNumber,
            @RequestBody Map<String, Object> body) {
        try {
            String correctAnswer = str(body.get("correctAnswer"));
            String analysis = str(body.get("analysis"));
            Integer maxScore = intOrNull(body.get("maxScore"));
            ExamContractValidator.Result result =
                    updatePaperQuestionCmdExe.execute(sessionId, questionNumber, correctAnswer, analysis, maxScore);
            return ResponseEntity.ok(validationMap(result));
        } catch (Exception e) {
            logger.warn("校对就地编辑失败 [session={}, number={}]: {}", sessionId, questionNumber, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * 校对通过并发布（契约校验不过则拒绝，不可绕过）
     */
    @PostMapping("/{sessionId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "admin") String reviewer) {
        try {
            ExamContractValidator.Result result = approvePaperCmdExe.execute(sessionId, reviewer);
            if (!result.pass()) {
                Map<String, Object> body = validationMap(result);
                body.put("error", "出卷契约校验未通过，无法发布，请先修正下列问题");
                return ResponseEntity.badRequest().body(body);
            }
            return ResponseEntity.ok(Map.of("message", "试卷已校对通过并发布", "published", true));
        } catch (Exception e) {
            logger.warn("校对发布失败 [session={}]: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /**
     * 按试卷原文重新切分回灌（修正原文 / 老数据首次结构化）
     */
    @PostMapping("/{sessionId}/resplit")
    public ResponseEntity<Map<String, Object>> resplit(@PathVariable String sessionId) {
        try {
            ExamQuestionSplitSupport.SplitOutcome outcome = resplitPaperCmdExe.execute(sessionId);
            Map<String, Object> body = validationMap(outcome.validation());
            body.put("count", outcome.count());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.warn("重新切分失败 [session={}]: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    // ==================== 内部装配 ====================

    private List<Map<String, Object>> toQuestionMaps(List<ExamQuestion> questions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ExamQuestion q : questions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("questionNumber", q.getQuestionNumber());
            m.put("sectionLabel", q.getSectionLabel());
            m.put("questionType", q.getQuestionType());
            m.put("stem", q.getStem());
            m.put("optionsJson", q.getOptionsJson());
            m.put("blankCount", q.getBlankCount());
            m.put("maxScore", q.getMaxScore());
            m.put("correctAnswer", q.getCorrectAnswer());
            m.put("analysis", q.getAnalysis());
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> validationMap(ExamContractValidator.Result result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pass", result.pass());
        map.put("issues", result.issues());
        return map;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
