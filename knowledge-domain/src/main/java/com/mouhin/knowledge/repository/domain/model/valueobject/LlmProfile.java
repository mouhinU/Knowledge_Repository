package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 租户 LLM 配置值对象（P4-T4）。
 *
 * <p>封装单个租户的 LLM 调用参数：API Key、base URL、对话模型、向量模型、向量维度。 不可变 record，由 {@code
 * TenantLlmProfileResolver.resolve(tenantId)} 返回。
 *
 * <p>{@code apiKey} 在 infra 边界由 {@code SecretCipher.decrypt} 解密后填入， 上层消费者拿到的始终是明文。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
public record LlmProfile(
        String apiKey, String baseUrl, String model, String embeddingModel, Integer dimension) {

    /**
     * 紧凑构造器——{@code apiKey} 与 {@code baseUrl} 为必填。
     *
     * @param apiKey 解密后的 API Key 明文
     * @param baseUrl LLM API base URL
     */
    public LlmProfile {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
    }
}
