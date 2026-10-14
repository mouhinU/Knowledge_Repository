package com.mouhin.knowledge.repository.infrastructure.upload;

import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内上传会话存储（Redis 关闭时降级）
 *
 * <p>行为与原 {@link UploadSessionManager} 内嵌 {@link ConcurrentHashMap} 一致。 过期清理仍为惰性（{@link #get} 时检查
 * TTL）。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = FeatureFlagKeys.REDIS_ENABLED, havingValue = "false")
public class NoopUploadSessionStore implements UploadSessionStore {

    private final Map<String, UploadSessionManager.UploadSession> sessions =
            new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public NoopUploadSessionStore(UploadSessionProperties properties) {
        this.ttlSeconds = properties.getSessionTtlSeconds();
    }

    @Override
    public void put(String uploadId, UploadSessionManager.UploadSession session) {
        sessions.put(uploadId, session);
    }

    @Override
    public Optional<UploadSessionManager.UploadSession> get(String uploadId) {
        var session = sessions.get(uploadId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.getCreatedAt()
                .plus(Duration.ofSeconds(ttlSeconds))
                .isBefore(java.time.Instant.now())) {
            log.info("清理过期上传会话（降级模式）[uploadId={}]", uploadId);
            sessions.remove(uploadId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void delete(String uploadId) {
        sessions.remove(uploadId);
    }

    @Override
    public void addReceivedChunk(String uploadId, int chunkIndex) {
        var session = sessions.get(uploadId);
        if (session != null) {
            session.markChunkReceived(chunkIndex);
        }
    }

    @Override
    public Set<Integer> getReceivedChunks(String uploadId) {
        var session = sessions.get(uploadId);
        return session != null ? session.getReceivedChunks() : Set.of();
    }
}
