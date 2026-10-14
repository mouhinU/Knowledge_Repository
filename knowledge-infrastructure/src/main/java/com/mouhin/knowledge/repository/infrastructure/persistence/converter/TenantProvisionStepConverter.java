package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.TenantProvisionStep;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantProvisionStepDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * {@link TenantProvisionStep} ⇄ {@link TenantProvisionStepDO} 转换（P3-T2）。
 *
 * <p>字段名完全一致，交由 MapStruct 编译期生成映射实现（{@code TenantProvisionStepConverterImpl}），相对原手写逐字段 set/get
 * 零行为漂移：{@code null} 入参返回 {@code null}，完整映射名称匹配的字段交集（含两侧同名 {@code tenantId}）。 采用 {@link Mappers} 静态
 * {@link #INSTANCE} 访问，保持 infrastructure 边界无副作用纯映射。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface TenantProvisionStepConverter {

    TenantProvisionStepConverter INSTANCE = Mappers.getMapper(TenantProvisionStepConverter.class);

    /** DO → domain entity；{@code null} 输入返回 {@code null}。 */
    TenantProvisionStep toDomain(TenantProvisionStepDO source);

    /** domain entity → DO；{@code null} 输入返回 {@code null}。 */
    TenantProvisionStepDO toDO(TenantProvisionStep source);
}
