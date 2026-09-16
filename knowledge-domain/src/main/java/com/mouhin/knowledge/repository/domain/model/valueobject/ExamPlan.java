package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.ArrayList;
import java.util.List;

/**
 * 题型分布方案（值对象）
 * <p>
 * 描述一份试卷"有哪些题型、每种多少道、每道多少分"，由多阶段分布 Agent 生成、可在出题页面调整，
 * 最终作为出卷流水线（分值分配 → 试卷编写）的权威输入。所有小题分值之和恒等于 {@code totalFullMark}。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
public class ExamPlan {

    /** 学段：PRIMARY / JUNIOR / SENIOR / UNKNOWN */
    private String schoolLevel;

    /** 本卷目标满分（依据学段与科目满分规则） */
    private int totalFullMark;

    /** 是否合卷 */
    private boolean combined;

    /** 识别到的科目（合卷时可有多个，用于说明） */
    private List<String> subjects = new ArrayList<>();

    /** 各题型计划 */
    private List<TypePlan> types = new ArrayList<>();

    /** 合理性评估建议（第④阶段产出，仅提示不自动改写） */
    private List<String> evaluationNotes = new ArrayList<>();

    public ExamPlan() {
    }

    /**
     * 当前所有小题分值之和
     */
    public int allocatedTotal() {
        int sum = 0;
        if (types != null) {
            for (TypePlan t : types) {
                sum += t.subtotal();
            }
        }
        return sum;
    }

    /**
     * 总题量
     */
    public int totalQuestions() {
        int n = 0;
        if (types != null) {
            for (TypePlan t : types) {
                n += Math.max(t.getCount(), 0);
            }
        }
        return n;
    }

    public String getSchoolLevel() {
        return schoolLevel;
    }

    public void setSchoolLevel(String schoolLevel) {
        this.schoolLevel = schoolLevel;
    }

    public int getTotalFullMark() {
        return totalFullMark;
    }

    public void setTotalFullMark(int totalFullMark) {
        this.totalFullMark = totalFullMark;
    }

    public boolean isCombined() {
        return combined;
    }

    public void setCombined(boolean combined) {
        this.combined = combined;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects != null ? subjects : new ArrayList<>();
    }

    public List<TypePlan> getTypes() {
        return types;
    }

    public void setTypes(List<TypePlan> types) {
        this.types = types != null ? types : new ArrayList<>();
    }

    public List<String> getEvaluationNotes() {
        return evaluationNotes;
    }

    public void setEvaluationNotes(List<String> evaluationNotes) {
        this.evaluationNotes = evaluationNotes != null ? evaluationNotes : new ArrayList<>();
    }
}
