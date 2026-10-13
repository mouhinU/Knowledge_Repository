package com.mouhin.knowledge.repository.infrastructure.security;

import com.mouhin.knowledge.repository.domain.gateway.SecretCipher;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地主密钥 AES-256-GCM 加解密实现（P4-T4 · B-2 方案 B）。
 *
 * <p>启动时从 {@code KNOWLEDGE_SECRET_MASTER_KEY_PATH}（默认 {@code /var/run/secrets/kr/master.key}） 一次性加载
 * 256 bit AES 主密钥；加载失败直接 fail-fast。
 *
 * <p>密文格式：{@code Base64(IV_12 || Tag_16 || Ciphertext)}——IV 与 Tag 拼接在密文前， 解密时从头部切出。JDK 原生 {@code
 * javax.crypto}，零新 Maven 依赖。
 *
 * <p>红线 #4：严禁在 INFO+ 日志输出明文或密钥。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "knowledge.secret.cipher.mode", havingValue = "local")
public class LocalSecretCipher implements SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final String masterKeyPath;
    private SecretKeySpec secretKey;

    public LocalSecretCipher(
            @Value("${knowledge.secret.master-key-path:/var/run/secrets/kr/master.key}")
                    String masterKeyPath) {
        this.masterKeyPath = masterKeyPath;
    }

    @PostConstruct
    void loadMasterKey() {
        try {
            byte[] keyBytes = Files.readAllBytes(Path.of(masterKeyPath));
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "Master key must be 256 bits (32 bytes), got "
                                + keyBytes.length
                                + " bytes");
            }
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("[SecretCipher] 主密钥已加载 (mode=local)");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load master key from " + masterKeyPath + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext =
                    cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // IV || ciphertext (JDK GCM 已把 Tag 追加到 ciphertext 尾部)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length < IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8) {
                throw new IllegalArgumentException("Ciphertext too short");
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            byte[] encrypted = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(encrypted);

            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed: " + e.getMessage(), e);
        }
    }
}
