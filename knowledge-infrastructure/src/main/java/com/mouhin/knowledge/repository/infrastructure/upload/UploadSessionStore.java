package com.mouhin.knowledge.repository.infrastructure.upload;

import java.util.Optional;
import java.util.Set;

/**
 * 上传会话存储端口
 *
 * <p>隔离会话持久化细节（Redis / 进程内 Map），供 {@link UploadSessionManager} 消费。 属于 infrastructure 内部契约，不暴露至
 * domain 层。
 */
public interface UploadSessionStore {

    /**
     * 写入会话元数据
     *
     * @param uploadId 会话 ID
     * @param session  会话对象
     */
    void put(String uploadId, UploadSessionManager.UploadSession session);

    /**
     * 读取会话元数据（不含 receivedChunks）
     *
     * @param uploadId 会话 ID
     * @return 会话对象，不存在返回 empty
     */
    Optional<UploadSessionManager.UploadSession> get(String uploadId);

    /**
     * 删除会话元数据及分片集合
     *
     * @param uploadId 会话 ID
     */
    void delete(String uploadId);

    /**
     * 追加已收到分片序号
     *
     * @param uploadId   会话 ID
     * @param chunkIndex 分片序号（0-based）
     */
    void addReceivedChunk(String uploadId, int chunkIndex);

    /**
     * 查询已收到分片序号集合
     *
     * @param uploadId 会话 ID
     * @return 分片序号集合，不存在返回空集
     */
    Set<Integer> getReceivedChunks(String uploadId);
}
