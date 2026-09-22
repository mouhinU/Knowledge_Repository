package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.application.converter.UserConverter;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修改用户命令执行器（app 层用例，事务边界）
 *
 * <p>username / departmentId / admin / status / password 为 null（或 password 为空串）时保持原值不变； password
 * 提供时 BCrypt 后重置，status 仅接受 ACTIVE / DISABLED。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class UserUpdateCmdExe {

    private final UserGateway userGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserUpdateCmdExe(UserGateway userGateway, BCryptPasswordEncoder passwordEncoder) {
        this.userGateway = userGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserVO execute(UserUpdateCmd cmd) {
        User user =
                userGateway
                        .findByUserKey(cmd.getUserKey())
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "User not found: " + cmd.getUserKey()));
        if (cmd.getUsername() != null) {
            user.setUsername(cmd.getUsername());
        }
        if (cmd.getDepartmentId() != null) {
            user.setDepartmentId(cmd.getDepartmentId());
        }
        if (cmd.getAdmin() != null) {
            user.setAdmin(cmd.getAdmin());
        }
        if (cmd.getStatus() != null) {
            if (!User.STATUS_ACTIVE.equalsIgnoreCase(cmd.getStatus())
                    && !User.STATUS_DISABLED.equalsIgnoreCase(cmd.getStatus())) {
                throw new IllegalArgumentException("Invalid status: " + cmd.getStatus());
            }
            user.setStatus(cmd.getStatus().toUpperCase());
        }
        if (cmd.getPassword() != null && !cmd.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(cmd.getPassword()));
        }
        userGateway.update(user);
        return UserConverter.toVO(user);
    }
}
