package com.mouhin.knowledge.repository.domain.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 「出处 / 位置类」记忆题确定性检测器（domain 层纯函数，不依赖大模型）。
 *
 * <p>语文、英语等偏理解运用的科目里，「某知识在第几单元 / 第几课 / 第几页 / 哪一章 / 出自哪篇」这类
 * 题只考教材编排位置、不考内容本身，属低质量命题。本检测器以规则命中这类题干，用于两处确定性后处理：
 *
 * <ol>
 *   <li>内容审核阶段（{@code exam-reviewer}）：命中即把分数压到阈值下、驱动 writer 打回重写；
 *   <li>发布契约门禁（{@link ExamContractValidator}）：命中即产出 issue，强制人工校对、绝不自动发布。
 * </ol>
 *
 * <p>为压制误报，仅覆盖教材编排类名词（单元 / 课 / 页 / 册 / 章 / 节 / 章节 / 部分），刻意不含 「段 /
 * 篇」等常被用于指代试卷内短文段落的词；且必须与序数（第…）或疑问（哪…）搭配才判为位置题。
 *
 * @author Knowledge-Repository
 * @date 2026-09-20
 */
public final class ExamMetaQuestionDetector {

    private ExamMetaQuestionDetector() {}

    /** 教材编排位置名词：单元 / 课 / 页 / 册 / 章节 / 章 / 节 / 部分（不含 段/篇 以降误报）。 */
    private static final String LOC = "(?:单元|课文|课|页|册|章节|章|节|部分)";

    /** 序数填空 / 数字 / 中文数字位：几 / 阿拉伯数字 / 中文数字 / 空括号（填空式的「第（ ）单元」）。 */
    private static final String ORD = "(?:几|[0-9]+|[一二三四五六七八九十百]+|[（(][\\s]*[）)])";

    /** 确定性规则集：序数 / 疑问 + 教材位置名词，命中即判为出处/位置类记忆题。 */
    private static final Pattern[] META_PATTERNS = {
        // 哪（一）（个）单元 / 哪一课 / 哪一页 / 哪一章
        Pattern.compile("哪[一]?(?:个)?" + LOC),
        // 第几单元 / 第3课 / 第五页 / 第（ ）单元
        Pattern.compile("第" + ORD + LOC),
        // 出自 / 选自 / 来自 …… 哪 / 几 …… 单元|篇|课
        Pattern.compile(
                "(?:出自|选自|来源于|来自|摘自|选取自)[^。？?]{0,8}(?:哪|几)[^。？?]{0,4}(?:单元|课|页|册|章节|章|节|篇)"),
        // 教材 / 课本 / 课文 / 书中 + 第X页|单元
        Pattern.compile("(?:教材|课本|教科书|书中|书上|课本里|课文)[^。？?]{0,2}第" + ORD + "(?:页|单元|课|章|节|册)"),
        // 属于 / 所在 + 哪个单元|章节
        Pattern.compile("(?:属于|所在)[^。？?]{0,4}(?:单元|课|章节|章|节|册)")
    };

    /** 命中时的标准化提示语（供审核反馈与契约 issue 复用）。 */
    public static final String ADVISORY =
            "疑似「出处/位置类」记忆题（考查教材编排位置而非内容理解），应改写为就内容实质（字音字形、词义语法、内容理解、阅读表达等）设问";

    /**
     * 判断单条题干是否为出处/位置类记忆题。
     *
     * @param stem 题干文本（可为 null / 空，均返回 false）
     * @return 命中返回 {@code true}
     */
    public static boolean isMetaRecall(String stem) {
        if (stem == null || stem.isBlank()) {
            return false;
        }
        // 压掉空白，兼容「第（ ）单元」等被排版拆开的写法（中文题干去空白不影响规则命中）
        String compact = stem.replaceAll("\\s+", "");
        for (Pattern pattern : META_PATTERNS) {
            if (pattern.matcher(compact).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从试卷 Markdown 逐题扫描，返回命中出处/位置类的题干原文片段（按出现顺序去重）。
     *
     * <p>仅识别以题号起始的行（{@code **1.**} / {@code 1.} / {@code 1、}），据此取该题所在行做判定。
     *
     * @param examPaperMd 试卷 Markdown（可为 null）
     * @return 命中题干片段列表（已 trim，保持稳定顺序）
     */
    public static List<String> scanMarkdown(String examPaperMd) {
        List<String> hits = new ArrayList<>();
        if (examPaperMd == null || examPaperMd.isBlank()) {
            return hits;
        }
        Set<String> seen = new LinkedHashSet<>();
        Pattern questionStart = Pattern.compile("^\\*{0,2}\\d{1,3}\\s*[.、．]");
        for (String raw : examPaperMd.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || !questionStart.matcher(line).find()) {
                continue;
            }
            if (isMetaRecall(line)) {
                String snippet = line.length() > 48 ? line.substring(0, 48) + "…" : line;
                if (seen.add(snippet)) {
                    hits.add(snippet);
                }
            }
        }
        return new ArrayList<>(seen);
    }
}
