package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.StudentServiceI;
import com.mouhin.knowledge.repository.client.dto.StudentLoginCmd;
import com.mouhin.knowledge.repository.client.dto.StudentRegisterCmd;
import com.mouhin.knowledge.repository.client.dto.StudentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 考生认证控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@RestController
@RequestMapping("/api/student/auth")
public class StudentAuthController {

    private static final Logger logger = LoggerFactory.getLogger(StudentAuthController.class);

    private final StudentServiceI studentService;

    public StudentAuthController(StudentServiceI studentService) {
        this.studentService = studentService;
    }

    /**
     * 考生注册
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody StudentRegisterCmd cmd) {
        try {
            StudentVO student = studentService.register(cmd).getData();
            return ResponseEntity.ok(Map.of(
                    "message", "注册成功",
                    "studentId", student.getStudentId(),
                    "username", student.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 考生登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody StudentLoginCmd cmd) {
        try {
            String token = studentService.login(cmd).getData();
            return ResponseEntity.ok(Map.of(
                    "message", "登录成功",
                    "token", token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken,
            @RequestBody(required = false) Map<String, String> body) {
        String token = headerToken;
        if (token == null && body != null) {
            token = body.get("token");
        }
        if (token != null) {
            studentService.logout(token);
        }
        return ResponseEntity.ok(Map.of("message", "已退出"));
    }

    /**
     * 验证令牌（获取当前考生信息）
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "X-Student-Token", required = false) String headerToken) {
        if (headerToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        StudentVO student = studentService.validateToken(headerToken).getData();
        if (student == null) {
            return ResponseEntity.status(401).body(Map.of("error", "登录已过期"));
        }
        return ResponseEntity.ok(Map.<String, Object>of(
                "studentId", student.getStudentId(),
                "username", student.getUsername(),
                "displayName", student.getDisplayName(),
                "studentNo", student.getStudentNo()
        ));
    }
}
