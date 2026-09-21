package com.mouhin.knowledge.repository.client.dto;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存答题请求
 *
 * @author Knowledge-Repository
 * @date 2026-09-15
 */
@Getter
@Setter
public class SaveAnswersRequest {

    /** 考生令牌 */
    private String token;

    /** 答题列表，每项包含 questionIndex、questionType、content、answer */
    private List<Map<String, String>> answers;
}
