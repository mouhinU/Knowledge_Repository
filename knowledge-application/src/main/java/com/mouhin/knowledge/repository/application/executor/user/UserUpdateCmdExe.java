package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.application.converter.UserConverter;
import com.mouhin.knowledge.repository.client.dto.UserUpdateCmd;
import com.mouhin.knowledge.repository.client.dto.UserVO;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 修改用户命令执行器（app 层用例，事务边界）
 *
 * <p>username / departmentId / admin 为 null 时保持原值不变（沿用既有语义）。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UserUpdateCmdExe {

    private final UserGateway userGateway;

    public UserUpdateCmdExe(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Transactional
    public UserVO execute(UserUpdateCmd cmd) {
        User user = userGateway.findByUserKey(cmd.getUserKey())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.getUserKey()));
        if (cmd.getUsername() != null) {
            user.setUsername(cmd.getUsername());
        }
        if (cmd.getDepartmentId() != null) {
            user.setDepartmentId(cmd.getDepartmentId());
        }
        if (cmd.getAdmin() != null) {
            user.setAdmin(cmd.getAdmin());
        }
        userGateway.update(user);
        return UserConverter.toVO(user);
    }
}
