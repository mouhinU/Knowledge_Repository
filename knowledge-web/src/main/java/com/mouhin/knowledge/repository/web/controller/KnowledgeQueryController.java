package com.mouhin.knowledge.repository.web.controller;

import com.mouhin.knowledge.repository.client.api.KnowledgeQueryServiceI;
import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import com.mouhin.knowledge.repository.client.dto.SearchResponseVO;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库查询控制器（adapter 层）
 *
 * @author mouhinU
 * @date 2026-09-02
 */
@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeQueryController {

    private final KnowledgeQueryServiceI queryService;

    public KnowledgeQueryController(KnowledgeQueryServiceI queryService) {
        this.queryService = queryService;
    }

    /**
     * 语义检索知识库
     *
     * @param body 请求体：query, userId, departmentId, roles, admin, maxResults, minScore, category
     */
    @PostMapping("/search")
    public ResponseEntity<Object> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        String userId = (String) body.get("userId");
        String departmentId = (String) body.get("departmentId");
        Object rolesObj = body.get("roles");
        String roles;
        if (rolesObj instanceof List<?> list) {
            roles = String.join(",", list.stream().map(Object::toString).toList());
        } else {
            roles = (String) rolesObj;
        }
        boolean admin = body.get("admin") != null && (Boolean) body.get("admin");
        Integer maxResults =
                body.get("maxResults") != null
                        ? ((Number) body.get("maxResults")).intValue()
                        : null;
        Double minScore =
                body.get("minScore") != null ? ((Number) body.get("minScore")).doubleValue() : null;
        String category = (String) body.get("category");

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "errorCode", "BAD_REQUEST",
                                    "errorMessage", "userId is required"));
        }

        SearchCmd cmd = new SearchCmd();
        cmd.setQuery(query);
        cmd.setUserId(userId);
        cmd.setDepartmentId(departmentId);
        cmd.setRoles(roles);
        cmd.setAdmin(admin);
        cmd.setMaxResults(maxResults);
        cmd.setMinScore(minScore);
        cmd.setCategory(category);

        SearchResponseVO response = queryService.search(cmd);
        log.debug("knowledge search returned {} results", response.getTotalResults());
        return ResponseEntity.ok(response);
    }

    /** 获取知识库分类列表 */
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        List<Map<String, Object>> categories =
                List.of(
                        Map.of("name", "工作", "sortOrder", 1),
                        Map.of("name", "学习", "sortOrder", 2),
                        Map.of("name", "休闲", "sortOrder", 3),
                        Map.of("name", "其他", "sortOrder", 4));
        return ResponseEntity.ok(categories);
    }
}
