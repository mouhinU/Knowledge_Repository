package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.client.dto.AdminChangePasswordCmd;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端自助改密命令执行器（app 层用例，事务边界）。
 *
 * <p>校验旧密码后以 BCrypt 落库新密码。userKey 由适配层从已认证令牌回填，不信任请求体，
 * 故只能修改自己的密码。新密码非空校验，避免误清空。</p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
public class AdminChangePasswordCmdExe {

    private static final Logger logger = LoggerFactory.getLogger(AdminChangePasswordCmdExe.class);

    private final UserGateway userGateway;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminChangePasswordCmdExe(UserGateway userGateway, BCryptPasswordEncoder passwordEncoder) {
        this.userGateway = userGateway;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void execute(AdminChangePasswordCmd cmd) {
        if (cmd.getUserKey() == null || cmd.getUserKey().isBlank()) {
            throw new IllegalArgumentException("未认证的改密请求");
        }
        if (cmd.getNewPassword() == null || cmd.getNewPassword().isBlank()) {
            throw new IllegalArgumentException("新密码不能为空");
        }
        User user = userGateway.findByUserKey(cmd.getUserKey())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        boolean oldMatches = user.getPasswordHash() != null
                && passwordEncoder.matches(cmd.getOldPassword(), user.getPasswordHash());
        if (!oldMatches) {
            throw new IllegalArgumentException("原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(cmd.getNewPassword()));
        userGateway.update(user);
        logger.info("管理端改密成功: userKey={}", user.getUserKey());
    }
}
