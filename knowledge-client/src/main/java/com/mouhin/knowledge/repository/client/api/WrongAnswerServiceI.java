package com.mouhin.knowledge.repository.client.api;

import com.mouhin.knowledge.repository.client.dto.StudentOptionVO;
import com.mouhin.knowledge.repository.client.dto.WrongAnswerPageVO;
import java.util.List;

/**
 * 错题本应用服务契约（client 层）
 *
 * @author mouhinU
 * @date 2026-09-17
 */
public interface WrongAnswerServiceI {

    /**
     * 分页查询学生的错题记录。
     *
     * @param studentId 学生 ID
     * @param topic 主题过滤条件，为 null 或空时不过滤
     * @param questionType 题型过滤条件，为 null 或空时不过滤
     * @param page 页码（从 1 开始）
     * @param size 每页条数
     * @return 错题分页结果，包含错题列表与分页信息
     */
    WrongAnswerPageVO pageWrongAnswers(
            Long studentId, String topic, String questionType, int page, int size);

    /**
     * 基于错题记录生成 AI 总结（分析薄弱知识点与改进建议）。
     *
     * @param studentId 学生 ID
     * @param topic 主题过滤条件，为 null 或空时针对全部错题
     * @param questionType 题型过滤条件，为 null 或空时针对全部题型
     * @return AI 生成的总结文本
     */
    String generateAiSummary(Long studentId, String topic, String questionType);

    /**
     * 查询学生可选的错题筛选选项（主题、题型等下拉选项）。
     *
     * @return 学生选项 VO 列表
     */
    List<StudentOptionVO> listStudentOptions();
}
