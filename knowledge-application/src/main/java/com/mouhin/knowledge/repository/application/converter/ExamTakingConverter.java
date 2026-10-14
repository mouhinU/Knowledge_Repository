package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.ExamAnswerDTO;
import com.mouhin.knowledge.repository.client.dto.ExamSessionDTO;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 考试场次 / 答题记录 领域实体 → 视图对象 转换器（app 层）。
 *
 * <p>MapStruct 编译期生成映射，字段映射力求与原实体 Jackson 序列化结果保持键集合与取值一致，行为零漂移：{@code toSessionDTO} 全字段同名自动映射（含
 * {@code voided}：实体原始 {@code boolean} 拆箱到 DTO 原始 {@code boolean}）；{@code toAnswerDTO} 额外求值 {@code
 * objective}（{@code isObjective()}）、{@code effectiveScore}（{@code
 * getEffectiveScore()}）两个派生属性（{@code expression}），并显式 {@code ignore} DTO 上实体无对应来源的 {@code
 * images}（维持 {@code null}，与原手写一致）。采用 {@link Mappers} 静态 {@link #INSTANCE} 访问，无副作用纯映射。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Mapper
public interface ExamTakingConverter {

    ExamTakingConverter INSTANCE = Mappers.getMapper(ExamTakingConverter.class);

    /** 考试场次实体 → DTO：全字段同名自动映射。 */
    ExamSessionDTO toSessionDTO(ExamSession session);

    /** 答题记录实体 → DTO：派生 {@code objective}/{@code effectiveScore}，忽略无来源的 {@code images}。 */
    @Mapping(target = "objective", expression = "java(answer.isObjective())")
    @Mapping(target = "effectiveScore", expression = "java(answer.getEffectiveScore())")
    @Mapping(target = "images", ignore = true)
    ExamAnswerDTO toAnswerDTO(ExamAnswer answer);
}
