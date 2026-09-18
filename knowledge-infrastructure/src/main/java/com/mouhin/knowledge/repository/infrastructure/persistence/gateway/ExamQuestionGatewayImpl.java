package com.mouhin.knowledge.repository.infrastructure.persistence.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mouhin.knowledge.repository.domain.gateway.ExamQuestionGateway;
import com.mouhin.knowledge.repository.domain.model.entity.ExamQuestion;
import com.mouhin.knowledge.repository.infrastructure.persistence.converter.ExamQuestionConverter;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamQuestionDO;
import com.mouhin.knowledge.repository.infrastructure.persistence.mapper.ExamQuestionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 结构化题目仓储实现
 *
 * @author Knowledge-Repository
 * @date 2026-09-18
 */
@Repository
public class ExamQuestionGatewayImpl implements ExamQuestionGateway {

    private final ExamQuestionMapper examQuestionMapper;

    public ExamQuestionGatewayImpl(ExamQuestionMapper examQuestionMapper) {
        this.examQuestionMapper = examQuestionMapper;
    }

    @Override
    public List<ExamQuestion> listBySessionKey(String sessionKey) {
        LambdaQueryWrapper<ExamQuestionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestionDO::getSessionKey, sessionKey)
                .orderByAsc(ExamQuestionDO::getQuestionNumber);
        return examQuestionMapper.selectList(wrapper).stream()
                .map(ExamQuestionConverter::toDomain)
                .toList();
    }

    @Override
    public Optional<ExamQuestion> findBySessionKeyAndNumber(String sessionKey, Integer questionNumber) {
        LambdaQueryWrapper<ExamQuestionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestionDO::getSessionKey, sessionKey)
                .eq(ExamQuestionDO::getQuestionNumber, questionNumber)
                .last("LIMIT 1");
        ExamQuestionDO doObj = examQuestionMapper.selectOne(wrapper);
        return Optional.ofNullable(ExamQuestionConverter.toDomain(doObj));
    }

    @Override
    public void batchInsert(List<ExamQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        for (ExamQuestion question : questions) {
            ExamQuestionDO doObj = ExamQuestionConverter.toDO(question);
            examQuestionMapper.insert(doObj);
            question.setId(doObj.getId());
        }
    }

    @Override
    public void updateCorrectAnswer(String sessionKey, Integer questionNumber, String correctAnswer) {
        LambdaUpdateWrapper<ExamQuestionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamQuestionDO::getSessionKey, sessionKey)
                .eq(ExamQuestionDO::getQuestionNumber, questionNumber)
                .set(ExamQuestionDO::getCorrectAnswer, correctAnswer)
                .set(ExamQuestionDO::getUpdateTime, LocalDateTime.now());
        examQuestionMapper.update(null, wrapper);
    }

    @Override
    public void updateCorrection(String sessionKey, Integer questionNumber,
                                 String correctAnswer, String analysis, Integer maxScore) {
        LambdaUpdateWrapper<ExamQuestionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ExamQuestionDO::getSessionKey, sessionKey)
                .eq(ExamQuestionDO::getQuestionNumber, questionNumber)
                .set(ExamQuestionDO::getCorrectAnswer, correctAnswer)
                .set(ExamQuestionDO::getAnalysis, analysis);
        if (maxScore != null) {
            wrapper.set(ExamQuestionDO::getMaxScore, maxScore);
        }
        wrapper.set(ExamQuestionDO::getUpdateTime, LocalDateTime.now());
        examQuestionMapper.update(null, wrapper);
    }

    @Override
    public void deleteBySessionKey(String sessionKey) {
        LambdaQueryWrapper<ExamQuestionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestionDO::getSessionKey, sessionKey);
        examQuestionMapper.delete(wrapper);
    }
}
