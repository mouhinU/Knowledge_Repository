package com.mouhin.knowledge.repository.application.executor.adminauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteCmd;
import com.mouhin.knowledge.repository.client.dto.SetPasswordByInviteVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * {@link SetPasswordByInviteCmdExe} 单元测试（P3-T3 邀请式设密）。
 *
 * <p>覆盖六类路径：
 *
 * <ol>
 *   <li>正常路径：raw code 有效 → 用户密码更新（BCrypt）+ mustChangePassword=false + 邀请 USED + 审计 SUCCESS；
 *   <li>邀请码未识别 → IllegalArgumentException + 审计 REJECTED；
 *   <li>邀请码已过期 / 已使用 / 已吊销 → IllegalArgumentException + 审计 REJECTED；
 *   <li>目标用户不存在（数据不一致）→ IllegalStateException；
 *   <li>入参校验：cmd=null / inviteCode 空 / 密码过短 / 密码过长 → IllegalArgumentException；
 *   <li>VO 回显 username / tenantKey / message，不含任何令牌。
 * </ol>
 *
 * <p>全部用 mock 网关 + 真实 {@link BCryptPasswordEncoder}（验 hash 可匹配原密码），不启 Spring。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("邀请式设密执行器 (SetPasswordByInviteCmdExe) P3-T3")
class SetPasswordByInviteCmdExeTest {

    private static final String RAW_CODE = "raw-invite-code-abc";
    private static final String NEW_PASSWORD = "Str0ng!Passw0rd";
    private static final long TENANT_ID = 42L;
    private static final long USER_ID = 500L;
    private static final long INVITE_ID = 77L;

    private UserInviteService userInviteService;
    private UserInviteGateway userInviteGateway;
    private UserGateway userGateway;
    private BCryptPasswordEncoder passwordEncoder;
    private PlatformAuditLog auditLog;
    private SetPasswordByInviteCmdExe exe;

    @BeforeEach
    void setUp() {
        userInviteService = mock(UserInviteService.class);
        userInviteGateway = mock(UserInviteGateway.class);
        userGateway = mock(UserGateway.class);
        passwordEncoder = new BCryptPasswordEncoder();
        auditLog = mock(PlatformAuditLog.class);
        exe =
                new SetPasswordByInviteCmdExe(
                        userInviteService,
                        userInviteGateway,
                        userGateway,
                        passwordEncoder,
                        auditLog);
    }

    private SetPasswordByInviteCmd cmd(String code, String pwd) {
        SetPasswordByInviteCmd c = new SetPasswordByInviteCmd();
        c.setInviteCode(code);
        c.setNewPassword(pwd);
        return c;
    }

    private UserInvite pendingInvite() {
        UserInvite inv = new UserInvite();
        inv.setInviteId(INVITE_ID);
        inv.setTenantId(TENANT_ID);
        inv.setUserId(USER_ID);
        inv.setInviteCodeHash(UserInviteService.sha256Hex(RAW_CODE));
        inv.setStatus(UserInvite.STATUS_PENDING);
        inv.setExpiresAt(LocalDateTime.now().plusHours(24));
        return inv;
    }

    private User targetUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setUserKey("user-key-acme-admin");
        u.setUsername("acme-admin");
        u.setTenantKey("acme");
        u.setAdmin(true);
        u.setStatus(User.STATUS_ACTIVE);
        u.setMustChangePassword(true);
        u.setPasswordHash(null);
        return u;
    }

    @Test
    @DisplayName("正常路径：密码 BCrypt 编码 + mustChangePassword=false + 邀请 USED + 审计 SUCCESS + VO 回显")
    void happyPath() {
        UserInvite invite = pendingInvite();
        User user = targetUser();
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(invite));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(user));

        SetPasswordByInviteVO vo = exe.execute(cmd(RAW_CODE, NEW_PASSWORD));

        assertThat(vo.isSuccess()).isTrue();
        assertThat(vo.getUsername()).isEqualTo("acme-admin");
        assertThat(vo.getTenantKey()).isEqualTo("acme");
        assertThat(vo.getMessage()).contains("密码已设置");

        // 用户密码已更新且可被 BCrypt 验证
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userGateway).update(userCap.capture());
        User updated = userCap.getValue();
        assertThat(updated.getPasswordHash()).isNotNull().isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(updated.getMustChangePassword()).isFalse();

        // 邀请已消费
        assertThat(invite.getStatus()).isEqualTo(UserInvite.STATUS_USED);
        assertThat(invite.getUsedAt()).isNotNull();
        verify(userInviteGateway).update(invite);

        // 审计 SUCCESS
        verify(auditLog)
                .logTenantAction(
                        eq("user-key-acme-admin"),
                        eq(SetPasswordByInviteCmdExe.ACTION),
                        eq("acme"),
                        eq("SUCCESS"),
                        anyString());
    }

    @Test
    @DisplayName("邀请码未识别 → IllegalArgumentException + 审计 REJECTED，不触用户/邀请更新")
    void unknownCodeRejected() {
        when(userInviteService.findByRawCode("wrong-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd("wrong-code", NEW_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("邀请码无效");

        verify(auditLog)
                .logTenantAction(
                        eq("anonymous"),
                        eq(SetPasswordByInviteCmdExe.ACTION),
                        eq("-"),
                        eq("REJECTED"),
                        anyString());
        verify(userGateway, never()).update(any());
        verify(userInviteGateway, never()).update(any());
    }

    @Test
    @DisplayName("邀请码已过期 → IllegalArgumentException + 审计 REJECTED(expired)")
    void expiredCodeRejected() {
        UserInvite expired = pendingInvite();
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> exe.execute(cmd(RAW_CODE, NEW_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过期");

        verify(auditLog)
                .logTenantAction(
                        eq("anonymous"),
                        eq(SetPasswordByInviteCmdExe.ACTION),
                        eq("-"),
                        eq("REJECTED"),
                        org.mockito.ArgumentMatchers.contains("expired"));
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("邀请码已使用 → IllegalArgumentException + 审计 REJECTED(not-pending)")
    void usedCodeRejected() {
        UserInvite used = pendingInvite();
        used.setStatus(UserInvite.STATUS_USED);
        used.setUsedAt(LocalDateTime.now().minusMinutes(5));
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> exe.execute(cmd(RAW_CODE, NEW_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已被使用");

        verify(auditLog)
                .logTenantAction(
                        eq("anonymous"),
                        eq(SetPasswordByInviteCmdExe.ACTION),
                        eq("-"),
                        eq("REJECTED"),
                        org.mockito.ArgumentMatchers.contains("not-pending"));
        verify(userGateway, never()).update(any());
    }

    @Test
    @DisplayName("目标用户不存在（数据不一致）→ IllegalStateException")
    void missingUserThrows() {
        UserInvite invite = pendingInvite();
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(invite));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd(RAW_CODE, NEW_PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Target user not found");

        verify(userInviteGateway, never()).update(any());
    }

    @Test
    @DisplayName("入参校验：cmd=null / inviteCode 空白 / 密码过短 / 密码过长 → IllegalArgumentException")
    void validationErrors() {
        assertThatThrownBy(() -> exe.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请求体不能为空");

        assertThatThrownBy(() -> exe.execute(cmd("  ", NEW_PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("邀请码不能为空");

        assertThatThrownBy(() -> exe.execute(cmd(RAW_CODE, "short")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少");

        String tooLong = "a".repeat(SetPasswordByInviteCmdExe.MAX_PASSWORD_LENGTH + 1);
        assertThatThrownBy(() -> exe.execute(cmd(RAW_CODE, tooLong)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");

        // 所有校验失败均不触任何下游
        verify(userInviteService, never()).findByRawCode(anyString());
        verify(userGateway, never()).update(any());
        verify(userInviteGateway, never()).update(any());
    }

    @Test
    @DisplayName("密码边界：恰好 MIN=8 / MAX=128 字符均被接受")
    void passwordLengthBoundaries() {
        UserInvite invite1 = pendingInvite();
        invite1.setInviteId(1L);
        User user1 = targetUser();
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(invite1));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(user1));

        String minPwd = "a".repeat(SetPasswordByInviteCmdExe.MIN_PASSWORD_LENGTH);
        assertThat(exe.execute(cmd(RAW_CODE, minPwd)).isSuccess()).isTrue();

        // 重置 mock 以跑第二次
        UserInvite invite2 = pendingInvite();
        invite2.setInviteId(2L);
        User user2 = targetUser();
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(invite2));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(user2));

        String maxPwd = "a".repeat(SetPasswordByInviteCmdExe.MAX_PASSWORD_LENGTH);
        assertThat(exe.execute(cmd(RAW_CODE, maxPwd)).isSuccess()).isTrue();

        verify(userGateway, times(2)).update(any());
    }
}
