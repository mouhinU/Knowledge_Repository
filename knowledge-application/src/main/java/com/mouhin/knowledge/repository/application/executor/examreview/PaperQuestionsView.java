package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.domain.service.ExamContractValidator;
import java.util.List;

/**
 * 校对视图数据（app 层内部结果，供适配层组装响应）
 *
 * @param history 试卷历史（含 topic / difficulty / status / qualityScore / examPlan 等）
 * @param questions 结构化题目行（按印刷题号升序）
 * @param validation 契约校验结果（issues 为待修正项）
 * @param reviewRequired 当前是否强制人工校对（供前端提示发布门槛）
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
public record PaperQuestionsView(
        ExamHistory history,
        List<ExamQuestion> questions,
        ExamContractValidator.Result validation,
        boolean reviewRequired) {}
