package com.mouhin.knowledge.repository.application.executor.adminauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.support.UserInviteService;
import com.mouhin.knowledge.repository.client.dto.InviteStatusVO;
import com.mouhin.knowledge.repository.domain.gateway.TenantGateway;
import com.mouhin.knowledge.repository.domain.gateway.UserGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.domain.model.entity.User;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link InviteStatusQryExe} 单元测试（P3-T7 邀请式设密<b>回访</b>）。
 *
 * <p>覆盖六类路径：
 *
 * <ol>
 *   <li>UNKNOWN：raw code 未命中 → 仅 status + 通用 hint，<b>不</b>触发 User / Tenant 网关查询（反枚举侧信道）；
 *   <li>PENDING 且未过期 → status=PENDING + expiresAt + tenantKeyHint / usernameHint 均脱敏；
 *   <li>USED → status=USED + 引导"请使用新密码登录"，{@code expiresAt} 保持 null；
 *   <li>REVOKED → status=REVOKED + 引导"已被吊销或重签"，{@code expiresAt} null；
 *   <li>PENDING 但已过期（{@code now >= expiresAt}） → status=EXPIRED，与 USED/REVOKED 返回体大小近似一致；
 *   <li>tenantKey 短标识（&lt; 6 字符）不脱敏直接返回原值；user 缺失回退 {@code "?"}。
 * </ol>
 *
 * <p>全部用 mock 网关 + 真实 {@link UserInvite} 实体，不启 Spring；用 {@link Mockito#never()} 断言 UNKNOWN 分支绝不触下游。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@DisplayName("邀请码状态查询执行器 (InviteStatusQryExe) P3-T7 回访")
class InviteStatusQryExeTest {

    private static final String RAW_CODE = "raw-invite-code-abc";
    private static final long TENANT_ID = 42L;
    private static final long USER_ID = 500L;
    private static final long INVITE_ID = 77L;
    private static final String TENANT_KEY = "acme-corp";
    private static final String USERNAME = "acme-admin";

    private UserInviteService userInviteService;
    private UserGateway userGateway;
    private TenantGateway tenantGateway;
    private InviteStatusQryExe exe;

    @BeforeEach
    void setUp() {
        userInviteService = Mockito.mock(UserInviteService.class);
        userGateway = Mockito.mock(UserGateway.class);
        tenantGateway = Mockito.mock(TenantGateway.class);
        exe = new InviteStatusQryExe(userInviteService, userGateway, tenantGateway);
    }

    private UserInvite invite(String status, LocalDateTime expiresAt) {
        UserInvite inv = new UserInvite();
        inv.setInviteId(INVITE_ID);
        inv.setTenantId(TENANT_ID);
        inv.setUserId(USER_ID);
        inv.setInviteCodeHash(UserInviteService.sha256Hex(RAW_CODE));
        inv.setStatus(status);
        inv.setExpiresAt(expiresAt);
        return inv;
    }

    private Tenant tenantWithKey(String key) {
        Tenant t = new Tenant();
        t.setTenantId(TENANT_ID);
        t.setTenantKey(key);
        return t;
    }

    private User userWithUsername(String username) {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(username);
        return u;
    }

    @Test
    @DisplayName("UNKNOWN：raw code 未命中 → 仅 status + 通用 hint，不触任何 User/Tenant 查询")
    void unknownSkipsLookups() {
        when(userInviteService.findByRawCode("bad-code")).thenReturn(Optional.empty());

        InviteStatusVO vo = exe.execute("bad-code");

        assertThat(vo.getStatus()).isEqualTo(InviteStatusVO.STATUS_UNKNOWN);
        assertThat(vo.getHint()).isNotBlank();
        assertThat(vo.getExpiresAt()).isNull();
        assertThat(vo.getTenantKeyHint()).isNull();
        assertThat(vo.getUsernameHint()).isNull();

        verify(userGateway, never()).findById(anyLong());
        verify(tenantGateway, never()).findById(any());
        verify(userInviteService).findByRawCode("bad-code");
    }

    @Test
    @DisplayName("UNKNOWN：raw code 为 null 或空白也走未知态，不抛异常")
    void nullOrBlankCodeYieldsUnknown() {
        when(userInviteService.findByRawCode(null)).thenReturn(Optional.empty());
        when(userInviteService.findByRawCode("   ")).thenReturn(Optional.empty());

        assertThat(exe.execute(null).getStatus()).isEqualTo(InviteStatusVO.STATUS_UNKNOWN);
        assertThat(exe.execute("   ").getStatus()).isEqualTo(InviteStatusVO.STATUS_UNKNOWN);
    }

    @Test
    @DisplayName("PENDING 未过期：status=PENDING + expiresAt 非空 + tenantKey/username 均脱敏")
    void pendingExposesMaskedHintsAndExpiresAt() {
        LocalDateTime expires = LocalDateTime.now().plusHours(24);
        when(userInviteService.findByRawCode(RAW_CODE))
                .thenReturn(Optional.of(invite(UserInvite.STATUS_PENDING, expires)));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey(TENANT_KEY)));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername(USERNAME)));

        InviteStatusVO vo = exe.execute(RAW_CODE);

        assertThat(vo.getStatus()).isEqualTo(InviteStatusVO.STATUS_PENDING);
        assertThat(vo.getExpiresAt()).isEqualTo(expires);
        assertThat(vo.getHint()).contains("请设置新密码");
        // tenantKey="acme-corp"（>=6 字符）→ "acm…"
        assertThat(vo.getTenantKeyHint()).isEqualTo("acm…");
        // username="acme-admin" → "a***"
        assertThat(vo.getUsernameHint()).isEqualTo("a***");
    }

    @Test
    @DisplayName("USED：status=USED + 引导文案，expiresAt 保持 null 防枚举")
    void usedBranch() {
        when(userInviteService.findByRawCode(RAW_CODE))
                .thenReturn(
                        Optional.of(
                                invite(UserInvite.STATUS_USED, LocalDateTime.now().plusHours(1))));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey(TENANT_KEY)));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername(USERNAME)));

        InviteStatusVO vo = exe.execute(RAW_CODE);

        assertThat(vo.getStatus()).isEqualTo(InviteStatusVO.STATUS_USED);
        assertThat(vo.getExpiresAt()).isNull();
        assertThat(vo.getHint()).contains("已完成设密");
    }

    @Test
    @DisplayName("REVOKED：status=REVOKED + 引导“重签”，expiresAt null")
    void revokedBranch() {
        when(userInviteService.findByRawCode(RAW_CODE))
                .thenReturn(
                        Optional.of(
                                invite(
                                        UserInvite.STATUS_REVOKED,
                                        LocalDateTime.now().plusHours(1))));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey(TENANT_KEY)));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername(USERNAME)));

        InviteStatusVO vo = exe.execute(RAW_CODE);

        assertThat(vo.getStatus()).isEqualTo(InviteStatusVO.STATUS_REVOKED);
        assertThat(vo.getExpiresAt()).isNull();
        assertThat(vo.getHint()).contains("已被吊销或重签");
    }

    @Test
    @DisplayName("惰性过期：status=PENDING 但 now>=expiresAt → 呈现 EXPIRED 且 expiresAt null")
    void expiredPendingSurfacesAsExpired() {
        LocalDateTime past = LocalDateTime.now().minusSeconds(1);
        when(userInviteService.findByRawCode(RAW_CODE))
                .thenReturn(Optional.of(invite(UserInvite.STATUS_PENDING, past)));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey(TENANT_KEY)));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername(USERNAME)));

        InviteStatusVO vo = exe.execute(RAW_CODE);

        assertThat(vo.getStatus()).isEqualTo(InviteStatusVO.STATUS_EXPIRED);
        assertThat(vo.getExpiresAt()).isNull();
        assertThat(vo.getHint()).contains("已过期");
    }

    @Test
    @DisplayName("短 tenantKey (<6 字符) 不脱敏直接返回原值；null / 空 tenantId 回退 '?'")
    void tenantKeyEdgeCases() {
        // 短 tenantKey：直接原值
        UserInvite inv = invite(UserInvite.STATUS_PENDING, LocalDateTime.now().plusHours(1));
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(inv));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey("acme")));
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername(USERNAME)));
        assertThat(exe.execute(RAW_CODE).getTenantKeyHint()).isEqualTo("acme");

        // tenant 查询 miss → "?"
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.empty());
        assertThat(exe.execute(RAW_CODE).getTenantKeyHint()).isEqualTo("?");
    }

    @Test
    @DisplayName("user 查询 miss / 空 username → “?”；tenantId null → “?”；userId null → “?”")
    void userAndNullIdFallbacks() {
        UserInvite inv = invite(UserInvite.STATUS_PENDING, LocalDateTime.now().plusHours(1));
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(inv));
        when(tenantGateway.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithKey(TENANT_KEY)));

        // user 查询 miss
        when(userGateway.findById(USER_ID)).thenReturn(Optional.empty());
        assertThat(exe.execute(RAW_CODE).getUsernameHint()).isEqualTo("?");

        // 空 username
        when(userGateway.findById(USER_ID)).thenReturn(Optional.of(userWithUsername("")));
        assertThat(exe.execute(RAW_CODE).getUsernameHint()).isEqualTo("?");

        // tenantId / userId null 走独立 invite
        UserInvite nullIds = new UserInvite();
        nullIds.setInviteId(INVITE_ID);
        nullIds.setStatus(UserInvite.STATUS_PENDING);
        nullIds.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(userInviteService.findByRawCode(RAW_CODE)).thenReturn(Optional.of(nullIds));

        InviteStatusVO vo = exe.execute(RAW_CODE);
        assertThat(vo.getTenantKeyHint()).isEqualTo("?");
        assertThat(vo.getUsernameHint()).isEqualTo("?");
    }
}
