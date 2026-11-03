package com.xxplatform.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.infra.PasswordHasher;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real isolated PostgreSQL, SID login and bound CSRF for DEV-02 HTTP tests. */
public abstract class DataHttpTestSupport {
    private static final String PASSWORD = "Pr5TestOnlyInitialPassword!2026";
    private static final String USER_AGENT = "pr5-compatibility-tests/1";
    private static String passwordHash;

    @Autowired private TestRestTemplate client;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHasher passwords;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private ClientHttpRequestInterceptor interceptor;
    private String token;
    private UUID userId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("Full HTTP tests require XX_TASK_IT_DB_URL for a disposable PostgreSQL/PostGIS database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("xx.task.reaper-enabled", () -> "false");
        // These are random-port loopback HTTP tests. Production TLS settings are unchanged.
        registry.add("xx.csrf.secure-cookie", () -> "false");
        registry.add("xx.session.secure-cookie", () -> "false");
    }

    @BeforeEach
    void signInDataTestClient() throws Exception {
        cookies.clear();
        interceptor = (request, body, execution) -> {
            var headers = request.getHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            if (!headers.containsKey(HttpHeaders.ORIGIN)) {
                URI uri = request.getURI();
                headers.setOrigin(uri.getScheme() + "://" + uri.getRawAuthority());
            }
            if (!cookies.isEmpty() && !headers.containsKey(HttpHeaders.COOKIE)) {
                headers.set(HttpHeaders.COOKIE, cookies.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
            }
            return execution.execute(request, body);
        };
        client.getRestTemplate().getInterceptors().add(interceptor);
        userId = UUID.randomUUID();
        String login = "it.pr5." + userId.toString().replace("-", "");
        synchronized (DataHttpTestSupport.class) {
            if (passwordHash == null) passwordHash = passwords.hash(PASSWORD);
        }
        jdbc.update("""
                INSERT INTO auth.app_user(id,user_code,login_name,display_name,password_hash,password_changed_at)
                VALUES(?,?,?,?,?,now())
                """, userId, "IT-PR5-" + userId, login, "PR5 synthetic test", passwordHash);
        UUID roleId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth.role(id,role_code,name,management_all_sites,data_all_sites,own_only,scope_policy_version)
                VALUES(?,?,?,true,true,false,'v1')
                """, roleId, "IT-PR5-" + roleId, "PR5 synthetic role");
        for (String permission : List.of("ingestion:read", "ingestion:write", "ingestion:replay",
                "capture:read", "capture:export", "map:read", "task:read")) {
            jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES(?,?)", roleId, permission);
        }
        jdbc.update("INSERT INTO auth.user_role(user_id,role_id) VALUES(?,?)", userId, roleId);
        jdbc.update("INSERT INTO auth.role_data_type(role_id,data_type) VALUES(?,'PLATE')", roleId);

        ResponseEntity<String> csrf = client.getForEntity("/auth/csrf", String.class);
        assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.OK);
        acceptCookies(csrf);
        token = json.readTree(csrf.getBody()).at("/data/token").asText();
        assertThat(token).isNotBlank();
        ResponseEntity<String> signed = client.postForEntity("/auth/login",
                new HttpEntity<>(Map.of("loginName", login, "password", PASSWORD), authenticatedHeaders()), String.class);
        assertThat(signed.getStatusCode()).as("real SID login").isEqualTo(HttpStatus.OK);
        acceptCookies(signed);
        token = signed.getHeaders().getFirst("X-CSRF-Token");
        assertThat(token).as("login-rotated, session-bound CSRF").isNotBlank();
        assertThat(cookies).containsKeys("SID", "CSRFID");
        ResponseEntity<String> me = client.getForEntity("/me", String.class);
        assertThat(me.getStatusCode()).as("real session authentication").isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(me.getBody()).at("/data/user/userId").asText()).isEqualTo(userId.toString());
    }

    private void acceptCookies(ResponseEntity<?> response) {
        List<String> values = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (values == null) return;
        for (String value : values) {
            String pair = value.split(";", 2)[0];
            int separator = pair.indexOf('=');
            cookies.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
    }

    protected final HttpHeaders authenticatedHeaders() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.set("X-CSRF-Token", token);
        return headers;
    }

    protected final String csrfToken() { return token; }
    protected final UUID currentUserId() { return userId; }

    @AfterEach
    void removeOwnedClientInterceptor() {
        if (interceptor != null) client.getRestTemplate().getInterceptors().remove(interceptor);
    }
}
