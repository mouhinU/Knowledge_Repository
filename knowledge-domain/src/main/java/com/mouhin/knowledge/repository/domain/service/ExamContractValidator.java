package com.mouhin.knowledge.repository.domain.service;

import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.model.valueobject.ExamPlan;
import com.mouhin.knowledge.repository.domain.model.valueobject.TypePlan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 出卷契约校验器（V2 阶段 1-C，发布前硬关口）
 *
 * <p>对「出卷即切分」产出的结构化题目行做确定性契约校验，确保切分结果与题型分布方案、 卷面分值、答案规范三者自洽，作为发布 / 校对闸门的规则门禁（不依赖大模型）。校验维度：
 *
 * <ol>
 *   <li>题数 == 方案总题量；
 *   <li>各题型数量 == 方案对应题型数量；
 *   <li>分值合计 == 方案满分；
 *   <li>印刷题号非空且唯一（权威编号）；
 *   <li>每题标准答案非空；
 *   <li>答案规范（先经 {@link ExamAnswerNormalizer} 剥离内联解释再判定）：单选恰 1 个 A~D 字母； 多选 ≥ 2 个 A~D 字母；判断为 正确 / 错误
 *       等判词头部；
 *   <li>选择题（单选 / 多选）选项非空（判断题为隐式二选一，不校验选项）。
 * </ol>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public final class ExamContractValidator {

    private ExamContractValidator() {}

    /**
     * 校验切分结果。
     *
     * @param questions 切分产出的结构化题目行
     * @param plan 题型分布方案（可为 null；缺省时跳过题数/分值/分布对齐，仅校验答案规范与编号）
     * @return 校验结果（issues 非空即不通过）
     */
    public static Result validate(List<ExamQuestion> questions, ExamPlan plan) {
        List<String> issues = new ArrayList<>();
        if (questions == null || questions.isEmpty()) {
            issues.add("试卷切分结果为空，无任何题目");
            return new Result(false, issues);
        }

        checkCountAlignment(questions, plan, issues);
        checkScoreSumAlignment(questions, plan, issues);
        checkTypeCounts(questions, plan, issues);
        checkQuestionNumbers(questions, issues);
        checkAnswersPerQuestion(questions, issues);

        return new Result(issues.isEmpty(), issues);
    }

    /** 1. 题数对齐：切分题数须等于方案总题量。 */
    private static void checkCountAlignment(
            List<ExamQuestion> questions, ExamPlan plan, List<String> issues) {
        if (plan != null
                && plan.totalQuestions() > 0
                && questions.size() != plan.totalQuestions()) {
            issues.add("切分题数 " + questions.size() + " ≠ 方案总题量 " + plan.totalQuestions());
        }
    }

    /** 3. 分值合计对齐：题目满分之和须等于方案总分值。 */
    private static void checkScoreSumAlignment(
            List<ExamQuestion> questions, ExamPlan plan, List<String> issues) {
        int sumScore = 0;
        for (ExamQuestion q : questions) {
            sumScore += q.getMaxScore() == null ? 0 : q.getMaxScore();
        }
        if (plan != null && plan.getTotalFullMark() > 0 && sumScore != plan.getTotalFullMark()) {
            issues.add("题目分值合计 " + sumScore + " ≠ 方案满分 " + plan.getTotalFullMark());
        }
    }

    /** 2. 分题型数量对齐：逐题型的切分数量须等于方案该题型题量。 */
    private static void checkTypeCounts(
            List<ExamQuestion> questions, ExamPlan plan, List<String> issues) {
        if (plan == null || plan.getTypes() == null) {
            return;
        }
        for (TypePlan t : plan.getTypes()) {
            if (t.getCount() <= 0) {
                continue;
            }
            String key = t.getKey() == null ? "" : t.getKey().toUpperCase();
            long actual =
                    questions.stream()
                            .filter(q -> key.equalsIgnoreCase(q.getQuestionType()))
                            .count();
            if (actual != t.getCount()) {
                issues.add("题型「" + labelOf(t, key) + "」切分数量 " + actual + " ≠ 方案题量 " + t.getCount());
            }
        }
    }

    /** 4. 印刷题号非空且全局唯一。 */
    private static void checkQuestionNumbers(List<ExamQuestion> questions, List<String> issues) {
        Set<Integer> numbers = new HashSet<>();
        for (ExamQuestion q : questions) {
            Integer num = q.getQuestionNumber();
            if (num == null) {
                issues.add("存在题目缺失印刷题号（question_number 为空）");
                break;
            }
            if (!numbers.add(num)) {
                issues.add("印刷题号重复：" + num + "（切分后题号必须全局唯一）");
                break;
            }
        }
    }

    /** 5/6/7. 逐题答案规范与选项校验（含出处/位置类记忆题的内容确定性复核）。 */
    private static void checkAnswersPerQuestion(List<ExamQuestion> questions, List<String> issues) {
        for (ExamQuestion q : questions) {
            checkSingleQuestion(q, issues);
        }
    }

    /** 校验单题：先复核元记忆题，再按题型校验答案规范与选项。 */
    private static void checkSingleQuestion(ExamQuestion q, List<String> issues) {
        Integer num = q.getQuestionNumber();
        String tag = num != null ? ("第 " + num + " 题") : "（无题号题）";
        // 内容确定性复核：出处/位置类记忆题——考查教材编排位置而非内容，强制人工改写后方可发布
        if (ExamMetaQuestionDetector.isMetaRecall(q.getStem())) {
            String stem = q.getStem();
            String brief = stem.length() > 40 ? stem.substring(0, 40) + "…" : stem;
            issues.add(tag + " " + ExamMetaQuestionDetector.ADVISORY + "：" + brief);
        }
        String answer = q.getCorrectAnswer();
        if (answer == null || answer.isBlank()) {
            issues.add(tag + " 缺少标准答案");
            return;
        }
        String type = q.getQuestionType() == null ? "" : q.getQuestionType().toUpperCase();
        switch (type) {
            case "SINGLE_CHOICE" -> {
                if (ExamAnswerNormalizer.choiceLetters(answer).length() != 1) {
                    issues.add(tag + " 单选答案非法（应为 A~D 单个字母）：" + answer);
                }
                requireOptions(q, tag, issues);
            }
            case "MULTI_CHOICE" -> {
                if (ExamAnswerNormalizer.choiceLetters(answer).length() < 2) {
                    issues.add(tag + " 多选答案非法（应为 A~D 两个及以上字母）：" + answer);
                }
                requireOptions(q, tag, issues);
            }
            case "TRUE_FALSE" -> {
                if (ExamAnswerNormalizer.trueFalseToken(answer) == null) {
                    issues.add(tag + " 判断答案非法（应为 正确/错误 等判词）：" + answer);
                }
                // 判断题为隐式二选一（正确/错误），试卷切分不产出 options_json，故不校验选项
            }
            default -> {
                // 填空 / 简答 / 论述：仅需答案非空（已在上方校验），不做字母规范约束
            }
        }
    }

    /** 渲染校验报告为 Markdown（供校对视图 / 日志展示）。 */
    public static String renderReport(Result result) {
        StringBuilder sb = new StringBuilder();
        if (result.pass()) {
            sb.append("✅ 出卷契约校验通过\n");
            return sb.toString();
        }
        sb.append("❌ 出卷契约校验未通过，共 ").append(result.issues().size()).append(" 项问题：\n");
        for (String issue : result.issues()) {
            sb.append("- ").append(issue).append("\n");
        }
        return sb.toString();
    }

    // ==================== 工具 ====================

    private static void requireOptions(ExamQuestion q, String tag, List<String> issues) {
        String optionsJson = q.getOptionsJson();
        if (optionsJson == null || optionsJson.isBlank() || "[]".equals(optionsJson.trim())) {
            issues.add(tag + " 为选择题但选项缺失");
        }
    }

    private static String labelOf(TypePlan t, String key) {
        if (t.getLabel() != null && !t.getLabel().isBlank()) {
            return t.getLabel();
        }
        return key;
    }

    /**
     * 契约校验结果。
     *
     * @param pass 是否通过（issues 为空）
     * @param issues 问题清单（不通过项）
     */
    public record Result(boolean pass, List<String> issues) {}
}
