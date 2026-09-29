package com.mouhin.knowledge.repository.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mouhin.knowledge.repository.domain.context.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link KnowledgeTenantLineHandler} 单元测试（P1-T7）。
 *
 * <p>覆盖： {@code getTenantId} 从 {@link TenantContext} 取 key → resolver 转 id → 包装成 {@link LongValue}；
 * 未绑定时用 {@link TenantContext#DEFAULT_TENANT_KEY} 兜底； {@code getTenantIdColumn} 恒为 {@code
 * tenant_id}； {@code ignoreTable} 白名单命中/未命中/大小写/schema 前缀/反引号变体； 未知租户透传 {@link
 * IllegalStateException} 供上层暴露配置漂移。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeTenantLineHandler 契约")
class KnowledgeTenantLineHandlerTest {

    @Mock private TenantIdResolver resolver;

    private KnowledgeTenantLineHandler handler;

    @BeforeEach
    void setUp() {
        handler = new KnowledgeTenantLineHandler(resolver);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("getTenantId")
    class TenantIdExtraction {

        @Test
        @DisplayName("已绑定 tenant_key 时用之解析")
        void usesBoundKey() {
            TenantContext.set("acme");
            when(resolver.resolve("acme")).thenReturn(42L);

            Expression expr = handler.getTenantId();

            assertThat(expr).isInstanceOf(LongValue.class);
            assertThat(((LongValue) expr).getValue()).isEqualTo(42L);
        }

        @Test
        @DisplayName("未绑定回退到 default key")
        void fallsBackToDefault() {
            when(resolver.resolve(TenantContext.DEFAULT_TENANT_KEY)).thenReturn(1L);

            Expression expr = handler.getTenantId();

            assertThat(((LongValue) expr).getValue()).isEqualTo(1L);
        }

        @Test
        @DisplayName("未知租户异常直接透传，不吞异常")
        void propagatesResolverFailure() {
            TenantContext.set("ghost");
            when(resolver.resolve("ghost"))
                    .thenThrow(new IllegalStateException("Unknown tenant_key: ghost"));

            assertThatThrownBy(() -> handler.getTenantId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unknown tenant_key");
        }
    }

    @Nested
    @DisplayName("getTenantIdColumn")
    class ColumnName {

        @Test
        @DisplayName("恒为 tenant_id 与 V24 列名对齐")
        void constantTenantId() {
            assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
        }
    }

    @Nested
    @DisplayName("ignoreTable 白名单")
    class IgnoreTable {

        @Test
        @DisplayName("平台/Flyway/Phase 4 延后表命中白名单")
        void ignoresPlatformTables() {
            for (String t :
                    new String[] {
                        "sys_tenant",
                        "sys_tenant_placement",
                        "flyway_schema_history",
                        "sys_config",
                        "kb_extraction_cache"
                    }) {
                assertThat(handler.ignoreTable(t))
                        .as("ignore " + t)
                        .withFailMessage("表 %s 应被忽略", t)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("15 业务表均不在白名单内")
        void doesNotIgnoreBusinessTables() {
            for (String t :
                    new String[] {
                        "kb_document",
                        "kb_document_chunk",
                        "kb_document_image",
                        "kb_category",
                        "kb_exam_history",
                        "kb_exam_session",
                        "kb_exam_question",
                        "kb_exam_answer",
                        "kb_student",
                        "kb_student_session",
                        "kb_writing_history",
                        "sys_user",
                        "sys_role",
                        "sys_user_role",
                        "sys_department"
                    }) {
                assertThat(handler.ignoreTable(t)).as("参与过滤 " + t).isFalse();
            }
        }

        @Test
        @DisplayName("大小写/schema 前缀/反引号变体归一化命中")
        void normalizesTableName() {
            assertThat(handler.ignoreTable("SYS_TENANT")).isTrue();
            assertThat(handler.ignoreTable("public.sys_tenant")).isTrue();
            assertThat(handler.ignoreTable("`sys_tenant_placement`")).isTrue();
            assertThat(handler.ignoreTable("\"kb_document\"")).isFalse();
        }

        @Test
        @DisplayName("null 输入按不忽略处理（保守：让 SQL 抛错而非静默放行）")
        void nullRejected() {
            assertThat(handler.ignoreTable(null)).isFalse();
        }
    }
}
