package com.mouhin.knowledge.repository.domain.model.valueobject;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个题型的分布计划（值对象）
 * <p>
 * {@code key} 为内核题型（决定做题端渲染与评分方式），{@code label} 为按科目显示的题型名称，
 * {@code perQuestion} 为每一道小题的分值，长度须等于 {@code count}。小计由逐题分值求和得到。
 * </p>
 *
 * @author Knowledge-Repository
 * @date 2026-09-16
 */
public class TypePlan {

    /** 内核题型：SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE / FILL_BLANK / SHORT_ANSWER / ESSAY */
    private String key;

    /** 显示题型名（可按科目命名，如"阅读理解""计算题""解答题"） */
    private String label;

    /** 题量 */
    private int count;

    /** 每道小题的分值，长度应等于 count */
    private List<Integer> perQuestion = new ArrayList<>();

    /** Agent 选题 / 定题量的简要理由（供评估与页面提示） */
    private String reason;

    public TypePlan() {
    }

    public TypePlan(String key, String label, int count, List<Integer> perQuestion) {
        this.key = key;
        this.label = label;
        this.count = count;
        if (perQuestion != null) {
            this.perQuestion = new ArrayList<>(perQuestion);
        }
    }

    /**
     * 该题型小计（逐题分值之和）
     */
    public int subtotal() {
        int sum = 0;
        if (perQuestion != null) {
            for (Integer p : perQuestion) {
                if (p != null) {
                    sum += p;
                }
            }
        }
        return sum;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<Integer> getPerQuestion() {
        return perQuestion;
    }

    public void setPerQuestion(List<Integer> perQuestion) {
        this.perQuestion = perQuestion != null ? perQuestion : new ArrayList<>();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
