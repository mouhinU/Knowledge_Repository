package com.mouhin.knowledge.repository.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.mouhin.knowledge.repository.domain.constant.FeatureFlagKeys;
import com.mouhin.knowledge.repository.infrastructure.tenant.KnowledgeTenantLineHandler;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器装配（P1-T7，SaaS 租户脊柱）。
 *
 * <p>关键设计：整段配置只在 {@code knowledge.saas.enabled=true} 时激活（红线 #11 回退闸门）。关闭态下本类根本不会被 Spring 加载， {@code
 * MybatisPlusInterceptor} bean 缺席，MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 走默认无
 * interceptor 路径，SQL 生成行为与 P1-T6 之前的旧版本完全等价——单租户存量功能不会因此承担任何风险。
 *
 * <p>不注入 {@code SaasProperties} 而采用 {@link ConditionalOnProperty}，是因为 COLA 依赖方向： infra 只依赖
 * domain/client，{@code SaasProperties} 位于 application/config 层，编译期不可达。字符串字面量 {@code
 * "knowledge.saas.enabled"} 与 application 层 {@code @ConfigurationProperties("knowledge.saas")}
 * 前缀对齐， 由 P1-T10 端到端测试负责保证二者不漂移。
 *
 * <p>本类只注册一个内部拦截器 {@link TenantLineInnerInterceptor}：
 *
 * <ul>
 *   <li>{@link KnowledgeTenantLineHandler} 负责从 {@code TenantContext} 读出 tenant_key，经 {@link
 *       TenantIdResolver} 转成 tenant_id 并注入到 SELECT/UPDATE/DELETE 的 WHERE 与 INSERT 的列。
 *   <li>分页插件按项目实际需要暂不引入（现有查询都是显式 limit/offset），如未来切 MyBatis-Plus {@code IPage} 再补 {@code
 *       PaginationInnerInterceptor}，注意需要放在 {@code TenantLineInnerInterceptor} 之后。
 * </ul>
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@Configuration
@ConditionalOnProperty(name = FeatureFlagKeys.SAAS_ENABLED, havingValue = "true")
public class MyBatisPlusConfig {

    /**
     * 装配带租户过滤的 {@link MybatisPlusInterceptor}。
     *
     * @param tenantIdResolver 租户键→id 解析器
     * @return 单内部拦截器（{@link TenantLineInnerInterceptor}）实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantIdResolver tenantIdResolver) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new KnowledgeTenantLineHandler(tenantIdResolver));
        interceptor.addInnerInterceptor(tenantInterceptor);
        return interceptor;
    }
}
