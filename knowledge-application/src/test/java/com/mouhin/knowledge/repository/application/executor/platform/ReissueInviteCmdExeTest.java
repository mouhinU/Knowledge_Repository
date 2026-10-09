package com.mouhin.knowledge.repository.application.executor.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.application.provisioning.step.CreateFirstAdminStep;
import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.application.support.UserInviteService.InviteIssueResult;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteCmd;
import com.mouhin.knowledge.repository.client.dto.TenantReissueInviteVO;
import com.mouhin.knowledge.repository.domain.gateway.PlatformAuditLog;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ReissueInviteCmdExe} 单元测试（P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>覆盖五类路径：
 *
 * <ol>
 *   <li>正常重签：定位 (tenantKey, username) → 前置 PENDING 计数 + 转调 issue（内部吊销+落新） → VO 含 raw code / url /
 *       expiresAt / revokedPrevious；审计 SUCCESS；
 *   <li>TTL 覆写：{@code cmd.ttlHours>0} 时用 {@code Duration.ofHours}，否则回落 {@code DEFAULT_TTL=72h}；
 *   <li>租户不存在 → IllegalArgumentException + 审计 REJECTED(tenant not found)；
 *   <li>用户不存在或非本租户 → IllegalArgumentException + 审计 REJECTED(user not found)；
 *   <li>入参校验：cmd=null / tenantKey 空 / username 空 → IllegalArgumentException 且不触任何下游。
 * </ol>
 *
 * <p>全部用 mock 网关 + 真实 {@link UserInviteService.InviteIssueResult} record，不启 Spring；关键断言包括 raw code
 * 仅出现在 VO 内、审计 detail 不含 raw code 明文。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("平台端邀请重签执行器 (ReissueInviteCmdExe) P3-T7 回访")
class ReissueInviteCmdExeTest {

    private static final String TENANT_KEY = "acme-corp";
    private static final String USERNAME = "acme-admin";
    private static final long TENANT_ID = 42L;
    private static final long USER_ID = 500L;
    private static final long INVITE_ID = 77L;
    private static final String NEW_RAW_CODE = "new-raw-code-xyz";
    private static final String OPERATOR_ID = "op-007";

    private TenantGateway tenantGateway;
    private UserGateway userGateway;
    private UserInviteGateway userInviteGateway;
    private UserInviteService userInviteService;
    private PlatformAuditLog auditLog;
    private ReissueInviteCmdExe exe;

    @BeforeEach
    void setUp() {
        tenantGateway = mock(TenantGateway.class);
        userGateway = mock(UserGateway.class);
        userInviteGateway = mock(UserInviteGateway.class);
        userInviteService = mock(UserInviteService.class);
        auditLog = mock(PlatformAuditLog.class);
        exe =
                new ReissueInviteCmdExe(
                        tenantGateway, userGateway, userInviteGateway, userInviteService, auditLog);
    }

    private TenantReissueInviteCmd cmd(String tenantKey, String username, Integer ttlHours) {
        TenantReissueInviteCmd c = new TenantReissueInviteCmd();
        c.setTenantKey(tenantKey);
        c.setUsername(username);
        c.setTtlHours(ttlHours);
        c.setOperatorId(OPERATOR_ID);
        c.setOperatorName("Ops");
        return c;
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setTenantId(TENANT_ID);
        t.setTenantKey(TENANT_KEY);
        return t;
    }

    private User user() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setTenantKey(TENANT_KEY);
        return u;
    }

    private InviteIssueResult issuedResult(Duration ttl) {
        UserInvite inv = new UserInvite();
        inv.setInviteId(INVITE_ID);
        inv.setTenantId(TENANT_ID);
        inv.setUserId(USER_ID);
        inv.setStatus(UserInvite.STATUS_PENDING);
        inv.setExpiresAt(LocalDateTime.now().plus(ttl));
        return new InviteIssueResult(inv, NEW_RAW_CODE);
    }

    @Test
    @DisplayName("正常重签：默认 TTL=72h + 前置 PENDING 计数 + 转调 issue + 审计 SUCCESS + VO 完整")
    void happyPathDefaultTtl() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.of(tenant()));
        when(userGateway.findByUsernameAndTenant(USERNAME, TENANT_ID))
                .thenReturn(Optional.of(user()));
        when(userInviteGateway.findPendingByUser(TENANT_ID, USER_ID))
                .thenReturn(List.of(new UserInvite(), new UserInvite())); // 前置 2 条旧 PENDING
        when(userInviteService.issue(eq(TENANT_ID), eq(USER_ID), any(Duration.class)))
                .thenReturn(issuedResult(UserInviteService.DEFAULT_TTL));

        TenantReissueInviteVO vo = exe.execute(cmd(TENANT_KEY, USERNAME, null));

        assertThat(vo.getTenantKey()).isEqualTo(TENANT_KEY);
        assertThat(vo.getUsername()).isEqualTo(USERNAME);
        assertThat(vo.getCode()).isEqualTo(NEW_RAW_CODE);
        assertThat(vo.getUrl()).isEqualTo(CreateFirstAdminStep.INVITE_URL_PREFIX + NEW_RAW_CODE);
        assertThat(vo.getExpiresAt()).isNotNull();
        assertThat(vo.getRevokedPrevious()).isEqualTo(2);

        // TTL 兜底走 DEFAULT_TTL
        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(userInviteService).issue(eq(TENANT_ID), eq(USER_ID), ttlCap.capture());
        assertThat(ttlCap.getValue()).isEqualTo(UserInviteService.DEFAULT_TTL);

        // 审计 SUCCESS，detail 含 inviteId + revokedPrevious，不含 raw code 明文
        ArgumentCaptor<String> detailCap = ArgumentCaptor.forClass(String.class);
        verify(auditLog)
                .logTenantAction(
                        eq(OPERATOR_ID),
                        eq(ReissueInviteCmdExe.ACTION),
                        eq(TENANT_KEY),
                        eq("SUCCESS"),
                        detailCap.capture());
        String detail = detailCap.getValue();
        assertThat(detail).contains("inviteId=" + INVITE_ID).contains("revokedPrevious=2");
        assertThat(detail).doesNotContain(NEW_RAW_CODE);
    }

    @Test
    @DisplayName("TTL 覆写：cmd.ttlHours=24 → Duration.ofHours(24)")
    void ttlOverride() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.of(tenant()));
        when(userGateway.findByUsernameAndTenant(USERNAME, TENANT_ID))
                .thenReturn(Optional.of(user()));
        when(userInviteGateway.findPendingByUser(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(userInviteService.issue(eq(TENANT_ID), eq(USER_ID), any(Duration.class)))
                .thenReturn(issuedResult(Duration.ofHours(24)));

        TenantReissueInviteVO vo = exe.execute(cmd(TENANT_KEY, USERNAME, 24));

        assertThat(vo.getRevokedPrevious()).isZero();
        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(userInviteService).issue(eq(TENANT_ID), eq(USER_ID), ttlCap.capture());
        assertThat(ttlCap.getValue()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("ttlHours ≤0 视为未指定，回落到 DEFAULT_TTL")
    void ttlZeroFallsBackToDefault() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.of(tenant()));
        when(userGateway.findByUsernameAndTenant(USERNAME, TENANT_ID))
                .thenReturn(Optional.of(user()));
        when(userInviteGateway.findPendingByUser(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(userInviteService.issue(eq(TENANT_ID), eq(USER_ID), any(Duration.class)))
                .thenReturn(issuedResult(UserInviteService.DEFAULT_TTL));

        exe.execute(cmd(TENANT_KEY, USERNAME, 0));

        ArgumentCaptor<Duration> ttlCap = ArgumentCaptor.forClass(Duration.class);
        verify(userInviteService).issue(eq(TENANT_ID), eq(USER_ID), ttlCap.capture());
        assertThat(ttlCap.getValue()).isEqualTo(UserInviteService.DEFAULT_TTL);
    }

    @Test
    @DisplayName("tenantKey 归一化：大写/带空白输入按 TenantKey.normalize 转小写并 trim 后再定位")
    void tenantKeyNormalized() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.of(tenant()));
        when(userGateway.findByUsernameAndTenant(USERNAME, TENANT_ID))
                .thenReturn(Optional.of(user()));
        when(userInviteGateway.findPendingByUser(TENANT_ID, USER_ID)).thenReturn(List.of());
        when(userInviteService.issue(eq(TENANT_ID), eq(USER_ID), any(Duration.class)))
                .thenReturn(issuedResult(UserInviteService.DEFAULT_TTL));

        exe.execute(cmd("  " + TENANT_KEY.toUpperCase() + " ", USERNAME, null));

        verify(tenantGateway).findByTenantKey(TENANT_KEY);
    }

    @Test
    @DisplayName("租户不存在 → IllegalArgumentException + 审计 REJECTED(tenant not found)")
    void tenantNotFound() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd(TENANT_KEY, USERNAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant not found");

        verify(auditLog)
                .logTenantAction(
                        eq(OPERATOR_ID),
                        eq(ReissueInviteCmdExe.ACTION),
                        eq(TENANT_KEY),
                        eq("REJECTED"),
                        contains("tenant not found"));
        verify(userInviteService, never()).issue(anyLong(), anyLong(), any(Duration.class));
    }

    @Test
    @DisplayName("用户不存在或非本租户 → IllegalArgumentException + 审计 REJECTED(user not found)")
    void userNotFound() {
        when(tenantGateway.findByTenantKey(TENANT_KEY)).thenReturn(Optional.of(tenant()));
        when(userGateway.findByUsernameAndTenant(USERNAME, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exe.execute(cmd(TENANT_KEY, USERNAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(auditLog)
                .logTenantAction(
                        eq(OPERATOR_ID),
                        eq(ReissueInviteCmdExe.ACTION),
                        eq(TENANT_KEY),
                        eq("REJECTED"),
                        contains("user not found"));
        verify(userInviteService, never()).issue(anyLong(), anyLong(), any(Duration.class));
    }

    @Test
    @DisplayName("入参校验：cmd=null / tenantKey 空白 / username 空白 → IllegalArgumentException 不触任何下游")
    void validation() {
        assertThatThrownBy(() -> exe.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请求体不能为空");

        assertThatThrownBy(() -> exe.execute(cmd("  ", USERNAME, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantKey 不能为空");

        assertThatThrownBy(() -> exe.execute(cmd(TENANT_KEY, " ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username 不能为空");

        verify(tenantGateway, never()).findByTenantKey(anyString());
        verify(userGateway, never()).findByUsernameAndTenant(anyString(), anyLong());
        verify(userInviteService, never()).issue(anyLong(), anyLong(), any(Duration.class));
        verify(auditLog, never()).logTenantAction(any(), any(), any(), any(), any());
    }
}
