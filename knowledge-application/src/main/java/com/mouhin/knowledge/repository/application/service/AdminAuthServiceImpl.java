package com.mouhin.knowledge.repository.application.service;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.application.executor.adminauth.AdminChangePasswordCmdExe;
import com.mouhin.knowledge.repository.application.executor.adminauth.AdminLoginCmdExe;
import com.mouhin.knowledge.repository.application.executor.adminauth.AdminLogoutCmdExe;
import com.mouhin.knowledge.repository.application.executor.adminauth.AdminValidateTokenQryExe;
import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.client.dto.AdminChangePasswordCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginCmd;
import com.mouhin.knowledge.repository.client.dto.AdminLoginResultDTO;
import com.mouhin.knowledge.repository.client.dto.AdminPrincipalDTO;
import org.springframework.stereotype.Service;

/**
 * 管理端认证应用服务实现（app 层，仅分发到 Executor）。
 *
 * @author Knowledge-Repository
 * @date 2026-09-19
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthServiceI {

    private final AdminLoginCmdExe adminLoginCmdExe;
    private final AdminLogoutCmdExe adminLogoutCmdExe;
    private final AdminValidateTokenQryExe adminValidateTokenQryExe;
    private final AdminChangePasswordCmdExe adminChangePasswordCmdExe;

    public AdminAuthServiceImpl(
            AdminLoginCmdExe adminLoginCmdExe,
            AdminLogoutCmdExe adminLogoutCmdExe,
            AdminValidateTokenQryExe adminValidateTokenQryExe,
            AdminChangePasswordCmdExe adminChangePasswordCmdExe) {
        this.adminLoginCmdExe = adminLoginCmdExe;
        this.adminLogoutCmdExe = adminLogoutCmdExe;
        this.adminValidateTokenQryExe = adminValidateTokenQryExe;
        this.adminChangePasswordCmdExe = adminChangePasswordCmdExe;
    }

    @Override
    public SingleResponse<AdminLoginResultDTO> login(AdminLoginCmd cmd) {
        return SingleResponse.of(adminLoginCmdExe.execute(cmd));
    }

    @Override
    public Response logout(String token) {
        adminLogoutCmdExe.execute(token);
        return Response.buildSuccess();
    }

    @Override
    public SingleResponse<AdminPrincipalDTO> validateToken(String token) {
        return SingleResponse.of(adminValidateTokenQryExe.execute(token));
    }

    @Override
    public Response changePassword(AdminChangePasswordCmd cmd) {
        adminChangePasswordCmdExe.execute(cmd);
        return Response.buildSuccess();
    }
}
