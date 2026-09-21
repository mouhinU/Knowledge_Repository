package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.application.executor.docingestion.BackfillDocumentImagesCmdExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.ListDocumentImagesQryExe;
import com.mouhin.knowledge.repository.application.executor.docingestion.SearchImagesQryExe;
import com.mouhin.knowledge.repository.client.dto.ExamDocumentImageVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 看图题配图管理端接口（adapter 层）。
 *
 * <p>位于 {@code /api/admin/exam-images}，受 {@link
 * com.mouhin.knowledge.repository.web.security.AdminTokenAuthFilter} 保护。承担三件事：（1）按文档 ID
 * 列出已提取并落库的配图（供校对选图界面渲染缩略图网格）； （2）对已入库但尚未提取图片的历史文档触发回填、按 {@code storage_path} 重跑一次抽取；
 * （3）全局配图检索（按关键词 / 限定文档），供校对页「全局图片搜索」选图。 图片二进制本体走公开的 {@code /api/exam/assets/{assetKey}} 端点由浏览器
 * {@code <img>} 直接加载。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
@RestController
@RequestMapping("/api/admin/exam-images")
@Slf4j
public class AdminDocumentImageController {

    private final ListDocumentImagesQryExe listDocumentImagesQryExe;
    private final BackfillDocumentImagesCmdExe backfillDocumentImagesCmdExe;
    private final SearchImagesQryExe searchImagesQryExe;

    public AdminDocumentImageController(
            ListDocumentImagesQryExe listDocumentImagesQryExe,
            BackfillDocumentImagesCmdExe backfillDocumentImagesCmdExe,
            SearchImagesQryExe searchImagesQryExe) {
        this.listDocumentImagesQryExe = listDocumentImagesQryExe;
        this.backfillDocumentImagesCmdExe = backfillDocumentImagesCmdExe;
        this.searchImagesQryExe = searchImagesQryExe;
    }

    /** 列出某文档已入库的全部配图（含展示 URL、尺寸、页序）。 */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<ExamDocumentImageVO>> listByDocument(@PathVariable Long documentId) {
        return ResponseEntity.ok(listDocumentImagesQryExe.execute(documentId));
    }

    /**
     * 全局配图检索（校对页选图）：按关键词匹配来源文档名 / 文档 Key，或按 documentKey 限定某篇文档； 二者皆空则按最新入库顺序返回全部配图。返回 {records,
     * total} 供前端分页缩略图网格渲染。
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String documentKey,
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        SearchImagesQryExe.SearchResult result =
                searchImagesQryExe.execute(keyword, documentKey, limit, offset);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", result.records());
        body.put("total", result.total());
        return ResponseEntity.ok(body);
    }

    /** 对已入库文档按持久存储路径回填一次图片提取（幂等：文档内 SHA-256 去重）。 */
    @PostMapping("/document/{documentId}/backfill")
    public ResponseEntity<Map<String, Object>> backfill(@PathVariable Long documentId) {
        try {
            int added = backfillDocumentImagesCmdExe.execute(documentId);
            return ResponseEntity.ok(Map.of("documentId", documentId, "added", added));
        } catch (IllegalArgumentException e) {
            log.warn("文档图片回填参数错误 [documentId={}]: {}", documentId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
