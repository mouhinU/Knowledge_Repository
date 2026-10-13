package com.mouhin.knowledge.repository.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户业务数据硬删 Mapper（P3-T5，S-4=方案 A "30 天保留期 + 双人复核后硬删全量业务行"）。
 *
 * <p>两个方法按 {@code tenant_id} 对单表统计 / 删除。表名以 {@code ${tableName}} 原样拼入 SQL——这是红线 #5 允许的
 * "动态标识符须白名单校验后再拼" 场景：{@code tableName} <b>只</b>来自 {@code TenantDataPurgeGatewayImpl} 的硬编码白名单常量，
 * 绝不来自请求入参；{@code tenantId} 始终走 {@code #{}} 参数绑定。
 *
 * <p>{@link InterceptorIgnore}（{@code tenantLine=true}）强制这两条语句<b>绕过</b> {@code
 * TenantLineInnerInterceptor}： reconciler 在无 {@code TenantContext} 绑定下运行，若被自动注入 {@code AND
 * tenant_id=<当前上下文>} 会导致错删 / 漏删。
 *
 * @author mouhinU
 * @date 2026-10-09
 */
@Mapper
public interface TenantPurgeMapper {

    /**
     * 统计某租户在指定表的行数（dry-run 预览，不删）。
     *
     * @param tableName 白名单校验后的表名
     * @param tenantId  数值租户主键
     * @return 行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM ${tableName} WHERE tenant_id = #{tenantId}")
    long countByTenant(@Param("tableName") String tableName, @Param("tenantId") long tenantId);

    /**
     * 硬删某租户在指定表的全部行（不可逆）。
     *
     * @param tableName 白名单校验后的表名
     * @param tenantId  数值租户主键
     * @return 实际删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM ${tableName} WHERE tenant_id = #{tenantId}")
    int deleteByTenant(@Param("tableName") String tableName, @Param("tenantId") long tenantId);
}
