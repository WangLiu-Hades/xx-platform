package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionService#renew} 的单测（W2 任务 4：{@code POST /auth/session/renew}）。
 *
 * <p>端到端能证明"续期后空闲期限变晚、绝对期限不变"，但三条更关键的分支在真实环境里很难
 * 稳定复现：并发续期把值推得更晚、会话在认证与续期之间被撤销、以及写库直接失败。这三条
 * 决定"续期失败时到底是报错还是假装成功"，所以用单测钉住。
 *
 * <p>不用 Mockito（沙箱禁止 JVM 自附加）：{@link UserSessionRepository} 是具体类，覆写
 * {@code touch}/{@code findById} 当桩；{@link JdbcTemplate} 用无参构造（不碰真实库）。
 */
class SessionServiceRenewTests {

    private static final UUID SESSION_ID = UUID.fromString("8c41f0a2-5d7e-4bb1-9a3c-2f6d1b8e7a05");
    private static final UUID USER_ID = UUID.fromString("0b7d3f52-94c1-4e8a-b6d0-1a2c3e4f5a60");

    /** 记录续期调用、可注入返回行数与故障的会话仓储桩。 */
    private static final class StubSessions extends UserSessionRepository {

        private final List<UUID> touched = new ArrayList<>();
        private OffsetDateTime lastNewIdle;
        private int touchResult = 1;
        private RuntimeException touchFailure;
        private UserSessionRepository.SessionRow current;

        StubSessions() {
            super(new JdbcTemplate());
        }

        @Override
        public int touch(UUID sessionId, OffsetDateTime newIdle) {
            touched.add(sessionId);
            lastNewIdle = newIdle;
            if (touchFailure != null) {
                throw touchFailure;
            }
            return touchResult;
        }

        @Override
        public Optional<SessionRow> findById(UUID sessionId) {
            return Optional.ofNullable(current);
        }
    }

    private static SessionService service(StubSessions sessions) {
        // CsrfSessionRepository 与 UserAccountRepository 只在 begin() 里用，续期路径不碰它们。
        return new SessionService(sessions, new CsrfSessionRepository(new JdbcTemplate()),
                new UserAccountRepository(new JdbcTemplate()), new SessionProperties());
    }

    private static AuthenticatedSession session(OffsetDateTime idle, OffsetDateTime absolute) {
        // user 只在需要展示身份时用，续期不读它。
        return new AuthenticatedSession(USER_ID, SESSION_ID, idle, absolute, null);
    }

    private static UserSessionRepository.SessionRow row(
            OffsetDateTime revokedAt, OffsetDateTime idle, OffsetDateTime absolute) {
        return new UserSessionRepository.SessionRow(SESSION_ID, USER_ID, revokedAt, idle, absolute);
    }

    @Test
    @DisplayName("续期把空闲期限推到 now + idle-ttl，绝对期限原样返回")
    void renewAdvancesIdleAndKeepsAbsolute() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime idle = now.plusMinutes(1);
        OffsetDateTime absolute = now.plusHours(8);
        SessionProperties properties = new SessionProperties();

        SessionService.RenewedSession renewed = service(sessions).renew(session(idle, absolute));

        assertEquals(List.of(SESSION_ID), sessions.touched, "必须对本次认证的会话行续期，且只续一次");
        OffsetDateTime expected = now.plus(properties.getIdleTtl());
        assertTrue(Duration.between(expected, sessions.lastNewIdle).abs().getSeconds() <= 2,
                "写库的新空闲期限应约等于 now + idle-ttl，实际 " + sessions.lastNewIdle);
        assertEquals(sessions.lastNewIdle, renewed.idleExpiresAt(), "响应必须返回真正写进库的值");
        assertEquals(absolute, renewed.absoluteExpiresAt(), "绝对期限不能被续期推后");
    }

    @Test
    @DisplayName("空闲期限接近绝对期限时被截到绝对期限，不会越界")
    void renewNeverPushesPastAbsolute() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime absolute = now.plusMinutes(2);
        OffsetDateTime idle = now.plusMinutes(1);

        SessionService.RenewedSession renewed = service(sessions).renew(session(idle, absolute));

        assertEquals(absolute, sessions.lastNewIdle, "续期只能推到绝对期限为止");
        assertEquals(absolute, renewed.idleExpiresAt());
        assertEquals(absolute, renewed.absoluteExpiresAt());
    }

    @Test
    @DisplayName("空闲期限已顶到绝对期限：不写库，原值返回（续期成功但没有余量）")
    void renewIsNoOpWhenAlreadyAtAbsolute() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime absolute = now.plusMinutes(1);

        SessionService.RenewedSession renewed = service(sessions).renew(session(absolute, absolute));

        assertTrue(sessions.touched.isEmpty(), "没有更晚的值可写时不应产生 UPDATE");
        assertEquals(absolute, renewed.idleExpiresAt());
        assertEquals(absolute, renewed.absoluteExpiresAt());
    }

    @Test
    @DisplayName("UPDATE 命中 0 行且会话已被撤销：报 401 AUTH_SESSION_EXPIRED，不假装成功")
    void renewRejectsSessionRevokedConcurrently() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        sessions.touchResult = 0;
        sessions.current = row(now.minusSeconds(1), now.plusMinutes(1), now.plusHours(8));

        ApiException e = assertThrows(ApiException.class,
                () -> service(sessions).renew(session(now.plusMinutes(1), now.plusHours(8))));

        assertEquals(ErrorCode.AUTH_SESSION_EXPIRED, e.code());
    }

    @Test
    @DisplayName("UPDATE 命中 0 行但库里的值更新（并发续期赢）：返回库里那个更晚的值")
    void renewReturnsLaterIdleWhenConcurrentRenewWon() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime later = now.plusMinutes(20);
        sessions.touchResult = 0;
        sessions.current = row(null, later, now.plusHours(8));

        SessionService.RenewedSession renewed = service(sessions)
                .renew(session(now.plusMinutes(1), now.plusHours(8)));

        assertEquals(later, renewed.idleExpiresAt(), "并发续期已把值推得更晚，按库里的值回答");
        assertEquals(now.plusHours(8), renewed.absoluteExpiresAt());
    }

    @Test
    @DisplayName("写库失败直接抛出：显式续期不能回 200 却什么都没续")
    void renewPropagatesStorageFailure() {
        StubSessions sessions = new StubSessions();
        OffsetDateTime now = Instants.nowUtc();
        sessions.touchFailure = new IllegalStateException("connection refused");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service(sessions).renew(session(now.plusMinutes(1), now.plusHours(8))));

        assertEquals("connection refused", e.getMessage());
    }

    @Test
    @DisplayName("真实仓储对 null 会话 id 直接拒绝")
    void realRepositoryRejectsNullSessionId() {
        UserSessionRepository repository = new UserSessionRepository(new JdbcTemplate());
        assertThrows(IllegalArgumentException.class, () -> repository.findById(null));
    }
}
