package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamAnswerGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamAnswer;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamAnswerConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamAnswerDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamAnswerMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 答题记录仓储实现
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Repository
public class ExamAnswerGatewayImpl implements ExamAnswerGateway {

    private final ExamAnswerMapper examAnswerMapper;

    public ExamAnswerGatewayImpl(ExamAnswerMapper examAnswerMapper) {
        this.examAnswerMapper = examAnswerMapper;
    }

    @Override
    public void save(ExamAnswer answer) {
        java.util.Objects.requireNonNull(answer, "ExamAnswer must not be null on save");
        ExamAnswerDO doObj = ExamAnswerConverter.toDO(answer);
        examAnswerMapper.insert(doObj);
        answer.setId(doObj.getId());
    }

    @Override
    public void update(ExamAnswer answer) {
        ExamAnswerDO doObj = ExamAnswerConverter.toDO(answer);
        examAnswerMapper.updateById(doObj);
    }

    @Override
    public void clearReviewOverride(Long answerId) {
        LambdaUpdateWrapper<ExamAnswerDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamAnswerDO::getId, answerId)
                .set(ExamAnswerDO::getReviewScore, null)
                .set(ExamAnswerDO::getReviewFeedback, null)
                .set(ExamAnswerDO::getReviewedBy, null)
                .set(ExamAnswerDO::getReviewTime, null)
                .set(ExamAnswerDO::getUpdateTime, LocalDateTime.now());
        examAnswerMapper.update(null, wrapper);
    }

    @Override
    public void clearAiTrace(Long answerId) {
        LambdaUpdateWrapper<ExamAnswerDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamAnswerDO::getId, answerId)
                .set(ExamAnswerDO::getAiInput, null)
                .set(ExamAnswerDO::getAiRawOutput, null)
                .set(ExamAnswerDO::getUpdateTime, LocalDateTime.now());
        examAnswerMapper.update(null, wrapper);
    }

    @Override
    public Optional<ExamAnswer> findById(Long id) {
        ExamAnswerDO doObj = examAnswerMapper.selectById(id);
        return Optional.ofNullable(ExamAnswerConverter.toDomain(doObj));
    }

    @Override
    public void saveAll(List<ExamAnswer> answers) {
        for (ExamAnswer answer : answers) {
            ExamAnswerDO doObj = ExamAnswerConverter.toDO(answer);
            examAnswerMapper.insert(doObj);
            answer.setId(doObj.getId());
        }
    }

    @Override
    public List<ExamAnswer> listBySessionId(Long sessionId) {
        LambdaQueryWrapper<ExamAnswerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswerDO::getSessionId, sessionId)
                .orderByAsc(ExamAnswerDO::getQuestionIndex);
        return examAnswerMapper.selectList(wrapper).stream()
                .map(ExamAnswerConverter::toDomain)
                .toList();
    }

    @Override
    public List<ExamAnswer> listNeedsReview(Long sessionId) {
        LambdaQueryWrapper<ExamAnswerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswerDO::getSessionId, sessionId)
                .isNull(ExamAnswerDO::getReviewScore)
                .orderByAsc(ExamAnswerDO::getQuestionIndex);
        return examAnswerMapper.selectList(wrapper).stream()
                .map(ExamAnswerConverter::toDomain)
                .toList();
    }

    @Override
    public long countNeedsReview(Long sessionId) {
        LambdaQueryWrapper<ExamAnswerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswerDO::getSessionId, sessionId).isNull(ExamAnswerDO::getReviewScore);
        return examAnswerMapper.selectCount(wrapper);
    }

    @Override
    public void deleteBySessionId(Long sessionId) {
        LambdaQueryWrapper<ExamAnswerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnswerDO::getSessionId, sessionId);
        examAnswerMapper.delete(wrapper);
    }

    @Override
    public List<ExamAnswer> listBySessionIds(List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExamAnswerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ExamAnswerDO::getSessionId, sessionIds)
                .orderByAsc(ExamAnswerDO::getSessionId)
                .orderByAsc(ExamAnswerDO::getQuestionIndex);
        return examAnswerMapper.selectList(wrapper).stream()
                .map(ExamAnswerConverter::toDomain)
                .toList();
    }
}
