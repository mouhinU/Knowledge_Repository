package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 题型分布方案校验与评估（值对象工具）
 * <p>
 * 出卷流水线进入「试卷编写」前的闸门：方案的分值分配已由题型分布方案 Agent 生成并由用户在页面确认，
 * 此处只做「二次校验 + 合理性评估」，不再重排分值。校验维度：
 * </p>
 * <ul>
 *     <li>硬校验（不合格直接阻断流水线）：非空、总题量 &gt; 0、满分 &gt; 0、逐题分值合计 = 满分、
 *         每题分值 &gt;= 1、perQuestion 长度 = count</li>
 *     <li>软评估（仅提示建议）：极端分值（单题 &gt; 满分 30% 或某题型小计占比 &gt; 60%）、
 *         题量偏斜、缺少主观题或客观题、总分非标准（≠ 100/120/150）</li>
 * </ul>
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
public final class ScorePlanValidator {

    /** 认可的常见卷面满分 */
    private static final int[] STANDARD_FULL_MARKS = {100, 120, 150};

    /** 单题分值占满分阈值（超过视为过重） */
    private static final double MAX_SINGLE_QUESTION_RATIO = 0.30;

    /** 单题型小计占满分阈值（超过视为失衡） */
    private static final double MAX_TYPE_SHARE_RATIO = 0.60;

    /** 单题型题量过多阈值 */
    private static final int MAX_TYPE_COUNT = 60;

    private ScorePlanValidator() {
    }

    /**
     * 校验并评估方案。返回 {@link Result} 包含 issues（阻断）与 suggestions（提示）。
     *
     * @param plan 待校验方案
     * @return 校验评估结果（plan 为 null 视为「缺少方案」错误）
     */
    public static Result validate(ExamPlan plan) {
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        if (plan == null) {
            issues.add("缺少题型分布方案，无法评估分值合理性");
            return new Result(false, issues, suggestions);
        }
        if (plan.getTypes() == null || plan.getTypes().isEmpty()) {
            issues.add("方案未包含任何题型");
            return new Result(false, issues, suggestions);
        }

        int targetFullMark = plan.getTotalFullMark();
        if (targetFullMark <= 0) {
            issues.add("本卷满分未设置或非法（当前 " + targetFullMark + "）");
        }

        int totalCount = plan.totalQuestions();
        if (totalCount <= 0) {
            issues.add("总题量为 0，无法出卷");
        }

        int allocated = 0;
        boolean hasSubjective = false;
        boolean hasObjective = false;
        int maxPerQuestion = 0;
        String maxPerQuestionType = null;

        for (TypePlan t : plan.getTypes()) {
            int c = t.getCount();
            List<Integer> pq = t.getPerQuestion();
            if (c <= 0) {
                continue;
            }
            if (pq == null) {
                pq = new ArrayList<>();
            }
            if (pq.size() != c) {
                issues.add("题型「" + labelOf(t) + "」的 perQuestion 长度（" + pq.size()
                        + "）与题量（" + c + "）不一致");
            }
            int subtotal = t.subtotal();
            allocated += subtotal;
            if (subtotal <= 0) {
                issues.add("题型「" + labelOf(t) + "」小计为 0 分，未指定任何分值");
            }

            for (Integer p : pq) {
                int v = p == null ? 0 : p;
                if (v < 1) {
                    issues.add("题型「" + labelOf(t) + "」存在分值 < 1 的小题（当前 " + v + "）");
                    break;
                }
                if (v > maxPerQuestion) {
                    maxPerQuestion = v;
                    maxPerQuestionType = labelOf(t);
                }
            }

            if (targetFullMark > 0 && subtotal > targetFullMark * MAX_TYPE_SHARE_RATIO) {
                suggestions.add("题型「" + labelOf(t) + "」小计 " + subtotal
                        + " 分，占满分 " + percent(subtotal, targetFullMark)
                        + "%，超过 60%，题型分布偏斜，建议拆分或调整");
            }
            if (c > MAX_TYPE_COUNT) {
                suggestions.add("题型「" + labelOf(t) + "」题量 " + c + " 偏多，考试时长可能不足");
            }

            String key = t.getKey() == null ? "" : t.getKey().toUpperCase();
            if ("SHORT_ANSWER".equals(key) || "ESSAY".equals(key) || "FILL_BLANK".equals(key)) {
                hasSubjective = true;
            } else if ("SINGLE_CHOICE".equals(key) || "MULTI_CHOICE".equals(key) || "TRUE_FALSE".equals(key)) {
                hasObjective = true;
            }
        }

        if (targetFullMark > 0 && allocated != targetFullMark) {
            issues.add("逐题分值合计 " + allocated + " ≠ 本卷满分 " + targetFullMark
                    + "（差 " + (targetFullMark - allocated) + "），请回到方案编辑或点自动平衡");
        }

        if (targetFullMark > 0) {
            boolean standard = false;
            for (int std : STANDARD_FULL_MARKS) {
                if (std == targetFullMark) {
                    standard = true;
                    break;
                }
            }
            if (!standard) {
                suggestions.add("本卷满分 " + targetFullMark + " 分不是常见标准（100 / 120 / 150），请确认是否符合教学目标");
            }
            if (maxPerQuestion > 0 && maxPerQuestion > targetFullMark * MAX_SINGLE_QUESTION_RATIO) {
                suggestions.add("「" + maxPerQuestionType + "」存在单题 " + maxPerQuestion
                        + " 分，超过满分 " + (int) (MAX_SINGLE_QUESTION_RATIO * 100) + "%，可能造成整卷偏题");
            }
        }
        if (!hasSubjective) {
            suggestions.add("方案缺少主观题（简答/论述/填空），不利于综合能力考查");
        }
        if (!hasObjective) {
            suggestions.add("方案缺少客观题（单选/多选/判断），批改工作量大且信度较低");
        }
        if (totalCount > 0 && targetFullMark > 0) {
            double avg = (double) targetFullMark / totalCount;
            if (avg < 1.5) {
                suggestions.add("平均每题仅 " + String.format("%.1f", avg) + " 分，题量偏多");
            } else if (avg > 20) {
                suggestions.add("平均每题 " + String.format("%.1f", avg) + " 分，题量偏少");
            }
        }

        boolean pass = issues.isEmpty();
        return new Result(pass, issues, suggestions);
    }

    /**
     * 渲染校验评估报告为 Markdown（供 SSE 面板 / 复核页展示）。
     *
     * @param plan   方案
     * @param result 校验结果
     * @return Markdown
     */
    public static String renderReport(ExamPlan plan, Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 结论\n\n");
        if (result.pass) {
            sb.append("✅ 方案分值校验通过");
            if (!result.suggestions.isEmpty()) {
                sb.append("（含 ").append(result.suggestions.size()).append(" 条优化建议）");
            }
            sb.append("\n\n");
        } else {
            sb.append("❌ 方案分值校验未通过，共 ").append(result.issues.size()).append(" 项硬性错误\n\n");
        }
        if (plan != null) {
            sb.append("- 本卷满分：").append(plan.getTotalFullMark()).append(" 分\n");
            sb.append("- 合计分值：").append(plan.allocatedTotal()).append(" 分\n");
            sb.append("- 总题量：").append(plan.totalQuestions()).append(" 题\n\n");
        }
        if (!result.issues.isEmpty()) {
            sb.append("### 硬性错误（阻断流水线）\n\n");
            for (String s : result.issues) {
                sb.append("- ").append(s).append("\n");
            }
            sb.append("\n");
        }
        if (!result.suggestions.isEmpty()) {
            sb.append("### 分布评估建议\n\n");
            for (String s : result.suggestions) {
                sb.append("- ").append(s).append("\n");
            }
            sb.append("\n");
        }
        if (result.pass && result.suggestions.isEmpty()) {
            sb.append("### 分布评估\n\n- 未发现问题，题型与分值分布合理\n");
        }
        return sb.toString();
    }

    private static String labelOf(TypePlan t) {
        if (t.getLabel() != null && !t.getLabel().isBlank()) {
            return t.getLabel();
        }
        return ScoreRuleEngine.chineseFromKey(t.getKey());
    }

    private static int percent(int part, int whole) {
        if (whole <= 0) {
            return 0;
        }
        return (int) Math.round(part * 100.0 / whole);
    }

    /**
     * 校验结果
     *
     * @param pass        是否通过硬校验
     * @param issues      阻断流水线的错误清单
     * @param suggestions 不阻断的优化建议
     */
    public record Result(boolean pass, List<String> issues, List<String> suggestions) {
    }
}
