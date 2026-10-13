package com.mouhin.knowledge.repository.application.service;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.dto.SingleResponse;
import com.mouhin.knowledge.repository.application.executor.adminauth.*;
import com.mouhin.knowledge.repository.client.api.AdminAuthServiceI;
import com.mouhin.knowledge.repository.client.dto.*;
import org.springframework.stereotype.Service;

/**
 * 管理端认证应用服务实现（app 层，仅分发到 Executor）。
 *
 * @author mouhinU
 * @date 2026-09-19
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthServiceI {

    private final AdminLoginCmdExe adminLoginCmdExe;
    private final AdminLogoutCmdExe adminLogoutCmdExe;
    private final AdminValidateTokenQryExe adminValidateTokenQryExe;
    private final AdminChangePasswordCmdExe adminChangePasswordCmdExe;
    private final SetPasswordByInviteCmdExe setPasswordByInviteCmdExe;
    private final InviteStatusQryExe inviteStatusQryExe;

    public AdminAuthServiceImpl(
            AdminLoginCmdExe adminLoginCmdExe,
            AdminLogoutCmdExe adminLogoutCmdExe,
            AdminValidateTokenQryExe adminValidateTokenQryExe,
            AdminChangePasswordCmdExe adminChangePasswordCmdExe,
            SetPasswordByInviteCmdExe setPasswordByInviteCmdExe,
            InviteStatusQryExe inviteStatusQryExe) {
        this.adminLoginCmdExe = adminLoginCmdExe;
        this.adminLogoutCmdExe = adminLogoutCmdExe;
        this.adminValidateTokenQryExe = adminValidateTokenQryExe;
        this.adminChangePasswordCmdExe = adminChangePasswordCmdExe;
        this.setPasswordByInviteCmdExe = setPasswordByInviteCmdExe;
        this.inviteStatusQryExe = inviteStatusQryExe;
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

    @Override
    public SingleResponse<SetPasswordByInviteVO> setPasswordByInvite(SetPasswordByInviteCmd cmd) {
        return SingleResponse.of(setPasswordByInviteCmdExe.execute(cmd));
    }

    @Override
    public SingleResponse<InviteStatusVO> queryInviteStatus(String rawCode) {
        return SingleResponse.of(inviteStatusQryExe.execute(rawCode));
    }
}
