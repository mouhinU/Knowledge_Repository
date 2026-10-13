package com.mouhin.knowledge.repository.application.executor.examreview;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询待校对试卷列表执行器（app 层用例，阶段 1-D）
 *
 * <p>返回状态为 REVIEWABLE / VALIDATION_FAILED 的试卷，按创建时间倒序分页。
 *
 * <p>P0-T3：新增带 {@link Permission} 作用域的重载；{@code permission==null}/{@code admin=true} 走全局视图， 否则由
 * Gateway 层强制 {@code (user_id = ? OR department_id = ?)} 谓词。旧无参签名保留一版兼容。
 *
 * @author mouhinU
 * @date 2026-09-18
 */
@Component("paperReviewListPendingQryExe")
public class ListReviewPendingQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public ListReviewPendingQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    /**
     * 兼容旧签名：全局视图。
     */
    public List<ExamHistoryDTO> execute(int limit, int offset) {
        return execute(limit, offset, null);
    }

    /**
     * P0-T3 作用域版：controller 传入 {@code AdminPrincipalSupport.toPermission(request)}。
     */
    public List<ExamHistoryDTO> execute(int limit, int offset, Permission permission) {
        return examHistoryGateway.listReviewPendingScoped(limit, offset, permission).stream()
                .map(ExamGenerationConverter::toHistoryDTO)
                .toList();
    }

    /**
     * 兼容旧签名：全局视图。
     */
    public long count() {
        return count(null);
    }

    /**
     * P0-T3 作用域版统计。
     */
    public long count(Permission permission) {
        return examHistoryGateway.countReviewPendingScoped(permission);
    }
}
