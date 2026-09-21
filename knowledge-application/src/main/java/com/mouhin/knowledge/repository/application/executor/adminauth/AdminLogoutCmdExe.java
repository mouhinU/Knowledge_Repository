package com.mouhin.knowledge.repository.application.executor.adminauth;

import com.mouhin.knowledge.repository.domain.gateway.AdminJwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理端退出登录命令执行器（app 层用例）。
 *
 * <p>JWT 无状态且不含服务端会话存储，退出仅需前端丢弃本地令牌；服务端这里做合法性宽松校验与审计日志， 不维护黑名单（令牌自然过期即失效）。此设计对当前单管理员后台规模足够。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Component
@Slf4j
public class AdminLogoutCmdExe {

    private final AdminJwtService adminJwtService;

    public AdminLogoutCmdExe(AdminJwtService adminJwtService) {
        this.adminJwtService = adminJwtService;
    }

    public void execute(String token) {
        adminJwtService
                .verify(token)
                .ifPresent(
                        payload ->
                                log.info(
                                        "管理端退出登录: username='{}', userKey={}",
                                        payload.username(),
                                        payload.userKey()));
    }
}
