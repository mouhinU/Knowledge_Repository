package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.service.UserManagementApplicationService;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/user")
public class UserAdminController {

    private static final Logger logger = LoggerFactory.getLogger(UserAdminController.class);

    private final UserManagementApplicationService userService;

    public UserAdminController(UserManagementApplicationService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<User> users = userService.listAll();
        return ResponseEntity.ok(users.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{userKey}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String userKey) {
        User user = userService.getByKey(userKey);
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String departmentId = (String) body.get("departmentId");
        Boolean admin = body.get("admin") != null && (Boolean) body.get("admin");

        User user = userService.create(username, departmentId, admin);
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{userKey}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String userKey,
                                                      @RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String departmentId = (String) body.get("departmentId");
        Boolean admin = body.get("admin") != null ? (Boolean) body.get("admin") : null;

        User user = userService.update(userKey, username, departmentId, admin);
        return ResponseEntity.ok(toResponse(user));
    }

    @DeleteMapping("/{userKey}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String userKey) {
        userService.delete(userKey);
        return ResponseEntity.ok(Map.of("message", "User deleted: " + userKey));
    }

    private Map<String, Object> toResponse(User user) {
        return Map.of(
                "userKey", user.getUserKey(),
                "username", user.getUsername(),
                "departmentId", user.getDepartmentId() != null ? user.getDepartmentId() : "",
                "admin", user.getAdmin() != null ? user.getAdmin() : false,
                "createdTime", user.getCreatedTime() != null ? user.getCreatedTime().toString() : ""
        );
    }
}
