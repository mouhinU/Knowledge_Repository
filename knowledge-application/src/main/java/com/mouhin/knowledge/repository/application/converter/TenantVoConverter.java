package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.TenantVO;
import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 租户 领域实体 → 视图对象 转换器（app 层，P3-T1）。
 *
 * <p>字段一一直映，无业务归一；VO 全部字段均在 {@link Tenant} 上存在同名 getter，交由 MapStruct 编译期生成映射实现 （{@code
 * TenantVoConverterImpl}），行为与原手写实现零漂移。与 infra 层 {@code TenantConverter}（Entity ⇄ DO）分属不同层边界， 遵循
 * COLA 红线 #6（转换只在层边界发生）。采用默认 component model +{@link Mappers} 静态 {@link #INSTANCE} 访问；{@code null}
 * 入参由 MapStruct 默认返回 {@code null}，与原手写实现一致。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface TenantVoConverter {

    TenantVoConverter INSTANCE = Mappers.getMapper(TenantVoConverter.class);

    /** 领域实体 → VO；入参为 {@code null} 时返回 {@code null}。 */
    TenantVO toVO(Tenant tenant);
}
