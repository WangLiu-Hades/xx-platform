package com.xxplatform.api.service;

import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.CsrfSessionRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SessionService#end} 的单测（任务 11：{@code POST /auth/logout}）。
 *
 * <p>端到端只能证明"退出后会话被撤销"这一条路径，而"重复退出""会话已被并发撤销"
 * 这类分支在真实环境里很难复现（需要两个请求抢同一行），所以用单测钉住返回值的语义：
 * {@code true} = 这次真的撤销了，{@code false} = 之前已经撤销过，两者都不能变成异常。
 *
 * <p>不用 Mockito（沙箱禁止 JVM 自附加）：{@link UserSessionRepository} 是具体类，
 * 用子类覆写 {@code revokeById} 当桩；{@link JdbcTemplate} 用无参构造即可（不碰真实库）。
 */
class SessionServiceLogoutTests {

    private static final UUID SESSION_ID = UUID.fromString("3f1d9a6e-6f3c-4c56-9c2f-0d0a6f9b1c11");
    private static final String TRACE_ID = "trace-logout-1";

    /** 只记录调用、按 {@code result} 返回行数的会话仓储桩。 */
    private static final class StubSessions extends UserSessionRepository {

        private final List<UUID> calls = new ArrayList<>();
        private int result = 1;

        StubSessions() {
            super(new JdbcTemplate());
        }

        @Override
        public int revokeById(UUID sessionId) {
            calls.add(sessionId);
            return result;
        }
    }

    private static SessionService service(StubSessions sessions) {
        // CsrfSessionRepository 不参与退出逻辑（见 SessionService#end 注释），给个不碰库的实例即可
        return new SessionService(sessions, new CsrfSessionRepository(new JdbcTemplate()), new SessionProperties());
    }

    @Test
    @DisplayName("退出撤销的正是本次认证通过的那个会话行")
    void endRevokesTheAuthenticatedSession() {
        StubSessions sessions = new StubSessions();

        boolean revoked = service(sessions).end(SESSION_ID, TRACE_ID);

        assertTrue(revoked, "第一次退出必须真的撤销一行");
        assertEquals(List.of(SESSION_ID), sessions.calls, "必须按会话行 id 撤销，且只撤销一次");
    }

    @Test
    @DisplayName("并发/重试导致的 0 行不抛异常，只报告未撤销")
    void endReportsFalseWhenTheSessionWasAlreadyRevoked() {
        StubSessions sessions = new StubSessions();
        sessions.result = 0;

        boolean revoked = service(sessions).end(SESSION_ID, TRACE_ID);

        assertFalse(revoked, "已经被撤销过时返回 false，调用方据此只记日志");
    }

    @Test
    @DisplayName("同一会话连退两次：第一次 true，第二次 false")
    void endIsIdempotentAcrossCalls() {
        StubSessions sessions = new StubSessions();
        SessionService service = service(sessions);

        assertTrue(service.end(SESSION_ID, TRACE_ID));
        sessions.result = 0; // 第二次 UPDATE 命中 0 行（revoked_at 已非空）
        assertFalse(service.end(SESSION_ID, TRACE_ID));
        assertEquals(2, sessions.calls.size());
    }

    @Test
    @DisplayName("真实仓储对 null 会话 id 直接拒绝，不把空值发给数据库")
    void realRepositoryRejectsNullSessionId() {
        UserSessionRepository repository = new UserSessionRepository(new JdbcTemplate());
        assertThrows(IllegalArgumentException.class, () -> repository.revokeById(null));
    }
}
