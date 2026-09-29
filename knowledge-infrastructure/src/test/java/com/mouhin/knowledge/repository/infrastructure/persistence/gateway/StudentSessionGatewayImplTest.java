package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentSessionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.StudentSessionMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link StudentSessionGatewayImpl} 契约单测（P0-T5，多会话支持）。
 *
 * <p>Mapper 以 {@code mock(StudentSessionMapper.class)} 手工注入，不启 Spring； 与 {@code
 * ExamHistoryGatewayImplTest} 一致预注册 MyBatis-Plus TableInfo 缓存以支撑 {@code getTargetSql()}。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("考生会话仓储实现契约单测 (StudentSessionGatewayImpl)")
class StudentSessionGatewayImplTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, StudentSessionDO.class);
    }

    private final StudentSessionMapper mapper = mock(StudentSessionMapper.class);
    private final StudentSessionGatewayImpl gateway = new StudentSessionGatewayImpl(mapper);

    private StudentSessionDO sampleDO(String token, Long studentId) {
        StudentSessionDO doObj = new StudentSessionDO();
        doObj.setId(11L);
        doObj.setSessionToken(token);
        doObj.setStudentId(studentId);
        doObj.setExpiresAt(LocalDateTime.now().plusHours(1));
        return doObj;
    }

    @Nested
    @DisplayName("save → Entity⇄DO 转换 + 主键回填")
    class Save {

        @Test
        @DisplayName("插入后回写 id 到 Entity")
        void insertsAndBackfillsId() {
            StudentSession entity = new StudentSession();
            entity.setSessionToken("tok-1");
            entity.setStudentId(99L);
            entity.setExpiresAt(LocalDateTime.now().plusHours(1));

            when(mapper.insert(any(StudentSessionDO.class)))
                    .thenAnswer(
                            inv -> {
                                inv.getArgument(0, StudentSessionDO.class).setId(555L);
                                return 1;
                            });

            gateway.save(entity);

            ArgumentCaptor<StudentSessionDO> cap = ArgumentCaptor.forClass(StudentSessionDO.class);
            verify(mapper, times(1)).insert(cap.capture());
            assertThat(cap.getValue().getSessionToken()).isEqualTo("tok-1");
            assertThat(cap.getValue().getStudentId()).isEqualTo(99L);
            assertThat(entity.getId()).isEqualTo(555L);
        }
    }

    @Nested
    @DisplayName("findByToken / findActiveByToken 查询契约")
    class FindByToken {

        @Test
        @DisplayName("findByToken 空白入参 → Optional.empty，不查库")
        void findByTokenBlank() {
            assertThat(gateway.findByToken(null)).isEmpty();
            assertThat(gateway.findByToken("   ")).isEmpty();
            verify(mapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("findByToken 命中 → DO→Entity；未命中回 empty")
        void findByTokenHitAndMiss() {
            when(mapper.selectOne(any())).thenReturn(sampleDO("T", 7L));
            Optional<StudentSession> hit = gateway.findByToken("T");
            assertThat(hit).isPresent();
            assertThat(hit.get().getStudentId()).isEqualTo(7L);

            when(mapper.selectOne(any())).thenReturn(null);
            assertThat(gateway.findByToken("nope")).isEmpty();
        }

        @Test
        @DisplayName("findActiveByToken 空白入参 → empty，不查库")
        void findActiveByTokenBlank() {
            assertThat(gateway.findActiveByToken(null)).isEmpty();
            assertThat(gateway.findActiveByToken("")).isEmpty();
            verify(mapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("findActiveByToken → SQL 含 revoked_at IS NULL 与 expires_at > ?")
        void findActiveByTokenAddsActivePredicate() {
            when(mapper.selectOne(any())).thenReturn(sampleDO("T2", 3L));

            assertThat(gateway.findActiveByToken("T2")).isPresent();

            ArgumentCaptor<LambdaQueryWrapper<StudentSessionDO>> cap = wrapperCaptor();
            verify(mapper).selectOne(cap.capture());
            String sql = cap.getValue().getTargetSql();
            assertThat(sql).contains("session_token").contains("revoked_at").contains("expires_at");
        }
    }

    @Nested
    @DisplayName("revoke / revokeAllByStudentId 撤销路径")
    class Revoke {

        @Test
        @DisplayName("revoke 空白入参 → 无库操作")
        void revokeBlankNoOp() {
            gateway.revoke(null);
            gateway.revoke("  ");
            verify(mapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("revoke 有效 token → update(null, wrapper)，wrapper 命中 session_token 且未 revoked")
        void revokeHitsOnlyActiveRows() {
            gateway.revoke("tok-x");

            ArgumentCaptor<Wrapper<StudentSessionDO>> wrapperCap =
                    ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper, times(1)).update(isNull(), wrapperCap.capture());
            LambdaUpdateWrapper<StudentSessionDO> uw =
                    (LambdaUpdateWrapper<StudentSessionDO>) wrapperCap.getValue();
            assertThat(uw.getTargetSql()).contains("session_token").contains("revoked_at");
        }

        @Test
        @DisplayName("revokeAllByStudentId null 入参 → 0 且不查库")
        void revokeAllByStudentIdNull() {
            assertThat(gateway.revokeAllByStudentId(null)).isZero();
            verify(mapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("revokeAllByStudentId → update 命中 student_id 与 revoked_at IS NULL")
        void revokeAllByStudentIdMaps() {
            when(mapper.update(isNull(), any())).thenReturn(3);

            int rows = gateway.revokeAllByStudentId(42L);

            assertThat(rows).isEqualTo(3);
            ArgumentCaptor<Wrapper<StudentSessionDO>> wrapperCap =
                    ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).update(eq(null), wrapperCap.capture());
            LambdaUpdateWrapper<StudentSessionDO> uw =
                    (LambdaUpdateWrapper<StudentSessionDO>) wrapperCap.getValue();
            assertThat(uw.getTargetSql()).contains("student_id").contains("revoked_at");
        }
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LambdaQueryWrapper<StudentSessionDO>> wrapperCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
