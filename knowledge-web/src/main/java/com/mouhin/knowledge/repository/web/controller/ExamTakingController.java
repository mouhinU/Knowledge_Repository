package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.ExamTakingServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.client.dto.SaveAnswersRequest;
import com.mouhin.knowledge.repository.client.dto.StartExamRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线做题控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/exam")
public class ExamTakingController {

    private static final Logger logger = LoggerFactory.getLogger(ExamTakingController.class);

    private final ExamTakingServiceI examTakingService;

    public ExamTakingController(ExamTakingServiceI examTakingService) {
        this.examTakingService = examTakingService;
    }

    /**
     * 开始考试
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startExam(@RequestBody StartExamRequest request) {
        try {
            ExamSessionDTO session;
            if (request.getHistorySessionId() != null && !request.getHistorySessionId().isBlank()) {
                session = examTakingService.startFromHistory(request.getToken(), request.getHistorySessionId());
            } else if (request.getExamPaper() != null && !request.getExamPaper().isBlank()) {
                session = examTakingService.startWithPaper(
                        request.getToken(),
                        request.getExamPaper(),
                        request.getAnswerKey(),
                        request.getTopic(),
                        request.getDifficulty());
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "请指定试卷来源"));
            }

            return ResponseEntity.ok(Map.of(
                    "sessionKey", session.getSessionKey(),
                    "topic", session.getTopic(),
                    "questionsJson", session.getQuestionsJson() != null ? session.getQuestionsJson() : "[]",
                    "totalScore", session.getTotalScore(),
                    "durationMinutes", session.getDurationMinutes() != null ? session.getDurationMinutes() : 0,
                    "validation", examTakingService.validateReport(session.getQuestionsJson()),
                    "startTime", session.getStartTime().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 保存答题（断点续答）
     */
    @PostMapping("/{sessionKey}/save")
    public ResponseEntity<Map<String, String>> saveAnswers(
            @PathVariable String sessionKey,
            @RequestBody SaveAnswersRequest request) {
        try {
            examTakingService.saveAnswers(sessionKey, request.getToken(), request.getAnswers());
            return ResponseEntity.ok(Map.of("message", "答案已保存"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 交卷
     */
    @PostMapping("/{sessionKey}/submit")
    public ResponseEntity<Map<String, Object>> submitExam(
            @PathVariable String sessionKey,
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken,
            @RequestBody(required = false) Map<String, String> body) {
        String token = headerToken;
        if (token == null && body != null) {
            token = body.get("token");
        }
        try {
            examTakingService.submitExam(sessionKey, token);
            return ResponseEntity.ok(Map.of("message", "交卷成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取考试详情
     */
    @GetMapping("/{sessionKey}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String sessionKey,
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            ExamSessionDTO session = examTakingService.getSession(sessionKey, headerToken);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionKey", session.getSessionKey());
            result.put("topic", session.getTopic() != null ? session.getTopic() : "");
            result.put("difficulty", session.getDifficulty() != null ? session.getDifficulty() : "");
            result.put("status", session.getStatus());
            result.put("totalScore", session.getTotalScore() != null ? session.getTotalScore() : 0);
            result.put("aiScore", session.getAiScore() != null ? session.getAiScore() : 0);
            result.put("finalScore", session.getFinalScore() != null ? session.getFinalScore() : 0);
            result.put("questionsJson", session.getQuestionsJson() != null ? session.getQuestionsJson() : "[]");
            result.put("examPlan", session.getExamPlan() != null ? session.getExamPlan() : "");
            result.put("examPaper", session.getExamPaper() != null ? session.getExamPaper() : "");
            result.put("durationMinutes", session.getDurationMinutes() != null ? session.getDurationMinutes() : 0);
            result.put("startTime", session.getStartTime() != null ? session.getStartTime().toString() : "");
            result.put("submitTime", session.getSubmitTime() != null ? session.getSubmitTime().toString() : "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取答题记录（含评分）
     */
    @GetMapping("/{sessionKey}/answers")
    public ResponseEntity<List<ExamAnswerDTO>> getAnswers(
            @PathVariable String sessionKey,
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            List<ExamAnswerDTO> answers = examTakingService.getAnswers(sessionKey, headerToken);
            return ResponseEntity.ok(answers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 查询我的考试历史
     */
    @GetMapping("/my-sessions")
    public ResponseEntity<List<ExamSessionDTO>> mySessions(
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            List<ExamSessionDTO> sessions = examTakingService.listMySessions(headerToken);
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
