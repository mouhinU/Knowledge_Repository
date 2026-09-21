package com.mouhin.knowledge.repository.application.executor.examtaking;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 题目 JSON 校验报告查询执行器（app 层用例，返回可序列化的报告 Map）
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Component
public class ExamValidateReportQryExe {

    private final ExamTakingSupport support;

    public ExamValidateReportQryExe(ExamTakingSupport support) {
        this.support = support;
    }

    public Map<String, Object> execute(String questionsJson) {
        return support.validateReport(questionsJson).toMap();
    }
}
