package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 试卷生成请求
 *
 * @author mouhinU
 * @date 2026-09-13
 */
@Getter
@Setter
public class ExamGenerationRequest {

    /** 会话 ID（前端预分配，用于 SSE 关联） */
    private String sessionId;

    /** 考试主题/科目 */
    private String topic;

    /** 难度：EASY / MEDIUM / HARD */
    private String difficulty;

    /** 单选题数量 */
    private Integer singleChoiceCount;

    /** 多选题数量 */
    private Integer multiChoiceCount;

    /** 判断题数量 */
    private Integer trueFalseCount;

    /** 填空题数量 */
    private Integer fillBlankCount;

    /** 简答题数量 */
    private Integer shortAnswerCount;

    /** 论述题数量 */
    private Integer essayCount;

    /** 用户 ID */
    private String userId;

    /** 用户部门 ID */
    private String departmentId;

    /** 用户角色（逗号分隔） */
    private String roles;

    /** 是否管理员 */
    private Boolean admin;

    /** 知识库分类过滤 */
    private String category;

    /** 学段：PRIMARY（小学）/ JUNIOR（初中）/ SENIOR（高中），留空则自动识别 */
    private String schoolLevel;

    /** 页面确认的题型分布方案（ExamPlan 的 JSON 序列化结果）。 两步式流程：先由后端 Agent 生成方案，用户在页面调整后回传此字段用于出卷。 */
    private String distribution;

    /** 是否跳过分值校验（Node 2 已校验通过时传 true，Node 3 的 Scoring Agent 仅写入 scheme 不再校验） */
    private Boolean skipScoringValidation;

    /** 计算总题目数 */
    public int getTotalCount() {
        return (singleChoiceCount != null ? singleChoiceCount : 0)
                + (multiChoiceCount != null ? multiChoiceCount : 0)
                + (trueFalseCount != null ? trueFalseCount : 0)
                + (fillBlankCount != null ? fillBlankCount : 0)
                + (shortAnswerCount != null ? shortAnswerCount : 0)
                + (essayCount != null ? essayCount : 0);
    }
}
