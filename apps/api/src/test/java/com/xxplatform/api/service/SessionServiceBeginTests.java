package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.CsrfSessionRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionService#begin} 的单测（评审 A02：单账号单会话）。
 *
 * <p>要钉住的是**调用顺序与调用集合**，不是 SQL 文本：端到端脚本只能证明"两次登录后旧 SID 401"，
 * 而它无法区分"因为我们撤销了全部旧会话"还是"因为碰巧只撤销了提交上来那个 SID"——后者在
 * 单浏览器场景下也能让两条断言同时通过。所以这里用记录型桩断言：即使客户端没有提交 SID，
 * 也必须发生一次 `revokeAllForUser`；并且它发生在 `insert` **之前**。
 *
 * <p>不用 Mockito（沙箱禁止 JVM 自附加）：仓储是具体类，用子类覆写方法当桩；
 * {@link JdbcTemplate} 用无参构造即可（这些桩不会真的访问数据库）。
 */
class SessionServiceBeginTests {

    private static final UUID USER_ID = UUID.fromString("0c9a1f5e-5c2b-4a4e-9a4d-2f7d3b1c8a10");
    private static final String TRACE_ID = "trace-begin-1";

    private static final UserAccountRepository.UserAccountRow ACTIVE =
            new UserAccountRepository.UserAccountRow(USER_ID, "u-001", "demo.admin",
                    "演示管理员", "$2a$12$not-a-real-hash", "ACTIVE");

    private static final UserAccountRepository.UserAccountRow DISABLED =
            new UserAccountRepository.UserAccountRow(USER_ID, "u-001", "demo.admin",
                    "演示管理员", "$2a$12$not-a-real-hash", "DISABLED");

    /** 记录调用顺序的会话仓储桩。 */
    private static final class StubSessions extends UserSessionRepository {

        private final List<String> calls = new ArrayList<>();
        private int revokedBySid = 1;

        StubSessions() {
            super(new JdbcTemplate());
        }

        @Override
        public int revokeBySidHash(byte[] sidHash) {
            calls.add("revokeBySidHash");
            return revokedBySid;
        }

        @Override
        public int revokeAllForUser(UUID userId) {
            calls.add("revokeAllForUser:" + userId);
            return 1;
        }

        @Override
        public void insert(UUID id, UUID userId, byte[] sidHash, OffsetDateTime idleExpiresAt,
                           OffsetDateTime absoluteExpiresAt, String clientLabel, String traceId) {
            calls.add("insert:" + userId);
        }
    }

    /** 只实现行锁的账号仓储桩；{@code row} 为 null 表示账号查不到（并发软删）。 */
    private static final class StubAccounts extends UserAccountRepository {

        private final List<String> calls;
        private final UserAccountRepository.UserAccountRow row;

        StubAccounts(List<String> calls, UserAccountRepository.UserAccountRow row) {
            super(new JdbcTemplate());
            this.calls = calls;
            this.row = row;
        }

        @Override
        public Optional<UserAccountRow> lockById(UUID userId) {
            calls.add("lockById:" + userId);
            return Optional.ofNullable(row);
        }
    }

    private static SessionService service(StubSessions sessions, StubAccounts accounts) {
        return new SessionService(sessions, new CsrfSessionRepository(new JdbcTemplate()),
                accounts, new SessionProperties());
    }

    @Test
    @DisplayName("首次登录（客户端没有 SID）也要撤销该账号的全部旧会话")
    void beginRevokesAllSessionsEvenWithoutPresentedSid() {
        StubSessions sessions = new StubSessions();
        StubAccounts accounts = new StubAccounts(sessions.calls, ACTIVE);

        SessionService.IssuedSession issued =
                service(sessions, accounts).begin(USER_ID, TRACE_ID, "junit", null, null);

        assertEquals(List.of("lockById:" + USER_ID, "revokeAllForUser:" + USER_ID, "insert:" + USER_ID),
                sessions.calls, "必须锁行 → 撤销该账号全部会话 → 插入新会话，且没有多余的 SID 撤销");
        assertNotNull(issued.sidValue(), "必须签发 SID");
        assertEquals(43, issued.sidValue().length(), "SID 是 32 字节的 base64url 无填充（43 字符）");
        assertFalse(sessions.calls.contains("revokeBySidHash"), "没有提交 SID 时不该调用按摘要撤销");
    }

    @Test
    @DisplayName("提交的旧 SID 属于别的账号时也要撤销（共用终端换账号）")
    void beginRevokesPresentedSidEvenIfItBelongsToAnotherAccount() {
        StubSessions sessions = new StubSessions();
        StubAccounts accounts = new StubAccounts(sessions.calls, ACTIVE);

        service(sessions, accounts).begin(USER_ID, TRACE_ID, "junit", "sid-of-someone-else", null);

        assertEquals(List.of("lockById:" + USER_ID, "revokeBySidHash",
                "revokeAllForUser:" + USER_ID, "insert:" + USER_ID), sessions.calls,
                "按摘要撤销不能省：它覆盖的是呈递上来的旧 SID，可能属于另一个账号");
    }

    @Test
    @DisplayName("锁里复核发现账号已停用：抛与登录失败一致的 401，且不插入会话")
    void beginRejectsAccountDisabledAfterPasswordCheck() {
        StubSessions sessions = new StubSessions();
        StubAccounts accounts = new StubAccounts(sessions.calls, DISABLED);

        ApiException error = assertThrows(ApiException.class,
                () -> service(sessions, accounts).begin(USER_ID, TRACE_ID, "junit", null, null));

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, error.code(), "必须是登录失败的错误码，不能暴露账号存在");
        assertEquals(LoginService.FAILURE_MESSAGE, error.getMessage(), "文案必须与登录失败完全一致");
        assertFalse(sessions.calls.contains("insert:" + USER_ID), "账号不可用时不得签发会话");
    }

    @Test
    @DisplayName("账号在口令校验后被软删（查不到行）：同样 401，不插入会话")
    void beginRejectsAccountDeletedAfterPasswordCheck() {
        StubSessions sessions = new StubSessions();
        StubAccounts accounts = new StubAccounts(sessions.calls, null);

        ApiException error = assertThrows(ApiException.class,
                () -> service(sessions, accounts).begin(USER_ID, TRACE_ID, "junit", null, null));

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, error.code());
        assertFalse(sessions.calls.contains("insert:" + USER_ID));
    }

    @Test
    @DisplayName("会话 TTL 取自 xx.session 配置（默认空闲 15 分钟、绝对 8 小时）")
    void beginUsesConfiguredTtls() {
        StubSessions sessions = new StubSessions();
        StubAccounts accounts = new StubAccounts(sessions.calls, ACTIVE);
        SessionService.IssuedSession issued =
                service(sessions, accounts).begin(USER_ID, TRACE_ID, "junit", null, null);

        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        long idleMinutes = Duration.between(now, issued.idleExpiresAt()).toMinutes();
        long absoluteHours = Duration.between(now, issued.absoluteExpiresAt()).toHours();
        assertTrue(idleMinutes >= 14 && idleMinutes <= 15, "空闲超时应在 15 分钟附近，实际 " + idleMinutes);
        assertTrue(absoluteHours >= 7 && absoluteHours <= 8, "绝对超时应在 8 小时附近，实际 " + absoluteHours);
    }
}
