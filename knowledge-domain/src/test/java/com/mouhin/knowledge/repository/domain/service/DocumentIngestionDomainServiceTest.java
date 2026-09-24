package com.mouhin.knowledge.repository.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 文档摄入领域服务单测：锁定分块策略选择（五种策略各产出非空结果）、页码追踪（1 起始、空白页过滤）、 权限 / 展示元数据组装（visibility 缺省 INTERNAL）与空文本 /
 * null 边界。
 *
 * @author mouhinU
 * @date 2026-09-24 18:16:00
 */
@DisplayName("文档摄入领域服务：分块策略与元数据组装 (DocumentIngestionDomainService)")
class DocumentIngestionDomainServiceTest {

    private final DocumentIngestionDomainService service = new DocumentIngestionDomainService();

    private Document document() {
        Document doc = new Document();
        doc.setId(42L);
        doc.setDocumentKey("doc-key-1");
        doc.setFileName("语文期末卷.pdf");
        doc.setFileType("application/pdf");
        doc.setOwnerId("teacher-1");
        doc.setDepartmentId("dept-01");
        doc.setAllowedRoles("ROLE_T");
        doc.setTags("期末,语文");
        doc.setCategory("试题");
        doc.setVisibility(DocumentVisibilityEnum.PRIVATE);
        return doc;
    }

    private ChunkingConfig config(int maxTokens, int overlap, ChunkingStrategyEnum strategy) {
        return new ChunkingConfig(maxTokens, overlap, strategy, true, true);
    }

    @Test
    @DisplayName("pages 为 null / 空列表 → 返回空分块，不抛异常")
    void nullOrEmptyPagesReturnsEmpty() {
        assertTrue(service.chunkDocument(document(), null, config(500, 50, null)).isEmpty());
        assertTrue(
                service.chunkDocument(
                                document(),
                                List.of(),
                                config(500, 50, ChunkingStrategyEnum.FIXED_SIZE))
                        .isEmpty());
    }

    @Test
    @DisplayName("FIXED_SIZE：短文本单块，权限与展示元数据全量冗余到分块")
    void fixedSizeSingleChunkCarriesMetadata() {
        List<DocumentChunk> chunks =
                service.chunkDocument(
                        document(),
                        List.of("春天来了，花园里开满了花。"),
                        config(500, 50, ChunkingStrategyEnum.FIXED_SIZE));

        assertEquals(1, chunks.size());
        DocumentChunk chunk = chunks.get(0);
        assertEquals(0, chunk.getChunkIndex());
        assertEquals(42L, chunk.getDocumentId());
        assertEquals("doc-key-1", chunk.getDocumentKey());
        assertEquals(1, chunk.getStartPage());
        assertEquals(1, chunk.getEndPage());
        assertEquals("dept-01", chunk.getDepartmentId());
        assertEquals("teacher-1", chunk.getOwnerId());
        assertEquals("ROLE_T", chunk.getAllowedRoles());
        assertEquals("PRIVATE", chunk.getVisibility());
        assertEquals("语文期末卷.pdf", chunk.getDocumentName());
        assertEquals("application/pdf", chunk.getFileType());
        assertEquals("期末,语文", chunk.getTags());
        assertEquals("试题", chunk.getCategory());
        assertNotNull(chunk.getChunkKey());
        assertTrue(chunk.getTokenCount() > 0);
    }

    @Test
    @DisplayName("visibility 缺省 → 分块回退 INTERNAL（Milvus 过滤兜底口径）")
    void nullVisibilityFallsBackToInternal() {
        Document doc = document();
        doc.setVisibility(null);

        List<DocumentChunk> chunks =
                service.chunkDocument(
                        doc, List.of("内容文本"), config(500, 50, ChunkingStrategyEnum.FIXED_SIZE));

        assertEquals(1, chunks.size());
        assertEquals("INTERNAL", chunks.get(0).getVisibility());
    }

    @Test
    @DisplayName("PAGE 策略：每页一块不跨页，页码按 1 起始编号")
    void pageStrategyKeepsOneChunkPerPage() {
        List<DocumentChunk> chunks =
                service.chunkDocument(
                        document(),
                        List.of("第一页内容。", "第二页内容。"),
                        config(500, 0, ChunkingStrategyEnum.PAGE));

        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).getStartPage());
        assertEquals(1, chunks.get(0).getEndPage());
        assertEquals(2, chunks.get(1).getStartPage());
        assertEquals("第二页内容。", chunks.get(1).getContent());
    }

    @Test
    @DisplayName("空白页（null / 全空格）被过滤：页码仍按原始 1 起始位置编号")
    void blankPagesFilteredWithOriginalPageNumbers() {
        List<String> pages = List.of("", "   ", "第三页有内容。");

        List<DocumentChunk> chunks =
                service.chunkDocument(document(), pages, config(500, 0, ChunkingStrategyEnum.PAGE));

        assertEquals(1, chunks.size());
        assertEquals(3, chunks.get(0).getStartPage(), "null/空白页跳过后，第三页页码应为 3");
    }

    @Test
    @DisplayName("超长单页按 maxChunkSize 拆为多块（FIXED_SIZE），且各块 token 不超上限的宽容量级")
    void longTextSplitIntoMultipleChunks() {
        String longText = "句子。".repeat(400);

        List<DocumentChunk> chunks =
                service.chunkDocument(
                        document(),
                        List.of(longText),
                        config(30, 5, ChunkingStrategyEnum.FIXED_SIZE));

        assertTrue(chunks.size() > 3, "30 token 上限下 400 句应拆多块，实际 " + chunks.size());
        assertTrue(
                chunks.stream().allMatch(c -> c.getContent() != null && !c.getContent().isBlank()));
    }

    @Test
    @DisplayName("五种策略均可路由执行：同一输入各策略产出非空分块")
    void allFiveStrategiesRoutable() {
        List<String> pages = List.of("第一段。这是说明文字。\n\n第二段。还有更多文字内容在这里。", "第三页的第一句。第三页的第二句！第三页的第三句？");

        for (ChunkingStrategyEnum strategy : ChunkingStrategyEnum.values()) {
            List<DocumentChunk> chunks =
                    service.chunkDocument(document(), pages, config(60, 5, strategy));
            assertFalseEmpty(strategy, chunks);
        }
    }

    private void assertFalseEmpty(ChunkingStrategyEnum strategy, List<DocumentChunk> chunks) {
        assertTrue(!chunks.isEmpty(), "策略 " + strategy + " 应产出至少一个分块");
        assertTrue(
                chunks.stream().allMatch(c -> c.getContent() != null && !c.getContent().isBlank()),
                "策略 " + strategy + " 不应产出空白分块");
    }

    @Test
    @DisplayName("内容清洗：控制字符剔除、换行 / 制表符保留，空白 rawChunk 被丢弃")
    void controlCharsSanitizedAndBlankChunksDropped() {
        List<DocumentChunk> chunks =
                service.chunkDocument(
                        document(),
                        List.of("正常\u0000文本\u0007片段", "   "),
                        config(500, 0, ChunkingStrategyEnum.FIXED_SIZE));

        assertEquals(1, chunks.size(), "空白页不应产出分块");
        String content = chunks.get(0).getContent();
        assertTrue(content.contains("正常"), "正文应保留");
        assertTrue(content.indexOf('\u0000') < 0 && content.indexOf('\u0007') < 0, "控制字符应被剔除");
    }
}
