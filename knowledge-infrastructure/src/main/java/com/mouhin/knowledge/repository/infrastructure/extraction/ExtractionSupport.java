package com.mouhin.knowledge.repository.infrastructure.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 文档提取公共工具类（checksum + 扩展名提取）
 *
 * <p>供 6 个 MIME 族提取服务共用，避免重复代码。
 *
 * @author mouhinU
 * @date 2026-09-22 16:21:33
 */
public final class ExtractionSupport {

    private ExtractionSupport() {}

    /**
     * 计算文件 MD5 校验和
     *
     * @param filePath 文件路径
     * @return 小写十六进制 MD5
     * @throws IOException 读取失败
     */
    public static String calculateChecksum(Path filePath) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] digest = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }

    /**
     * 提取文件名扩展名（不含点号）
     *
     * @param fileName 文件名
     * @return 扩展名小写；无扩展名返回空串
     */
    public static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "";
    }
}
