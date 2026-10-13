package com.mouhin.knowledge.repository.infrastructure.upload;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的上传会话存储
 *
 * <p>会话元数据存 Redis Hash（{@code kr:upload:session:{uploadId}}），已收分片存 Redis Set（{@code
 * kr:upload:chunks:{uploadId}}）。两者共享 TTL，每次写入刷新。
 *
 * <p>Redis 不可用时由 {@link NoopUploadSessionStore} 降级替代。
 */
@Component
@Slf4j
@ConditionalOnProperty(
        name = "knowledge.redis.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RedisUploadSessionStore implements UploadSessionStore {

    private static final String SESSION_KEY_PREFIX = "kr:upload:session:";
    private static final String CHUNKS_KEY_PREFIX = "kr:upload:chunks:";

    private static final String FIELD_FILE_NAME = "fileName";
    private static final String FIELD_FILE_SIZE = "fileSize";
    private static final String FIELD_TOTAL_CHUNKS = "totalChunks";
    private static final String FIELD_SESSION_DIR = "sessionDir";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_TENANT_KEY = "tenantKey";

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public RedisUploadSessionStore(
            StringRedisTemplate redisTemplate, UploadSessionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = properties.getSessionTtlSeconds();
    }

    @Override
    public void put(String uploadId, UploadSessionManager.UploadSession session) {
        String sessionKey = SESSION_KEY_PREFIX + uploadId;
        String chunksKey = CHUNKS_KEY_PREFIX + uploadId;
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(FIELD_FILE_NAME, session.getFileName());
            fields.put(FIELD_FILE_SIZE, String.valueOf(session.getFileSize()));
            fields.put(FIELD_TOTAL_CHUNKS, String.valueOf(session.getTotalChunks()));
            fields.put(FIELD_SESSION_DIR, session.getSessionDir().toString());
            fields.put(FIELD_CREATED_AT, String.valueOf(session.getCreatedAt().toEpochMilli()));
            fields.put(FIELD_TENANT_KEY, session.getTenantKey());
            redisTemplate.opsForHash().putAll(sessionKey, fields);
            redisTemplate.expire(sessionKey, java.time.Duration.ofSeconds(ttlSeconds));
            redisTemplate.expire(chunksKey, java.time.Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis 写入上传会话失败 [uploadId={}]: {}", uploadId, e.getMessage());
            throw e;
        }
    }

    @Override
    public Optional<UploadSessionManager.UploadSession> get(String uploadId) {
        String sessionKey = SESSION_KEY_PREFIX + uploadId;
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(sessionKey);
            if (fields.isEmpty()) {
                return Optional.empty();
            }
            String fileName = (String) fields.get(FIELD_FILE_NAME);
            long fileSize = Long.parseLong((String) fields.get(FIELD_FILE_SIZE));
            int totalChunks = Integer.parseInt((String) fields.get(FIELD_TOTAL_CHUNKS));
            Path sessionDir = Path.of((String) fields.get(FIELD_SESSION_DIR));
            Instant createdAt =
                    Instant.ofEpochMilli(Long.parseLong((String) fields.get(FIELD_CREATED_AT)));
            String tenantKey = (String) fields.get(FIELD_TENANT_KEY);

            Set<Integer> chunks = getReceivedChunks(uploadId);
            var session =
                    new UploadSessionManager.UploadSession(
                            uploadId,
                            fileName,
                            fileSize,
                            totalChunks,
                            sessionDir,
                            createdAt,
                            tenantKey);
            chunks.forEach(session::markChunkReceived);
            return Optional.of(session);
        } catch (Exception e) {
            log.warn("Redis 读取上传会话失败 [uploadId={}]: {}", uploadId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String uploadId) {
        String sessionKey = SESSION_KEY_PREFIX + uploadId;
        String chunksKey = CHUNKS_KEY_PREFIX + uploadId;
        try {
            redisTemplate.delete(List.of(sessionKey, chunksKey));
        } catch (Exception e) {
            log.warn("Redis 删除上传会话失败 [uploadId={}]: {}", uploadId, e.getMessage());
        }
    }

    @Override
    public void addReceivedChunk(String uploadId, int chunkIndex) {
        String chunksKey = CHUNKS_KEY_PREFIX + uploadId;
        String sessionKey = SESSION_KEY_PREFIX + uploadId;
        try {
            redisTemplate.opsForSet().add(chunksKey, String.valueOf(chunkIndex));
            redisTemplate.expire(chunksKey, java.time.Duration.ofSeconds(ttlSeconds));
            redisTemplate.expire(sessionKey, java.time.Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn(
                    "Redis 追加上传分片失败 [uploadId={}, chunk={}]: {}",
                    uploadId,
                    chunkIndex,
                    e.getMessage());
            throw e;
        }
    }

    @Override
    public Set<Integer> getReceivedChunks(String uploadId) {
        String chunksKey = CHUNKS_KEY_PREFIX + uploadId;
        try {
            Set<String> members = redisTemplate.opsForSet().members(chunksKey);
            if (members == null || members.isEmpty()) {
                return Set.of();
            }
            return members.stream().map(Integer::parseInt).collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            log.warn("Redis 查询上传分片失败 [uploadId={}]: {}", uploadId, e.getMessage());
            return Set.of();
        }
    }
}
