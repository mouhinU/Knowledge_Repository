package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamHistoryGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamHistory;
import com.mouhin.knowledge.repository.domain.model.valueobject.Permission;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamHistoryConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamHistoryDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamHistoryMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * AI 出卷历史仓储实现
 *
 * <p>P0-T3：新增带 {@link Permission} 作用域的 scoped 查询。谓词规则： {@code permission==null} 或 {@code
 * permission.isAdmin()} → 无过滤（管理员全局视图）； 否则强制 {@code (user_id = ? OR department_id = ?)}， 空 /
 * 空白的一侧自动跳过，两者都空则退化为 {@code id = -1}（永零）。
 *
 * @author mouhinU
 * @date 2026-09-14
 */
@Repository
public class ExamHistoryGatewayImpl implements ExamHistoryGateway {

    /** MyBatis-Plus .last() 前缀（java:S1192 抽常量防 4 处 SQL_LIMIT_PREFIX 漂移）。 */
    private static final String SQL_LIMIT_PREFIX = "LIMIT ";

    /** 作用域两侧皆空时的兜底谓词：永假条件，避免跨租户全表命中。 */
    private static final String SQL_NEVER_TRUE = "1 = 0";

    /** 分页查询默认条数 */
    private static final int DEFAULT_LIMIT = 20;

    private final ExamHistoryMapper examHistoryMapper;

    public ExamHistoryGatewayImpl(ExamHistoryMapper examHistoryMapper) {
        this.examHistoryMapper = examHistoryMapper;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public void save(ExamHistory history) {
        java.util.Objects.requireNonNull(history, "ExamHistory must not be null on save");
        ExamHistoryDO doObj = ExamHistoryConverter.INSTANCE.toDO(history);
        examHistoryMapper.insert(doObj);
        history.setId(doObj.getId());
    }

    @Override
    public void update(ExamHistory history) {
        ExamHistoryDO doObj = ExamHistoryConverter.INSTANCE.toDO(history);
        examHistoryMapper.updateById(doObj);
    }

    @Override
    public Optional<ExamHistory> findById(Long id) {
        ExamHistoryDO doObj = examHistoryMapper.selectById(id);
        return Optional.ofNullable(ExamHistoryConverter.INSTANCE.toDomain(doObj));
    }

    @Override
    public Optional<ExamHistory> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getSessionId, sessionId);
        ExamHistoryDO doObj = examHistoryMapper.selectOne(wrapper);
        return Optional.ofNullable(ExamHistoryConverter.INSTANCE.toDomain(doObj));
    }

    @Override
    public List<ExamHistory> listRecent(int limit) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime).last(SQL_LIMIT_PREFIX + limit);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public List<ExamHistory> listPublished(int limit) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getStatus, ExamHistory.STATUS_PUBLISHED)
                .orderByDesc(ExamHistoryDO::getCreateTime)
                .last(SQL_LIMIT_PREFIX + (limit > 0 ? limit : DEFAULT_LIMIT));
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public List<ExamHistory> listReviewPending(int limit, int offset) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                        ExamHistoryDO::getStatus,
                        ExamHistory.STATUS_REVIEWABLE,
                        ExamHistory.STATUS_VALIDATION_FAILED)
                .orderByDesc(ExamHistoryDO::getCreateTime)
                .last(SQL_LIMIT_PREFIX + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public long countReviewPending() {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                ExamHistoryDO::getStatus,
                ExamHistory.STATUS_REVIEWABLE,
                ExamHistory.STATUS_VALIDATION_FAILED);
        return examHistoryMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamHistory> listPage(int limit, int offset) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime)
                .last(SQL_LIMIT_PREFIX + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return examHistoryMapper.selectCount(null);
    }

    // ==================== P0-T3 作用域查询 ====================

    @Override
    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamHistoryDO::getSessionId, sessionId);
        examHistoryMapper.delete(wrapper);
    }

    @Override
    public List<ExamHistory> listPageScoped(int limit, int offset, Permission permission) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, permission);
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime)
                .last(SQL_LIMIT_PREFIX + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public long countAllScoped(Permission permission) {
        if (permission == null || permission.isAdmin()) {
            return examHistoryMapper.selectCount(null);
        }
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        applyScope(wrapper, permission);
        return examHistoryMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamHistory> listReviewPendingScoped(int limit, int offset, Permission permission) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        int safeOffset = Math.max(offset, 0);
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                ExamHistoryDO::getStatus,
                ExamHistory.STATUS_REVIEWABLE,
                ExamHistory.STATUS_VALIDATION_FAILED);
        applyScope(wrapper, permission);
        wrapper.orderByDesc(ExamHistoryDO::getCreateTime)
                .last(SQL_LIMIT_PREFIX + safeLimit + " OFFSET " + safeOffset);
        return examHistoryMapper.selectList(wrapper).stream()
                .map(ExamHistoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public long countReviewPendingScoped(Permission permission) {
        LambdaQueryWrapper<ExamHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(
                ExamHistoryDO::getStatus,
                ExamHistory.STATUS_REVIEWABLE,
                ExamHistory.STATUS_VALIDATION_FAILED);
        applyScope(wrapper, permission);
        return examHistoryMapper.selectCount(wrapper);
    }

    /**
     * 施加作用域谓词到 wrapper：admin/全局视图不加；否则 (user_id = ? OR department_id = ?)，两侧皆空 → 永假。
     *
     * <p>参数值一律以 MyBatis-Plus {@code eq(col, val)} 走 {@code #{}} 占位符，绝不拼接，符合红线 #5。
     */
    private void applyScope(LambdaQueryWrapper<ExamHistoryDO> wrapper, Permission permission) {
        if (permission == null || permission.isAdmin()) {
            return;
        }
        boolean hasUser = isNotBlank(permission.getUserId());
        boolean hasDept = isNotBlank(permission.getDepartmentId());
        if (!hasUser && !hasDept) {
            // 兜底：任何一侧都拿不到 → 拒绝全表命中
            wrapper.apply(SQL_NEVER_TRUE);
            return;
        }
        if (hasUser && hasDept) {
            wrapper.and(
                    w ->
                            w.eq(ExamHistoryDO::getUserId, permission.getUserId())
                                    .or()
                                    .eq(
                                            ExamHistoryDO::getDepartmentId,
                                            permission.getDepartmentId()));
        } else if (hasUser) {
            wrapper.eq(ExamHistoryDO::getUserId, permission.getUserId());
        } else {
            wrapper.eq(ExamHistoryDO::getDepartmentId, permission.getDepartmentId());
        }
    }
}
