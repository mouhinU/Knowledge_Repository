package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.ExamHistoryDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 出卷历史 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>DTO 全部字段均在 {@link ExamHistory} 上存在同名 getter（实体额外的 reviewedBy / reviewedTime 等源字段无对应目标属性， 由
 * MapStruct 自动忽略），交由 MapStruct 编译期生成映射实现（{@code ExamGenerationConverterImpl}），力求与原实体 Jackson
 * 序列化结果保持键集合、顺序与取值一致，行为零漂移。采用默认 component model +{@link Mappers} 静态 {@link #INSTANCE} 访问； {@code
 * null} 入参由 MapStruct 默认返回 {@code null}，与原手写实现一致。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Mapper
public interface ExamGenerationConverter {

    ExamGenerationConverter INSTANCE = Mappers.getMapper(ExamGenerationConverter.class);

    ExamHistoryDTO toHistoryDTO(ExamHistory history);
}
