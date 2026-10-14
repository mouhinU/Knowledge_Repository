package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.DocumentChunk;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.DocumentChunkDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 文档分块 DO ↔ 领域对象转换器（infrastructure 层，COLA 红线 #6 层边界）。
 *
 * <p>MapStruct 编译期生成映射。唯一异名字段显式声明：DO {@code createTime} ↔ 领域 {@code createdTime}。其余同名字段自动映射；领域侧多出的
 * {@code documentName}/{@code fileType}/{@code tags}/{@code tenantKey} 与 DO 侧的 {@code
 * tenantId}（由租户拦截器在 SQL 层注入，领域模型不承载） 均无同名目标属性，依名匹配自然跳过，语义与原手写「字段一一直映、不做业务归一」的无副作用纯映射一致。{@code null}
 * 入参由 MapStruct 默认返回 {@code null}。
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@Mapper
public interface DocumentChunkConverter {

    DocumentChunkConverter INSTANCE = Mappers.getMapper(DocumentChunkConverter.class);

    /** DO → 领域实体；{@code createTime} 重命名落到领域 {@code createdTime}。 */
    @Mapping(target = "createdTime", source = "createTime")
    DocumentChunk toDomain(DocumentChunkDO doObj);

    /** 领域实体 → DO；领域 {@code createdTime} 落到 DO {@code createTime}。 */
    @Mapping(target = "createTime", source = "createdTime")
    DocumentChunkDO toDO(DocumentChunk domain);
}
