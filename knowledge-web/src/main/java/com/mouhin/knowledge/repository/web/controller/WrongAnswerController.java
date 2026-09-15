package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.WrongAnswerApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.domain.repository.StudentRepository;
import com.mouhin.knowledge.repository.web.dto.WrongAnswerSummaryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本控制器（管理端）
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/admin/wrong-answers")
public class WrongAnswerController {

    private static final Logger logger = LoggerFactory.getLogger(WrongAnswerController.class);

    private final WrongAnswerApplicationService wrongAnswerService;
    private final StudentRepository studentRepository;

    public WrongAnswerController(WrongAnswerApplicationService wrongAnswerService,
                                 StudentRepository studentRepository) {
        this.wrongAnswerService = wrongAnswerService;
        this.studentRepository = studentRepository;
    }

    /**
     * 查询错题列表（支持按考生 / 主题 / 题型过滤）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listWrongAnswers(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String questionType) {
        List<Map<String, Object>> records = wrongAnswerService.listWrongAnswers(studentId, topic, questionType);
        return ResponseEntity.ok(Map.of(
                "records", records,
                "total", records.size()
        ));
    }

    /**
     * AI 错题总结
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, String>> generateSummary(
            @RequestBody WrongAnswerSummaryRequest request) {
        try {
            String summary = wrongAnswerService.generateAiSummary(
                    request.getStudentId(), request.getTopic(), request.getQuestionType());
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (Exception e) {
            logger.error("AI 错题总结失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 查询考生列表（供错题本筛选下拉框使用）
     */
    @GetMapping("/students")
    public ResponseEntity<List<Map<String, Object>>> listStudents() {
        List<Student> students = studentRepository.listAll();
        List<Map<String, Object>> result = students.stream()
                .map(s -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", s.getId());
                    map.put("username", s.getUsername());
                    map.put("displayName", s.getDisplayName());
                    map.put("studentNo", s.getStudentNo());
                    return map;
                })
                .toList();
        return ResponseEntity.ok(result);
    }
}
