package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.SystemConfig;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.SystemConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 系统配置 Entity ⇄ DO 转换器。
 *
 * <p>字段名完全一致，交由 MapStruct 编译期生成映射实现（{@code SystemConfigConverterImpl}），相对原手写逐字段 set/get
 * 零行为漂移：{@code null} 入参返回 {@code null}，完整映射名称匹配的字段交集（含两侧同名 {@code tenantId}）。采用 {@link Mappers} 静态
 * {@link #INSTANCE} 访问，契合无状态纯映射的既有约定。
 *
 * @author mouhinU
 * @date 2026-09-23
 */
@Mapper
public interface SystemConfigConverter {

    SystemConfigConverter INSTANCE = Mappers.getMapper(SystemConfigConverter.class);

    /** DO → 领域实体；入参为 {@code null} 时返回 {@code null}。 */
    SystemConfig toDomain(SystemConfigDO doObj);

    /** 领域实体 → DO；入参为 {@code null} 时返回 {@code null}。 */
    SystemConfigDO toDO(SystemConfig entity);
}
