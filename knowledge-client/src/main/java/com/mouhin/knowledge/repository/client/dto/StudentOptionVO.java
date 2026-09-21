package com.mouhin.knowledge.repository.client.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 考生下拉选项视图对象（错题本筛选用）
 *
 * <p>字段顺序与键名对齐原 {@code WrongAnswerController.listStudents}：
 * id、username、displayName、studentNo。displayName / studentNo 保持原始值（可为 null）， 不做兜底转换。
 *
 * @author Knowledge-Repository
 * @date 2026-09-17
 */
@Getter
@Setter
public class StudentOptionVO {

    private Long id;
    private String username;
    private String displayName;
    private String studentNo;
}
