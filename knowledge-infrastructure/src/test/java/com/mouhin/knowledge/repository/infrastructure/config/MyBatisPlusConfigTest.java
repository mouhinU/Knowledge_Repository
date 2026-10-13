package com.mouhin.knowledge.repository.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.TenantMapper;
import com.mouhin.knowledge.repository.infrastructure.tenant.TenantIdResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link MyBatisPlusConfig} 条件装配测试（P1-T7）。
 *
 * <p>用 {@link ApplicationContextRunner} 在最小上下文中加载配置类，验证 {@code knowledge.saas.enabled} 门控：{@code
 * true} 时装配 {@link MybatisPlusInterceptor}，内部包含且仅包含一个 {@link TenantLineInnerInterceptor}；未设置或
 * {@code false} 时配置类根本不被加载、容器里没有 {@link MybatisPlusInterceptor} bean（红线 #11 回退闸门）。
 *
 * <p>不启动 Spring 上下文以外的东西：{@link TenantIdResolver} 与 {@link TenantMapper} 用 mock 顶替，避免触发真实数据源。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("MyBatisPlusConfig 条件装配")
class MyBatisPlusConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            TenantIdResolver.class,
                            () -> new TenantIdResolver(mock(TenantMapper.class)));

    @Test
    @DisplayName("saas.enabled=true 时装配带 TenantLine 的 MybatisPlusInterceptor")
    void interceptorRegisteredWhenEnabled() {
        runner.withPropertyValues("knowledge.saas.enabled=true")
                .withUserConfiguration(MyBatisPlusConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                            MybatisPlusInterceptor interceptor =
                                    context.getBean(MybatisPlusInterceptor.class);
                            assertThat(interceptor.getInterceptors())
                                    .singleElement()
                                    .isInstanceOf(TenantLineInnerInterceptor.class);
                        });
    }

    @Test
    @DisplayName("saas.enabled=false 时配置类不加载，容器无 interceptor bean")
    void noInterceptorWhenDisabled() {
        runner.withPropertyValues("knowledge.saas.enabled=false")
                .withUserConfiguration(MyBatisPlusConfig.class)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MyBatisPlusConfig.class);
                            assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
                        });
    }

    @Test
    @DisplayName("saas.enabled 未设置时行为等价 false（默认关闭 = 旧行为）")
    void noInterceptorWhenMissing() {
        runner.withUserConfiguration(MyBatisPlusConfig.class)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MyBatisPlusConfig.class);
                            assertThat(context).doesNotHaveBean(MybatisPlusInterceptor.class);
                        });
    }

    @Test
    @DisplayName("装配出的 TenantLine 内部 handler 非空且绑定到本项目的 resolver")
    void handlerWiredToResolver() {
        runner.withPropertyValues("knowledge.saas.enabled=true")
                .withUserConfiguration(MyBatisPlusConfig.class)
                .run(
                        context -> {
                            MybatisPlusInterceptor interceptor =
                                    context.getBean(MybatisPlusInterceptor.class);
                            InnerInterceptor first = interceptor.getInterceptors().get(0);
                            assertThat(first).isInstanceOf(TenantLineInnerInterceptor.class);
                            TenantLineInnerInterceptor tenant = (TenantLineInnerInterceptor) first;
                            // 反射可读：TenantLineInnerInterceptor 暴露 getTenantLineHandler()
                            assertThat(tenant.getTenantLineHandler()).isNotNull();
                        });
    }
}
