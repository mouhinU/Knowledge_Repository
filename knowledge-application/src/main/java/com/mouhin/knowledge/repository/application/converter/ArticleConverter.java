package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.WritingHistoryDTO;
import com.mouhin.knowledge.repository.domain.model.entity.WritingHistory;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 写作历史 领域实体 → 视图对象 转换器（app 层）
 *
 * <p>15 个属性名称在实体与 DTO 上完全一致，交由 MapStruct 编译期生成映射实现（{@code ArticleConverterImpl}）， 力求与原实体 Jackson
 * 序列化结果保持键集合、顺序与取值一致，行为零漂移。采用默认 component model +{@link Mappers} 静态 {@link #INSTANCE} 访问；{@code
 * null} 入参由 MapStruct 默认返回 {@code null}，与原手写实现一致。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Mapper
public interface ArticleConverter {

    ArticleConverter INSTANCE = Mappers.getMapper(ArticleConverter.class);

    WritingHistoryDTO toHistoryDTO(WritingHistory history);
}
