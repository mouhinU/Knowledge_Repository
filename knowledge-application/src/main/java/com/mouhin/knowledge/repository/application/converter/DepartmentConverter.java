package com.mouhin.knowledge.repository.application.converter;

import com.mouhin.knowledge.repository.client.dto.DepartmentTreeNodeVO;
import com.mouhin.knowledge.repository.client.dto.DepartmentVO;
import com.mouhin.knowledge.repository.domain.model.entity.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 部门 领域实体 → 视图对象 转换器（app 层）。
 *
 * <p>MapStruct 编译期生成映射，字段映射与既有 {@code DepartmentAdminController.toResponse} 完全一致，保证 REST JSON
 * 结构不变：扁平 VO 的 parentId 为 null 时归一为 0（{@code defaultValue}，Long 装箱）；树节点 VO 保留原始 parentId（可为
 * null，children 由调用方组装）。实体侧多出的 {@code createdTime} 在两个 VO 上均无对应目标，依名匹配自然跳过。采用 {@link Mappers} 静态
 * {@link #INSTANCE} 访问，无副作用纯映射。
 *
 * @author mouhinU
 * @date 2026-09-17
 */
@Mapper
public interface DepartmentConverter {

    DepartmentConverter INSTANCE = Mappers.getMapper(DepartmentConverter.class);

    /** 领域实体 → 扁平 VO（{@code parentId} 为 null 时取默认 0）。 */
    @Mapping(target = "parentId", defaultValue = "0L")
    DepartmentVO toVO(Department dept);

    /** 领域实体 → 树节点 VO（保留原始 {@code parentId}，可为 null；children 由调用方组装）。 */
    DepartmentTreeNodeVO toTreeNode(Department dept);
}
