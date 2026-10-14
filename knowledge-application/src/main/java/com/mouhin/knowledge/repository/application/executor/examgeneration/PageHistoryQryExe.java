package com.mouhin.knowledge.repository.application.executor.examgeneration;

import com.mouhin.knowledge.repository.application.converter.ExamGenerationConverter;
import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 分页查询出卷历史执行器（records / total / page / size）
 *
 * <p>P0-T3：新增带 {@link Permission} 作用域的 {@link #execute(int, int, Permission)}，由 controller 从 {@code
 * AdminPrincipalSupport.toPermission(request)} 传入；非管理员 → 仅返回自己 owner/dept 范围内的历史。
 * 旧签名保留为"全局视图"委托，兼容历史 caller 与学生端旁路调用，1 个版本后移除。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Component
public class PageHistoryQryExe {

    private final ExamHistoryGateway examHistoryGateway;

    public PageHistoryQryExe(ExamHistoryGateway examHistoryGateway) {
        this.examHistoryGateway = examHistoryGateway;
    }

    /** 兼容旧签名：不带作用域 → 全局视图。 */
    public Map<String, Object> execute(int page, int size) {
        return execute(page, size, null);
    }

    /** P0-T3：带 {@link Permission} 作用域分页；{@code permission==null}/{@code admin=true} 视为全局。 */
    public Map<String, Object> execute(int page, int size, Permission permission) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : 10;
        int offset = safePage * safeSize;
        List<ExamHistoryDTO> records =
                examHistoryGateway.listPageScoped(safeSize, offset, permission).stream()
                        .map(ExamGenerationConverter.INSTANCE::toHistoryDTO)
                        .toList();
        long total = examHistoryGateway.countAllScoped(permission);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }
}
