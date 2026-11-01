package com.xxplatform.api.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.ApiApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * M21：跨源配置必须允许 {@code X-Approval-Id} 等契约头，且只对受信任 Origin 放行。
 *
 * <p><b>为什么用真实 Tomcat 的 OPTIONS 预检</b>：浏览器在发跨源审批请求（带
 * {@code X-Approval-Id}）前会先发预检；只要 {@code Access-Control-Allow-Headers}
 * 少了这个头，审批请求就在浏览器里直接失败，后端日志一条都没有。MockMvc 里的
 * CORS 断言容易被配置差异掩盖（例如把 {@code /api/v1/**} 写进 CORS 匹配，
 * 由于匹配的是 servlet 内路径，会一条都匹配不上），所以这里走真实端口。
 *
 * <p>应用启动需要数据源，故与其它真库用例一样用环境变量开关；断言本身不读写数据库。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class CorsContractTests {

    private static final List<String> TRUSTED_ORIGINS =
            List.of("http://127.0.0.1:5180", "http://localhost:5180");

    private static ConfigurableApplicationContext context;
    private static HttpClient http;
    private static String baseUrl;

    @BeforeAll
    static void startApplication() {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        String user = System.getenv("XX_TASK_IT_DB_USER");
        String password = System.getenv("XX_TASK_IT_DB_PASSWORD");
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("db")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + (user == null ? "" : user),
                        "--spring.datasource.password=" + (password == null ? "" : password));
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        baseUrl = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    @DisplayName("受信任 Origin 的预检放行 X-Approval-Id / X-CSRF-Token / Idempotency-Key")
    void preflightFromTrustedOriginAllowsContractHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/tasks/"
                        + "00000000-0000-0000-0000-000000000000"))
                .header("Origin", "http://127.0.0.1:5180")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers",
                        "content-type,x-csrf-token,idempotency-key,x-approval-id,x-trace-id")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "预检应返回 200：" + response.body());
        assertEquals("http://127.0.0.1:5180",
                response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertEquals("true",
                response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null));
        assertTrue(response.headers().firstValue("Vary").orElse("")
                        .toLowerCase(Locale.ROOT).contains("origin"),
                "响应必须 Vary: Origin，否则缓存会把一个 Origin 的判定复用给另一个");

        String allowedHeaders = response.headers()
                .firstValue("Access-Control-Allow-Headers").orElse("")
                .toLowerCase(Locale.ROOT);
        for (String header : List.of("x-approval-id", "x-csrf-token", "idempotency-key",
                "content-type", "x-trace-id")) {
            assertTrue(allowedHeaders.contains(header),
                    "Allow-Headers 缺少 " + header + "：" + allowedHeaders);
        }
        String allowedMethods = response.headers()
                .firstValue("Access-Control-Allow-Methods").orElse("").toUpperCase(Locale.ROOT);
        assertTrue(allowedMethods.contains("POST"), "预检必须允许 POST：" + allowedMethods);
        assertTrue(response.headers().firstValue("Access-Control-Max-Age").isPresent(),
                "预检应带 Max-Age（减少 OPTIONS 往返）");
    }

    @Test
    @DisplayName("非受信任 Origin 的预检被拒，且不返回任何 Access-Control-Allow-*")
    void preflightFromUntrustedOriginIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/tasks/"
                        + "00000000-0000-0000-0000-000000000000"))
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-approval-id")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode(), "非受信任 Origin 必须 403：" + response.body());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty(),
                "拒绝时不得回显 Origin");
        // 拒绝体走统一错误壳，且用契约错误码（AUTH_CSRF_INVALID），不是纯文本
        assertTrue(response.body().contains("AUTH_CSRF_INVALID"),
                "拒绝响应必须是契约错误码：" + response.body());
    }

    @Test
    @DisplayName("受信任 Origin 的实际请求带 Allow-Origin，且不是通配 *")
    void actualRequestEchoesTrustedOriginWithoutWildcard() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/health"))
                .header("Origin", "http://localhost:5180")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        String allowOrigin = response.headers()
                .firstValue("Access-Control-Allow-Origin").orElse(null);
        assertEquals("http://localhost:5180", allowOrigin);
        assertFalse("*".equals(allowOrigin), "契约禁止通配凭据 CORS");
        assertNotNull(response.headers().firstValue("Access-Control-Allow-Credentials").orElse(null));
    }

    @Test
    @DisplayName("非受信任 Origin 的实际请求同样被拒（不是只在预检里拦）")
    void actualRequestFromUntrustedOriginIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/health"))
                .header("Origin", "https://evil.example")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    @DisplayName("没有 Origin 的同源请求不受 CORS 影响")
    void sameOriginRequestWithoutOriginHeaderPasses() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/health")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }
}
