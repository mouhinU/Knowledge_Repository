package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.UserInvite;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.UserInviteDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * {@link UserInvite} ⇄ {@link UserInviteDO} 转换器（P3-T3）。
 *
 * <p>两侧字段命名一致，交由 MapStruct 编译期生成映射实现（{@code UserInviteConverterImpl}），相对原手写逐字段 set/get 零行为漂移：{@code
 * null} 输入返回 {@code null}，完整映射名称匹配的字段交集（含两侧同名 {@code tenantId}）。采用 {@link Mappers} 静态 {@link
 * #INSTANCE} 访问，保持无副作用纯映射。
 *
 * @author mouhinU
 * @date 2026-10-08
 */
@Mapper
public interface UserInviteConverter {

    UserInviteConverter INSTANCE = Mappers.getMapper(UserInviteConverter.class);

    /** DO → Entity。 */
    UserInvite toDomain(UserInviteDO doObj);

    /** Entity → DO。 */
    UserInviteDO toDO(UserInvite entity);
}
