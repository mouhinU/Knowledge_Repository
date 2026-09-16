package com.mouhin.knowledge.repository.application.agent;

import java.util.List;

/**
 * 试卷校验结果
 * <p>
 * 内容校验 Agent 的输出，描述一份试卷结构化题目是否可正常渲染与内容是否正确。
 * {@code pass} 为 true 表示无阻断性错误（errors 为空），试卷可以正常开考；
 * 否则应阻止开考。{@code warnings} 为非阻断性提示，仅用于告知。
 * </p>
 *
 * @param pass          是否通过校验（无阻断性错误）
 * @param questionCount 题目总数
 * @param totalMaxScore 各题分值之和
 * @param errors        阻断性错误列表
 * @param warnings      非阻断性提示列表
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public record PaperValidationReport(
        boolean pass,
        int questionCount,
        int totalMaxScore,
        List<String> errors,
        List<String> warnings) {

    public PaperValidationReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 转为前端友好的 Map 结构
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("pass", pass);
        map.put("questionCount", questionCount);
        map.put("totalMaxScore", totalMaxScore);
        map.put("errors", errors);
        map.put("warnings", warnings);
        return map;
    }
}
