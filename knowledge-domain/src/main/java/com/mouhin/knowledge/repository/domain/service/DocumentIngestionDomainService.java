package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.aggregate.Document;
import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkWindow;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingConfig;
import com.mouhin.knowledge.repository.domain.model.valueobject.ChunkingStrategyEnum;
import com.mouhin.knowledge.repository.domain.model.valueobject.DocumentVisibilityEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档摄入领域服务
 *
 * <p>负责将提取的文本按配置进行分块，并为每个分块附加权限元数据。 支持五种切分策略，每种策略均追踪页码信息。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Service
@Slf4j
public class DocumentIngestionDomainService {

    /** 增强句子分割正则： - 中英文句号、问号、叹号、分号、冒号 - 省略号（中英文） - 换行符（作为弱句子边界） - 避免在缩写、数字中间断开 */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.。!！?？;；…\\n])\\s+");

    /** 中文句子结尾标点 */
    private static final Pattern CHINESE_SENTENCE_END = Pattern.compile("[.。!！?？;；…]+\\s*");

    /** 列表项模式：数字编号、字母编号、中文编号 */
    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d+[.、)）]|\\([0-9]+\\)|[a-zA-Z][.、)）]|[-•·]\\s|[一二三四五六七八九十]+[、.．])");

    /**
     * 将文本内容按配置分块，并附加文档权限元数据
     *
     * @param document 文档聚合根
     * @param pages 按页提取的文本列表（index 0 = page 1）
     * @param config 分块配置
     * @return 分块列表
     */
    public List<DocumentChunk> chunkDocument(
            Document document, List<String> pages, ChunkingConfig config) {
        if (pages == null || pages.isEmpty()) {
            log.warn("Document {} has no pages to chunk", document.getDocumentKey());
            return List.of();
        }

        ChunkingStrategyEnum strategy = config.getStrategy();
        if (log.isInfoEnabled()) {
            log.info(
                    "Chunking document {} with strategy={}, maxChunk={}, overlap={}",
                    document.getDocumentKey(),
                    strategy,
                    config.getMaxChunkSize(),
                    config.getOverlapSize());
        }

        // 构建带页码的文本段列表
        List<PageText> pageTexts = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            String text = pages.get(i);
            if (text != null && !text.isBlank()) {
                pageTexts.add(new PageText(text.trim(), i + 1));
            }
        }

        List<RawChunk> rawChunks =
                switch (strategy) {
                    case FIXED_SIZE -> chunkFixedSize(pageTexts, config);
                    case RECURSIVE -> chunkRecursive(pageTexts, config);
                    case SENTENCE -> chunkSentence(pageTexts, config);
                    case PAGE -> chunkPage(pageTexts);
                    case PARAGRAPH -> chunkParagraph(pageTexts, config);
                };

        // 将中间表示转为 DocumentChunk 并附加元数据
        List<DocumentChunk> chunks = new ArrayList<>(rawChunks.size());
        int chunkIndex = 0;
        for (RawChunk raw : rawChunks) {
            if (raw.content() == null || raw.content().isBlank()) {
                continue;
            }
            DocumentChunk chunk =
                    buildChunk(document, chunkIndex, raw.startPage(), raw.endPage(), raw.content());
            chunks.add(chunk);
            chunkIndex++;
        }

        log.info(
                "Document {} chunked into {} pieces (strategy={})",
                document.getDocumentKey(),
                chunks.size(),
                strategy);
        return chunks;
    }

    /**
     * FIXED_SIZE：按 Token 上限切分
     *
     * <p>优先在段落边界切分；单个段落超长时，降级到句子边界； 句子仍超长时，降级到空格/词边界；最后兜底按字符强制切分。
     */
    private List<RawChunk> chunkFixedSize(List<PageText> pages, ChunkingConfig config) {
        // 合并所有页面文本，同时记录每个字符对应的页码
        MergedPages merged = mergePages(pages, "\n\n");
        String text = merged.text();
        List<int[]> charPageMap = merged.charPageMap();
        int maxTokens = config.getMaxChunkSize();
        int overlapTokens = config.getOverlapSize();

        // 按段落切分
        List<int[]> paragraphRanges = findParagraphRanges(text);
        List<RawChunk> result = new ArrayList<>();
        int chunkStart = 0;
        int chunkTokens = 0;
        int lastPageNum = pages.isEmpty() ? 1 : pages.get(0).pageNumber();

        for (int[] paraRange : paragraphRanges) {
            int paraStart = paraRange[0];
            int paraEnd = paraRange[1];
            String paraText = text.substring(paraStart, paraEnd).trim();
            if (paraText.isEmpty()) {
                continue;
            }

            int paraTokens = estimateTokenCount(paraText);

            // 单段落超长：需要二次切分
            if (paraTokens > maxTokens) {
                // 先把之前积累的内容作为一个分块
                if (chunkStart < paraStart && chunkTokens > 0) {
                    String chunkContent = text.substring(chunkStart, paraStart).trim();
                    int startPage = lookupPage(charPageMap, chunkStart);
                    int endPage =
                            lookupPage(
                                    charPageMap, Math.min(paraStart - 1, charPageMap.size() - 1));
                    result.add(new RawChunk(chunkContent, startPage, endPage));
                }

                // 对超长段落进行句子级切分
                List<RawChunk> subChunks =
                        splitLongText(paraText, maxTokens, overlapTokens, charPageMap, paraStart);
                result.addAll(subChunks);

                chunkStart = paraEnd;
                chunkTokens = 0;
                continue;
            }

            int candidateTokens = chunkTokens + (chunkTokens > 0 ? 1 : 0) + paraTokens;

            if (candidateTokens > maxTokens && chunkTokens > 0) {
                // 当前块已满，输出
                String chunkContent = text.substring(chunkStart, paraStart).trim();
                int startPage = lookupPage(charPageMap, chunkStart);
                int endPage =
                        lookupPage(charPageMap, Math.min(paraStart - 1, charPageMap.size() - 1));
                result.add(new RawChunk(chunkContent, startPage, endPage));

                // 重叠处理
                if (overlapTokens > 0 && chunkTokens > 0) {
                    int overlapStart =
                            findOverlapStart(new ChunkWindow(chunkStart, paraStart, overlapTokens));
                    chunkStart = overlapStart;
                    chunkTokens = estimateTokenCount(text.substring(chunkStart, paraStart));
                } else {
                    chunkStart = paraStart;
                    chunkTokens = 0;
                }
            }

            chunkTokens += (chunkTokens > 0 ? 1 : 0) + paraTokens;
        }

        // 输出最后一个分块
        if (chunkStart < text.length()) {
            String chunkContent = text.substring(chunkStart).trim();
            if (!chunkContent.isEmpty()) {
                int startPage = lookupPage(charPageMap, chunkStart);
                int endPage =
                        lookupPage(
                                charPageMap, Math.min(text.length() - 1, charPageMap.size() - 1));
                result.add(new RawChunk(chunkContent, startPage, endPage));
            }
        }

        return result;
    }

    /**
     * RECURSIVE：递归分隔符切分
     *
     * <p>分隔符层级：双换行(段落) → 单换行(行) → 句子边界 → 空格(词) → 强制字符
     */
    private List<RawChunk> chunkRecursive(List<PageText> pages, ChunkingConfig config) {
        MergedPages merged = mergePages(pages, "\n\n");
        String text = merged.text();
        List<int[]> charPageMap = merged.charPageMap();
        int maxChars = config.getMaxChunkSize() * 3;
        int overlapChars = config.getOverlapSize() * 3;

        // 分隔符层级：段落 → 行 → 句子 → 空格 → 字符
        String[] separators = {"\n\n", "\n", "。.!！?？;；…", " ", ""};
        List<RawChunk> result = new ArrayList<>();
        recursiveSplitWithPages(text, maxChars, overlapChars, separators, 0, charPageMap, result);
        return result;
    }

    // ==================== FIXED_SIZE 策略 ====================

    private void recursiveSplitWithPages(
            String text,
            int maxChars,
            int overlapChars,
            String[] separators,
            int sepIndex,
            List<int[]> charPageMap,
            List<RawChunk> result) {
        if (text.length() <= maxChars) {
            if (!text.isBlank()) {
                int startPage = lookupPage(charPageMap, 0);
                int endPage = lookupPage(charPageMap, Math.max(0, text.length() - 1));
                result.add(new RawChunk(text.trim(), startPage, endPage));
            }
            return;
        }

        if (sepIndex >= separators.length) {
            // 兜底：强制字符切分
            List<RawChunk> forced = forceSplitWithPages(text, maxChars, overlapChars, charPageMap);
            result.addAll(forced);
            return;
        }

        String sep = separators[sepIndex];
        if (sep.isEmpty()) {
            List<RawChunk> forced = forceSplitWithPages(text, maxChars, overlapChars, charPageMap);
            result.addAll(forced);
            return;
        }

        // 按当前层级分隔符切分
        String[] parts;
        if (sep.length() > 1 && !sep.contains("\n")) {
            // 句子边界：按字符集分割
            parts = splitByCharSet(text, sep);
        } else {
            parts = text.split(Pattern.quote(sep), -1);
        }

        StringBuilder current = new StringBuilder();
        int currentOffset = 0;

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            String candidate =
                    !current.isEmpty() ? current + (sep.length() <= 2 ? sep : " ") + part : part;

            if (candidate.length() > maxChars && !current.isEmpty()) {
                // 输出当前块
                String chunkText = current.toString().trim();
                if (!chunkText.isEmpty()) {
                    int startPage = lookupPage(charPageMap, currentOffset);
                    int endPage =
                            lookupPage(
                                    charPageMap,
                                    Math.min(
                                            currentOffset + current.length() - 1,
                                            charPageMap.size() - 1));
                    result.add(new RawChunk(chunkText, startPage, endPage));
                }

                // 重叠处理
                if (overlapChars > 0 && current.length() > overlapChars) {
                    String overlapText = current.substring(current.length() - overlapChars);
                    currentOffset = currentOffset + current.length() - overlapChars;
                    current = new StringBuilder(overlapText);
                    current.append(sep.length() <= 2 ? sep : " ").append(part);
                } else {
                    currentOffset = currentOffset + current.length();
                    current = new StringBuilder(part);
                }
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (current.length() > 0) {
            String remaining = current.toString();
            if (remaining.length() > maxChars && sepIndex + 1 < separators.length) {
                recursiveSplitWithPages(
                        remaining,
                        maxChars,
                        overlapChars,
                        separators,
                        sepIndex + 1,
                        charPageMap,
                        result);
            } else if (!remaining.isBlank()) {
                int startPage = lookupPage(charPageMap, currentOffset);
                int endPage =
                        lookupPage(
                                charPageMap,
                                Math.min(
                                        currentOffset + remaining.length() - 1,
                                        charPageMap.size() - 1));
                result.add(new RawChunk(remaining.trim(), startPage, endPage));
            }
        }
    }

    // ==================== RECURSIVE 策略 ====================

    /**
     * SENTENCE：按句子边界切分，合并至 Token 上限
     *
     * <p>增强句子检测：支持中英文标点、省略号、列表项、换行边界。
     */
    private List<RawChunk> chunkSentence(List<PageText> pages, ChunkingConfig config) {
        MergedPages merged = mergePages(pages, "\n");
        String text = merged.text();
        List<int[]> charPageMap = merged.charPageMap();
        int maxChars = config.getMaxChunkSize() * 3;
        int overlapChars = config.getOverlapSize() * 3;

        // 按句子边界分割
        List<String> sentences = splitSentences(text);
        List<RawChunk> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentOffset = 0;

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String candidate = !current.isEmpty() ? current + " " + trimmed : trimmed;

            if (candidate.length() > maxChars && !current.isEmpty()) {
                String chunkText = current.toString().trim();
                int startPage = lookupPage(charPageMap, currentOffset);
                int endPage =
                        lookupPage(
                                charPageMap,
                                Math.min(
                                        currentOffset + current.length() - 1,
                                        charPageMap.size() - 1));
                result.add(new RawChunk(chunkText, startPage, endPage));

                // 重叠：保留上一块末尾内容
                if (overlapChars > 0 && current.length() > overlapChars) {
                    String overlapText = current.substring(current.length() - overlapChars);
                    currentOffset = currentOffset + current.length() - overlapChars;
                    current = new StringBuilder(overlapText);
                    current.append(" ").append(trimmed);
                } else {
                    currentOffset = currentOffset + current.length();
                    current = new StringBuilder(trimmed);
                }
            } else {
                current = new StringBuilder(candidate);
            }
        }

        if (current.length() > 0) {
            String chunkText = current.toString().trim();
            if (!chunkText.isEmpty()) {
                int startPage = lookupPage(charPageMap, currentOffset);
                int endPage =
                        lookupPage(
                                charPageMap,
                                Math.min(
                                        currentOffset + current.length() - 1,
                                        charPageMap.size() - 1));
                result.add(new RawChunk(chunkText, startPage, endPage));
            }
        }

        return result;
    }

    /**
     * PAGE：每页作为一个独立分块
     *
     * <p>过滤空白页，保留页码信息。
     */
    private List<RawChunk> chunkPage(List<PageText> pages) {
        List<RawChunk> result = new ArrayList<>();
        for (PageText pt : pages) {
            String text = pt.text().trim();
            if (!text.isEmpty()) {
                result.add(new RawChunk(text, pt.pageNumber(), pt.pageNumber()));
            }
        }
        return result;
    }

    // ==================== SENTENCE 策略 ====================

    /**
     * PARAGRAPH：按段落切分，小段落合并至 Token 上限
     *
     * <p>识别列表项结构，尽量不在列表中间断开。 超长段落降级到句子级切分。
     */
    private List<RawChunk> chunkParagraph(List<PageText> pages, ChunkingConfig config) {
        MergedPages merged = mergePages(pages, "\n\n");
        String text = merged.text();
        List<int[]> charPageMap = merged.charPageMap();
        int maxChars = config.getMaxChunkSize() * 3;
        int overlapChars = config.getOverlapSize() * 3;

        List<int[]> paraRanges = findParagraphRanges(text);
        List<RawChunk> result = new ArrayList<>();
        int chunkStart = 0;
        int chunkLen = 0;

        for (int[] paraRange : paraRanges) {
            int paraStart = paraRange[0];
            int paraEnd = paraRange[1];
            String paraText = text.substring(paraStart, paraEnd).trim();
            if (paraText.isEmpty()) {
                continue;
            }

            int paraLen = paraText.length();

            // 超长段落：先输出已有内容，再对超长段落做句子级切分
            if (paraLen > maxChars) {
                if (chunkLen > 0) {
                    result.add(buildWindowChunk(text, charPageMap, chunkStart, chunkLen));
                    chunkLen = 0;
                }

                List<RawChunk> subChunks =
                        splitLongText(
                                paraText,
                                config.getMaxChunkSize(),
                                config.getOverlapSize(),
                                charPageMap,
                                paraStart);
                result.addAll(subChunks);
                chunkStart = paraEnd;
                continue;
            }

            int candidateLen = chunkLen + (chunkLen > 0 ? 2 : 0) + paraLen;

            if (candidateLen > maxChars && chunkLen > 0) {
                result.add(buildWindowChunk(text, charPageMap, chunkStart, chunkLen));

                if (overlapChars > 0 && chunkLen > overlapChars) {
                    int overlapStart = chunkStart + chunkLen - overlapChars;
                    chunkStart = overlapStart;
                    chunkLen = text.substring(chunkStart, paraStart).length();
                } else {
                    chunkStart = paraStart;
                    chunkLen = 0;
                }
            }

            if (chunkLen == 0) {
                chunkStart = paraStart;
                chunkLen = paraEnd - paraStart;
            } else {
                chunkLen = paraEnd - chunkStart;
            }
        }

        if (chunkLen > 0) {
            RawChunk tail = buildWindowChunk(text, charPageMap, chunkStart, chunkLen);
            if (!tail.content().isEmpty()) {
                result.add(tail);
            }
        }

        return result;
    }

    /**
     * 从合并文本中按 [start, start+len) 窗口构造一个分块，并回填其起止页码。
     *
     * <p>窗口上界自动收敛到 charPageMap 末位，行为与原内联实现一致。
     */
    private RawChunk buildWindowChunk(String text, List<int[]> charPageMap, int start, int len) {
        String chunkText = text.substring(start, start + len).trim();
        int startPage = lookupPage(charPageMap, start);
        int endPage = lookupPage(charPageMap, Math.min(start + len - 1, charPageMap.size() - 1));
        return new RawChunk(chunkText, startPage, endPage);
    }

    // ==================== PAGE 策略 ====================

    /** 查找段落范围：返回 List of [start, end] */
    private List<int[]> findParagraphRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        Pattern paraPattern =
                Pattern.compile("(?:^|\\n\\s*\\n)\\s*(.+?)(?=\\n\\s*\\n|$)", Pattern.DOTALL);
        Matcher matcher = paraPattern.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[] {matcher.start(1), matcher.end(1)});
        }
        if (ranges.isEmpty() && !text.isBlank()) {
            ranges.add(new int[] {0, text.length()});
        }
        return ranges;
    }

    // ==================== PARAGRAPH 策略 ====================

    /** 按字符集分割文本（用于句子边界分割） */
    private String[] splitByCharSet(String text, String charSet) {
        String regex = "(?<=[" + Pattern.quote(charSet) + "])\\s*";
        return text.split(regex);
    }

    // ==================== 工具方法 ====================

    /** 增强句子分割 */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 先按段落/换行分割
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 按句子标点分割
            String[] parts = CHINESE_SENTENCE_END.split(trimmed);
            for (String part : parts) {
                String s = part.trim();
                if (!s.isEmpty()) {
                    sentences.add(s);
                }
            }
        }
        return sentences;
    }

    /** 对超长文本进行句子级切分 */
    private List<RawChunk> splitLongText(
            String text,
            int maxTokens,
            int overlapTokens,
            List<int[]> charPageMap,
            int textOffset) {
        List<RawChunk> result = new ArrayList<>();
        List<String> sentences = splitSentences(text);
        StringBuilder current = new StringBuilder();
        int currentLen = 0;
        int chunkOffset = textOffset;

        int maxChars = maxTokens * 3;
        int overlapChars = overlapTokens * 3;

        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 > maxChars && current.length() > 0) {
                String chunkText = current.toString().trim();
                int startPage = lookupPage(charPageMap, chunkOffset);
                int endPage =
                        lookupPage(
                                charPageMap,
                                Math.min(
                                        chunkOffset + current.length() - 1,
                                        charPageMap.size() - 1));
                result.add(new RawChunk(chunkText, startPage, endPage));

                if (overlapChars > 0 && current.length() > overlapChars) {
                    String overlapText = current.substring(current.length() - overlapChars);
                    chunkOffset = chunkOffset + current.length() - overlapChars;
                    current = new StringBuilder(overlapText);
                    current.append(" ").append(sentence);
                } else {
                    chunkOffset = chunkOffset + current.length();
                    current = new StringBuilder(sentence);
                }
            } else {
                if (!current.isEmpty()) {
                    current.append(" ");
                }
                current.append(sentence);
            }
        }

        if (current.length() > 0) {
            String chunkText = current.toString().trim();
            if (!chunkText.isEmpty()) {
                int startPage = lookupPage(charPageMap, chunkOffset);
                int endPage =
                        lookupPage(
                                charPageMap,
                                Math.min(
                                        chunkOffset + current.length() - 1,
                                        charPageMap.size() - 1));
                result.add(new RawChunk(chunkText, startPage, endPage));
            }
        }

        return result;
    }

    /** 强制字符切分（带页码追踪） */
    private List<RawChunk> forceSplitWithPages(
            String text, int maxChars, int overlapChars, List<int[]> charPageMap) {
        List<RawChunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                int startPage = lookupPage(charPageMap, start);
                int endPage = lookupPage(charPageMap, Math.min(end - 1, charPageMap.size() - 1));
                result.add(new RawChunk(chunk, startPage, endPage));
            }
            start = end - overlapChars;
            if (start >= text.length()) {
                break;
            }
        }
        return result;
    }

    /** 根据字符位置查找页码 */
    private int lookupPage(List<int[]> charPageMap, int charIndex) {
        if (charPageMap == null || charPageMap.isEmpty()) {
            return 1;
        }
        int idx = Math.max(0, Math.min(charIndex, charPageMap.size() - 1));
        return charPageMap.get(idx)[1];
    }

    /** 查找重叠起始位置 */
    private int findOverlapStart(ChunkWindow window) {
        int overlapChars =
                Math.min(window.overlapTokens() * 3, window.chunkEnd() - window.chunkStart());
        return window.chunkEnd() - overlapChars;
    }

    /** 构建分块对象，附加权限元数据 */
    private DocumentChunk buildChunk(
            Document document, int chunkIndex, int startPage, int endPage, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkKey(UUID.randomUUID().toString());
        chunk.setDocumentId(document.getId());
        chunk.setDocumentKey(document.getDocumentKey());
        chunk.setChunkIndex(chunkIndex);
        chunk.setStartPage(startPage);
        chunk.setEndPage(endPage);
        chunk.setContent(sanitizeContent(content));
        chunk.setTokenCount(estimateTokenCount(content));

        // 附加权限元数据（冗余到分块级别，用于 Milvus 过滤）
        chunk.setDepartmentId(document.getDepartmentId());
        chunk.setVisibility(
                document.getVisibility() != null
                        ? document.getVisibility().name()
                        : DocumentVisibilityEnum.INTERNAL.name());
        chunk.setAllowedRoles(document.getAllowedRoles());
        chunk.setOwnerId(document.getOwnerId());

        // 附加文档展示信息（冗余到分块级别，用于 Milvus 元数据展示）
        chunk.setDocumentName(document.getFileName());
        chunk.setFileType(document.getFileType());
        chunk.setTags(document.getTags());
        chunk.setCategory(document.getCategory());

        return chunk;
    }

    /**
     * 估算文本 token 数
     *
     * <p>中文约 1.5 字/token，英文约 4 字符/token，数字约 2 字符/token。
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int digitChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (Character.isDigit(c)) {
                digitChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 1.5)
                + (int) Math.ceil(digitChars / 2.0)
                + (int) Math.ceil(otherChars / 4.0);
    }

    /**
     * 清理文本中的控制字符，避免 JSON 序列化失败
     *
     * <p>保留换行符(\n)、回车符(\r)、制表符(\t)，移除其他 ASCII 控制字符(0x00-0x1F, 0x7F)。
     */
    private String sanitizeContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= ' ') {
                sb.append(c);
            }
            // 跳过其他控制字符(0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F, 0x7F)
        }
        return sb.toString();
    }

    /**
     * 按页合并文本，同时记录每个字符归属的页码，供下游 chunk 定位 startPage/endPage。
     *
     * <p>FIXED_SIZE / RECURSIVE / SENTENCE / PARAGRAPH 四种策略共用此合并骨架；PAGE 不合并。
     *
     * @param pages 带页码的文本段列表（已由 {@link #chunkDocument} 过滤空白页并按 1-based 编号）
     * @param separator 页与页之间插入的分隔符：段落/固定/递归用 {@code \n\n}，句子级用 {@code \n}
     * @return 合并文本 + 字符→页码映射（{@code charPageMap.get(i)[1]} 即位置 i 的页码）
     */
    private MergedPages mergePages(List<PageText> pages, String separator) {
        StringBuilder merged = new StringBuilder();
        List<int[]> charPageMap = new ArrayList<>();
        for (PageText pt : pages) {
            if (!merged.isEmpty()) {
                merged.append(separator);
            }
            int pageStart = merged.length();
            merged.append(pt.text());
            for (int i = pageStart; i < merged.length(); i++) {
                charPageMap.add(new int[] {i, pt.pageNumber()});
            }
        }
        return new MergedPages(merged.toString(), charPageMap);
    }

    /** 页面合并中间产物：整体文本 + 每字符页码映射，供下游 chunk 使用。 */
    private record MergedPages(String text, List<int[]> charPageMap) {}

    /** 带页码信息的文本段 */
    private record PageText(String text, int pageNumber) {}

    /** 带页码信息的分块中间表示 */
    private record RawChunk(String content, int startPage, int endPage) {}
}
