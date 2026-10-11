package com.mouhin.knowledge.repository.infrastructure.security;

import com.mouhin.knowledge.repository.domain.gateway.SecretCipher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 明文透传加解密实现（P4-T4 · T-3 定案 · {@code mode=none}）。
 *
 * <p>装配条件：{@code knowledge.secret.cipher.mode} 未设置或为 {@code none}（默认）。 启动时打 WARN 日志提醒生产环境应切换到
 * {@code local} / {@code kms} / {@code vault} 模式。
 *
 * <p>红线 #4：严禁在 INFO+ 日志输出明文。{@code encrypt} 仅打 DEBUG 级别日志。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "knowledge.secret.cipher.mode",
        havingValue = "none",
        matchIfMissing = true)
public class NoopSecretCipher implements SecretCipher {

    @PostConstruct
    void warnPlaintextMode() {
        log.warn(
                "[SecretCipher] mode=none：API Key 将以明文入库。"
                        + "生产环境请设置 knowledge.secret.cipher.mode=local 并配置主密钥文件。");
    }

    @Override
    public String encrypt(String plaintext) {
        log.debug("[SecretCipher] mode=none encrypt（明文透传）");
        return plaintext;
    }

    @Override
    public String decrypt(String ciphertext) {
        return ciphertext;
    }
}
