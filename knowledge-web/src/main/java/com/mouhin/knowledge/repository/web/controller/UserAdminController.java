package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.UserServiceI;
import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器（adapter 层）
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/admin/user")
public class UserAdminController {

    private static final Logger logger = LoggerFactory.getLogger(UserAdminController.class);

    private final UserServiceI userService;

    public UserAdminController(UserServiceI userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserVO>> list() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @GetMapping("/{userKey}")
    public ResponseEntity<UserVO> get(@PathVariable String userKey) {
        return ResponseEntity.ok(userService.getUser(userKey));
    }

    @PostMapping
    public ResponseEntity<UserVO> create(@RequestBody Map<String, Object> body) {
        UserCreateCmd cmd = new UserCreateCmd();
        cmd.setUsername((String) body.get("username"));
        cmd.setDepartmentId((String) body.get("departmentId"));
        cmd.setAdmin(body.get("admin") != null && (Boolean) body.get("admin"));

        return ResponseEntity.ok(userService.createUser(cmd));
    }

    @PutMapping("/{userKey}")
    public ResponseEntity<UserVO> update(@PathVariable String userKey,
                                         @RequestBody Map<String, Object> body) {
        UserUpdateCmd cmd = new UserUpdateCmd();
        cmd.setUserKey(userKey);
        cmd.setUsername((String) body.get("username"));
        cmd.setDepartmentId((String) body.get("departmentId"));
        cmd.setAdmin(body.get("admin") != null ? (Boolean) body.get("admin") : null);

        return ResponseEntity.ok(userService.updateUser(cmd));
    }

    @DeleteMapping("/{userKey}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String userKey) {
        userService.deleteUser(userKey);
        return ResponseEntity.ok(Map.of("message", "User deleted: " + userKey));
    }
}
