package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.examgeneration.AgentDecoratorRegistrar;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardPhase;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardProgressEvent;
import com.mouhin.knowledge.repository.domain.model.valueobject.BlackboardState;
import com.mouhin.knowledge.repository.domain.service.BlackboardAgent;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 错题分析流式控制器
 *
 * <p>提供基于 SSE 的错题模式分析流式端点，前端通过 progress/{sessionId} 接收实时进度， 再 POST 触发分析。
 *
 * @author mouhinU
 * @date 2026-09-28
 */
@RestController
@RequestMapping("/api/agent/wrong-answer")
@Slf4j
public class WrongAnswerAnalysisController {

    private final BlackboardAgent wrongAnswerAnalyzerAgent;
    private final BlackboardProgressStore progressStore;
    private final ExecutorService analysisExecutor = Executors.newCachedThreadPool();

    public WrongAnswerAnalysisController(
            @Qualifier("wrongAnswerAnalyzerAgent") BlackboardAgent wrongAnswerAnalyzerAgent,
            BlackboardProgressStore progressStore,
            AgentDecoratorRegistrar registrar) {
        this.wrongAnswerAnalyzerAgent = registrar.wrap(wrongAnswerAnalyzerAgent);
        this.progressStore = progressStore;
    }

    /** SSE 进度流（前端先建立连接，再 POST 触发分析） */
    @GetMapping(value = "/progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getProgress(@PathVariable String sessionId) {
        return progressStore.createEmitter(sessionId);
    }

    /**
     * 异步启动错题模式分析（流式）
     *
     * <p>请求体：{ "sessionId": "...", "examPaper": "...", "answerKey": "...", "studentAnswers": "...",
     * "topic": "..." }
     */
    @PostMapping("/analyze-stream")
    public ResponseEntity<?> analyzeStream(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        String examPaper = request.get("examPaper");
        String answerKey = request.get("answerKey");
        String topic = request.getOrDefault("topic", "错题分析");

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId 不能为空"));
        }
        if (examPaper == null || examPaper.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "试卷内容不能为空"));
        }

        log.info("[WrongAnswerAnalysis] 启动错题分析 [session={}]", sessionId);

        try {
            analysisExecutor.submit(
                    () -> {
                        BlackboardState blackboard = new BlackboardState(sessionId, topic);
                        blackboard.setExamPaper(examPaper);
                        blackboard.setAnswerKey(answerKey);

                        blackboard.advanceTo(BlackboardPhase.WRONG_ANSWER_ANALYZING);
                        progressStore.pushEvent(
                                sessionId,
                                BlackboardProgressEvent.phaseChanged(
                                        BlackboardPhase.WRONG_ANSWER_ANALYZING, "正在分析错题模式..."));

                        try {
                            wrongAnswerAnalyzerAgent.execute(
                                    blackboard, event -> progressStore.pushEvent(sessionId, event));

                            // 推送完成事件
                            progressStore.pushEvent(
                                    sessionId,
                                    new BlackboardProgressEvent.Builder()
                                            .type("COMPLETED")
                                            .phase(BlackboardPhase.COMPLETED)
                                            .output(blackboard.getWrongAnswerAnalysis())
                                            .build());
                        } catch (Exception e) {
                            log.error("[WrongAnswerAnalysis] 分析失败 [session={}]", sessionId, e);
                            progressStore.pushEvent(
                                    sessionId, BlackboardProgressEvent.error(e.getMessage()));
                        }
                    });
        } catch (Exception e) {
            log.error("[WrongAnswerAnalysis] 提交任务失败 [session={}]", sessionId, e);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "系统繁忙，请稍后重试"));
        }

        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }
}
