package com.mouhin.knowledge.repository.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mouhin.knowledge.repository.application.support.UserInviteService.InviteIssueResult;
import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link UserInviteService} 单元测试（P3-T3 邀请式引导）。
 *
 * <p>锁定核心契约：
 *
 * <ul>
 *   <li>{@code issue} 生成 raw code（43 字符 Base64URL 无填充）+ SHA-256 hash（64 字符 hex）并落库 PENDING；
 *   <li>{@code issue} 先吊销同 (tenantId,userId) 下旧 PENDING 邀请，保证任一时刻最多一条有效；
 *   <li>{@code issue} 的 {@code ttl} 为 null/零/负时回落 {@link UserInviteService#DEFAULT_TTL}；
 *   <li>{@code findByRawCode} 把 raw code 哈希后按 hash 精确匹配；空/空白直接回 empty；
 *   <li>{@code sha256Hex} 对同一输入幂等、对不同输入产出不同 hash、长度恒 64；
 *   <li>raw code 与 hash 关系：{@code sha256Hex(rawCode) == invite.inviteCodeHash}。
 * </ul>
 *
 * <p>用 mock {@link UserInviteGateway} + 固定种子 {@link SecureRandom}（通过构造器注入）保证 raw code 可预测。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@DisplayName("用户邀请服务 (UserInviteService) P3-T3")
class UserInviteServiceTest {

    private UserInviteGateway gateway;
    private UserInviteService service;

    /** 固定种子 SecureRandom，使 raw code 在测试内可预测（不跨 JVM 断言具体值，只断言长度/哈希关系）。 */
    private SecureRandom deterministicRandom;

    @BeforeEach
    void setUp() {
        gateway = mock(UserInviteGateway.class);
        deterministicRandom = new SecureRandom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        service = new UserInviteService(gateway, deterministicRandom);
        // 默认：无旧 PENDING 邀请
        when(gateway.findPendingByUser(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("issue 生成 43 字符 Base64URL 无填充 raw code 与 64 字符 hex hash，并落库 PENDING")
    void issueGeneratesRawCodeAndHash() {
        InviteIssueResult result = service.issue(1L, 100L, Duration.ofHours(72));

        assertThat(result.rawCode()).hasSize(43);
        assertThat(result.rawCode()).matches("[A-Za-z0-9_-]+"); // Base64URL 无填充字符集
        assertThat(result.invite().getInviteCodeHash()).hasSize(64);
        assertThat(result.invite().getInviteCodeHash()).matches("[0-9a-f]{64}");
        assertThat(result.invite().getStatus()).isEqualTo(UserInvite.STATUS_PENDING);
        assertThat(result.invite().getTenantId()).isEqualTo(1L);
        assertThat(result.invite().getUserId()).isEqualTo(100L);
        assertThat(result.invite().getExpiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<UserInvite> cap = ArgumentCaptor.forClass(UserInvite.class);
        verify(gateway).insert(cap.capture());
        assertThat(cap.getValue().getInviteCodeHash())
                .isEqualTo(result.invite().getInviteCodeHash());
    }

    @Test
    @DisplayName("issue 先吊销同用户旧 PENDING 邀请（保证任一时刻最多一条有效）")
    void issueRevokesPriorPending() {
        UserInvite old1 = pendingInvite(10L, 1L, 100L);
        UserInvite old2 = pendingInvite(11L, 1L, 100L);
        when(gateway.findPendingByUser(eq(1L), eq(100L))).thenReturn(List.of(old1, old2));

        service.issue(1L, 100L, Duration.ofHours(24));

        assertThat(old1.getStatus()).isEqualTo(UserInvite.STATUS_REVOKED);
        assertThat(old2.getStatus()).isEqualTo(UserInvite.STATUS_REVOKED);
        verify(gateway, times(2)).update(any(UserInvite.class));
        verify(gateway).insert(any(UserInvite.class));
    }

    @Test
    @DisplayName("issue ttl 为 null/零/负 → 回落 DEFAULT_TTL=72h")
    void issueFallsBackToDefaultTtl() {
        LocalDateTime before = LocalDateTime.now();

        InviteIssueResult r1 = service.issue(1L, 1L, null);
        InviteIssueResult r2 = service.issue(1L, 2L, Duration.ZERO);
        InviteIssueResult r3 = service.issue(1L, 3L, Duration.ofHours(-1));

        for (InviteIssueResult r : List.of(r1, r2, r3)) {
            assertThat(r.invite().getExpiresAt())
                    .isAfterOrEqualTo(before.plus(UserInviteService.DEFAULT_TTL).minusSeconds(5))
                    .isBeforeOrEqualTo(
                            LocalDateTime.now().plus(UserInviteService.DEFAULT_TTL).plusSeconds(5));
        }
    }

    @Test
    @DisplayName("issue 参数 null → NullPointerException")
    void issueRejectsNullArgs() {
        assertThatThrownBy(() -> service.issue(null, 1L, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.issue(1L, null, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("findByRawCode 命中 → 返回对应邀请；未命中/空/空白 → empty")
    void findByRawCodeHashesAndQueries() {
        String raw = "some-raw-code-abc";
        String hash = UserInviteService.sha256Hex(raw);
        UserInvite stored = pendingInvite(1L, 1L, 100L);
        stored.setInviteCodeHash(hash);
        when(gateway.findByCodeHash(hash)).thenReturn(Optional.of(stored));

        assertThat(service.findByRawCode(raw)).contains(stored);
        assertThat(service.findByRawCode("wrong-code")).isEmpty();
        assertThat(service.findByRawCode(null)).isEmpty();
        assertThat(service.findByRawCode("   ")).isEmpty();

        // 非空白的错误码仍会哈希后查库一次（命中 empty）；null/空白在 isBlank 处短路，绝不触库
        verify(gateway, times(1)).findByCodeHash(UserInviteService.sha256Hex("wrong-code"));
        verify(gateway, never()).findByCodeHash(UserInviteService.sha256Hex("   "));
    }

    @Test
    @DisplayName("sha256Hex 幂等、长度 64、不同输入产出不同 hash")
    void sha256HexIsDeterministic() {
        String h1 = UserInviteService.sha256Hex("abc");
        String h2 = UserInviteService.sha256Hex("abc");
        String h3 = UserInviteService.sha256Hex("abd");

        assertThat(h1).isEqualTo(h2).hasSize(64);
        assertThat(h1).isNotEqualTo(h3);
        // 已知向量：SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(h1)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("issue 落库的 hash 与 raw code 满足 sha256Hex(rawCode) == hash（端到端一致性）")
    void issuedHashMatchesRawCode() {
        InviteIssueResult result = service.issue(1L, 100L, Duration.ofHours(1));

        assertThat(UserInviteService.sha256Hex(result.rawCode()))
                .isEqualTo(result.invite().getInviteCodeHash());
    }

    private UserInvite pendingInvite(Long inviteId, Long tenantId, Long userId) {
        UserInvite inv = new UserInvite();
        inv.setInviteId(inviteId);
        inv.setTenantId(tenantId);
        inv.setUserId(userId);
        inv.setInviteCodeHash("old-hash-" + inviteId);
        inv.setStatus(UserInvite.STATUS_PENDING);
        inv.setExpiresAt(LocalDateTime.now().plusHours(24));
        return inv;
    }
}
