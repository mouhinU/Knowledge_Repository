package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;

import java.util.*;

/**
 * 试卷分数规则引擎
 * <p>
 * 依据学段与科目确定试卷目标满分，并按题型权重、小题数量把满分拆分为整数分值方案。
 * 纯计算，无外部依赖，供出卷分值分配 Agent、试卷编写与同步出题路径共同复用。
 * </p>
 *
 * <h3>满分规则</h3>
 * <ul>
 *     <li>小学：各科 100 分；</li>
 *     <li>初中：语文/数学/外语各 120；物理 70、化学 50（合卷 120）；道德与法治 60、历史 60（合卷 120）；
 *         实验操作（物理、化学、生物）各 10、共 30；体育与健康 50；</li>
 *     <li>高中：语文/数学/外语各 150；首选（物理、历史）100；再选（思想政治、地理、化学、生物学）100。</li>
 * </ul>
 * 主题中若同时识别到合卷的两科（如"物理""化学"），则按合卷满分（两科之和）出为一张卷子。
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
public final class ScoreRuleEngine {

    /**
     * 识别不到学段/科目时的兜底满分
     */
    public static final int DEFAULT_FULL_MARK = 100;
    /**
     * 科目识别关键词表（按匹配优先级）
     */
    private static final Map<Subject, String[]> SUBJECT_KEYWORDS = new LinkedHashMap<>();

    // ==================== 学段 ====================
    /**
     * 题型权重（用于总分在各题型间的分配比例）
     */
    private static final Map<String, Integer> TYPE_WEIGHTS = new LinkedHashMap<>();

    static {
        SUBJECT_KEYWORDS.put(Subject.CHINESE, new String[]{"语文", "中文"});
        SUBJECT_KEYWORDS.put(Subject.MATH, new String[]{"数学"});
        SUBJECT_KEYWORDS.put(Subject.FOREIGN, new String[]{"英语", "外语", "英文"});
        SUBJECT_KEYWORDS.put(Subject.PHYSICS, new String[]{"物理"});
        SUBJECT_KEYWORDS.put(Subject.CHEMISTRY, new String[]{"化学"});
        SUBJECT_KEYWORDS.put(Subject.BIOLOGY, new String[]{"生物", "生命科学"});
        SUBJECT_KEYWORDS.put(Subject.MORAL_LAW, new String[]{"道德与法治", "道法", "政治", "思想政治"});
        SUBJECT_KEYWORDS.put(Subject.HISTORY, new String[]{"历史"});
        SUBJECT_KEYWORDS.put(Subject.GEOGRAPHY, new String[]{"地理"});
        SUBJECT_KEYWORDS.put(Subject.EXPERIMENT, new String[]{"实验操作", "实验"});
        SUBJECT_KEYWORDS.put(Subject.PE, new String[]{"体育", "体育与健康", "健康"});
        SUBJECT_KEYWORDS.put(Subject.SCIENCE, new String[]{"科学"});
    }

    static {
        TYPE_WEIGHTS.put("单选题", 2);
        TYPE_WEIGHTS.put("多选题", 3);
        TYPE_WEIGHTS.put("判断题", 2);
        TYPE_WEIGHTS.put("填空题", 3);
        TYPE_WEIGHTS.put("简答题", 6);
        TYPE_WEIGHTS.put("论述题", 10);
    }

    private ScoreRuleEngine() {
    }

    /**
     * 学段中文名
     */
    public static String levelLabel(SchoolLevel level) {
        return switch (level) {
            case PRIMARY -> "小学";
            case JUNIOR -> "初中";
            case SENIOR -> "高中";
            default -> "未识别";
        };
    }

    // ==================== 科目 ====================

    /**
     * 解析学段：优先使用显式指定值，否则从主题文本识别。
     *
     * @param topic  主题文本
     * @param forced 前端显式选择的学段（可空）
     * @return 学段枚举，识别不到返回 {@link SchoolLevel#UNKNOWN}
     */
    public static SchoolLevel resolveLevel(String topic, String forced) {
        SchoolLevel fromForced = parseLevelCode(forced);
        if (fromForced != SchoolLevel.UNKNOWN) {
            return fromForced;
        }
        return detectLevel(topic);
    }

    /**
     * 学段编码解析（PRIMARY / JUNIOR / SENIOR，兼容中文）。
     */
    public static SchoolLevel parseLevelCode(String code) {
        if (code == null || code.isBlank()) {
            return SchoolLevel.UNKNOWN;
        }
        String c = code.trim().toUpperCase();
        return switch (c) {
            case "PRIMARY", "小学" -> SchoolLevel.PRIMARY;
            case "JUNIOR", "初中", "JUNIOR_HIGH" -> SchoolLevel.JUNIOR;
            case "SENIOR", "高中", "SENIOR_HIGH" -> SchoolLevel.SENIOR;
            default -> SchoolLevel.UNKNOWN;
        };
    }

    /**
     * 从主题文本识别学段。
     */
    public static SchoolLevel detectLevel(String topic) {
        if (topic == null || topic.isBlank()) {
            return SchoolLevel.UNKNOWN;
        }
        if (containsAny(topic, "高中", "高考", "高三", "高二", "高一")) {
            return SchoolLevel.SENIOR;
        }
        if (containsAny(topic, "初中", "中考", "初三", "初二", "初一", "七年级", "八年级", "九年级")) {
            return SchoolLevel.JUNIOR;
        }
        if (containsAny(topic, "小学", "一年级", "二年级", "三年级", "四年级", "五年级", "六年级")) {
            return SchoolLevel.PRIMARY;
        }
        return SchoolLevel.UNKNOWN;
    }

    /**
     * 科目中文名
     */
    public static String subjectLabel(Subject subject) {
        return switch (subject) {
            case CHINESE -> "语文";
            case MATH -> "数学";
            case FOREIGN -> "外语";
            case PHYSICS -> "物理";
            case CHEMISTRY -> "化学";
            case BIOLOGY -> "生物";
            case MORAL_LAW -> "道德与法治";
            case HISTORY -> "历史";
            case GEOGRAPHY -> "地理";
            case SCIENCE -> "科学";
            case PE -> "体育与健康";
            case EXPERIMENT -> "实验操作";
            default -> "综合";
        };
    }

    /**
     * 从主题文本识别涉及的科目（可能多科，用于合卷）。
     *
     * @param topic 主题文本
     * @return 命中的科目列表（保持关键词优先级顺序），无命中返回空列表
     */
    public static List<Subject> detectSubjects(String topic) {
        List<Subject> found = new ArrayList<>();
        if (topic == null || topic.isBlank()) {
            return found;
        }
        for (Map.Entry<Subject, String[]> e : SUBJECT_KEYWORDS.entrySet()) {
            if (containsAny(topic, e.getValue()) && !found.contains(e.getKey())) {
                found.add(e.getKey());
            }
        }
        return found;
    }

    // ==================== 满分解析 ====================

    /**
     * 单科在指定学段下的满分。
     *
     * @param level   学段
     * @param subject 科目
     * @return 满分；无法判定时返回 {@link #DEFAULT_FULL_MARK}
     */
    public static int subjectFullMark(SchoolLevel level, Subject subject) {
        return switch (level) {
            case JUNIOR -> juniorFullMark(subject);
            case SENIOR -> seniorFullMark(subject);
            case PRIMARY -> 100;
            default -> 100;
        };
    }

    private static int juniorFullMark(Subject subject) {
        return switch (subject) {
            case CHINESE, MATH, FOREIGN -> 120;
            case PHYSICS -> 70;
            case CHEMISTRY -> 50;
            case MORAL_LAW, HISTORY -> 60;
            case PE -> 50;
            case EXPERIMENT -> 10;
            default -> 100;
        };
    }

    private static int seniorFullMark(Subject subject) {
        return switch (subject) {
            case CHINESE, MATH, FOREIGN -> 150;
            default -> 100;
        };
    }

    /**
     * 计算试卷目标满分。
     * <p>识别到多科时按各科满分累加（合卷一张卷）；识别不到科目时按学段兜底（小学/初中/高中默认 100）。</p>
     *
     * @param topic           主题文本
     * @param level           学段
     * @param forcedLevelCode 前端显式学段（可空）
     * @return 目标满分
     */
    public static int resolveTotalFullMark(String topic, SchoolLevel level, String forcedLevelCode) {
        SchoolLevel resolved = resolveLevel(topic, forcedLevelCode);
        List<Subject> subjects = detectSubjects(topic);
        if (subjects.isEmpty()) {
            // 未识别科目：小学/其它按 100；初高中单一常见科（语数外）倾向 120/150 但科目未知，保守用 100
            return DEFAULT_FULL_MARK;
        }
        int total = 0;
        for (Subject s : subjects) {
            total += subjectFullMark(resolved, s);
        }
        return total;
    }

    /**
     * 生成合卷结构说明（多于一科时）。
     *
     * @param topic 主题文本
     * @param level 学段
     * @return 结构说明文本；单科或无科目时返回空字符串
     */
    public static String combinedNote(String topic, SchoolLevel level) {
        List<Subject> subjects = detectSubjects(topic);
        if (subjects.size() <= 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本卷为合卷（").append(levelLabel(level)).append("），按科目满分占比组织大题：");
        List<String> parts = new ArrayList<>();
        for (Subject s : subjects) {
            parts.add(subjectLabel(s) + subjectFullMark(level, s) + "分");
        }
        sb.append(String.join(" + ", parts)).append("。");
        return sb.toString();
    }

    // ==================== 分值分配 ====================

    /**
     * 把目标满分按题型权重、再按小题拆分为整数分值方案，保证各题分值之和恰好等于满分。
     * <p>单题（总题数为 1）时该题即为满分。</p>
     *
     * @param total         目标满分
     * @param orderedCounts 题型 → 数量（保持题型出现顺序）
     * @return 分值方案；total≤0 或无题时返回空方案
     */
    public static ScoreScheme allocate(int total, LinkedHashMap<String, Integer> orderedCounts) {
        if (total <= 0 || orderedCounts == null || orderedCounts.isEmpty()) {
            return new ScoreScheme(Math.max(total, 0), List.of());
        }

        // 展开为逐题数组：记录题型、权重、目标比例
        List<String> types = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Map.Entry<String, Integer> e : orderedCounts.entrySet()) {
            int count = e.getValue() == null ? 0 : e.getValue();
            for (int k = 0; k < count; k++) {
                types.add(e.getKey());
                weights.add((double) TYPE_WEIGHTS.getOrDefault(e.getKey(), 3));
            }
        }
        int n = types.size();
        if (n == 0) {
            return new ScoreScheme(total, List.of());
        }

        int[] pts = distributeIntegers(total, weights, n);

        // 汇总回题型
        List<TypeAllocation> allocations = new ArrayList<>();
        for (Map.Entry<String, Integer> e : orderedCounts.entrySet()) {
            String type = e.getKey();
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (type.equals(types.get(i))) {
                    list.add(pts[i]);
                }
            }
            if (list.isEmpty()) {
                continue;
            }
            int[] perQ = new int[list.size()];
            int sub = 0;
            for (int i = 0; i < list.size(); i++) {
                perQ[i] = list.get(i);
                sub += list.get(i);
            }
            allocations.add(new TypeAllocation(type, perQ.length, sub, perQ));
        }
        return new ScoreScheme(total, allocations);
    }

    /**
     * 最大余额法：把 total 按 weight 比例拆成 n 份整数，且每份 ≥ 1（当 n ≤ total 时）。
     */
    private static int[] distributeIntegers(int total, List<Double> weights, int n) {
        int[] pts = new int[n];
        double wSum = 0;
        for (double w : weights) {
            wSum += w;
        }
        if (wSum <= 0) {
            wSum = n;
            weights.clear();
            for (int i = 0; i < n; i++) {
                weights.add(1.0);
            }
        }
        double[] raw = new double[n];
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            raw[i] = (double) total * weights.get(i) / wSum;
            pts[i] = (int) Math.floor(raw[i]);
            assigned += pts[i];
        }
        // 余数按小数部分从大到小补 1
        int remainder = total - assigned;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double[] frac = new double[n];
        for (int i = 0; i < n; i++) {
            frac[i] = raw[i] - Math.floor(raw[i]);
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> frac[i]).reversed());
        for (int k = 0; k < remainder && k < n; k++) {
            pts[order[k]] += 1;
        }
        // 保证每题 ≥ 1：把 0 分的题补到 1 分，从分值最高的题各借 1 分（仅当 total ≥ n）
        if (total >= n) {
            for (int i = 0; i < n; i++) {
                while (pts[i] < 1) {
                    int donor = argMax(pts);
                    if (pts[donor] <= 1) {
                        break;
                    }
                    pts[donor] -= 1;
                    pts[i] += 1;
                }
            }
        }
        return pts;
    }

    private static int argMax(int[] pts) {
        int idx = 0;
        for (int i = 1; i < pts.length; i++) {
            if (pts[i] > pts[idx]) {
                idx = i;
            }
        }
        return idx;
    }

    /**
     * 渲染分值分配方案为 Markdown 文本，供试卷编写 Agent 严格遵循。
     *
     * @param scheme 分值方案
     * @param topic  主题（用于合卷说明）
     * @param level  学段
     * @return 方案文本
     */
    public static String renderScheme(ScoreScheme scheme, String topic, SchoolLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("本卷满分：").append(scheme.total()).append(" 分（")
                .append(levelLabel(level)).append("分数规则）\n\n");

        boolean allUniform = true;
        for (TypeAllocation a : scheme.allocations()) {
            int first = a.perQuestion().length > 0 ? a.perQuestion()[0] : 0;
            for (int p : a.perQuestion()) {
                if (p != first) {
                    allUniform = false;
                    break;
                }
            }
            if (!allUniform) {
                break;
            }
        }

        if (allUniform) {
            sb.append("| 题型 | 题数 | 每题分值 | 小计 |\n");
            sb.append("|------|------|----------|------|\n");
            for (TypeAllocation a : scheme.allocations()) {
                int perQ = a.perQuestion().length > 0 ? a.perQuestion()[0] : 0;
                sb.append("| ").append(a.type()).append(" | ").append(a.count())
                        .append(" | ").append(perQ).append(" 分 | ").append(a.subtotal()).append(" 分 |\n");
            }
        } else {
            sb.append("| 题型 | 题数 | 各小题分值 | 小计 |\n");
            sb.append("|------|------|------------|------|\n");
            for (TypeAllocation a : scheme.allocations()) {
                StringBuilder pq = new StringBuilder();
                for (int i = 0; i < a.perQuestion().length; i++) {
                    if (i > 0) {
                        pq.append("、");
                    }
                    pq.append(a.perQuestion()[i]);
                }
                sb.append("| ").append(a.type()).append(" | ").append(a.count())
                        .append(" | ").append(pq).append(" 分 | ").append(a.subtotal()).append(" 分 |\n");
            }
        }
        sb.append("| **合计** | | | **").append(scheme.total()).append(" 分** |\n");

        String note = combinedNote(topic, level);
        if (!note.isEmpty()) {
            sb.append("\n").append(note).append("\n");
        }
        return sb.toString();
    }

    private static boolean containsAny(String text, String... keys) {
        if (text == null) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 学段
     */
    public enum SchoolLevel {
        /**
         * 小学
         */
        PRIMARY,
        /**
         * 初中
         */
        JUNIOR,
        /**
         * 高中
         */
        SENIOR,
        /**
         * 未知
         */
        UNKNOWN
    }

    /**
     * 规范科目
     */
    public enum Subject {
        CHINESE, MATH, FOREIGN, PHYSICS, CHEMISTRY, BIOLOGY,
        MORAL_LAW, HISTORY, GEOGRAPHY, SCIENCE, PE, EXPERIMENT, OTHER
    }

    /**
     * 单个题型的分值分配结果
     */
    public record TypeAllocation(String type, int count, int subtotal, int[] perQuestion) {
    }

    // ==================== 题型分布方案（ExamPlan） ====================

    /** 内核题型 key → 中文规范名 */
    public static String chineseFromKey(String key) {
        if (key == null) {
            return "综合题";
        }
        return switch (key.toUpperCase()) {
            case "SINGLE_CHOICE" -> "单选题";
            case "MULTI_CHOICE" -> "多选题";
            case "TRUE_FALSE" -> "判断题";
            case "FILL_BLANK" -> "填空题";
            case "SHORT_ANSWER" -> "简答题";
            case "ESSAY" -> "论述题";
            default -> key;
        };
    }

    /** 中文规范名（或内核 key）→ 内核题型 key */
    public static String keyFromType(String typeOrKey) {
        if (typeOrKey == null) {
            return "OTHER";
        }
        String t = typeOrKey.trim();
        return switch (t) {
            case "单选题" -> "SINGLE_CHOICE";
            case "多选题" -> "MULTI_CHOICE";
            case "判断题" -> "TRUE_FALSE";
            case "填空题" -> "FILL_BLANK";
            case "简答题" -> "SHORT_ANSWER";
            case "论述题" -> "ESSAY";
            default -> t.toUpperCase();
        };
    }

    /** 内核题型权重（缺省 3） */
    public static int weightOfKind(String key) {
        Integer w = TYPE_WEIGHTS.get(chineseFromKey(key));
        return w != null ? w : 3;
    }

    /**
     * 由"题型 → 数量"映射构建默认分布方案（满分按题型权重再拆到每题）。
     *
     * @param topic           主题（用于学段/科目/合卷识别）
     * @param counts          题型（中文名或内核 key）→ 数量
     * @return 分值已归一化（Σ=满分）的方案
     */
    public static ExamPlan buildDefaultPlan(String topic, LinkedHashMap<String, Integer> counts) {
        SchoolLevel level = resolveLevel(topic, null);
        int total = resolveTotalFullMark(topic, level, null);
        LinkedHashMap<String, Integer> norm = new LinkedHashMap<>();
        if (counts != null) {
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int c = e.getValue() == null ? 0 : e.getValue();
                if (c > 0) {
                    norm.put(chineseFromKey(keyFromType(e.getKey())), c);
                }
            }
        }
        ScoreScheme scheme = allocate(total, norm);
        ExamPlan plan = new ExamPlan();
        plan.setSchoolLevel(level.name());
        plan.setTotalFullMark(total);
        List<Subject> subs = detectSubjects(topic);
        plan.setCombined(subs.size() > 1);
        for (Subject s : subs) {
            plan.getSubjects().add(subjectLabel(s));
        }
        for (TypeAllocation a : scheme.allocations()) {
            List<Integer> pq = new ArrayList<>();
            for (int p : a.perQuestion()) {
                pq.add(p);
            }
            plan.getTypes().add(new TypePlan(keyFromType(a.type()), a.type(), a.count(), pq));
        }
        normalizePlan(plan);
        return plan;
    }

    /**
     * 归一化方案：逐题数组长度对齐 count，并保证所有小题分值之和恰等于目标满分。
     * <p>
     * 若方案已平衡（题量匹配且 Σ=满分），保持用户逐题编辑不变；否则以各题现值为权重做
     * 最大余额法整体重分配（目标 &lt; 题量时无法每题≥1，交由评估提示）。
     * </p>
     *
     * @param plan 待归一化方案（原地修改）
     */
    public static void normalizePlan(ExamPlan plan) {
        if (plan == null || plan.getTypes() == null) {
            return;
        }
        int target = plan.getTotalFullMark() > 0 ? plan.getTotalFullMark() : DEFAULT_FULL_MARK;
        plan.setTotalFullMark(target);

        List<Double> weights = new ArrayList<>();
        boolean sizesOk = true;
        for (TypePlan t : plan.getTypes()) {
            int c = Math.max(t.getCount(), 0);
            List<Integer> pq = t.getPerQuestion() == null ? new ArrayList<>() : new ArrayList<>(t.getPerQuestion());
            if (pq.size() != c) {
                sizesOk = false;
                pq = resizeScoreList(pq, c);
            }
            t.setCount(c);
            t.setPerQuestion(pq);
            for (Integer v : pq) {
                weights.add((double) Math.max(v == null ? 1 : v, 1));
            }
        }

        int n = weights.size();
        if (n == 0) {
            return;
        }
        int sum = plan.allocatedTotal();
        if (sizesOk && sum == target) {
            return; // 已平衡，保留用户编辑
        }

        int[] res = distributeIntegers(target, weights, n);
        int idx = 0;
        for (TypePlan t : plan.getTypes()) {
            List<Integer> pq = new ArrayList<>();
            for (int k = 0; k < t.getCount(); k++) {
                pq.add(res[idx++]);
            }
            t.setPerQuestion(pq);
        }
    }

    /**
     * 把逐题分值列表增删到指定长度：截断尾部；不足则以现值平均（无值时按 1）补齐。
     */
    private static List<Integer> resizeScoreList(List<Integer> src, int size) {
        List<Integer> out = new ArrayList<>();
        if (size <= 0) {
            return out;
        }
        int cur = src.size();
        int fill = 1;
        if (cur > 0) {
            int s = 0;
            for (Integer v : src) {
                s += v == null ? 0 : v;
            }
            fill = Math.max((int) Math.round((double) s / cur), 1);
        }
        for (int i = 0; i < size; i++) {
            out.add(i < cur ? src.get(i) : fill);
        }
        return out;
    }

    /**
     * 依据内核题型权重，把满分分配到每一道小题（各题分值之和恰等于满分）。
     * <p>用于方案生成初期，仅有题型与题量时给出"简答/论述分更高"的默认逐题分值。</p>
     *
     * @param plan 方案（原地写入各题型的 perQuestion）
     */
    public static void assignScoresByKindWeights(ExamPlan plan) {
        if (plan == null || plan.getTypes() == null) {
            return;
        }
        int target = plan.getTotalFullMark() > 0 ? plan.getTotalFullMark() : DEFAULT_FULL_MARK;
        plan.setTotalFullMark(target);

        List<Double> weights = new ArrayList<>();
        for (TypePlan t : plan.getTypes()) {
            int c = Math.max(t.getCount(), 0);
            double w = weightOfKind(t.getKey());
            for (int i = 0; i < c; i++) {
                weights.add(w);
            }
        }
        int n = weights.size();
        if (n == 0) {
            return;
        }
        int[] res = distributeIntegers(target, weights, n);
        int idx = 0;
        for (TypePlan t : plan.getTypes()) {
            List<Integer> pq = new ArrayList<>();
            int c = Math.max(t.getCount(), 0);
            for (int k = 0; k < c; k++) {
                pq.add(res[idx++]);
            }
            t.setCount(c);
            t.setPerQuestion(pq);
        }
    }

    /**
     * 渲染分布方案为权威分值说明（供试卷编写 Agent 严格遵循：逐题分值 + 小计 + 满分）。
     *
     * @param plan  已归一化的方案
     * @param topic 主题（用于合卷说明）
     * @return Markdown 文本
     */
    public static String renderPlan(ExamPlan plan, String topic) {
        SchoolLevel level = parseLevelCode(plan.getSchoolLevel());
        StringBuilder sb = new StringBuilder();
        sb.append("本卷满分：").append(plan.getTotalFullMark()).append(" 分（")
                .append(levelLabel(level)).append("分数规则），共 ").append(plan.totalQuestions()).append(" 道题\n\n");

        boolean allUniform = true;
        for (TypePlan t : plan.getTypes()) {
            List<Integer> pq = t.getPerQuestion();
            if (pq.isEmpty()) {
                continue;
            }
            int first = pq.get(0);
            for (Integer p : pq) {
                if (p != null && p != first) {
                    allUniform = false;
                    break;
                }
            }
            if (!allUniform) {
                break;
            }
        }

        if (allUniform) {
            sb.append("| 题型 | 题数 | 每题分值 | 小计 |\n");
            sb.append("|------|------|----------|------|\n");
            for (TypePlan t : plan.getTypes()) {
                int perQ = t.getPerQuestion().isEmpty() ? 0 : t.getPerQuestion().get(0);
                sb.append("| ").append(t.getLabel()).append(" | ").append(t.getCount())
                        .append(" | ").append(perQ).append(" 分 | ").append(t.subtotal()).append(" 分 |\n");
            }
        } else {
            sb.append("| 题型 | 题数 | 各小题分值（按顺序） | 小计 |\n");
            sb.append("|------|------|--------------------|------|\n");
            for (TypePlan t : plan.getTypes()) {
                StringBuilder pq = new StringBuilder();
                List<Integer> list = t.getPerQuestion();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        pq.append("、");
                    }
                    pq.append(list.get(i));
                }
                sb.append("| ").append(t.getLabel()).append(" | ").append(t.getCount())
                        .append(" | ").append(pq).append(" 分 | ").append(t.subtotal()).append(" 分 |\n");
            }
        }
        sb.append("| **合计** | | | **").append(plan.getTotalFullMark()).append(" 分** |\n");

        String note = combinedNote(topic, level);
        if (!note.isEmpty()) {
            sb.append("\n").append(note).append("\n");
        }
        sb.append("\n出题时，每道小题的分值必须严格取自上表：把该题分值以\"（X分）\"标注在题干文字最末尾、选项之前；")
                .append("同一题型内若分值不同，按表中\"各小题分值\"从左到右依次对应第 1、2、3… 道小题。\n");
        return sb.toString();
    }

    /**
     * 自动平衡：以每题当前值为权重，用最大余额法把总分重新分配到各题，保证 Σ=满分。
     * <p>若方案已平衡则保持不变，仅返回说明。原地修改 {@code plan}。</p>
     *
     * @param plan 待平衡方案（含用户已编辑的分值）
     * @return 平衡后的方案与人类可读的过程说明（Markdown）
     */
    public static BalanceResult balancePlan(ExamPlan plan) {
        if (plan == null || plan.getTypes() == null || plan.getTypes().isEmpty()) {
            return new BalanceResult(plan, "无可平衡的方案。");
        }
        int target = plan.getTotalFullMark() > 0 ? plan.getTotalFullMark() : DEFAULT_FULL_MARK;
        plan.setTotalFullMark(target);

        // 记录输入：题量/当前每题/当前小计/合计
        StringBuilder input = new StringBuilder();
        input.append("目标满分：").append(target).append(" 分\n");
        int beforeSum = 0;
        int totalQ = 0;
        for (TypePlan t : plan.getTypes()) {
            int c = Math.max(t.getCount(), 0);
            List<Integer> pq = t.getPerQuestion() == null ? new ArrayList<>() : new ArrayList<>(t.getPerQuestion());
            if (pq.size() != c) {
                pq = resizeScoreList(pq, c);
                t.setPerQuestion(pq);
                t.setCount(c);
            }
            input.append("· ").append(t.getLabel()).append("：题量 ").append(c)
                    .append("，每题 ").append(pq).append("，小计 ").append(t.subtotal()).append(" 分\n");
            beforeSum += t.subtotal();
            totalQ += c;
        }
        if (totalQ == 0) {
            return new BalanceResult(plan, "题量为 0，无法平衡。\n\n输入：\n" + input);
        }

        // 已平衡则原样返回
        if (beforeSum == target) {
            String trace = "### 输入\n" + input
                    + "\n### 思考\n当前合计已等于满分，保留用户逐题编辑，不做改动。\n"
                    + "\n### 输出\n合计 " + beforeSum + " / 满分 " + target + " 分 ✓";
            return new BalanceResult(plan, trace);
        }

        // 逐题现值（≥1）为权重
        List<Double> weights = new ArrayList<>();
        for (TypePlan t : plan.getTypes()) {
            for (Integer v : t.getPerQuestion()) {
                weights.add((double) Math.max(v == null ? 1 : v, 1));
            }
        }
        int n = weights.size();
        int[] res = distributeIntegers(target, weights, n);
        int idx = 0;
        for (TypePlan t : plan.getTypes()) {
            List<Integer> pq = new ArrayList<>();
            for (int k = 0; k < t.getCount(); k++) {
                pq.add(res[idx++]);
            }
            t.setPerQuestion(pq);
        }

        StringBuilder output = new StringBuilder();
        for (TypePlan t : plan.getTypes()) {
            output.append("· ").append(t.getLabel()).append("：每题 ").append(t.getPerQuestion())
                    .append("，小计 ").append(t.subtotal()).append(" 分\n");
        }
        output.append("合计：").append(plan.allocatedTotal()).append(" 分 / 满分 ").append(target).append(" 分");

        String thinking = "当前合计 " + beforeSum + " 分与目标 " + target + " 分不一致（差 "
                + (beforeSum - target) + "）。以每道小题的现值为权重，用最大余额法整体缩放到满分，"
                + "既保证 Σ=满分，也尽量保留用户设定的题型内/题型间相对分值差异。";
        String trace = "### 输入\n" + input + "\n### 思考\n" + thinking + "\n\n### 输出\n" + output;
        return new BalanceResult(plan, trace);
    }

    // ==================== 工具 ====================

    /**
     * 完整分值分配方案
     */
    public record ScoreScheme(int total, List<TypeAllocation> allocations) {
    }

    /**
     * 自动平衡结果：原地更新后的方案 + 过程说明（Markdown）
     */
    public record BalanceResult(ExamPlan plan, String trace) {
    }
}
