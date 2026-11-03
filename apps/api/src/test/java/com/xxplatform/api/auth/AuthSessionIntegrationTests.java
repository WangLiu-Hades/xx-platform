package com.xxplatform.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.PasswordHasher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 登录/会话/CSRF/改密/审计的真库 + 真 HTTP 集成测试（M01、M02、M03、M04、M20）。
 *
 * <p><b>为什么必须用真实 Tomcat 而不是 MockMvc</b>：这几条规则里有一半发生在容器与过滤器层
 * ——{@code Set-Cookie} 的 SameSite、{@code Cache-Control}、{@code X-Forwarded-For} 的取值、
 * {@code Origin} 校验，以及"后台轮询不续期"所依赖的 {@code request.getRequestURI()}。
 * MockMvc 里这些都是模拟值，测出来的"通过"证明不了浏览器/代理下的行为。
 *
 * <p><b>为什么用真实数据库</b>：M01 的验收标准不是 HTTP 200，而是
 * {@code idle_expires_at / last_seen_at / absolute_expires_at} 与 DB {@code now()} 的关系；
 * M20 的验收标准是 {@code audit.audit_event} 里真的落了行。这两件事只有真库能答。
 *
 * <p><b>为什么每个用例都自建账号</b>：{@code audit.audit_event} 有 append-only 触发器，
 * 清不掉历史行；靠"清表"保证隔离是不可能的。改用每次唯一的登录名
 * （{@code it.auth.<random>}）后，审计断言按 actor 快照过滤，天然互不干扰。
 *
 * <p><b>测试配置</b>：{@code --xx.session.trusted-proxies=127.0.0.0/8} 让本机充当受信代理，
 * 从而可以用 {@code X-Forwarded-For} 模拟不同客户端 IP；{@code allowed-networks} 只放行
 * 回环与 10/8，8.8.8.8 这类公网地址会命中"来源不在允许范围"的拒绝路径。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class AuthSessionIntegrationTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PASSWORD = "ItInitialPassw0rd!2026";
    private static final String NEW_PASSWORD = "ItChangedPassw0rd!2026";
    private static final String OTHER_PASSWORD = "ItAnotherPassw0rd!2026";
    private static final String UA = "it-agent/1.0";
    private static final String INITIAL_PASSWORD_MESSAGE = "首次登录必须先修改初始密码";

    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static PasswordHasher hasher;
    private static String baseUrl;
    private static int port;
    private static HttpClient http;

    /** 口令哈希每次都带随机盐，但 PBKDF2-600000 约 600ms；同一明文复用同一哈希可省掉十几秒。 */
    private static final Map<String, String> HASH_CACHE = new ConcurrentHashMap<>();

    @BeforeAll
    static void startContext() {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        String user = System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", "");
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("db")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + password,
                        "--xx.task.reaper-enabled=false",
                        // 只放行回环与 10/8：既让本机请求通过，又能用 8.8.8.8 触发"来源拒绝"。
                        "--xx.session.allowed-networks=127.0.0.0/8,::1/128,10.0.0.0/8",
                        // 本机充当受信代理，测试才能用 XFF 模拟客户端 IP；默认配置里这里是空的。
                        "--xx.session.trusted-proxies=127.0.0.0/8,::1/128");
        jdbc = context.getBean(JdbcTemplate.class);
        hasher = context.getBean(PasswordHasher.class);
        port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
        baseUrl = "http://127.0.0.1:" + port + "/api/v1";
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        // 默认值与部署配置一致（15 分钟空闲、8 小时绝对）；若被别的测试改过，这里复位。
        SessionProperties properties = context.getBean(SessionProperties.class);
        assertEquals(Duration.ofMinutes(15), properties.getIdleTtl());
        assertEquals(Duration.ofHours(8), properties.getAbsoluteTtl());
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // ======================= M01：空闲期只由主动请求滑动 =======================

    @Test
    void idleSlidesOnActiveRequestsButNotOnPollingPathsOrBackgroundHeader() throws Exception {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        OffsetDateTime idleAtLogin = latestSession(user.id()).idleExpiresAt();

        Thread.sleep(1200);
        assertEquals(200, get("/me", headers(signed, UA)).statusCode());
        OffsetDateTime idleAfterActive = latestSession(user.id()).idleExpiresAt();
        assertTrue(idleAfterActive.isAfter(idleAtLogin.plusNanos(500_000_000)),
                "主动业务请求必须滑动空闲期：" + idleAtLogin + " -> " + idleAfterActive);

        // 服务端白名单里的轮询读路径（GET /tasks/{id}）不续期——不依赖任何客户端自报头。
        Thread.sleep(1200);
        HttpResponse<String> polling = get("/tasks/" + UUID.randomUUID(), headers(signed, UA));
        assertNotEquals(401, polling.statusCode(), "会话必须仍然有效（轮询只是不续期）");
        assertEquals(idleAfterActive, latestSession(user.id()).idleExpiresAt(),
                "轮询路径不得滑动空闲期");

        // 客户端自报的后台头同样不续期（对 /me 这种白名单外的路径也生效）。
        Thread.sleep(1200);
        assertEquals(200, get("/me", headers(signed, UA, "X-Background-Request", "true")).statusCode());
        assertEquals(idleAfterActive, latestSession(user.id()).idleExpiresAt(),
                "X-Background-Request: true 不得滑动空闲期");

        // 预加载提示头（Sec-Purpose: prefetch）同属"非主动"。
        Thread.sleep(1200);
        assertEquals(200, get("/me", headers(signed, UA, "Sec-Purpose", "prefetch")).statusCode());
        assertEquals(idleAfterActive, latestSession(user.id()).idleExpiresAt(),
                "预加载请求不得滑动空闲期");

        // 对照组：同一路径去掉这些头之后，续期恢复正常。
        Thread.sleep(1200);
        assertEquals(200, get("/me", headers(signed, UA)).statusCode());
        assertTrue(latestSession(user.id()).idleExpiresAt().isAfter(idleAfterActive.plusNanos(500_000_000)),
                "普通 /me 请求应继续滑动空闲期");
    }

    @Test
    void renewIsClampedToTheAbsoluteDeadlineAndKeepsSidAndCsrf() {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        // 把绝对期限压到 3 分钟后、空闲期限压到 2 秒后：续期候选值（now+15m）超界，必须被夹紧。
        jdbc.update("""
                UPDATE auth.user_session
                   SET idle_expires_at = now() + interval '2 seconds',
                       absolute_expires_at = now() + interval '3 minutes'
                 WHERE id = ?
                """, latestSession(user.id()).id());

        HttpResponse<String> renew = post("/auth/session/renew", headers(signed, UA), null);
        assertEquals(200, renew.statusCode(), renew.body());

        SessionRow after = latestSession(user.id());
        assertFalse(after.idleExpiresAt().isAfter(after.absoluteExpiresAt()),
                "空闲期不得越过绝对期限");
        long gapMillis = Math.abs(Duration.between(after.idleExpiresAt(), after.absoluteExpiresAt()).toMillis());
        assertTrue(gapMillis <= 1000, "续期应夹到绝对期限，实际差值 " + gapMillis + "ms");

        // 常规续期不轮换 SID/CSRF：响应里没有新的 Set-Cookie，旧凭据继续可用。
        assertNull(setCookie(renew, "SID"), "常规续期不得重发 SID");
        assertNull(setCookie(renew, "CSRFID"), "常规续期不得重发 CSRF");
        assertEquals(200, get("/me", headers(signed, UA)).statusCode(), "续期后旧 SID 仍有效");
        assertEquals(200, post("/auth/logout", headers(signed, UA), null).statusCode(),
                "续期后旧 CSRF 对仍有效（否则每次续期都要前端重新取 token）");
    }

    @Test
    void expiredSessionCannotBeRevivedEvenWhenLastSeenIsFresh() {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        // last_seen_at 是"刚刚"（落在 60 秒节流窗口内），但 idle 已经过期：
        // 严格到期必须只看 idle_expires_at，不能被节流的 last_seen 拖后。
        jdbc.update("""
                UPDATE auth.user_session
                   SET idle_expires_at = now() - interval '1 second', last_seen_at = now()
                 WHERE id = ?
                """, latestSession(user.id()).id());
        SessionRow expired = latestSession(user.id());

        HttpResponse<String> me = get("/me", headers(signed, UA));
        assertEquals(401, me.statusCode());
        assertEquals("AUTH_SESSION_EXPIRED", code(me));
        assertEquals(expired.idleExpiresAt(), latestSession(user.id()).idleExpiresAt(),
                "过期判定不得顺手写长空闲期");
        assertEquals(expired.lastSeenAt(), latestSession(user.id()).lastSeenAt());

        HttpResponse<String> renew = post("/auth/session/renew", headers(signed, UA), null);
        assertEquals(401, renew.statusCode(), "过期会话不能被 renew 复活");
        assertEquals(expired.idleExpiresAt(), latestSession(user.id()).idleExpiresAt());
    }

    // ======================= M02：来源 CIDR、客户端绑定、可信代理 =======================

    @Test
    void loginOutsideConfiguredCidrIsRejectedAndAudited() {
        User user = createUser(false);
        Csrf csrf = getCsrf();

        HttpResponse<String> denied = login(user.login(), user.password(), csrf, UA, "8.8.8.8");
        assertEquals(403, denied.statusCode(), denied.body());
        assertEquals("PERMISSION_DENIED", code(denied));

        Map<String, Object> event = latestAudit(user.login(), "auth.login", "FAILURE");
        assertEquals("PERMISSION_DENIED", event.get("failure_code"));
        assertTrue(String.valueOf(event.get("after_summary")).contains("SOURCE_NETWORK_DENIED"),
                "审计必须能区分「来源不在允许 CIDR」：" + event.get("after_summary"));
        assertEquals("8.8.8.8", event.get("source_ip"), "审计的 source_ip 必须是解析后的真实客户端地址");
        assertEquals(traceId(denied), event.get("trace_id"));

        // 允许网段内的地址可以登录。
        Signed ok = signIn(user, UA, "10.1.2.3");
        assertTrue(latestSession(user.id()).clientLabel().endsWith("|ip=10.1.2.3"),
                "登录时必须把解析后的客户端 IP 写进会话绑定：" + latestSession(user.id()).clientLabel());
        assertEquals(200, get("/me", headers(ok, UA, "X-Forwarded-For", "10.1.2.3")).statusCode());
    }

    @Test
    void userAgentChangeRequiresReloginAndIsAudited() {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        assertEquals(200, get("/me", headers(signed, UA)).statusCode());

        HttpResponse<String> changed = get("/me", headers(signed, "it-agent/2.0"));
        assertEquals(401, changed.statusCode());
        assertEquals("AUTH_SESSION_EXPIRED", code(changed));

        Map<String, Object> event = latestAudit(user.login(), "auth.session_rejected", "FAILURE");
        assertEquals("AUTH_SESSION_EXPIRED", event.get("failure_code"));
        assertTrue(String.valueOf(event.get("after_summary")).contains("USER_AGENT_MISMATCH"));
        assertNull(latestSession(user.id()).revokedAt(), "只是拒绝本次请求，不应把会话标成已撤销");
        assertEquals(200, get("/me", headers(signed, UA)).statusCode(), "原 UA 仍然可用");
    }

    @Test
    void clientIpChangeRequiresReloginAndIsAudited() {
        User user = createUser(false);
        Signed signed = signIn(user, UA, "10.1.2.3");

        HttpResponse<String> changed = get("/me", headers(signed, UA, "X-Forwarded-For", "10.1.2.4"));
        assertEquals(401, changed.statusCode());
        assertEquals("AUTH_SESSION_EXPIRED", code(changed));

        Map<String, Object> event = latestAudit(user.login(), "auth.session_rejected", "FAILURE");
        assertTrue(String.valueOf(event.get("after_summary")).contains("CLIENT_IP_MISMATCH"),
                "审计必须记录 IP 变化：" + event.get("after_summary"));
        assertEquals(200, get("/me", headers(signed, UA, "X-Forwarded-For", "10.1.2.3")).statusCode());
    }

    // ======================= M03：CSRF 轮换与不可缓存 =======================

    @Test
    void csrfIssuedBeforeLoginIsRevokedByLoginAndRotatedValueWorks() {
        User user = createUser(false);
        Csrf beforeLogin = getCsrf();

        HttpResponse<String> loginResponse = login(user.login(), user.password(), beforeLogin, UA, null);
        assertEquals(200, loginResponse.statusCode(), loginResponse.body());
        String sid = setCookie(loginResponse, "SID");
        String rotatedCookie = setCookie(loginResponse, "CSRFID");
        String rotatedToken = header(loginResponse, "X-CSRF-Token");
        assertNotNull(sid);
        assertNotNull(rotatedCookie, "登录必须轮换 CSRF Cookie");
        assertNotNull(rotatedToken, "登录必须通过响应头下发新 token");
        assertNotEquals(beforeLogin.cookie(), rotatedCookie);

        // 登录前拿到的旧 token 在登录后不能再用于受保护写操作。
        Map<String, String> withOldCsrf = new LinkedHashMap<>();
        withOldCsrf.put("Cookie", "SID=" + sid + "; CSRFID=" + beforeLogin.cookie());
        withOldCsrf.put("X-CSRF-Token", beforeLogin.token());
        withOldCsrf.put("User-Agent", UA);
        HttpResponse<String> logoutWithOld = post("/auth/logout", withOldCsrf, null);
        assertEquals(403, logoutWithOld.statusCode(), "旧 CSRF 值在登录后必须失效");
        assertEquals("AUTH_CSRF_INVALID", code(logoutWithOld));

        // 轮换后的新值可用：退出成功。
        Map<String, String> withNewCsrf = new LinkedHashMap<>();
        withNewCsrf.put("Cookie", "SID=" + sid + "; CSRFID=" + rotatedCookie);
        withNewCsrf.put("X-CSRF-Token", rotatedToken);
        withNewCsrf.put("User-Agent", UA);
        assertEquals(200, post("/auth/logout", withNewCsrf, null).statusCode());
    }

    @Test
    void credentialResponsesAreNotCacheable() {
        HttpResponse<String> csrf = get("/auth/csrf", Map.of());
        assertTrue(header(csrf, "Cache-Control").toLowerCase().contains("no-store"),
                "GET /auth/csrf 必须 no-store，实际 " + header(csrf, "Cache-Control"));

        User user = createUser(false);
        Csrf prefix = getCsrf();
        HttpResponse<String> login = login(user.login(), user.password(), prefix, UA, null);
        assertTrue(header(login, "Cache-Control").toLowerCase().contains("no-store"),
                "登录响应含有新凭据，同样不得被缓存，实际 " + header(login, "Cache-Control"));
    }

    @Test
    void originIsCheckedBothWaysOnReadAndWrite() {
        HttpResponse<String> crossOrigin = get("/auth/csrf", Map.of("Origin", "https://evil.example"));
        assertEquals(403, crossOrigin.statusCode());
        assertEquals("AUTH_CSRF_INVALID", code(crossOrigin));

        HttpResponse<String> sameOrigin = get("/auth/csrf",
                Map.of("Origin", "http://127.0.0.1:" + port));
        assertEquals(200, sameOrigin.statusCode());

        // 写请求同理：跨源即使带了合法 CSRF 对也要拒。
        User user = createUser(false);
        Csrf csrf = getCsrf();
        HttpResponse<String> crossWrite = login(user.login(), user.password(), csrf, UA, null,
                "https://evil.example");
        assertEquals(403, crossWrite.statusCode());
        assertEquals("AUTH_CSRF_INVALID", code(crossWrite));
    }

    // ======================= M04：初始口令闸门与本人改密 =======================

    @Test
    void initialPasswordUserIsGatedAndUnblockedByChangingPassword() throws Exception {
        User user = createUser(true);
        Signed signed = signIn(user, UA, null);

        assertEquals(200, get("/me", headers(signed, UA)).statusCode(), "/me 属于必要入口，必须放行");

        HttpResponse<String> gated = get("/tasks", headers(signed, UA));
        assertEquals(403, gated.statusCode());
        assertEquals("PERMISSION_DENIED", code(gated));
        assertEquals(INITIAL_PASSWORD_MESSAGE, message(gated));

        String key = "it-gate-" + UUID.randomUUID();
        HttpResponse<String> wrongCurrent = changePassword(signed, "not-the-current-one", NEW_PASSWORD, key);
        assertEquals(401, wrongCurrent.statusCode(), "当前口令错必须是 401 AUTH_INVALID_CREDENTIALS");
        assertEquals("AUTH_INVALID_CREDENTIALS", code(wrongCurrent));
        assertEquals("currentPassword", wrongCurrent.body().isEmpty() ? ""
                : MAPPER.readTree(wrongCurrent.body()).path("details").path(0).path("field").asText(""));
        // 失败改密不得解除闸门。
        assertEquals(INITIAL_PASSWORD_MESSAGE, message(get("/tasks", headers(signed, UA))));

        HttpResponse<String> changed = changePassword(signed, user.password(), NEW_PASSWORD, key);
        assertEquals(200, changed.statusCode(), changed.body());
        assertTrue(MAPPER.readTree(changed.body()).path("data").path("accepted").asBoolean(),
                "成功响应体必须是 ActionResult{accepted}" );
        String rotatedSid = setCookie(changed, "SID");
        assertNotNull(rotatedSid, "改密必须轮换 SID");
        assertEquals(403, post("/auth/logout", headers(signed, UA), null).statusCode(),
                "改密前的 CSRF 对必须作废");

        // 旧 SID 已撤销，新 SID 可用。
        assertEquals(401, get("/me", headers(signed, UA)).statusCode());
        Map<String, String> fresh = new LinkedHashMap<>();
        fresh.put("Cookie", "SID=" + rotatedSid);
        fresh.put("User-Agent", UA);
        assertEquals(200, get("/me", fresh).statusCode());

        // 业务接口恢复：错误码/文案不再是初始口令闸门。
        HttpResponse<String> after = get("/tasks", fresh);
        assertNotEquals(INITIAL_PASSWORD_MESSAGE, message(after),
                "改密后业务接口不应再被初始口令闸门拦住");

        // 新口令可登录，旧口令不可。
        assertEquals(200, login(user.login(), NEW_PASSWORD, getCsrf(), UA, null).statusCode());
        assertEquals(401, login(user.login(), user.password(), getCsrf(), UA, null).statusCode());

        assertNotNull(jdbc.queryForObject(
                "SELECT password_changed_at FROM auth.app_user WHERE id = ?", OffsetDateTime.class, user.id()));
    }

    @Test
    void passwordChangeRotatesCredentialsAndEnforcesIdempotency() throws Exception {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        String key = "it-chg-" + UUID.randomUUID();

        HttpResponse<String> changed = changePassword(signed, user.password(), NEW_PASSWORD, key);
        assertEquals(200, changed.statusCode(), changed.body());
        String rotatedSid = setCookie(changed, "SID");
        String rotatedCsrfCookie = setCookie(changed, "CSRFID");
        String rotatedCsrfToken = header(changed, "X-CSRF-Token");
        assertNotNull(rotatedSid);
        assertNotNull(rotatedCsrfCookie);

        // 旧 SID 立刻失效；同一时刻该用户只有一个未撤销会话。
        assertEquals(401, get("/me", headers(signed, UA)).statusCode());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM auth.user_session
                 WHERE user_id = ? AND revoked_at IS NULL
                """, Integer.class, user.id()));

        // 新凭据可用。
        Map<String, String> fresh = new LinkedHashMap<>();
        fresh.put("Cookie", "SID=" + rotatedSid + "; CSRFID=" + rotatedCsrfCookie);
        fresh.put("X-CSRF-Token", rotatedCsrfToken);
        fresh.put("User-Agent", UA);
        assertEquals(200, get("/me", fresh).statusCode());

        // 同 key 同内容重放：返回原结果，不再产生副作用（不再轮换、不再改密）。
        int sessionsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM auth.user_session WHERE user_id = ?", Integer.class, user.id());
        HttpResponse<String> replay = changePassword(fresh, user.password(), NEW_PASSWORD, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertTrue(MAPPER.readTree(replay.body()).path("data").path("accepted").asBoolean());
        assertNull(setCookie(replay, "SID"), "重放不得再次轮换 SID");
        assertEquals(sessionsBefore, jdbc.queryForObject(
                "SELECT count(*) FROM auth.user_session WHERE user_id = ?", Integer.class, user.id()));

        // 同 key 不同内容：409。
        HttpResponse<String> conflict = changePassword(fresh, user.password(), OTHER_PASSWORD, key);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertEquals("IDEMPOTENCY_CONFLICT", code(conflict));

        // 缺 Idempotency-Key：400 VALIDATION_ERROR（契约把该头声明为必填）。
        Map<String, String> noKey = new LinkedHashMap<>(fresh);
        HttpResponse<String> missing = post("/me/password", noKey,
                "{\"currentPassword\":\"" + user.password() + "\",\"newPassword\":\"" + OTHER_PASSWORD + "\"}");
        assertEquals(400, missing.statusCode(), missing.body());
        assertEquals("VALIDATION_ERROR", code(missing));
        assertEquals("Idempotency-Key",
                MAPPER.readTree(missing.body()).path("details").path(0).path("field").asText(""));
    }

    // ======================= M20：正式审计事件 =======================

    @Test
    void loginLogoutAndPasswordChangeLeaveAuditEvents() {
        User user = createUser(false);

        HttpResponse<String> failed = login(user.login(), "definitely-wrong-pass", getCsrf(), UA, null);
        assertEquals(401, failed.statusCode());
        Map<String, Object> loginFailure = latestAudit(user.login(), "auth.login", "FAILURE");
        assertEquals("AUTH_INVALID_CREDENTIALS", loginFailure.get("failure_code"));
        assertTrue(String.valueOf(loginFailure.get("after_summary")).contains("BAD_PASSWORD"));
        assertEquals("127.0.0.1", loginFailure.get("source_ip"));

        Signed signed = signIn(user, UA, null);
        Map<String, Object> loginSuccess = latestAudit(user.login(), "auth.login", "SUCCESS");
        assertEquals(user.login(), loginSuccess.get("actor_login_name_snapshot"));
        assertEquals("认证集成测试", loginSuccess.get("actor_display_name_snapshot"));
        assertEquals("127.0.0.1", loginSuccess.get("source_ip"));
        assertNotNull(loginSuccess.get("trace_id"));

        assertEquals(200, post("/auth/logout", headers(signed, UA), null).statusCode());
        Map<String, Object> logout = latestAudit(user.login(), "auth.logout", "SUCCESS");
        assertEquals(user.login(), logout.get("actor_login_name_snapshot"));
        assertEquals("127.0.0.1", logout.get("source_ip"));

        // 改密失败与成功都必须留下正式事件，且失败事件没有被事务回滚吞掉。
        Signed again = signIn(user, UA, null);
        String key = "it-audit-" + UUID.randomUUID();
        assertEquals(401, changePassword(again, "wrong-current", NEW_PASSWORD, key).statusCode());
        Map<String, Object> changeFailure = latestAudit(user.login(), "user.password_change", "FAILURE");
        assertEquals("AUTH_INVALID_CREDENTIALS", changeFailure.get("failure_code"));
        assertEquals("127.0.0.1", changeFailure.get("source_ip"));

        assertEquals(200, changePassword(again, user.password(), NEW_PASSWORD, key).statusCode());
        Map<String, Object> changeSuccess = latestAudit(user.login(), "user.password_change", "SUCCESS");
        assertEquals("127.0.0.1", changeSuccess.get("source_ip"));
        assertFalse(String.valueOf(changeSuccess.get("after_summary")).contains(NEW_PASSWORD),
                "审计快照不得包含口令明文");

        // append-only：审计表不允许 UPDATE / DELETE。
        UUID eventId = (UUID) loginSuccess.get("id");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE audit.audit_event SET result = 'SUCCESS' WHERE id = ?", eventId),
                "audit.audit_event 必须拒绝 UPDATE（append-only 触发器）");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "DELETE FROM audit.audit_event WHERE id = ?", eventId),
                "audit.audit_event 必须拒绝 DELETE");
    }

    // ======================= 基础设施 =======================

    private record User(UUID id, String login, String password) {
    }

    @Test
    void longUserAgentRemainsBoundAfterLoginAndSuffixChangesAreRejected() {
        User user = createUser(false);
        String longUa = "A".repeat(150) + "B";
        Signed signed = signIn(user, longUa, null);
        assertEquals(200, get("/me", headers(signed, longUa)).statusCode());
        assertEquals(401, get("/me", headers(signed, "A".repeat(150) + "C")).statusCode());
        assertEquals(200, get("/me", headers(signed, longUa)).statusCode());
    }

    @Test
    void legacySessionWithoutCompleteBindingRequiresNewLogin() {
        User user = createUser(false);
        Signed signed = signIn(user, UA, null);
        jdbc.update("UPDATE auth.user_session SET client_label=NULL WHERE user_id=?", user.id());
        assertEquals(401, get("/me", headers(signed, UA)).statusCode());
        signed = signIn(user, UA, null);
        jdbc.update("UPDATE auth.user_session SET client_label=? WHERE user_id=?", UA, user.id());
        assertEquals(401, get("/me", headers(signed, UA)).statusCode());
    }

    private record Csrf(String cookie, String token) {
    }

    private record Signed(String sid, Csrf csrf) {
    }

    private record SessionRow(UUID id, OffsetDateTime lastSeenAt, OffsetDateTime idleExpiresAt,
                              OffsetDateTime absoluteExpiresAt, String clientLabel,
                              OffsetDateTime revokedAt) {
    }

    private static User createUser(boolean initialPassword) {
        UUID id = UUID.randomUUID();
        String login = "it.auth." + id.toString().replace("-", "").substring(0, 10);
        String hash = HASH_CACHE.computeIfAbsent(PASSWORD, hasher::hash);
        jdbc.update("""
                INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash,
                                           password_changed_at)
                VALUES (?, ?, ?, '认证集成测试', ?, ?)
                """, id, "IT-" + login, login, hash,
                initialPassword ? null : OffsetDateTime.now());
        return new User(id, login, PASSWORD);
    }

    private static Csrf getCsrf() {
        HttpResponse<String> response = get("/auth/csrf", Map.of());
        assertEquals(200, response.statusCode(), response.body());
        // 契约里 GET /auth/csrf 把 token 放在响应体（data.token），登录响应才额外用响应头
        // 复用同一个 token 值；这里两种都接受，测试不依赖发放在哪一侧。
        String token = header(response, "X-CSRF-Token");
        if (token.isBlank()) {
            token = tree(response).path("data").path("token").asText("");
        }
        return new Csrf(setCookie(response, "CSRFID"), token);
    }

    private static Signed signIn(User user, String userAgent, String forwardedFor) {
        HttpResponse<String> response = login(user.login(), user.password(), getCsrf(), userAgent, forwardedFor);
        assertEquals(200, response.statusCode(), response.body());
        return new Signed(setCookie(response, "SID"),
                new Csrf(setCookie(response, "CSRFID"), header(response, "X-CSRF-Token")));
    }

    private static HttpResponse<String> login(String loginName, String password, Csrf csrf,
                                              String userAgent, String forwardedFor) {
        return login(loginName, password, csrf, userAgent, forwardedFor, null);
    }

    private static HttpResponse<String> login(String loginName, String password, Csrf csrf,
                                              String userAgent, String forwardedFor, String origin) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Cookie", "CSRFID=" + csrf.cookie());
        headers.put("X-CSRF-Token", csrf.token());
        if (userAgent != null) {
            headers.put("User-Agent", userAgent);
        }
        if (forwardedFor != null) {
            headers.put("X-Forwarded-For", forwardedFor);
        }
        if (origin != null) {
            headers.put("Origin", origin);
        }
        return post("/auth/login", headers,
                "{\"loginName\":\"" + loginName + "\",\"password\":\"" + password + "\"}");
    }

    private static HttpResponse<String> changePassword(Map<String, String> baseHeaders,
                                                       String current, String next, String key) {
        Map<String, String> headers = new LinkedHashMap<>(baseHeaders);
        headers.put("Content-Type", "application/json");
        headers.put("Idempotency-Key", key);
        return post("/me/password", headers,
                "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private static HttpResponse<String> changePassword(Signed signed, String current, String next, String key) {
        return changePassword(headers(signed, UA), current, next, key);
    }

    private static Map<String, String> headers(Signed signed, String userAgent, String... extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cookie", "SID=" + signed.sid() + "; CSRFID=" + signed.csrf().cookie());
        headers.put("X-CSRF-Token", signed.csrf().token());
        if (userAgent != null) {
            headers.put("User-Agent", userAgent);
        }
        for (int i = 0; i + 1 < extra.length; i += 2) {
            headers.put(extra[i], extra[i + 1]);
        }
        return headers;
    }

    private static HttpResponse<String> get(String path, Map<String, String> headers) {
        return send("GET", path, headers, null);
    }

    private static HttpResponse<String> post(String path, Map<String, String> headers, String body) {
        return send("POST", path, headers, body);
    }

    private static HttpResponse<String> send(String method, String path, Map<String, String> headers,
                                             String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20));
        headers.forEach(builder::header);
        if (body != null && headers.keySet().stream()
                .noneMatch(name -> name.equalsIgnoreCase("Content-Type"))) {
            // 缺 Content-Type 时容器会当成 application/octet-stream，Spring 直接 415→500，
            // 掩盖真正的断言失败。默认按 JSON 发。
            builder.header("Content-Type", "application/json");
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " 失败", e);
        }
    }

    private static String setCookie(HttpResponse<String> response, String name) {
        for (String raw : response.headers().allValues("set-cookie")) {
            if (raw.startsWith(name + "=")) {
                String value = raw.substring(name.length() + 1);
                int semicolon = value.indexOf(';');
                return semicolon < 0 ? value : value.substring(0, semicolon);
            }
        }
        return null;
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }

    private static String code(HttpResponse<String> response) {
        return tree(response).path("code").asText("");
    }

    private static String message(HttpResponse<String> response) {
        return tree(response).path("message").asText("");
    }

    private static String traceId(HttpResponse<String> response) {
        return tree(response).path("traceId").asText("");
    }

    private static JsonNode tree(HttpResponse<String> response) {
        try {
            return MAPPER.readTree(response.body() == null ? "{}" : response.body());
        } catch (Exception e) {
            throw new IllegalStateException("响应不是 JSON：" + response.body(), e);
        }
    }

    private static SessionRow latestSession(UUID userId) {
        return jdbc.queryForObject("""
                SELECT id, last_seen_at, idle_expires_at, absolute_expires_at, client_label, revoked_at
                  FROM auth.user_session
                 WHERE user_id = ?
                 ORDER BY created_at DESC
                 LIMIT 1
                """, (rs, index) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("last_seen_at", OffsetDateTime.class),
                rs.getObject("idle_expires_at", OffsetDateTime.class),
                rs.getObject("absolute_expires_at", OffsetDateTime.class),
                rs.getString("client_label"),
                rs.getObject("revoked_at", OffsetDateTime.class)), userId);
    }

    private static Map<String, Object> latestAudit(String loginName, String action, String result) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, action, result, failure_code, host(source_ip) AS source_ip, trace_id,
                       actor_login_name_snapshot, actor_display_name_snapshot,
                       after_summary::text AS after_summary
                  FROM audit.audit_event
                 WHERE actor_login_name_snapshot = ? AND action = ? AND result = ?
                 ORDER BY occurred_at DESC, id DESC
                """, loginName, action, result);
        assertFalse(rows.isEmpty(), "缺少审计事件 " + action + "/" + result + " actor=" + loginName);
        return rows.get(0);
    }
}
