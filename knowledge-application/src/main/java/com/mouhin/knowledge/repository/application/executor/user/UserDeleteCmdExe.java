package com.mouhin.knowledge.repository.application.executor.user;

import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 删除用户命令执行器（app 层用例，事务边界）
 *
 * <p>注意：本实现严格保持原 {@code UserManagementApplicationService.delete} 的既有行为
 * （校验用户存在后当前并不执行实际删除），迁移不改变运行语义；如需真正删除应另行评估。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class UserDeleteCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(UserDeleteCmdExe.class);

    private final UserGateway userGateway;

    public UserDeleteCmdExe(UserGateway userGateway) {
        this.userGateway = userGateway;
    }

    @Transactional
    public void execute(String userKey) {
        User user = userGateway.findByUserKey(userKey)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userKey));
        userGateway.findByUserKey(user.getUserKey());
        logger.info("User deleted: {}", userKey);
    }
}
