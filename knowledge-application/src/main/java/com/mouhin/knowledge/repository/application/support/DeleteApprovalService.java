package com.mouhin.knowledge.repository.application.support;

import com.mouhin.knowledge.repository.domain.gateway.TenantDeleteApprovalGateway;
import com.mouhin.knowledge.repository.domain.model.entity.TenantDeleteApproval;
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
 * 租户删除审批令牌服务（P3-T5，app/support 层，S-4=方案 A 双人复核载体）。
 *
 * <p>集中承载"一次性删除审批令牌"的生成、哈希与查询，镜像 {@link UserInviteService} 的一次性码模式：
 *
 * <ul>
 *   <li>{@link #issue(Long, String, Duration)}：为某租户签发一条 PENDING 审批，返回 <b>raw token</b>（仅此一次可见）
 *       与已落库的 {@link TenantDeleteApproval}（含 hash 与 expiresAt）。签发前先吊销同租户旧 PENDING 审批，
 *       保证任一时刻最多一条有效令牌，简化重签路径。
 *   <li>{@link #findByRawToken(String)}：把 raw token 哈希后查库；命中即返回（可能是任意状态）， 过期 / 状态判定交由消费方（{@code
 *       ConfirmTenantDeletionCmdExe}）用 {@link TenantDeleteApproval#isUsable} 决策。
 * </ul>
 *
 * <p><b>安全纪律</b>（红线 #4）：raw token <b>不</b>落库、<b>不</b>入日志；本类日志只记 {@code approvalId} 与 {@code
 * tokenHash} 前 8 位。SHA-256 复用 {@link UserInviteService#sha256Hex(String)}，避免跨文件重复实现（Sonar S8786）。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Component
public class DeleteApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DeleteApprovalService.class);

    /** raw token 熵源字节数；32 字节经 Base64URL 无填充编码后为 43 字符，抗暴力枚举足够。 */
    private static final int RAW_TOKEN_BYTES = 32;

    /** 默认审批令牌 TTL：24 小时（删除是高敏操作，有效期短于邀请码）。 */
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /** 日志脱敏：hash 前缀可见长度。 */
    private static final int HASH_LOG_PREFIX = 8;

    private final TenantDeleteApprovalGateway approvalGateway;
    private final SecureRandom secureRandom;

    /** 生产注入路径：由 Spring 选定此构造器（存在两个构造器时须显式标注，否则回退到不存在的无参构造器）。 */
    @Autowired
    public DeleteApprovalService(TenantDeleteApprovalGateway approvalGateway) {
        this(approvalGateway, new SecureRandom());
    }

    /** 测试可注入自定义 {@link SecureRandom}（如固定种子）以断言 raw token 生成路径。 */
    public DeleteApprovalService(
            TenantDeleteApprovalGateway approvalGateway, SecureRandom secureRandom) {
        this.approvalGateway = Objects.requireNonNull(approvalGateway);
        this.secureRandom = Objects.requireNonNull(secureRandom);
    }

    /**
     * 为某租户签发一条 PENDING 删除审批令牌。
     *
     * <p>流程：吊销同租户旧 PENDING 审批 → 生成 raw token → SHA-256 → 落库 PENDING 行 → 返回 (entity, rawToken)。
     *
     * @param tenantId 目标租户 id
     * @param approverOperatorId 签发者（项目负责人）operator id
     * @param ttl 有效期；{@code null} 或 ≤0 时用 {@link #DEFAULT_TTL}
     * @return 签发结果，包含仅本次可见的 raw token 与已落库的审批实体
     */
    public ApprovalIssueResult issue(Long tenantId, String approverOperatorId, Duration ttl) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(approverOperatorId, "approverOperatorId required");
        Duration effectiveTtl =
                (ttl == null || ttl.isZero() || ttl.isNegative()) ? DEFAULT_TTL : ttl;
        LocalDateTime now = LocalDateTime.now();

        // 1) 吊销同租户旧 PENDING 审批（幂等：无旧审批即为空操作）
        List<TenantDeleteApproval> pending = approvalGateway.findPendingByTenant(tenantId);
        for (TenantDeleteApproval old : pending) {
            old.markRevoked(now);
            approvalGateway.update(old);
        }

        // 2) 生成 raw token + hash
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = UserInviteService.sha256Hex(rawToken);

        // 3) 落库
        TenantDeleteApproval approval = new TenantDeleteApproval();
        approval.setTenantId(tenantId);
        approval.setApproverOperatorId(approverOperatorId);
        approval.setTokenHash(tokenHash);
        approval.setStatus(TenantDeleteApproval.STATUS_PENDING);
        approval.setExpiresAt(now.plus(effectiveTtl));
        approvalGateway.insert(approval);

        log.info(
                "[DeleteApproval] issued approvalId={} tenantId={} approver={} tokenHashPrefix={} expiresAt={} (revoked {} prior pending)",
                approval.getApprovalId(),
                tenantId,
                approverOperatorId,
                tokenHash.substring(0, Math.min(HASH_LOG_PREFIX, tokenHash.length())),
                approval.getExpiresAt(),
                pending.size());
        return new ApprovalIssueResult(approval, rawToken);
    }

    /**
     * 按 raw token 查找审批（内部先 SHA-256 再按 hash 精确匹配）。
     *
     * @param rawToken 原始审批令牌
     * @return 命中的审批实体（可能是任意状态）；{@code rawToken} 为空或未命中时 {@link Optional#empty()}
     */
    public Optional<TenantDeleteApproval> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return approvalGateway.findByTokenHash(UserInviteService.sha256Hex(rawToken));
    }

    /**
     * 消费审批令牌：置 USED + 回填 {@code usedAt}/{@code usedByOperatorId} 并落库（双人复核第二步的最终确认点）。
     *
     * <p>调用方（{@code ConfirmTenantDeletionCmdExe}）须已完成"令牌有效 + 属于本租户 + 确认者 ≠ 签发者"校验。
     *
     * @param approval 待消费的审批实体（{@code approvalId} 必填）
     * @param usedAt 消费时间
     * @param usedByOperatorId 确认者（第二操作者）operator id
     */
    public void consume(
            TenantDeleteApproval approval, LocalDateTime usedAt, String usedByOperatorId) {
        Objects.requireNonNull(approval, "approval required");
        approval.markUsed(usedAt, usedByOperatorId);
        approvalGateway.update(approval);
        log.info(
                "[DeleteApproval] consumed approvalId={} tenantId={} usedBy={}",
                approval.getApprovalId(),
                approval.getTenantId(),
                usedByOperatorId);
    }

    /**
     * 审批签发结果。
     *
     * @param approval 已落库的审批实体（含 hash / expiresAt，不含 raw token）
     * @param rawToken 原始审批令牌，<b>仅此一次可见</b>，caller 应立即返回给项目负责人并放弃本地引用
     */
    public record ApprovalIssueResult(TenantDeleteApproval approval, String rawToken) {}
}
