package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.gateway.ExamSessionGateway;
import com.mouhin.knowledge.repository.domain.gateway.StudentGateway;
import com.mouhin.knowledge.repository.domain.model.entity.Student;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 查询已发布试卷列表执行器（app 层用例，阶段 1-D）
 *
 * <p>学生端「可用考试」入口专用：
 *
 * <ol>
 *   <li>仅返回 {@code PUBLISHED} 状态试卷（未发布 / 待校对 / 校验不通过 / 已作废的卷不出现）；
 *   <li>传入 {@code studentToken} 时，进一步剔除该考生已开考过的试卷（一人一卷一次系统级限制）； 未登录或不传 token 时保持原全量返回语义，以兼容旧行为。
 * </ol>
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Component("examListPublishedHistoryQryExe")
public class ListPublishedHistoryQryExe {

    /** 剔除已考卷时向网关多取若干条做过滤后截断，避免过滤后不足 limit 造成前端"看起来只有几份"。 */
    private static final int OVERFETCH_FACTOR = 4;

    private final ExamHistoryGateway examHistoryGateway;
    private final ExamSessionGateway examSessionGateway;
    private final StudentGateway studentGateway;

    public ListPublishedHistoryQryExe(
            ExamHistoryGateway examHistoryGateway,
            ExamSessionGateway examSessionGateway,
            StudentGateway studentGateway) {
        this.examHistoryGateway = examHistoryGateway;
        this.examSessionGateway = examSessionGateway;
        this.studentGateway = studentGateway;
    }

    /** 保留旧签名（不带学生身份）供其它调用方使用。 */
    public List<ExamHistoryDTO> execute(int limit) {
        return execute(limit, null);
    }

    /**
     * 面向学生端的可用列表：可按登录态剔除已开考过的试卷。
     *
     * @param limit 最大返回数量
     * @param studentToken 学生会话令牌；为空或失效时按未登录处理，仅返回全量已发布列表
     * @return 可用试卷 DTO 列表（按创建时间倒序）
     */
    public List<ExamHistoryDTO> execute(int limit, String studentToken) {
        int safeLimit = limit > 0 ? limit : 20;
        Student student = resolveStudentOrNull(studentToken);
        if (student == null) {
            return examHistoryGateway.listPublished(safeLimit).stream()
                    .map(ExamGenerationConverter::toHistoryDTO)
                    .toList();
        }
        Set<Long> takenHistoryIds =
                examSessionGateway.listExamHistoryIdsByStudentId(student.getId()).stream()
                        .collect(Collectors.toSet());
        // 已考卷剔除后可能不足 limit，向 DB 多取若干条后再截断
        List<ExamHistoryDTO> filtered =
                examHistoryGateway.listPublished(safeLimit * OVERFETCH_FACTOR).stream()
                        .filter(h -> h.getId() == null || !takenHistoryIds.contains(h.getId()))
                        .limit(safeLimit)
                        .map(ExamGenerationConverter::toHistoryDTO)
                        .toList();
        return filtered;
    }

    private Student resolveStudentOrNull(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return studentGateway.findBySessionToken(token).filter(Student::isTokenValid).orElse(null);
    }
}
