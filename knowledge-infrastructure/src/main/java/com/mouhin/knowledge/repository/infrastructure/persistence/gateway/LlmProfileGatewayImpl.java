package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.LlmProfileGateway;
import com.mouhin.knowledge.repository.domain.gateway.SecretCipher;
import com.mouhin.knowledge.repository.domain.model.valueobject.LlmProfile;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.LlmProfileDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.LlmProfileMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 租户 LLM 配置持久化实现（P4-T4）。
 *
 * <p>infra 层负责 DO ↔ Entity 转换：
 *
 * <ul>
 *   <li>读取时：DO 的 {@code apiKey}（密文）经 {@link SecretCipher#decrypt} 解密后填入 {@link LlmProfile}；
 *   <li>写入时：{@link LlmProfile} 的 {@code apiKey}（明文）经 {@link SecretCipher#encrypt} 加密后入库。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
public class LlmProfileGatewayImpl implements LlmProfileGateway {

    private final LlmProfileMapper mapper;
    private final SecretCipher secretCipher;

    public LlmProfileGatewayImpl(LlmProfileMapper mapper, SecretCipher secretCipher) {
        this.mapper = mapper;
        this.secretCipher = secretCipher;
    }

    @Override
    public Optional<LlmProfile> findByTenantId(long tenantId) {
        LlmProfileDO doObj =
                mapper.selectOne(
                        new LambdaQueryWrapper<LlmProfileDO>()
                                .eq(LlmProfileDO::getTenantId, tenantId));

        if (doObj == null) {
            return Optional.empty();
        }
        // 解密 apiKey
        String decryptedKey =
                doObj.getApiKey() != null ? secretCipher.decrypt(doObj.getApiKey()) : null;
        LlmProfile profile =
                new LlmProfile(
                        decryptedKey,
                        doObj.getBaseUrl(),
                        doObj.getModel(),
                        doObj.getEmbeddingModel(),
                        doObj.getDimension());
        return Optional.of(profile);
    }

    @Override
    public void saveOrUpdate(long tenantId, LlmProfile profile) {
        LlmProfileDO existing =
                mapper.selectOne(
                        new LambdaQueryWrapper<LlmProfileDO>()
                                .eq(LlmProfileDO::getTenantId, tenantId));

        // 加密 apiKey
        String encryptedKey =
                profile.apiKey() != null ? secretCipher.encrypt(profile.apiKey()) : null;

        if (existing == null) {
            LlmProfileDO doObj = new LlmProfileDO();
            doObj.setTenantId(tenantId);
            doObj.setApiKey(encryptedKey);
            doObj.setBaseUrl(profile.baseUrl());
            doObj.setModel(profile.model());
            doObj.setEmbeddingModel(profile.embeddingModel());
            doObj.setDimension(profile.dimension());
            doObj.setUpdatedAt(LocalDateTime.now());
            mapper.insert(doObj);
        } else {
            existing.setApiKey(encryptedKey);
            existing.setBaseUrl(profile.baseUrl());
            existing.setModel(profile.model());
            existing.setEmbeddingModel(profile.embeddingModel());
            existing.setDimension(profile.dimension());
            existing.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(existing);
        }
        log.debug("[LlmProfile] 已保存: tenantId={}", tenantId);
    }
}
