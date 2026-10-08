package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.time.Instant;

/**
 * 平台端令牌载荷（domain 值对象，P3-T1 / T-4 方案 B）。
 *
 * <p>与 {@code AdminTokenPayload}（租户管理员身份）<b>完全分离</b>：平台令牌承载的是「平台运维/管理员」身份， 用于访问 {@code
 * /api/platform/**} 生命周期端点，不携带 {@code tenantKey}（平台管理员跨租户，不归属任何单一租户）。 由独立的 {@code
 * knowledge.platform.jwt.secret} 签发，与租户管理员令牌密钥隔离， 一枚令牌无法跨体系复用（T-4 方案 B 的核心安全边界）。
 *
 * <p>纯 JDK 类型，不依赖任何基础设施。
 *
 * @param operatorId 平台操作者唯一标识（写入 JWT {@code sub} claim，用于审计溯源）
 * @param operatorName 平台操作者展示名（写入 {@code username} claim）
 * @param expiresAt 令牌过期时刻
 * @author mouhinU
 * @date 2026-10-08
 */
public record PlatformTokenPayload(String operatorId, String operatorName, Instant expiresAt) {}
