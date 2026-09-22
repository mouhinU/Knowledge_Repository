package com.mouhin.knowledge.repository.infrastructure.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 分片上传会话管理器
 *
 * <p>管理大文件的分片上传会话，支持断点续传。 每个会话对应一个临时目录，分片按序号存储，全部到齐后组装为完整文件。
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Component
@Slf4j
public class UploadSessionManager {

    /** 会话超时时间：2 小时 */
    private static final long SESSION_TIMEOUT_MS = 7_200_000L;

    /** 活跃会话 */
    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    @Value("${knowledge.storage.path:./data/documents}")
    private String storagePath;

    /**
     * 创建上传会话
     *
     * @param fileName 原始文件名
     * @param fileSize 文件总大小
     * @param totalChunks 总分片数
     * @return 会话 ID
     */
    public String createSession(String fileName, long fileSize, int totalChunks)
            throws IOException {
        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = getSessionDir(uploadId);
        Files.createDirectories(sessionDir);

        UploadSession session =
                new UploadSession(uploadId, fileName, fileSize, totalChunks, sessionDir);
        sessions.put(uploadId, session);

        log.info("创建上传会话 [uploadId={}, fileName={}, chunks={}]", uploadId, fileName, totalChunks);
        cleanupExpiredSessions();
        return uploadId;
    }

    /**
     * 获取会话信息
     *
     * @param uploadId 会话 ID
     * @return 会话对象，不存在返回 null
     */
    public UploadSession getSession(String uploadId) {
        return sessions.get(uploadId);
    }

    /**
     * 查询已上传的分片序号集合（用于断点续传）
     *
     * @param uploadId 会话 ID
     * @return 已上传的分片序号集合
     */
    public Set<Integer> getUploadedChunks(String uploadId) {
        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            return Set.of();
        }
        return session.getReceivedChunks();
    }

    /**
     * 保存分片
     *
     * @param uploadId 会话 ID
     * @param chunkIndex 分片序号
     * @param data 分片数据流
     * @return 是否为最后一个分片（全部到齐）
     */
    public boolean saveChunk(String uploadId, int chunkIndex, InputStream data) throws IOException {
        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在: " + uploadId);
        }

        Path chunkFile = session.getSessionDir().resolve("chunk_" + chunkIndex);
        Files.copy(data, chunkFile, StandardCopyOption.REPLACE_EXISTING);
        session.markChunkReceived(chunkIndex);

        log.debug(
                "保存分片 [uploadId={}, chunk={}/{}]",
                uploadId,
                chunkIndex + 1,
                session.getTotalChunks());

        return session.isComplete();
    }

    /**
     * 组装所有分片为完整文件
     *
     * @param uploadId 会话 ID
     * @return 组装后的文件路径
     */
    public Path assembleChunks(String uploadId) throws IOException {
        UploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在: " + uploadId);
        }
        if (!session.isComplete()) {
            throw new IllegalStateException(
                    String.format(
                            "分片不完整 [uploadId=%s, received=%d/%d]",
                            uploadId,
                            session.getReceivedChunks().size(),
                            session.getTotalChunks()));
        }

        // 确定文件扩展名
        String fileName = session.getFileName();
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = fileName.substring(dotIndex);
        }

        // 组装到永久存储目录
        Path targetFile = Path.of(storagePath, UUID.randomUUID() + extension);
        Files.createDirectories(targetFile.getParent());

        log.info("开始组装分片 [uploadId={}, target={}]", uploadId, targetFile);

        try (var outputStream = Files.newOutputStream(targetFile)) {
            for (int i = 0; i < session.getTotalChunks(); i++) {
                Path chunkFile = session.getSessionDir().resolve("chunk_" + i);
                if (!Files.exists(chunkFile)) {
                    throw new IOException("缺少分片: " + i);
                }
                Files.copy(chunkFile, outputStream);
            }
        }

        // 清理会话临时目录
        cleanupSession(session);

        log.info("分片组装完成 [uploadId={}, size={}]", uploadId, Files.size(targetFile));
        return targetFile;
    }

    /** 取消上传会话 */
    public void cancelSession(String uploadId) {
        UploadSession session = sessions.remove(uploadId);
        if (session != null) {
            cleanupSession(session);
            log.info("取消上传会话 [uploadId={}]", uploadId);
        }
    }

    private Path getSessionDir(String uploadId) {
        return Path.of(System.getProperty("java.io.tmpdir"), "knowledge-upload-" + uploadId);
    }

    private void cleanupSession(UploadSession session) {
        try {
            Path dir = session.getSessionDir();
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(
                                    path -> {
                                        try {
                                            Files.deleteIfExists(path);
                                        } catch (IOException e) {
                                            log.debug("清理临时文件失败: {}", path);
                                        }
                                    });
                }
            }
        } catch (IOException e) {
            log.debug("清理会话目录失败 [uploadId={}]", session.getUploadId());
        }
    }

    private void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusMillis(SESSION_TIMEOUT_MS);
        sessions.entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getValue().getCreatedAt().isBefore(cutoff)) {
                                log.info("清理过期上传会话 [uploadId={}]", entry.getKey());
                                cleanupSession(entry.getValue());
                                return true;
                            }
                            return false;
                        });
    }

    /** 上传会话 */
    public static class UploadSession {
        private final String uploadId;
        private final String fileName;
        private final long fileSize;
        private final int totalChunks;
        private final Path sessionDir;
        private final Instant createdAt;
        private final Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();

        public UploadSession(
                String uploadId, String fileName, long fileSize, int totalChunks, Path sessionDir) {
            this.uploadId = uploadId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.totalChunks = totalChunks;
            this.sessionDir = sessionDir;
            this.createdAt = Instant.now();
        }

        public void markChunkReceived(int chunkIndex) {
            receivedChunks.add(chunkIndex);
        }

        public boolean isComplete() {
            return receivedChunks.size() >= totalChunks;
        }

        public String getUploadId() {
            return uploadId;
        }

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public int getTotalChunks() {
            return totalChunks;
        }

        public Path getSessionDir() {
            return sessionDir;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Set<Integer> getReceivedChunks() {
            return receivedChunks;
        }
    }
}
