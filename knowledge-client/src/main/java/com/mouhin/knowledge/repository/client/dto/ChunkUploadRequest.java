package com.mouhin.knowledge.repository.client.dto;

/**
 * 分片上传请求
 *
 * @author mouhinU
 * @date 2026-09-13
 */
public class ChunkUploadRequest {

    /** 上传会话 ID */
    private String uploadId;

    /** 当前分片序号（从 0 开始） */
    private Integer chunkIndex;

    /** 总分片数 */
    private Integer totalChunks;

    /** 原始文件名 */
    private String fileName;

    /** 文件总大小（字节） */
    private Long fileSize;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}
