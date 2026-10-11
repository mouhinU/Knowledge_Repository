package com.mouhin.knowledge.repository.domain.gateway;

import com.mouhin.knowledge.repository.domain.model.valueobject.LlmProfile;
import java.util.Optional;

/**
 * 租户 LLM 配置持久化端口（P4-T4）。
 *
 * <p>domain 层端口，infra 通过 MyBatis-Plus 实现。查询返回的 {@link LlmProfile} 中 {@code apiKey} 为密文（由 infra 层
 * {@code SecretCipher.decrypt} 解密后回填）。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
public interface LlmProfileGateway {

    /**
     * 按租户 ID 查找 LLM 配置。
     *
     * @param tenantId 租户数字 ID
     * @return 命中的 LLM 配置（apiKey 为密文）；未配置返回 {@link Optional#empty()}
     */
    Optional<LlmProfile> findByTenantId(long tenantId);

    /**
     * 保存或更新租户 LLM 配置。
     *
     * @param tenantId 租户数字 ID
     * @param profile LLM 配置（apiKey 为明文，由 infra 层加密后入库）
     */
    void saveOrUpdate(long tenantId, LlmProfile profile);
}
