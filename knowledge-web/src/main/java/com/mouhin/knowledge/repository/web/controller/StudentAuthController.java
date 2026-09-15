package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.StudentAuthApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.web.dto.StudentAuthRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    private final StudentAuthApplicationService authService;

    public StudentAuthController(StudentAuthApplicationService authService) {
        this.authService = authService;
    }

    /**
     * 考生注册
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody StudentAuthRequest request) {
        try {
            Student student = authService.register(
                    request.getUsername(),
                    request.getPassword(),
                    request.getDisplayName(),
                    request.getStudentNo());
            return ResponseEntity.ok(Map.of(
                    "message", "注册成功",
                    "studentId", student.getId(),
                    "username", student.getUsername()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 考生登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody StudentAuthRequest request) {
        try {
            String token = authService.login(request.getUsername(), request.getPassword());
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
            authService.logout(token);
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
        return authService.validateToken(headerToken)
                .map(student -> ResponseEntity.ok(Map.<String, Object>of(
                        "studentId", student.getId(),
                        "username", student.getUsername(),
                        "displayName", student.getDisplayName() != null
                                ? student.getDisplayName() : student.getUsername(),
                        "studentNo", student.getStudentNo() != null ? student.getStudentNo() : ""
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "登录已过期")));
    }
}
