package com.mouhin.knowledge.repository.domain.gateway;

/**
 * 密钥加解密端口（P4-T4 · B-2 方案 B · T-3 定案）。
 *
 * <p>domain 层端口，infra 提供多实现：
 *
 * <ul>
 *   <li>{@code LocalSecretCipher}（{@code knowledge.secret.cipher.mode=local}，AES-256-GCM JDK 原生）；
 *   <li>{@code NoopSecretCipher}（{@code mode=none}，明文透传 + 启动 WARN）。
 * </ul>
 *
 * <p>未来可扩展 {@code KmsSecretCipher}（{@code mode=kms}）或 {@code VaultSecretCipher}（{@code
 * mode=vault}）， 端口不变只换实现。
 *
 * @author mouhinU
 * @date 2026-10-11
 */
public interface SecretCipher {

    /**
     * 加密明文为密文。
     *
     * @param plaintext 待加密明文
     * @return 密文字符串（格式由实现决定，{@code LocalSecretCipher} 为 {@code {IV}:{Tag}:{Ciphertext}} base64）
     */
    String encrypt(String plaintext);

    /**
     * 解密密文为明文。
     *
     * @param ciphertext 密文字符串
     * @return 解密后的明文
     * @throws javax.crypto.AEADBadTagException 密文损坏或密钥不匹配时
     */
    String decrypt(String ciphertext);
}
