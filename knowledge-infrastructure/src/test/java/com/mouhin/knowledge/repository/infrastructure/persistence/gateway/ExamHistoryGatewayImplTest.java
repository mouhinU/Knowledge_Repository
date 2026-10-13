package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamHistoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link ExamHistoryGatewayImpl} 契约单测：锁定 CRUD 转换、按 sessionId 查询与计数透传，以及 P0-T3 owner/dept 作用域谓词。
 *
 * <p>Mapper 以 {@code mock(ExamHistoryMapper.class)} 手工注入，不启 Spring。
 *
 * @author mouhinU
 * @date 2026-09-24 18:21:00
 */
@DisplayName("AI 出卷历史仓储实现契约单测 (ExamHistoryGatewayImpl)")
class ExamHistoryGatewayImplTest {

    static {
        // LambdaQueryWrapper.getTargetSql() 依赖 MyBatis-Plus TableInfo 缓存；
        // 纯 mock 单测未启 Spring，需手工注册一次 DO 元数据。
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ExamHistoryDO.class);
    }

    private final ExamHistoryMapper examHistoryMapper = mock(ExamHistoryMapper.class);
    private final ExamHistoryGatewayImpl gateway = new ExamHistoryGatewayImpl(examHistoryMapper);

    private ExamHistoryDO doOf(Long id, String sessionId) {
        ExamHistoryDO doObj = new ExamHistoryDO();
        doObj.setId(id);
        doObj.setSessionId(sessionId);
        doObj.setTopic("数据结构");
        doObj.setStatus("PUBLISHED");
        return doObj;
    }

    @Test
    @DisplayName("save → Entity→DO 回写 + 主键回填")
    void saveWritesAndBackfillsId() {
        ExamHistory history = new ExamHistory();
        history.setSessionId("sess-1");
        history.setTopic("操作系统");
        history.setStatus("DRAFT");

        when(examHistoryMapper.insert(any(ExamHistoryDO.class)))
                .thenAnswer(
                        inv -> {
                            inv.getArgument(0, ExamHistoryDO.class).setId(66L);
                            return 1;
                        });

        gateway.save(history);

        ArgumentCaptor<ExamHistoryDO> cap = ArgumentCaptor.forClass(ExamHistoryDO.class);
        verify(examHistoryMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getSessionId()).isEqualTo("sess-1");
        assertThat(cap.getValue().getTopic()).isEqualTo("操作系统");
        assertThat(history.getId()).isEqualTo(66L);
    }

    @Test
    @DisplayName("findById 命中 → DO→Entity 映射；未命中回 empty")
    void findByIdMapsAndHandlesMissing() {
        when(examHistoryMapper.selectById(1L)).thenReturn(doOf(1L, "sess-1"));
        Optional<ExamHistory> hit = gateway.findById(1L);
        assertThat(hit).isPresent();
        assertThat(hit.get().getTopic()).isEqualTo("数据结构");

        when(examHistoryMapper.selectById(99L)).thenReturn(null);
        assertThat(gateway.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("findBySessionId → 条件查询命中转 Entity；未命中回 empty")
    void findBySessionIdMapsAndHandlesMissing() {
        when(examHistoryMapper.selectOne(any())).thenReturn(doOf(2L, "sess-2"));
        assertThat(gateway.findBySessionId("sess-2")).isPresent();

        when(examHistoryMapper.selectOne(any())).thenReturn(null);
        assertThat(gateway.findBySessionId("ghost")).isEmpty();
    }

    @Test
    @DisplayName("listRecent → selectList 批量转 Entity")
    void listRecentMapsEveryRow() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of(doOf(1L, "a"), doOf(2L, "b")));

        List<ExamHistory> recent = gateway.listRecent(10);

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(ExamHistory::getSessionId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("countAll → selectCount(null) 透传")
    void countAllMapsToNullWrapper() {
        when(examHistoryMapper.selectCount(null)).thenReturn(12L);
        assertThat(gateway.countAll()).isEqualTo(12L);
    }

    // ==================== P0-T3 作用域谓词 ====================

    @Test
    @DisplayName("P0-T3: listPageScoped 非管理员 → wrapper SQL 含 user_id 与 department_id")
    void listPageScopedNonAdminAddsOwnerDeptPredicate() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of());
        Permission perm = new Permission("u-9", "d-9", null, false);

        gateway.listPageScoped(10, 0, perm);

        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectList(cap.capture());
        String sql = cap.getValue().getTargetSql();
        assertThat(sql)
                .as("应包含 owner/dept 谓词段: %s", sql)
                .contains("user_id")
                .contains("department_id");
    }

    @Test
    @DisplayName("P0-T3: listPageScoped admin=true → wrapper 不加 owner/dept 谓词（全局视图）")
    void listPageScopedAdminSkipsPredicate() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of());
        Permission perm = new Permission("u-1", "d-1", null, true);

        gateway.listPageScoped(10, 0, perm);

        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectList(cap.capture());
        String sql = cap.getValue().getTargetSql();
        assertThat(sql).doesNotContain("user_id").doesNotContain("department_id");
    }

    @Test
    @DisplayName("P0-T3: permission=null → 与 admin 一致，全局视图不加谓词")
    void listPageScopedNullPermissionIsGlobal() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of());

        gateway.listPageScoped(10, 0, null);

        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectList(cap.capture());
        assertThat(cap.getValue().getTargetSql())
                .doesNotContain("user_id")
                .doesNotContain("department_id");
    }

    @Test
    @DisplayName("P0-T3: 非管理员 + departmentId 空 → 走 user_id 单侧谓词，不含 department_id")
    void listPageScopedOnlyUser() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of());
        Permission perm = new Permission("u-9", null, null, false);

        gateway.listPageScoped(10, 0, perm);

        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectList(cap.capture());
        String sql = cap.getValue().getTargetSql();
        assertThat(sql).contains("user_id").doesNotContain("department_id");
    }

    @Test
    @DisplayName("P0-T3: countAllScoped 非管理员 → 走带 wrapper 的 selectCount 而非 selectCount(null)")
    void countAllScopedNonAdminPassesWrapper() {
        when(examHistoryMapper.selectCount(any())).thenReturn(3L);
        Permission perm = new Permission("u-5", "d-5", null, false);

        long total = gateway.countAllScoped(perm);

        assertThat(total).isEqualTo(3L);
        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectCount(cap.capture());
        assertThat(cap.getValue().getTargetSql()).contains("user_id").contains("department_id");
    }

    @Test
    @DisplayName("P0-T3: countAllScoped admin → 直接透传 selectCount(null)")
    void countAllScopedAdminUsesNullWrapper() {
        when(examHistoryMapper.selectCount(null)).thenReturn(99L);
        Permission perm = new Permission("u-1", null, null, true);

        assertThat(gateway.countAllScoped(perm)).isEqualTo(99L);
        verify(examHistoryMapper).selectCount(null);
    }

    @Test
    @DisplayName("P0-T3: listReviewPendingScoped 非管理员 → wrapper 同时含 status IN 与 owner/dept")
    void listReviewPendingScopedCombinesStatusAndScope() {
        when(examHistoryMapper.selectList(any())).thenReturn(List.of());
        Permission perm = new Permission("u-7", "d-7", null, false);

        gateway.listReviewPendingScoped(10, 0, perm);

        ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> cap = wrapperCaptor();
        verify(examHistoryMapper).selectList(cap.capture());
        String sql = cap.getValue().getTargetSql();
        assertThat(sql).contains("status").contains("user_id").contains("department_id");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<ExamHistoryDO>> wrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
