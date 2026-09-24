package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.examgeneration.ListPublishedHistoryQryExe;
import com.mouhin.knowledge.repository.client.api.ExamTakingServiceI;
import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.client.dto.SaveAnswersRequest;
import com.mouhin.knowledge.repository.client.dto.StartExamRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 在线做题控制器
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/exam")
@Slf4j
public class ExamTakingController {

    private final ExamTakingServiceI examTakingService;
    private final ListPublishedHistoryQryExe listPublishedHistoryQryExe;

    public ExamTakingController(
            ExamTakingServiceI examTakingService,
            ListPublishedHistoryQryExe listPublishedHistoryQryExe) {
        this.examTakingService = examTakingService;
        this.listPublishedHistoryQryExe = listPublishedHistoryQryExe;
    }

    /**
     * 可开考的学生端试卷列表（仅返回已发布 PUBLISHED 的试卷）。
     *
     * <p>学生端「可用考试」入口专用，位于已放行的 {@code /api/exam} 命名空间下，与教师/管理端的 {@code /api/agent/exam/history}
     * 解耦——后者属受管理端令牌保护的出卷历史接口，不应由学生端直接调用。
     *
     * <p>系统级过滤：登录态下会剔除该考生已开考过的试卷（一人一卷一次），并排除已作废（VOIDED）卷。 未登录 / token 缺失时退化为「全部已发布试卷」列表（保持向后兼容）。
     *
     * @param limit 最大返回数量，默认 20
     * @param token 学生会话令牌（{@code X-Student-Token} 请求头），可选
     * @return 已发布且该考生未考过的试卷列表（按时间倒序）
     */
    @GetMapping("/available")
    public ResponseEntity<List<ExamHistoryDTO>> listAvailableExams(
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Student-Token", required = false) String token) {
        return ResponseEntity.ok(listPublishedHistoryQryExe.execute(limit, token));
    }

    /** 开始考试 */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startExam(@RequestBody StartExamRequest request) {
        try {
            ExamSessionDTO session;
            if (request.getHistorySessionId() != null && !request.getHistorySessionId().isBlank()) {
                session =
                        examTakingService
                                .startFromHistory(request.getToken(), request.getHistorySessionId())
                                .getData();
            } else if (request.getExamPaper() != null && !request.getExamPaper().isBlank()) {
                session =
                        examTakingService
                                .startWithPaper(
                                        request.getToken(),
                                        request.getExamPaper(),
                                        request.getAnswerKey(),
                                        request.getTopic(),
                                        request.getDifficulty())
                                .getData();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "请指定试卷来源"));
            }

            return ResponseEntity.ok(
                    Map.of(
                            "sessionKey", session.getSessionKey(),
                            "topic", session.getTopic(),
                            "questionsJson",
                                    session.getQuestionsJson() != null
                                            ? session.getQuestionsJson()
                                            : "[]",
                            "totalScore", session.getTotalScore(),
                            "durationMinutes",
                                    session.getDurationMinutes() != null
                                            ? session.getDurationMinutes()
                                            : 0,
                            "validation",
                                    examTakingService
                                            .validateReport(session.getQuestionsJson())
                                            .getData(),
                            "startTime", session.getStartTime().toString(),
                            "serverNow", LocalDateTime.now().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "开始考试失败"));
        }
    }

    /** 保存答题（断点续答） */
    @PostMapping("/{sessionKey}/save")
    public ResponseEntity<Map<String, String>> saveAnswers(
            @PathVariable String sessionKey, @RequestBody SaveAnswersRequest request) {
        try {
            examTakingService.saveAnswers(sessionKey, request.getToken(), request.getAnswers());
            return ResponseEntity.ok(Map.of("message", "答案已保存"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "保存答案失败"));
        }
    }

    /** 交卷 */
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
            return ResponseEntity.badRequest().body(Map.of("error", "交卷失败"));
        }
    }

    /** 获取考试详情 */
    @GetMapping("/{sessionKey}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String sessionKey,
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            ExamSessionDTO session =
                    examTakingService.getSession(sessionKey, headerToken).getData();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionKey", session.getSessionKey());
            result.put("topic", session.getTopic() != null ? session.getTopic() : "");
            result.put(
                    "difficulty", session.getDifficulty() != null ? session.getDifficulty() : "");
            result.put("status", session.getStatus());
            result.put("totalScore", session.getTotalScore() != null ? session.getTotalScore() : 0);
            result.put("aiScore", session.getAiScore() != null ? session.getAiScore() : 0);
            result.put("finalScore", session.getFinalScore() != null ? session.getFinalScore() : 0);
            result.put(
                    "questionsJson",
                    session.getQuestionsJson() != null ? session.getQuestionsJson() : "[]");
            result.put("examPlan", session.getExamPlan() != null ? session.getExamPlan() : "");
            result.put("examPaper", session.getExamPaper() != null ? session.getExamPaper() : "");
            result.put(
                    "durationMinutes",
                    session.getDurationMinutes() != null ? session.getDurationMinutes() : 0);
            result.put(
                    "startTime",
                    session.getStartTime() != null ? session.getStartTime().toString() : "");
            result.put(
                    "submitTime",
                    session.getSubmitTime() != null ? session.getSubmitTime().toString() : "");
            // 供前端校正本地时钟：客户端 Date.now() 与服务端时钟的漂移会使倒计时 / 自动交卷提前或延后触发。
            result.put("serverNow", LocalDateTime.now().toString());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "获取考试详情失败"));
        }
    }

    /** 获取答题记录（含评分） */
    @GetMapping("/{sessionKey}/answers")
    public ResponseEntity<List<ExamAnswerDTO>> getAnswers(
            @PathVariable String sessionKey,
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            List<ExamAnswerDTO> answers =
                    examTakingService.getAnswers(sessionKey, headerToken).getData();
            return ResponseEntity.ok(answers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** 查询我的考试历史 */
    @GetMapping("/my-sessions")
    public ResponseEntity<List<ExamSessionDTO>> mySessions(
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        try {
            List<ExamSessionDTO> sessions = examTakingService.listMySessions(headerToken).getData();
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
