package com.mouhin.knowledge.repository.domain.service;

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

    // ==================== 工具 ====================

    /**
     * 完整分值分配方案
     */
    public record ScoreScheme(int total, List<TypeAllocation> allocations) {
    }
}
