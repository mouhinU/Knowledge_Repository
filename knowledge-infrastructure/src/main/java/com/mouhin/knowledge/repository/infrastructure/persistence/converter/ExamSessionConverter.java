package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.ExamSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.ExamSessionDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 考试场次 DO ↔ 领域对象转换器（infrastructure 层，COLA 红线 #6 层边界）。
 *
 * <p>领域 ⇄ DO 字段全部同名，{@code toDO} 交由 MapStruct 编译期生成：{@code gradingToken}（CAS 令牌，由 Gateway 单独维护） 与
 * {@code tenantId}（租户拦截器 SQL 层注入）在领域模型上无对应 getter，故映射后保持 {@code null}，与原手写实现逐字段一致。{@code toDomain}
 * 保留为 {@code default} 方法：{@code voided} 需 {@code Boolean}→原始 {@code boolean} 的 null 归一（{@code
 * null→false}）， 语义与原 {@code Boolean.TRUE.equals(...)} 完全对齐，规避自动拆箱版本差异，确保零行为漂移。
 *
 * @author mouhinU
 * @date 2026-09-15
 */
@Mapper
public interface ExamSessionConverter {

    ExamSessionConverter INSTANCE = Mappers.getMapper(ExamSessionConverter.class);

    /** DO → 领域实体：全字段同名映射，{@code voided} 对 null 归一为 {@code false}。 */
    default ExamSession toDomain(ExamSessionDO doObj) {
        if (doObj == null) {
            return null;
        }
        ExamSession domain = new ExamSession();
        domain.setId(doObj.getId());
        domain.setSessionKey(doObj.getSessionKey());
        domain.setStudentId(doObj.getStudentId());
        domain.setExamHistoryId(doObj.getExamHistoryId());
        domain.setTopic(doObj.getTopic());
        domain.setDifficulty(doObj.getDifficulty());
        domain.setDurationMinutes(doObj.getDurationMinutes());
        domain.setExamPaper(doObj.getExamPaper());
        domain.setAnswerKey(doObj.getAnswerKey());
        domain.setQuestionsJson(doObj.getQuestionsJson());
        domain.setExamPlan(doObj.getExamPlan());
        domain.setTotalScore(doObj.getTotalScore());
        domain.setAiScore(doObj.getAiScore());
        domain.setFinalScore(doObj.getFinalScore());
        domain.setStatus(doObj.getStatus());
        domain.setVoided(Boolean.TRUE.equals(doObj.getVoided()));
        domain.setStartTime(doObj.getStartTime());
        domain.setSubmitTime(doObj.getSubmitTime());
        domain.setGradeTime(doObj.getGradeTime());
        domain.setPublishTime(doObj.getPublishTime());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    /** 领域实体 → DO：同名自动映射；{@code gradingToken}/{@code tenantId} 领域无源，保持 null（与原实现一致）。 */
    ExamSessionDO toDO(ExamSession domain);
}
