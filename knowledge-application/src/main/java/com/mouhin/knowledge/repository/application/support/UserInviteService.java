package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.gateway.UserInviteGateway;
import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户邀请服务（P3-T3，app/support 层）。
 *
 * <p>集中承载"一次性设密邀请码"的生成、哈希与查询逻辑，避免各调用点散落 SHA-256 / SecureRandom 细节：
 *
 * <ul>
 *   <li>{@link #issue(Long, Long, Duration)}：为新用户签发一条 PENDING 邀请，返回 <b>raw code</b>（仅此一次可见） 与已落库的
 *       {@link UserInvite}（含 hash 与 expiresAt）。签发前会先吊销同一 (tenantId,userId) 下的旧 PENDING 邀请，
 *       保证任一时刻最多一条有效邀请，简化"重放 provision"路径。
 *   <li>{@link #findByRawCode(String)}：把 raw code 哈希后查库；命中即返回，未命中或 hash 不匹配均 {@link
 *       Optional#empty()}。 <b>不</b>在此判定过期 / 状态——由消费方（{@code SetPasswordByInviteCmdExe}）用 {@link
 *       UserInvite#isUsable} 决定， 以便区分"码错"与"码过期"两种业务错误。
 *   <li>{@link #sha256Hex(String)}：静态工具，供消费方独立计算 hash（若需绕过 service 直接查 gateway）。
 * </ul>
 *
 * <p><b>安全纪律</b>（红线 #4）：raw code <b>不</b>落库、<b>不</b>入日志；本类日志只记 {@code inviteId} 与 {@code codeHash}
 * 前 8 位。响应体一次性回显 raw code 由 caller（{@code CreateFirstAdminStep} → {@code
 * ProvisioningOutcome.InviteInfo} → {@code TenantProvisionVO}）负责，且仅在 provision 请求的 HTTP
 * 响应体出现一次，符合"邀请码 ≠ 长期令牌"的行业惯例（GitHub/GitLab 同模式）。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Component
public class UserInviteService {

    private static final Logger log = LoggerFactory.getLogger(UserInviteService.class);

    /** raw code 熵源字节数；32 字节经 Base64URL 无填充编码后为 43 字符，抗暴力枚举足够。 */
    private static final int RAW_CODE_BYTES = 32;

    /** 默认邀请 TTL：72 小时。可被 {@link #issue} 参数覆盖。 */
    public static final Duration DEFAULT_TTL = Duration.ofHours(72);

    /** 日志脱敏：hash 前缀可见长度，用于问题排查又不泄漏完整 hash。 */
    private static final int HASH_LOG_PREFIX = 8;

    private final UserInviteGateway userInviteGateway;
    private final SecureRandom secureRandom;

    /** 生产注入路径：由 Spring 选定此构造器（存在两个构造器时须显式标注，否则回退到不存在的无参构造器）。 */
    @Autowired
    public UserInviteService(UserInviteGateway userInviteGateway) {
        this(userInviteGateway, new SecureRandom());
    }

    /** 测试可注入自定义 {@link SecureRandom}（如固定种子的伪随机源）以断言 raw code 生成路径。 */
    public UserInviteService(UserInviteGateway userInviteGateway, SecureRandom secureRandom) {
        this.userInviteGateway = Objects.requireNonNull(userInviteGateway);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    /**
     * 为 (tenantId, userId) 签发一条 PENDING 邀请。
     *
     * <p>流程：吊销同用户旧 PENDING 邀请 → 生成 raw code → SHA-256 → 落库 PENDING 行 → 返回 (rawCode, entity)。
     *
     * @param tenantId 目标租户 id
     * @param userId 目标用户 id
     * @param ttl 有效期；{@code null} 或 ≤ 0 时用 {@link #DEFAULT_TTL}
     * @return 签发结果，包含仅本次可见的 raw code 与已落库的邀请实体
     */
    public InviteIssueResult issue(Long tenantId, Long userId, Duration ttl) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(userId, "userId required");
        Duration effectiveTtl =
                (ttl == null || ttl.isZero() || ttl.isNegative()) ? DEFAULT_TTL : ttl;
        LocalDateTime now = LocalDateTime.now();

        // 1) 吊销同用户旧 PENDING 邀请（幂等：无旧邀请即为空操作）
        List<UserInvite> pending = userInviteGateway.findPendingByUser(tenantId, userId);
        for (UserInvite old : pending) {
            old.markRevoked(now);
            userInviteGateway.update(old);
        }

        // 2) 生成 raw code + hash
        byte[] bytes = new byte[RAW_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String codeHash = sha256Hex(rawCode);

        // 3) 落库
        UserInvite invite = new UserInvite();
        invite.setTenantId(tenantId);
        invite.setUserId(userId);
        invite.setInviteCodeHash(codeHash);
        invite.setStatus(UserInvite.STATUS_PENDING);
        invite.setExpiresAt(now.plus(effectiveTtl));
        userInviteGateway.insert(invite);

        log.info(
                "[UserInvite] issued inviteId={} tenantId={} userId={} codeHashPrefix={} expiresAt={} (revoked {} prior pending)",
                invite.getInviteId(),
                tenantId,
                userId,
                codeHash.substring(0, Math.min(HASH_LOG_PREFIX, codeHash.length())),
                invite.getExpiresAt(),
                pending.size());
        return new InviteIssueResult(invite, rawCode);
    }

    /**
     * 按 raw code 查找邀请（内部先 SHA-256 再按 hash 精确匹配）。
     *
     * @param rawCode 原始邀请码
     * @return 命中的邀请实体（可能是任意状态）；{@code rawCode} 为空或未命中时 {@link Optional#empty()}
     */
    public Optional<UserInvite> findByRawCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        return userInviteGateway.findByCodeHash(sha256Hex(rawCode));
    }

    /**
     * SHA-256 hex 编码（小写，64 字符）。
     *
     * @param raw 原始字符串（UTF-8）
     * @return 64 字符 hex
     */
    public static String sha256Hex(String raw) {
        Objects.requireNonNull(raw, "raw required");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，任何 JRE 都必须提供；命中此路径意味着运行时被人为破坏。
            throw new IllegalStateException("SHA-256 not available in JRE", e);
        }
    }

    /**
     * 邀请签发结果。
     *
     * @param invite 已落库的邀请实体（含 hash / expiresAt，不含 raw code）
     * @param rawCode 原始邀请码，<b>仅此一次可见</b>，caller 应立即返回给平台运维并放弃本地引用
     */
    public record InviteIssueResult(UserInvite invite, String rawCode) {}
}
