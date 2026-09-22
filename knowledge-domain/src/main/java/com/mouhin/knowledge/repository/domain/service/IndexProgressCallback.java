package com.mouhin.knowledge.repository.domain.service;

/**
 * 索引进度回调接口
 *
 * <p>在文档入库过程中，按分块数量报告进度。 由应用层传入，基础设施层（向量化）调用。
 *
 * @author mouhinU
 * @date 2026-09-13
 */
public interface IndexProgressCallback {

    /**
     * 报告向量化进度
     *
     * @param completedChunks 已完成的分块数
     * @param totalChunks 总分块数
     */
    void onProgress(int completedChunks, int totalChunks);

    /** 索引完成 */
    void onComplete();

    /**
     * 索引失败
     *
     * @param errorMessage 错误信息
     */
    void onError(String errorMessage);
}
