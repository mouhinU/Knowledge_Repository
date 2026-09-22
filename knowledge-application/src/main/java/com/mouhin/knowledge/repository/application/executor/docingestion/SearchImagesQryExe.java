package com.mouhin.knowledge.repository.application.executor.docingestion;

import com.mouhin.knowledge.repository.client.dto.ExamDocumentImageVO;
import com.mouhin.knowledge.repository.client.dto.SearchImagesQuery;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentImage;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentImageHit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 全局配图检索查询执行器（app 层，看图题配图 · 阶段 2）。
 *
 * <p>供校对页「全局图片搜索」入口：按关键词匹配来源文档名 / 文档 Key，或按 documentKey 限定到某篇文档， 分页返回配图缩略图所需元信息（含来源文档名与展示
 * URL）。keyword 与 documentKey 皆空时按最新入库顺序返回全部。
 *
 * @author mouhinU
 * @date 2026-09-20
 */
@Component
@Slf4j
public class SearchImagesQryExe {

    /** 单页上限，防止前端误传超大 limit 拖垮查询。 */
    private static final int MAX_LIMIT = 200;

    private final DocumentImageSupport documentImageSupport;

    public SearchImagesQryExe(DocumentImageSupport documentImageSupport) {
        this.documentImageSupport = documentImageSupport;
    }

    public SearchResult execute(SearchImagesQuery query) {
        String keyword = query.getKeyword();
        String documentKey = query.getDocumentKey();
        int safeLimit = Math.max(1, Math.min(query.getLimit(), MAX_LIMIT));
        int safeOffset = Math.max(0, query.getOffset());
        List<ExamDocumentImageVO> records =
                documentImageSupport
                        .searchImages(
                                trimToNull(keyword), trimToNull(documentKey), safeLimit, safeOffset)
                        .stream()
                        .map(SearchImagesQryExe::toVO)
                        .toList();
        long total = documentImageSupport.countImages(trimToNull(keyword), trimToNull(documentKey));
        log.debug(
                "全局配图检索 [keyword={}, documentKey={}, limit={}, offset={}, hit={}, total={}]",
                keyword,
                documentKey,
                safeLimit,
                safeOffset,
                records.size(),
                total);
        return new SearchResult(records, total);
    }

    private static ExamDocumentImageVO toVO(DocumentImageHit hit) {
        DocumentImage image = hit.image();
        ExamDocumentImageVO vo = new ExamDocumentImageVO();
        vo.setAssetKey(image.getAssetKey());
        vo.setDocumentId(image.getDocumentId());
        vo.setDocumentKey(image.getDocumentKey());
        vo.setSourceDocumentName(hit.sourceDocumentName());
        vo.setUrl("/api/exam/assets/" + image.getAssetKey());
        vo.setPageNo(image.getPageNo());
        vo.setSeqOnPage(image.getSeqOnPage());
        vo.setMimeType(image.getMimeType());
        vo.setWidth(image.getWidth());
        vo.setHeight(image.getHeight());
        vo.setByteSize(image.getByteSize());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 检索结果分页封装。
     *
     * @param records 当前页配图列表
     * @param total 同条件命中总数
     */
    public record SearchResult(List<ExamDocumentImageVO> records, long total) {}
}
