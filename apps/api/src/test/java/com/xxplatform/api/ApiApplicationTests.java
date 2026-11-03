package com.xxplatform.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 骨架冒烟测试：上下文能启动 + 统一响应壳符合契约。
 *
 * <p>为什么测"未知路由"：它同时覆盖三件事——Spring 能装配、请求进得来、
 * 异常兜底把框架的 404 翻译成契约错误壳。契约对每个操作都声明了 400/401/403/500，
 * 所以错误壳是骨架必须成立的部分。
 *
 * <p><b>为什么不用 {@code @SpringBootTest}</b>：它会给测试上下文挂上
 * {@code MockitoContextCustomizer}，而 Mockito 初始化需要 ByteBuddy 自附加 JVM 代理，
 * 在本项目的开发沙箱里被拦（{@code Could not self-attach to current VM using external
 * process}，加 {@code -Dnet.bytebuddy.experimental=true} 无效）。整个 {@code mvn test}
 * 因此从"套件里有测试失败"退化成"套件根本没跑完"。这里改为手工启动应用，
 * 用 JDK 自带 {@link HttpClient} 发请求：同样的断言，不碰 Mockito，套件在任何环境下都能出结果。
 *
 * <p><b>为什么要手工喂一个 datasource</b>：任务 4/5 加入 JdbcTemplate 仓储后，全上下文启动
 * 需要一个 {@code DataSource} bean，否则报 {@code Failed to determine a suitable driver
 * class}。单元测试不该依赖一台真实的 PostgreSQL，所以用一个"永不会连上"的连接池：
 * <ul>
 *   <li>{@code initialization-fail-timeout=-1} 让 Hikari 启动时不建连接；</li>
 *   <li>{@code minimum-idle=0} 避免后台补齐空闲连接而刷错误日志；</li>
 *   <li>{@code spring.flyway.enabled=false} 否则 Flyway 启动即连库迁移。</li>
 * </ul>
 * 本测试只做 HTTP 层断言、从不触库。需要真实数据库的验收走 {@code scripts/*.ps1}。
 *
 * <p><b>为什么覆盖参数走 {@code run(args...)} 而不是 {@code .properties(...)}</b>：
 * {@code SpringApplicationBuilder.properties()} 设置的是 <i>default properties</i>，
 * 优先级**低于** {@code application.yml}。所以 {@code properties("server.port=0")}
 * 会被 yml 里的 {@code server.port: 8080} 覆盖，测试去绑 8080——只在"没有别的进程占用
 * 8080"时才会碰巧通过，一旦本地已起着服务就必然失败。命令行参数优先级最高，才能真的拿到
 * 随机端口。这个坑很隐蔽：失败信息是端口占用，看起来像环境问题而不是测试写错。
 */
class ApiApplicationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void unknownRouteReturnsContractErrorEnvelope() throws Exception {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        // 命令行参数优先级高于 application.yml，server.port 才能真正被覆盖
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/xxplatform",
                        "--spring.datasource.username=test-placeholder",
                        "--spring.datasource.password=test-placeholder",
                        "--spring.datasource.hikari.initialization-fail-timeout=-1",
                        "--spring.datasource.hikari.minimum-idle=0",
                        "--spring.datasource.hikari.maximum-pool-size=2",
                        "--spring.flyway.enabled=false");
        try {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            // context-path 是 /api/v1，未知路由也必须落在它下面。
            URI uri = URI.create("http://127.0.0.1:" + port + "/api/v1/no-such-route");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(404);

            // X-Trace-Id 头必须存在，且与响应体 traceId 相同（契约 §1）
            String headerTraceId = response.headers().firstValue("X-Trace-Id").orElse(null);
            assertThat(headerTraceId).as("响应头 X-Trace-Id").isNotBlank();

            JsonNode body = MAPPER.readTree(response.body());
            assertThat(body.get("code").asText()).isEqualTo("DATA_NOT_FOUND");
            assertThat(body.has("message")).isTrue();
            assertThat(body.get("traceId").asText()).isEqualTo(headerTraceId);
            // 失败响应必须显式带 data=null，且 details 必须是数组
            assertThat(body.has("data")).as("失败响应必须包含 data 字段").isTrue();
            assertThat(body.get("data").isNull()).isTrue();
            assertThat(body.get("details").isArray()).isTrue();
        } finally {
            context.close();
        }
    }
}
