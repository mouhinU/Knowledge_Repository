package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.StudentServiceI;
import com.mouhin.knowledge.repository.client.api.WrongAnswerServiceI;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 错题本控制器（考生端）
 *
 * <p>考生仅可查看自己的错题记录，通过 X-Student-Token 解析当前考生身份。
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/student/wrong-answers")
@Slf4j
public class StudentWrongAnswerController {

    private final WrongAnswerServiceI wrongAnswerService;
    private final StudentServiceI studentService;

    public StudentWrongAnswerController(
            WrongAnswerServiceI wrongAnswerService, StudentServiceI studentService) {
        this.wrongAnswerService = wrongAnswerService;
        this.studentService = studentService;
    }

    /** 查询当前考生的错题列表（支持按主题 / 题型过滤 + 分页） */
    @GetMapping
    public ResponseEntity<Object> listMyWrongAnswers(
            @RequestHeader(value = "X-Student-Token", required = false) String token,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String questionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        StudentVO student = studentService.validateToken(token).getData();
        if (student == null) {
            return ResponseEntity.status(401).body(Map.of("error", "登录已过期"));
        }
        WrongAnswerPageVO result =
                wrongAnswerService.pageWrongAnswers(
                        student.getStudentId(), topic, questionType, page, size);
        return ResponseEntity.ok(result);
    }
}
