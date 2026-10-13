package com.mouhin.knowledge.repository.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mouhin.knowledge.repository.client.dto.SearchCmd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SearchCmd} 身份字段反序列化契约测试（P0-T1 加固回归）。
 *
 * <p>锁定：客户端请求体伪造的 {@code userId}/{@code departmentId}/{@code roles}/{@code admin} 一律被 Jackson 忽略；
 * 业务参数 {@code query}/{@code maxResults}/{@code minScore}/{@code category} 正常绑定。 setter 仍可由 adapter
 * 层从令牌主体显式写入，保证"只信令牌回读"的语义。
 *
 * @author mouhinU
 * @date 2026-09-29
 */
@DisplayName("SearchCmd 身份字段反序列化契约 (P0-T1)")
class SearchCmdIdentityBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("请求体伪造 userId/departmentId/roles/admin → 反序列化后全部为空/false")
    void spoofedIdentityFieldsIgnored() throws Exception {
        String body =
                "{\"query\":\"向量检索\","
                        + "\"userId\":\"attacker\","
                        + "\"departmentId\":\"dept-x\","
                        + "\"roles\":\"SUPERUSER,OWNER\","
                        + "\"admin\":true,"
                        + "\"maxResults\":5,"
                        + "\"minScore\":0.9,"
                        + "\"category\":\"工作\"}";

        SearchCmd cmd = mapper.readValue(body, SearchCmd.class);

        assertThat(cmd.getQuery()).isEqualTo("向量检索");
        assertThat(cmd.getMaxResults()).isEqualTo(5);
        assertThat(cmd.getMinScore()).isEqualTo(0.9);
        assertThat(cmd.getCategory()).isEqualTo("工作");

        assertThat(cmd.getUserId()).isNull();
        assertThat(cmd.getDepartmentId()).isNull();
        assertThat(cmd.getRoles()).isNull();
        assertThat(cmd.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("adapter 层 setter 写入仍生效：Java 语义不受 Jackson 注解影响")
    void serverSideSettersStillWork() {
        SearchCmd cmd = new SearchCmd();
        cmd.setUserId("u-real");
        cmd.setDepartmentId("d-real");
        cmd.setRoles("TEACHER");
        cmd.setAdmin(true);

        assertThat(cmd.getUserId()).isEqualTo("u-real");
        assertThat(cmd.getDepartmentId()).isEqualTo("d-real");
        assertThat(cmd.getRoles()).isEqualTo("TEACHER");
        assertThat(cmd.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("序列化：DTO 输出保留身份字段供日志/审计；READ_ONLY 只影响反序列化方向")
    void serializationStillEmitsIdentity() throws Exception {
        SearchCmd cmd = new SearchCmd();
        cmd.setQuery("q");
        cmd.setUserId("u-1");
        cmd.setDepartmentId("d-1");
        cmd.setRoles("TEACHER");

        String json = mapper.writeValueAsString(cmd);

        assertThat(json).contains("\"userId\":\"u-1\"");
        assertThat(json).contains("\"departmentId\":\"d-1\"");
        assertThat(json).contains("\"roles\":\"TEACHER\"");
    }
}
