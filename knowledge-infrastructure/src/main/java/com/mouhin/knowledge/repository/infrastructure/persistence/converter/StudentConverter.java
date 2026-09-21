package com.mouhin.knowledge.repository.infrastructure.persistence.converter;

import com.mouhin.knowledge.repository.domain.model.entity.Student;
import com.mouhin.knowledge.repository.infrastructure.persistence.dataobject.StudentDO;

/**
 * 考生 DO ↔ 领域对象转换器
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
public final class StudentConverter {

    private StudentConverter() {}

    public static Student toDomain(StudentDO doObj) {
        if (doObj == null) {
            return null;
        }
        Student domain = new Student();
        domain.setId(doObj.getId());
        domain.setUsername(doObj.getUsername());
        domain.setPasswordHash(doObj.getPasswordHash());
        domain.setDisplayName(doObj.getDisplayName());
        domain.setStudentNo(doObj.getStudentNo());
        domain.setDepartmentId(doObj.getDepartmentId());
        domain.setSessionToken(doObj.getSessionToken());
        domain.setTokenExpiry(doObj.getTokenExpiry());
        domain.setStatus(doObj.getStatus());
        domain.setCreateTime(doObj.getCreateTime());
        domain.setUpdateTime(doObj.getUpdateTime());
        return domain;
    }

    public static StudentDO toDO(Student domain) {
        if (domain == null) {
            return null;
        }
        StudentDO doObj = new StudentDO();
        doObj.setId(domain.getId());
        doObj.setUsername(domain.getUsername());
        doObj.setPasswordHash(domain.getPasswordHash());
        doObj.setDisplayName(domain.getDisplayName());
        doObj.setStudentNo(domain.getStudentNo());
        doObj.setDepartmentId(domain.getDepartmentId());
        doObj.setSessionToken(domain.getSessionToken());
        doObj.setTokenExpiry(domain.getTokenExpiry());
        doObj.setStatus(domain.getStatus());
        doObj.setCreateTime(domain.getCreateTime());
        doObj.setUpdateTime(domain.getUpdateTime());
        return doObj;
    }
}
