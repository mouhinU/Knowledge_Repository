package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.StudentSession;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentSessionDO;

/**
 * 考生会话 DO ↔ 领域对象转换器（P0-T5）
 *
 * @author mouhinU
 * @date 2026-09-29
 */
public final class StudentSessionConverter {

    private StudentSessionConverter() {
    }

    public static StudentSession toDomain(StudentSessionDO doObj) {
        if (doObj == null) {
            return null;
        }
        StudentSession domain = new StudentSession();
        domain.setId(doObj.getId());
        domain.setSessionToken(doObj.getSessionToken());
        domain.setStudentId(doObj.getStudentId());
        domain.setExpiresAt(doObj.getExpiresAt());
        domain.setRevokedAt(doObj.getRevokedAt());
        domain.setUserAgent(doObj.getUserAgent());
        domain.setIpAddress(doObj.getIpAddress());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static StudentSessionDO toDO(StudentSession domain) {
        if (domain == null) {
            return null;
        }
        StudentSessionDO doObj = new StudentSessionDO();
        doObj.setId(domain.getId());
        doObj.setSessionToken(domain.getSessionToken());
        doObj.setStudentId(domain.getStudentId());
        doObj.setExpiresAt(domain.getExpiresAt());
        doObj.setRevokedAt(domain.getRevokedAt());
        doObj.setUserAgent(domain.getUserAgent());
        doObj.setIpAddress(domain.getIpAddress());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
