package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.Tenant;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.TenantDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 租户 领域实体 ⇄ 数据对象 转换器（infrastructure 层，COLA 红线 #6 的层边界）。
 *
 * <p>字段名完全一致，交由 MapStruct 编译期生成映射实现（{@code TenantConverterImpl}）；无业务归一（{@code tenantKey} 的小写归一在
 * {@code TenantGatewayImpl} 查询入口与 {@code TenantIdResolver} 各自处理），转换器保持无副作用的纯映射。采用默认 component model
 * +{@link Mappers} 静态 {@link #INSTANCE} 访问，而非 Spring
 * bean：契合「无状态纯映射、便于脱离容器单测」的既有约定，且不强制调用点（Gateway）改为字段注入（红线 #1 倾向构造器注入，静态工具访问对此中立）。{@code null} 入参由
 * MapStruct 默认返回 {@code null}，与原手写实现一致。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface TenantConverter {

    TenantConverter INSTANCE = Mappers.getMapper(TenantConverter.class);

    /** DO → 领域实体；入参为 {@code null} 时返回 {@code null}。 */
    Tenant toDomain(TenantDO doObj);

    /** 领域实体 → DO；入参为 {@code null} 时返回 {@code null}。 */
    TenantDO toDO(Tenant tenant);
}
