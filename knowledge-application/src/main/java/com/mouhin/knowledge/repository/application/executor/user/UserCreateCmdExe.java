package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.application.converter.UserConverter;
import com.mouhin.knowledge.repository.client.dto.UserCreateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新增用户命令执行器（app 层用例，事务边界）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
@Slf4j
public class UserCreateCmdExe {

    private final UserGateway userGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserCreateCmdExe(UserGateway userGateway, BCryptPasswordEncoder passwordEncoder) {
        this.userGateway = userGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserVO execute(UserCreateCmd cmd) {
        userGateway
                .findByUsername(cmd.getUsername())
                .ifPresent(
                        u -> {
                            throw new IllegalArgumentException(
                                    "Username already exists: " + cmd.getUsername());
                        });

        User user = new User();
        user.setUserKey(UUID.randomUUID().toString());
        user.setUsername(cmd.getUsername());
        user.setDepartmentId(cmd.getDepartmentId());
        user.setAdmin(cmd.getAdmin() != null ? cmd.getAdmin() : false);
        user.setStatus(User.STATUS_ACTIVE);
        if (cmd.getPassword() != null && !cmd.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(cmd.getPassword()));
        }
        userGateway.save(user);

        log.info("User created: {} ({})", cmd.getUsername(), user.getUserKey());
        return UserConverter.toVO(user);
    }
}
