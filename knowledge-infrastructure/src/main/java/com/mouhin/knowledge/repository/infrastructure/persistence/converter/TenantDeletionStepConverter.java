package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.TenantDeletionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDeletionStepDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * {@link TenantDeletionStep} ⇄ {@link TenantDeletionStepDO} 转换（P3-T5）。
 *
 * <p>字段名完全一致，交由 MapStruct 编译期生成映射实现（{@code TenantDeletionStepConverterImpl}），相对原手写逐字段 set/get
 * 零行为漂移：{@code null} 入参返回 {@code null}，完整映射名称匹配的字段交集（含两侧同名 {@code tenantId}）； 镜像 {@code
 * TenantProvisionStepConverter}。采用 {@link Mappers} 静态 {@link #INSTANCE} 访问。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Mapper
public interface TenantDeletionStepConverter {

    TenantDeletionStepConverter INSTANCE = Mappers.getMapper(TenantDeletionStepConverter.class);

    /** DO → domain entity；{@code null} 输入返回 {@code null}。 */
    TenantDeletionStep toDomain(TenantDeletionStepDO source);

    /** domain entity → DO；{@code null} 输入返回 {@code null}。 */
    TenantDeletionStepDO toDO(TenantDeletionStep source);
}
