package com.xxplatform.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 骨架冒烟测试：上下文能启动 + 统一响应壳符合契约。
 *
 * <p>为什么测"未知路由"：它同时覆盖三件事——Spring 能装配、请求进得来、
 * 异常兜底把框架的 404 翻译成契约错误壳。契约对每个操作都声明了 400/401/403/500，
 * 所以错误壳是骨架必须成立的部分。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiApplicationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // 上下文加载成功即通过（本测试类本身不做业务断言）
    }

    @Test
    void unknownRouteReturnsContractErrorEnvelope() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/no-such-route", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // X-Trace-Id 头必须存在，且与响应体 traceId 相同（契约 §1）
        String headerTraceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(headerTraceId).as("响应头 X-Trace-Id").isNotBlank();

        JsonNode body = MAPPER.readTree(response.getBody());
        assertThat(body.get("code").asText()).isEqualTo("DATA_NOT_FOUND");
        assertThat(body.has("message")).isTrue();
        assertThat(body.get("traceId").asText()).isEqualTo(headerTraceId);
        // 失败响应必须显式带 data=null，且 details 必须是数组
        assertThat(body.has("data")).as("失败响应必须包含 data 字段").isTrue();
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("details").isArray()).isTrue();
    }
}
