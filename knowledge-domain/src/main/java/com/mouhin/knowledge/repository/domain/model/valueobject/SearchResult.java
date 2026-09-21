package com.mouhin.knowledge.repository.domain.model.valueobject;

/**
 * 检索结果值对象
 *
 * @author Knowledge-Repository
 * @date 2026-09-02
 */
public final class SearchResult {

    /** 匹配的文本分块 */
    private final String text;

    /** 所属文档 ID */
    private final String documentId;

    /** 所属文档名称 */
    private final String documentName;

    /** 页码（从 1 开始） */
    private final Integer pageNumber;

    /** 分块在文档内的序号 */
    private final Integer chunkIndex;

    /** 相似度得分（0~1，1 为最相似） */
    private final double score;

    /** 文档分类 */
    private final String category;

    public SearchResult(
            String text,
            String documentId,
            String documentName,
            Integer pageNumber,
            Integer chunkIndex,
            double score) {
        this(text, documentId, documentName, pageNumber, chunkIndex, score, null);
    }

    public SearchResult(
            String text,
            String documentId,
            String documentName,
            Integer pageNumber,
            Integer chunkIndex,
            double score,
            String category) {
        this.text = text;
        this.documentId = documentId;
        this.documentName = documentName;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.category = category;
    }

    public String getText() {
        return text;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public double getScore() {
        return score;
    }

    public String getCategory() {
        return category;
    }
}
