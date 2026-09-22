package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.WrongAnswerServiceI;
import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerSummaryRequest;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 错题本控制器（管理端）
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/admin/wrong-answers")
@Slf4j
public class WrongAnswerController {

    private final WrongAnswerServiceI wrongAnswerService;

    public WrongAnswerController(WrongAnswerServiceI wrongAnswerService) {
        this.wrongAnswerService = wrongAnswerService;
    }

    /** 查询错题列表（支持按考生 / 主题 / 题型过滤 + 分页） */
    @GetMapping
    public ResponseEntity<WrongAnswerPageVO> listWrongAnswers(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                wrongAnswerService.pageWrongAnswers(studentId, topic, questionType, page, size));
    }

    /** AI 错题总结 */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, String>> generateSummary(
            @RequestBody WrongAnswerSummaryRequest request) {
        try {
            String summary =
                    wrongAnswerService.generateAiSummary(
                            request.getStudentId(), request.getTopic(), request.getQuestionType());
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            log.error("AI 错题总结失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 查询考生列表（供错题本筛选下拉框使用） */
    @GetMapping("/students")
    public ResponseEntity<List<StudentOptionVO>> listStudents() {
        return ResponseEntity.ok(wrongAnswerService.listStudentOptions());
    }
}
