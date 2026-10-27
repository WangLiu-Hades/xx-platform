package com.xxplatform.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.config.CsrfProperties;
import com.xxplatform.api.service.CsrfTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CsrfFilter} 的闸门行为。
 *
 * <p>这些用例不启动 Spring 上下文：闸门的全部判断都只依赖 request/response 与
 * {@link CsrfTokenService}，用一个手写桩替代 token 查询，就能把"哪些请求被拦、
 * 拦截时返回什么"钉死，而不必准备数据库（{@code verify-csrf.ps1} 负责真实 HTTP 那一层）。
 *
 * <p>不用 Mockito：本机 JDK 17 下 inline mock maker 无法自附加 agent
 * （{@code Could not self-attach to current VM}），仓库里其他测试也都用手写桩。
 */
class CsrfFilterTests {

    private static final String BASE = "/api/v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubCsrfTokenService csrfTokenService;
    private CsrfFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        csrfTokenService = new StubCsrfTokenService();
        filter = new CsrfFilter(csrfTokenService, objectMapper);
        request = newRequest("POST", "/tasks");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    @DisplayName("读请求不进闸门：GET 直接放行，不查 token")
    void readRequestsAreNotGated() throws Exception {
        request = newRequest("GET", "/tasks");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(csrfTokenService.validateCalls).isZero();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST 无 Cookie 无 token -> 403 AUTH_CSRF_INVALID 且响应是契约壳")
    void postWithoutTokenIsRejected() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("AUTH_CSRF_INVALID");
        assertThat(body.path("message").asText()).isEqualTo("请求来源不可信");
        assertThat(body.has("data")).isTrue();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.path("details").isArray()).isTrue();
        assertThat(body.path("traceId").asText()).isNotBlank();
        // 两个要素都可能为 null，闸门照样要查（不能"没 Cookie 就直接放行"）
        assertThat(csrfTokenService.lastCookie).isNull();
        assertThat(csrfTokenService.lastToken).isNull();
    }

    @Test
    @DisplayName("有效 Cookie + 头 -> 放行")
    void postWithValidTokenPairPasses() throws Exception {
        request.setCookies(new Cookie("CSRFID", "cookie-value"));
        request.addHeader("X-CSRF-Token", "token-value");
        csrfTokenService.valid = true;

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(csrfTokenService.lastCookie).isEqualTo("cookie-value");
        assertThat(csrfTokenService.lastToken).isEqualTo("token-value");
    }

    @Test
    @DisplayName("token 不匹配 -> 403，且不把请求交给控制器")
    void postWithWrongTokenIsRejected() throws Exception {
        request.setCookies(new Cookie("CSRFID", "cookie-value"));
        request.addHeader("X-CSRF-Token", "wrong");
        csrfTokenService.valid = false;

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0} 也在闸门内")
    @MethodSource("writeMethods")
    @DisplayName("PUT/PATCH/DELETE 与 POST 同等对待")
    void everyWriteMethodIsGated(String method) throws Exception {
        request = newRequest(method, "/tasks/t-1");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    static Stream<String> writeMethods() {
        return Stream.of("PUT", "PATCH", "DELETE");
    }

    @Test
    @DisplayName("外源 Origin 即使带着有效 token 也拒绝")
    void foreignOriginIsRejectedEvenWithValidToken() throws Exception {
        request.addHeader("Origin", "https://evil.example.com");
        request.setCookies(new Cookie("CSRFID", "cookie-value"));
        request.addHeader("X-CSRF-Token", "token-value");
        csrfTokenService.valid = true;

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("code").asText())
                .isEqualTo("AUTH_CSRF_INVALID");
    }

    @Test
    @DisplayName("同源 Origin 放行")
    void sameOriginPasses() throws Exception {
        request.addHeader("Origin", "http://127.0.0.1:8080");
        request.setCookies(new Cookie("CSRFID", "cookie-value"));
        request.addHeader("X-CSRF-Token", "token-value");
        csrfTokenService.valid = true;

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("没有 Origin 时用 Referer 判定；外源 Referer 拒绝")
    void refererFallsBackWhenOriginIsAbsent() throws Exception {
        request.addHeader("Referer", "https://evil.example.com/page");
        csrfTokenService.valid = true;

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("既无 Origin 也无 Referer 视为非浏览器客户端，但仍要 token")
    void nonBrowserStillNeedsToken() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(csrfTokenService.validateCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("/auth/login 免 token，但本机同源 Origin 仍放行")
    void loginIsTokenExempt() throws Exception {
        request = newRequest("POST", "/auth/login");
        request.addHeader("Origin", "http://127.0.0.1:8080");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(csrfTokenService.validateCalls).isZero();
    }

    @Test
    @DisplayName("/auth/login 外源 Origin -> 403")
    void loginFromForeignOriginIsRejected() throws Exception {
        request = newRequest("POST", "/auth/login");
        request.addHeader("Origin", "https://evil.example.com");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("code").asText())
                .isEqualTo("AUTH_CSRF_INVALID");
    }

    @Test
    @DisplayName("token 查询抛异常时失败关闭：500 契约壳，不放行")
    void storageFailureFailsClosed() throws Exception {
        csrfTokenService.failure = new IllegalStateException("db down");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("code").asText())
                .isEqualTo("SYSTEM_INTERNAL");
    }

    @Test
    @DisplayName("拒绝体 traceId 与 X-Trace-Id 头一致（过滤器顺序正确）")
    void rejectionCarriesRequestTraceId() throws Exception {
        FilterChain next = (req, res) -> filter.doFilter(req, res, new MockFilterChain());

        // 按真实顺序跑一遍：RequestIdFilter 先写入 traceId，闸门再拒绝。
        new RequestIdFilter(false).doFilter(request, response, next);

        String traceId = response.getHeader(RequestIdFilter.TRACE_HEADER);
        assertThat(traceId).isNotBlank();
        assertThat(objectMapper.readTree(response.getContentAsString()).path("traceId").asText())
                .isEqualTo(traceId);
    }

    private static MockHttpServletRequest newRequest(String method, String pathWithinContext) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, BASE + pathWithinContext);
        req.setContextPath(BASE);
        req.setServletPath(""); // 走 requestURI 裁剪分支，覆盖 context-path 场景
        req.setScheme("http");
        req.setServerName("127.0.0.1");
        req.setServerPort(8080);
        return req;
    }

    /**
     * 手写桩：只关心"这次查询是否通过"。
     *
     * <p>{@code repository} 传 null 是安全的——这些用例不会调用 {@code issue()}，
     * 而 {@code properties()} 需要的那份配置在构造时已注入。
     */
    private static final class StubCsrfTokenService extends CsrfTokenService {

        private boolean valid;
        private RuntimeException failure;
        private int validateCalls;
        private String lastCookie;
        private String lastToken;

        private StubCsrfTokenService() {
            super(null, new CsrfProperties());
        }

        @Override
        public boolean validate(String cookieValue, String token) {
            validateCalls++;
            lastCookie = cookieValue;
            lastToken = token;
            if (failure != null) {
                throw failure;
            }
            return valid;
        }
    }
}
