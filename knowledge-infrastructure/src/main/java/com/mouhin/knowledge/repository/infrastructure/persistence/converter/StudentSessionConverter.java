package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentSessionDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 考生会话 DO ↔ 领域对象转换器（P0-T5）。
 *
 * <p>字段名完全一致，交由 MapStruct 编译期生成映射实现（{@code StudentSessionConverterImpl}），相对原手写逐字段 set/get
 * 零行为漂移：{@code null} 入参返回 {@code null}，完整映射名称匹配的字段交集。领域实体不含 {@code tenantId}（由 infrastructure
 * 层租户拦截器注入），该 DO 独有字段被自动跳过，与原手写实现一致。采用 {@link Mappers} 静态 {@link #INSTANCE} 访问，保持无状态纯映射。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Mapper
public interface StudentSessionConverter {

    StudentSessionConverter INSTANCE = Mappers.getMapper(StudentSessionConverter.class);

    /** DO → 领域实体；入参为 {@code null} 时返回 {@code null}。 */
    StudentSession toDomain(StudentSessionDO doObj);

    /** 领域实体 → DO；入参为 {@code null} 时返回 {@code null}。 */
    StudentSessionDO toDO(StudentSession domain);
}
