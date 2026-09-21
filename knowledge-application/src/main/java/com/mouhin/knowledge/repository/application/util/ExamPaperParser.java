package com.mouhin.knowledge.repository.application.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import com.mouhin.knowledge.repository.domain.service.ExamBlankCounter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 试卷 Markdown 解析器
 *
 * <p>将 AI 生成的 Markdown 格式试卷解析为结构化 JSON，供在线做题 UI 使用。 支持六大内核题型：单选题、多选题、判断题、填空题、简答题、论述题；同时兼容
 * 「题型分布方案」里的自定义学科标签（如 阅读理解、看图写话、实验与简答题、综合解答题）。
 *
 * <p>解析以「大题分节」为单位：先按大题标题（{@code ## 一、<标签>（…）}）把试卷切成若干 section， 再把每个 section
 * 映射回题型分布方案中的内核类型与分值，保证考试端渲染的 题型分布 == 出卷方案 == 试卷，三者一致。当方案缺省时回退到关键词启发式判定。
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
@Slf4j
public final class ExamPaperParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 六大内核题型 → 枚举 key */
    private static final Map<String, String> SECTION_TYPE_MAP = new LinkedHashMap<>();

    /** 匹配大题标题（泛化）：{@code #~#### 一、<任意标签>（…）} 或无括号形式。 group(1)=中文序号，group(2)=标题正文（含可能的括注分值）。 */
    private static final Pattern SECTION_PATTERN =
            Pattern.compile("^#{1,4}\\s*[一二三四五六七八九十]+\s*[、.．]\\s*(.+?)\\s*$");

    /** 匹配"每题X分" */
    private static final Pattern SCORE_PATTERN = Pattern.compile("每题\\s*(\\d+)\\s*分");

    /** 匹配"共X分"（大题总分） */
    private static final Pattern SECTION_TOTAL_GONG_PATTERN = Pattern.compile("共\\s*(\\d+)\\s*分");

    /** 匹配独立"X分"（无每题/共前缀的大题总分，如「看图写话（14分）」剥离后为 "14分"） */
    private static final Pattern SECTION_TOTAL_BARE_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*分");

    /** 匹配小题分值标记：（5分）/（本题 3 分）/【2分】，通常紧跟在题干末尾 */
    private static final Pattern QUESTION_SCORE_PATTERN =
            Pattern.compile("[（(【]\\s*(?:本题)?\\s*(\\d+)\\s*分\\s*[）)】]");

    /** 小题分值标记的整段（含前后空白），用于从正文/选项中剥离 */
    private static final Pattern QUESTION_SCORE_STRIP =
            Pattern.compile("\\s*[（(【]\\s*(?:本题)?\\s*\\d+\\s*分\\s*[）)】]\\s*");

    /** 匹配单个选项：A. xxx 或 A、xxx 或 **A.** xxx */
    private static final Pattern SINGLE_OPTION_PATTERN = Pattern.compile("([A-Da-d])\\s*[.、．]\\s*");

    /** 匹配考试时长：**考试时间：XX分钟** 或 考试时间：XX分钟 */
    private static final Pattern DURATION_PATTERN = Pattern.compile("考试时间[：:]\\s*(\\d+)\\s*分钟?");

    /** 无方案时：关键词 → 内核题型 的启发式映射（按声明顺序优先匹配，故 多选/判断 在前） */
    private static final List<String[]> KEYWORD_TYPE_RULES =
            List.of(
                    new String[] {"多选", "MULTI_CHOICE"},
                    new String[] {"单选", "SINGLE_CHOICE"},
                    new String[] {"选择", "SINGLE_CHOICE"},
                    new String[] {"听力", "SINGLE_CHOICE"},
                    new String[] {"口语", "SINGLE_CHOICE"},
                    new String[] {"判断", "TRUE_FALSE"},
                    new String[] {"对错", "TRUE_FALSE"},
                    new String[] {"填空", "FILL_BLANK"},
                    new String[] {"补写", "FILL_BLANK"},
                    new String[] {"默写", "FILL_BLANK"},
                    new String[] {"词", "FILL_BLANK"},
                    new String[] {"完形", "FILL_BLANK"},
                    new String[] {"论述", "ESSAY"},
                    new String[] {"作文", "ESSAY"},
                    new String[] {"写作", "ESSAY"},
                    new String[] {"写话", "ESSAY"},
                    new String[] {"表达", "ESSAY"},
                    new String[] {"解答", "ESSAY"},
                    new String[] {"计算", "SHORT_ANSWER"},
                    new String[] {"应用", "SHORT_ANSWER"},
                    new String[] {"综合", "SHORT_ANSWER"},
                    new String[] {"实验", "SHORT_ANSWER"},
                    new String[] {"探究", "SHORT_ANSWER"},
                    new String[] {"操作", "SHORT_ANSWER"},
                    new String[] {"阅读", "SHORT_ANSWER"},
                    new String[] {"理解", "SHORT_ANSWER"},
                    new String[] {"材料", "SHORT_ANSWER"},
                    new String[] {"简答", "SHORT_ANSWER"},
                    new String[] {"问答", "SHORT_ANSWER"},
                    new String[] {"分析", "SHORT_ANSWER"});

    static {
        SECTION_TYPE_MAP.put("单选题", "SINGLE_CHOICE");
        SECTION_TYPE_MAP.put("多选题", "MULTI_CHOICE");
        SECTION_TYPE_MAP.put("判断题", "TRUE_FALSE");
        SECTION_TYPE_MAP.put("填空题", "FILL_BLANK");
        SECTION_TYPE_MAP.put("简答题", "SHORT_ANSWER");
        SECTION_TYPE_MAP.put("论述题", "ESSAY");
    }

    private ExamPaperParser() {}

    /**
     * 从试卷 Markdown 中解析考试时长（分钟）
     *
     * @param examPaper 试卷 Markdown 内容
     * @return 时长（分钟），未找到返回 null
     */
    public static Integer parseDuration(String examPaper) {
        if (examPaper == null || examPaper.isBlank()) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(examPaper);
        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            if (minutes > 0 && minutes <= 600) {
                return minutes;
            }
        }
        return null;
    }

    /** 解析试卷 Markdown 为结构化 JSON 字符串（无方案，回退关键词判定） */
    public static String parseToJson(String examPaper) {
        return toJsonString(parse(examPaper, null));
    }

    /**
     * 解析试卷 Markdown 为结构化 JSON 字符串（带题型分布方案）
     *
     * @param examPaper 试卷 Markdown
     * @param planJson 题型分布方案 JSON（可为 null）
     * @return 结构化题目 JSON
     */
    public static String parseToJson(String examPaper, String planJson) {
        return toJsonString(parse(examPaper, readPlan(planJson)));
    }

    private static String toJsonString(List<Map<String, Object>> questions) {
        try {
            return OBJECT_MAPPER.writeValueAsString(questions);
        } catch (JsonProcessingException e) {
            log.error("序列化题目 JSON 失败", e);
            return "[]";
        }
    }

    /** 反序列化题型分布方案；失败返回 null（调用方据此回退）。 */
    public static ExamPlan readPlan(String planJson) {
        if (planJson == null || planJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(planJson, ExamPlan.class);
        } catch (Exception e) {
            log.warn("题型分布方案解析失败，考试端回退到试卷关键词判定: {}", e.getMessage());
            return null;
        }
    }

    /** 解析试卷 Markdown 为结构化题目列表（无方案） */
    public static List<Map<String, Object>> parse(String examPaper) {
        return parse(examPaper, null);
    }

    /**
     * 解析试卷 Markdown 为结构化题目列表。
     *
     * @param examPaper 试卷 Markdown
     * @param plan 题型分布方案（可为 null；存在时以其为题型/分值真源）
     */
    public static List<Map<String, Object>> parse(String examPaper, ExamPlan plan) {
        List<Map<String, Object>> questions = new ArrayList<>();
        if (examPaper == null || examPaper.isBlank()) {
            return questions;
        }
        String[] lines = examPaper.split("\\n");

        List<Section> sections = splitSections(lines);
        boolean hasNamedSection = sections.stream().anyMatch(s -> s.name != null);
        if (!hasNamedSection) {
            // 无大题标题：回退到逐行松散解析（兼容早期无分节试卷）
            return parseLoose(lines, plan);
        }

        List<TypePlan> planTypes =
                plan != null && plan.getTypes() != null ? plan.getTypes() : new ArrayList<>();
        int sectionCursor = 0;
        int globalIndex = 0;

        for (Section sec : sections) {
            if (sec.name == null) {
                // 标题前的卷头，忽略其中的零散题（一般无题）
                continue;
            }
            TypePlan matched = matchPlanType(planTypes, sec.name, sectionCursor);
            if (matched != null) {
                sectionCursor++;
            }
            String kernel = resolveKernel(sec.name, matched, sec.rawHeading);
            String displayLabel =
                    matched != null && notBlank(matched.getLabel()) ? matched.getLabel() : sec.name;
            int defaultScore = resolveDefaultScore(sec, matched, kernel);

            List<Map<String, Object>> secQuestions =
                    collectQuestions(
                            sec.lines, sec.scoreInfo, kernel, displayLabel, defaultScore, matched);
            for (Map<String, Object> q : secQuestions) {
                globalIndex++;
                q.put("index", globalIndex);
                if (q.get("number") == null) {
                    q.put("number", globalIndex);
                }
                questions.add(q);
            }
        }
        return questions;
    }

    // ==================== 分节 ====================

    /** 一个"大题"分节 */
    private static final class Section {
        final String name;
        final String rawHeading;
        final String scoreInfo;
        final List<String> lines = new ArrayList<>();

        Section(String name, String rawHeading, String scoreInfo) {
            this.name = name;
            this.rawHeading = rawHeading;
            this.scoreInfo = scoreInfo;
        }
    }

    /** 按大题标题把整卷切成 section；第一段（标题前）name 为 null。 */
    private static List<Section> splitSections(String[] lines) {
        List<Section> sections = new ArrayList<>();
        Section current = new Section(null, null, null);
        sections.add(current);
        for (String raw : lines) {
            String line = raw.trim();
            Matcher m = SECTION_PATTERN.matcher(line);
            if (m.matches()) {
                String head = m.group(1);
                String name = extractSectionName(head);
                String scoreInfo = extractScoreInfo(head);
                current = new Section(name, head, scoreInfo);
                sections.add(current);
            } else {
                current.lines.add(raw);
            }
        }
        return sections;
    }

    /** 从标题正文里取题型名（第一个括号/空格之前），如 "阅读理解（共17分）" → "阅读理解"。 */
    private static String extractSectionName(String head) {
        int idx = indexOfFirstBracket(head);
        String name = idx >= 0 ? head.substring(0, idx) : head;
        return name.trim();
    }

    /** 收集标题正文里所有括号内容拼成分值信息串，便于后续正则。 */
    private static String extractScoreInfo(String head) {
        Matcher bm = Pattern.compile("[（(【]([^）)】]*)[）)】]").matcher(head);
        StringBuilder sb = new StringBuilder();
        while (bm.find()) {
            sb.append(bm.group(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private static int indexOfFirstBracket(String s) {
        int min = -1;
        for (char c : new char[] {'（', '(', '【'}) {
            int i = s.indexOf(c);
            if (i >= 0 && (min < 0 || i < min)) {
                min = i;
            }
        }
        return min;
    }

    // ==================== 题型/分值决策 ====================

    /**
     * 按标签匹配方案中的 TypePlan。匹配路径（按优先级）：
     *
     * <ol>
     *   <li>{@code baseName} 相等：剥离方案 label 与试卷标题里的括注（如「（单选题）」）， 让「我会选（单选题）」与试卷标题「我会选」对齐；
     *   <li>试卷标题关键词精确命中某内核题型中文名，且方案中同 key 项存在；
     *   <li>顺序兜底：section 数与 plan 数一一对应且 label 的 baseName 相同；或 cursor 处仍有未匹配项且试卷端关键词回落到同
     *       key（避免整卷全部走关键词导致答案漂移）。
     * </ol>
     */
    private static TypePlan matchPlanType(List<TypePlan> planTypes, String name, int cursor) {
        if (planTypes.isEmpty()) {
            return null;
        }
        String norm = baseName(normalizeLabel(name));
        if (!norm.isEmpty()) {
            for (TypePlan t : planTypes) {
                if (t.getLabel() == null || t.getLabel().isEmpty()) {
                    continue;
                }
                if (baseName(normalizeLabel(t.getLabel())).equals(norm)) {
                    return t;
                }
            }
        }
        // 关键词精确等于内核中文名
        String kernelDirect = SECTION_TYPE_MAP.get(name);
        if (kernelDirect != null) {
            for (TypePlan t : planTypes) {
                if (kernelDirect.equals(t.getKey())) {
                    return t;
                }
            }
        }
        // 顺序兜底：section 数与 plan 数一致时按序对应
        if (cursor < planTypes.size()) {
            TypePlan byOrder = planTypes.get(cursor);
            String byOrderBase = baseName(normalizeLabel(byOrder.getLabel()));
            if (byOrderBase.equals(norm)) {
                return byOrder;
            }
        }
        return null;
    }

    /** 解析大题的内核题型 key：方案优先，其次关键词启发式，最后默认简答题。 */
    private static String resolveKernel(String name, TypePlan matched) {
        return resolveKernel(name, matched, null);
    }

    /**
     * 解析大题的内核题型 key：方案优先，其次关键词启发式，最后默认简答题。
     *
     * @param name 剥离括注后的大题标题名
     * @param matched 匹配到的方案题型（可为 null）
     * @param keywordHint 关键词启发式的额外扫描文本，通常传入含括注的完整标题， 使「我会选（单选题）」这类被剥离的类型提示仍能被识别；为 null 时仅扫描 name
     */
    private static String resolveKernel(String name, TypePlan matched, String keywordHint) {
        if (matched != null && notBlank(matched.getKey())) {
            return matched.getKey().toUpperCase();
        }
        String exact = SECTION_TYPE_MAP.get(name);
        if (exact != null) {
            return exact;
        }
        String scan = keywordHint != null && !keywordHint.isBlank() ? keywordHint : name;
        String norm = normalizeLabel(scan);
        for (String[] rule : KEYWORD_TYPE_RULES) {
            if (norm.contains(normalizeLabel(rule[0]))) {
                return rule[1];
            }
        }
        return "SHORT_ANSWER";
    }

    /**
     * 计算大题默认分值：每题X分 → X；共X分/(X分) → 均分（此处先给 0，交由 collectQuestions 按题数分摊）； 方案有每题数组且与题数吻合时优先方案值。返回 0
     * 表示"需在知道题数后再分摊"。
     */
    private static int resolveDefaultScore(Section sec, TypePlan matched, String kernel) {
        if (sec.scoreInfo != null) {
            Matcher perQ = SCORE_PATTERN.matcher(sec.scoreInfo);
            if (perQ.find()) {
                return Integer.parseInt(perQ.group(1));
            }
        }
        return 0;
    }

    /** 在一个 section 的行集合里抽取题目，填充题型/分值/内容/选项/标签。 */
    private static List<Map<String, Object>> collectQuestions(
            List<String> secLines,
            String scoreInfo,
            String kernel,
            String label,
            int perQuestionUniform,
            TypePlan matched) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 先按题号切成块
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String raw : secLines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                if (current != null) {
                    current.add(raw);
                }
                continue;
            }
            if (isQuestionStart(line)) {
                current = new ArrayList<>();
                blocks.add(current);
                current.add(raw);
            } else if (current != null) {
                current.add(raw);
            }
            // current==null 的散行（题干说明/材料/小标题（一））忽略
        }

        int n = blocks.size();
        // 大题总分：方案 subtotal 优先，其次标题里的"共X分/（X分）"
        Integer sectionTotal;
        if (matched != null && matched.subtotal() > 0) {
            sectionTotal = matched.subtotal();
        } else {
            sectionTotal = parseSectionTotal(scoreInfo);
        }
        // 每题分值判定：区分"有方案(整题真源)"与"无方案(内联优先)"
        boolean havePlanScores =
                matched != null
                        && matched.getPerQuestion() != null
                        && matched.getPerQuestion().size() == n
                        && n > 0;
        int[] spread =
                (sectionTotal != null && sectionTotal > 0 && n > 0)
                        ? distribute(sectionTotal, n)
                        : null;

        for (int bi = 0; bi < n; bi++) {
            List<String> block = blocks.get(bi);
            Integer printedNumber = extractQuestionNumber(block.get(0).trim());
            String firstLine = removeQuestionNumber(block.get(0).trim());
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder rawOptionsBuilder = new StringBuilder();
            String extractedContent = extractContentBeforeOptions(firstLine);
            String inlineOptions = extractInlineOptions(firstLine);
            contentBuilder.append(extractedContent);
            if (!inlineOptions.isEmpty()) {
                rawOptionsBuilder.append(inlineOptions).append("\n");
            }
            boolean inOptions = !inlineOptions.isEmpty();
            for (int li = 1; li < block.size(); li++) {
                String ln = block.get(li).trim();
                if (ln.isEmpty()) {
                    continue;
                }
                if (isHorizontalRule(ln)) {
                    break;
                }
                if (startsOption(ln)) {
                    inOptions = true;
                    rawOptionsBuilder.append(stripLeadingBullet(ln)).append("\n");
                } else if (!inOptions) {
                    contentBuilder.append("\n").append(ln);
                } else {
                    rawOptionsBuilder.append(ln).append("\n");
                }
            }

            int inline = extractQuestionScore(contentBuilder.toString());
            if (inline == 0) {
                inline = extractQuestionScore(rawOptionsBuilder.toString());
            }
            int finalScore;
            if (matched != null) {
                // 有方案：整题分值以方案/大题标题为真源，忽略题干子问（X分）
                if (havePlanScores) {
                    finalScore = matched.getPerQuestion().get(bi);
                } else if (perQuestionUniform > 0) {
                    finalScore = perQuestionUniform;
                } else if (spread != null) {
                    finalScore = spread[bi];
                } else {
                    finalScore = inline > 0 ? inline : 5;
                }
            } else {
                // 无方案（老数据）：内联整题分值优先，其次每题X分，再次总分均分
                if (inline > 0) {
                    finalScore = inline;
                } else if (perQuestionUniform > 0) {
                    finalScore = perQuestionUniform;
                } else if (spread != null) {
                    finalScore = spread[bi];
                } else {
                    finalScore = 5;
                }
            }

            Map<String, Object> q = new LinkedHashMap<>();
            q.put("type", kernel);
            q.put("sectionLabel", label);
            q.put("number", printedNumber);
            q.put("maxScore", finalScore);
            String content = cleanContent(contentBuilder.toString());
            q.put("content", content);
            q.put("blankCount", ExamBlankCounter.count(content));
            if ("SINGLE_CHOICE".equals(kernel) || "MULTI_CHOICE".equals(kernel)) {
                q.put("options", parseOptions(rawOptionsBuilder.toString()));
            }
            result.add(q);
        }
        return result;
    }

    /** 松散解析：无大题标题时按题号顺序推进，从题型分布方案的扁平化结果还原每题题型/分值。 */
    private static List<Map<String, Object>> parseLoose(String[] lines, ExamPlan plan) {
        List<Map<String, Object>> questions = new ArrayList<>();
        List<String[]> flatTypes = flattenPlanTypes(plan);
        List<String> labels = new ArrayList<>();
        List<Integer> planScores = new ArrayList<>();
        if (plan != null) {
            for (TypePlan t : safeTypes(plan)) {
                int c =
                        Math.max(
                                t.getCount(),
                                t.getPerQuestion() != null ? t.getPerQuestion().size() : 0);
                for (int j = 0; j < c; j++) {
                    labels.add(notBlank(t.getLabel()) ? t.getLabel() : t.getKey());
                    planScores.add(
                            t.getPerQuestion() != null && j < t.getPerQuestion().size()
                                    ? t.getPerQuestion().get(j)
                                    : 0);
                }
            }
        }
        int globalIndex = 0;
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                i++;
                continue;
            }
            if (isQuestionStart(line)) {
                int end = i + 1;
                while (end < lines.length) {
                    String nl = lines[end].trim();
                    if (isQuestionStart(nl)) {
                        break;
                    }
                    end++;
                }
                globalIndex++;
                int pos = globalIndex - 1;
                String kernel = pos < flatTypes.size() ? flatTypes.get(pos)[1] : null;
                String label = pos < labels.size() ? labels.get(pos) : null;
                int score = pos < planScores.size() ? planScores.get(pos) : 5;
                List<String> block = Arrays.asList(lines).subList(i, end);
                questions.add(buildOneQuestion(block, kernel, label, score, globalIndex));
                i = end;
                continue;
            }
            i++;
        }
        return questions;
    }

    /** 把方案展开成 [label, kernel] 数组，按题型顺序每题一项（count 与 perQuestion 取较大者）。 */
    private static List<String[]> flattenPlanTypes(ExamPlan plan) {
        List<String[]> list = new ArrayList<>();
        if (plan == null) {
            return list;
        }
        for (TypePlan t : safeTypes(plan)) {
            String kernel =
                    notBlank(t.getKey())
                            ? t.getKey().toUpperCase()
                            : resolveKernel(t.getLabel(), t);
            int c =
                    Math.max(
                            t.getCount(),
                            t.getPerQuestion() != null ? t.getPerQuestion().size() : 0);
            for (int j = 0; j < c; j++) {
                list.add(new String[] {t.getLabel(), kernel});
            }
        }
        return list;
    }

    private static Map<String, Object> buildOneQuestion(
            List<String> block, String type, String label, int defaultScore, int index) {
        Integer printedNumber = extractQuestionNumber(block.get(0).trim());
        String firstLine = removeQuestionNumber(block.get(0).trim());
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder rawOptionsBuilder = new StringBuilder();
        contentBuilder.append(extractContentBeforeOptions(firstLine));
        String inlineOptions = extractInlineOptions(firstLine);
        if (!inlineOptions.isEmpty()) {
            rawOptionsBuilder.append(inlineOptions).append("\n");
        }
        boolean inOptions = !inlineOptions.isEmpty();
        for (int li = 1; li < block.size(); li++) {
            String ln = block.get(li).trim();
            if (ln.isEmpty()) {
                continue;
            }
            if (isHorizontalRule(ln)) {
                break;
            }
            if (startsOption(ln)) {
                inOptions = true;
                rawOptionsBuilder.append(stripLeadingBullet(ln)).append("\n");
            } else if (!inOptions) {
                contentBuilder.append("\n").append(ln);
            } else {
                rawOptionsBuilder.append(ln).append("\n");
            }
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("index", index);
        q.put("number", printedNumber != null ? printedNumber : index);
        String kernel = type != null ? type : resolveKernel(label != null ? label : "", null);
        q.put("type", kernel);
        if (label != null) {
            q.put("sectionLabel", label);
        }
        int stem = extractQuestionScore(contentBuilder.toString());
        if (stem == 0) {
            stem = extractQuestionScore(rawOptionsBuilder.toString());
        }
        q.put("maxScore", stem > 0 ? stem : defaultScore);
        String content = cleanContent(contentBuilder.toString());
        q.put("content", content);
        q.put("blankCount", ExamBlankCounter.count(content));
        if ("SINGLE_CHOICE".equals(kernel) || "MULTI_CHOICE".equals(kernel)) {
            q.put("options", parseOptions(rawOptionsBuilder.toString()));
        }
        return q;
    }

    private static List<TypePlan> safeTypes(ExamPlan plan) {
        return plan != null && plan.getTypes() != null ? plan.getTypes() : new ArrayList<>();
    }

    /** 解析大题标题里的"共X分"或独立"（X分）"。 */
    private static Integer parseSectionTotal(String head) {
        if (head == null) {
            return null;
        }
        Matcher g = SECTION_TOTAL_GONG_PATTERN.matcher(head);
        if (g.find()) {
            return Integer.parseInt(g.group(1));
        }
        Matcher b = SECTION_TOTAL_BARE_PATTERN.matcher(head.trim());
        if (b.find()) {
            return Integer.parseInt(b.group(1));
        }
        return null;
    }

    /** 把 total 以最大余数法分成 n 份（每份>=1，和==total）。 */
    private static int[] distribute(int total, int n) {
        int[] r = new int[n];
        if (n <= 0) {
            return r;
        }
        int base = total / n;
        int rem = total % n;
        for (int i = 0; i < n; i++) {
            r[i] = base;
        }
        for (int i = 0; i < rem; i++) {
            r[i] += 1;
        }
        if (base == 0) {
            // 保证每题至少 1 分（total<n 时降级）
            for (int i = 0; i < n; i++) {
                if (r[i] == 0) {
                    r[i] = 1;
                }
            }
        }
        return r;
    }

    // ==================== 工具 ====================

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 归一化题型标签：去空白、去全角空格、去尾部"题"字差异容错。 */
    private static String normalizeLabel(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s　]", "").trim();
    }

    /** 取标签的基础名：剥离首个括号起的括注（如「我会选（单选题）」→「我会选」）， 使方案 label 与试卷标题在题型对齐时忽略类型标注差异。 */
    private static String baseName(String s) {
        if (s == null) {
            return "";
        }
        int idx = indexOfFirstBracket(s);
        return (idx >= 0 ? s.substring(0, idx) : s).trim();
    }

    /** 判断一行是否是新题目的开始 */
    private static boolean isQuestionStart(String line) {
        return line.matches("^\\*{0,2}\\d+[.、．]\\s*.+");
    }

    /** 判断一行是否为水平分隔线（--- / *** / ___） */
    private static boolean isHorizontalRule(String line) {
        return line != null && line.trim().matches("^[-*_]{3,}$");
    }

    /** 判断一行是否以选项标记开头（允许前置 markdown 列表符号与加粗符号） */
    private static boolean startsOption(String line) {
        String cleaned = stripLeadingBullet(line).replaceAll("^\\*{0,2}", "").trim();
        return SINGLE_OPTION_PATTERN.matcher(cleaned).lookingAt();
    }

    /** 去掉行首的 markdown 列表符号（- / * / + 加空格） */
    private static String stripLeadingBullet(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceFirst("^\\s*[-*+]\\s+", "").trim();
    }

    /** 匹配题目行首的印刷题号（可选前置加粗 + 1~3 位数字 + 分隔符 + 可选后置加粗），group(1)=题号 */
    private static final Pattern QUESTION_NUMBER_STRIP =
            Pattern.compile("^\\*{0,2}(\\d{1,3})\\s*[.、．]\\s*\\*{0,2}");

    /** 移除题目行中的序号 */
    private static String removeQuestionNumber(String line) {
        return line.replaceFirst("^\\*{0,2}\\d+[.、．]\\s*\\*{0,2}", "").trim();
    }

    /**
     * 提取题目行首的印刷题号；无法识别时返回 null（调用方按位置序号回退）。
     *
     * @param line 题目块首行（原始，含题号标记）
     * @return 印刷题号，或 null
     */
    private static Integer extractQuestionNumber(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = QUESTION_NUMBER_STRIP.matcher(line.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 提取题目内容中选项之前的部分 */
    private static String extractContentBeforeOptions(String line) {
        String cleaned = line.replaceAll("\\*{0,2}", "");
        Matcher matcher = SINGLE_OPTION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String before = cleaned.substring(0, matcher.start()).trim();
            return before.isEmpty() ? cleaned : before;
        }
        return line;
    }

    /** 提取行内选项部分（如 "A. xxx B. xxx C. xxx"） */
    private static String extractInlineOptions(String line) {
        String cleaned = line.replaceAll("\\*{0,2}", "");
        Matcher matcher = SINGLE_OPTION_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            return cleaned.substring(matcher.start());
        }
        return "";
    }

    /** 提取题干/选项中显式标注的小题分值（如（5分）/（本题 3 分）），无则返回 0。 */
    private static int extractQuestionScore(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher matcher = QUESTION_SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int score = Integer.parseInt(matcher.group(1));
                if (score > 0) {
                    return score;
                }
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** 清理题目内容（移除尾部的选项标记、小题分值标记与 markdown 格式符号） */
    private static String cleanContent(String content) {
        return QUESTION_SCORE_STRIP
                .matcher(content)
                .replaceAll(" ")
                .replaceAll("\\*{1,2}", "")
                .replaceAll("\\s+$", "")
                .trim();
    }

    /**
     * 解析选项文本为选项列表
     *
     * <p>能处理：每个选项一行 / 所有选项在一行 / 选项间多个空格。
     */
    private static List<Map<String, String>> parseOptions(String optionsText) {
        List<Map<String, String>> options = new ArrayList<>();
        if (optionsText == null || optionsText.isBlank()) {
            return options;
        }

        String normalized =
                optionsText
                        .replaceAll("\\n", " ")
                        .replaceAll("\\*{1,2}", "")
                        .replaceAll("　", " ")
                        .replaceAll("\\s+", " ")
                        .trim();

        Pattern optionPattern =
                Pattern.compile("([A-Da-d])\\s*[.、．]\\s*(.*?)(?=\\s+[A-Da-d]\\s*[.、．]|$)");
        Matcher matcher = optionPattern.matcher(normalized);

        while (matcher.find()) {
            String key = matcher.group(1).toUpperCase();
            String value = QUESTION_SCORE_STRIP.matcher(matcher.group(2)).replaceAll(" ").trim();
            if (!value.isEmpty()) {
                Map<String, String> option = new LinkedHashMap<>();
                option.put("key", key);
                option.put("value", value);
                options.add(option);
            }
        }

        if (options.isEmpty()) {
            String[] optLines = optionsText.split("\\n");
            for (String line : optLines) {
                String trimmed = line.trim().replaceAll("\\*{1,2}", "");
                Matcher lineMatcher = SINGLE_OPTION_PATTERN.matcher(trimmed);
                if (lineMatcher.find()) {
                    String key = lineMatcher.group(1).toUpperCase();
                    String value =
                            QUESTION_SCORE_STRIP
                                    .matcher(trimmed.substring(lineMatcher.end()))
                                    .replaceAll(" ")
                                    .trim();
                    if (!value.isEmpty()) {
                        Map<String, String> option = new LinkedHashMap<>();
                        option.put("key", key);
                        option.put("value", value);
                        options.add(option);
                    }
                }
            }
        }

        return options;
    }
}
