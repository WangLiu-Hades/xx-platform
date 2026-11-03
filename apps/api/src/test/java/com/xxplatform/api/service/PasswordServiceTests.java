package com.xxplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.CsrfProperties;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.AuditEventRepository;
import com.xxplatform.api.infra.CsrfSessionRepository;
import com.xxplatform.api.infra.IdempotencyRequestRepository;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 自助改密（M04）的单元测试。
 *
 * <p><b>为什么连 {@link PasswordHasher} 也做成桩</b>：真实实现每次派生 600000 次 PBKDF2，
 * 单次约 600 ms；本类有十来个用例、每个要算 1-3 次，跑起来会到十几秒。这里要验证的是
 * {@link PasswordService} 的<b>规则与顺序</b>（先验旧口令、再判规则、写入后才轮换），
 * 哈希本身的正确性由 {@code PasswordHasherTests} 负责，不该在这里重复付出代价。
 *
 * <p>数据库写入、会话轮换、CSRF 轮换都在真库用例
 * {@code auth/AuthSessionIntegrationTests} 里验证；这里只断言"调用到了没有、参数对不对"，
 * 所以会话与 CSRF 服务用只记录调用的子类桩，不连数据库。
 *
 * <p>桩的口令哈希形如 {@code "H:" + 明文}，能表达"相同/不同"，且不会把口令写进断言输出。
 */
class PasswordServiceTests {

    private static final String CURRENT = "InitialPassw0rd!";
    private static final String CURRENT_HASH = "H:" + CURRENT;
    private static final String NEW = "BrandNewPassw0rd!";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID ROTATED_SESSION_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID CSRF_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID ROTATED_CSRF_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final String KEY = "idem-key-0123456789abcdef";

    private StubUsers users;
    private StubHasher hasher;
    private StubAudit audit;
    private StubIdempotency idempotency;
    private StubSessions sessions;
    private StubCsrf csrf;
    private PasswordService service;

    @BeforeEach
    void setUp() {
        users = new StubUsers();
        hasher = new StubHasher();
        audit = new StubAudit();
        idempotency = new StubIdempotency();
        sessions = new StubSessions();
        csrf = new StubCsrf();
        service = new PasswordService(users, hasher, audit, idempotency, sessions, csrf,
                NO_TRANSACTION);
    }

    @Test
    void rejectsNewPasswordShorterThanFifteenCharacters() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, "short-Pass!", KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("15-128"));
        assertNull(users.lastUpdatedHash);
        assertEquals(0, sessions.beginCalls);
        assertEquals("user.password_change", audit.last().action());
        assertEquals("FAILURE", audit.last().result());
        assertEquals("VALIDATION_ERROR", audit.last().failureCode());
    }

    @Test
    void rejectsNewPasswordLongerThan128Characters() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, "a".repeat(129), KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertNull(users.lastUpdatedHash);
    }

    @Test
    void rejectsNewPasswordEqualToCurrentOne() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, CURRENT, KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertTrue(error.getMessage().contains("相同"));
        assertNull(users.lastUpdatedHash);
    }

    @Test
    void rejectsWeakPasswordThatContainsTheLoginName() {
        // "admin" 是登录名：管理员设置的口令常常就带着账号名，离线清单必须能拦。
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, "my-admin-password-2026", KEY,
                        "sid-old", "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertNull(users.lastUpdatedHash);
    }

    @Test
    void rejectsWrongCurrentPasswordWith401NotValidationError() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), "not-the-current-one", NEW, KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        // openapi.yaml:16381：当前口令错 → 401 AUTH_INVALID_CREDENTIALS。
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, error.code());
        assertEquals("currentPassword", error.details().get(0).field());
        assertNull(users.lastUpdatedHash);
        assertEquals(0, sessions.beginCalls);
        assertEquals("AUTH_INVALID_CREDENTIALS", audit.last().failureCode());
    }

    @Test
    void changesPasswordAndRotatesSessionAndCsrf() {
        PasswordService.ChangeResult result = service.changePassword(session(), CURRENT, NEW, KEY,
                "sid-old", "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1");

        assertTrue(result.accepted());
        assertFalse(result.replayed());
        assertEquals("H:" + NEW, users.lastUpdatedHash);
        assertNotNull(users.lastUpdatedAt, "password_changed_at 必须写入，否则初始口令闸门永远放行不了");
        assertNotNull(result.session());
        assertEquals(ROTATED_SESSION_ID, result.session().sessionId());
        // 轮换必须基于"当前 SID + 新 CSRF"，登录/改密走同一份 SessionService.begin 语义。
        assertEquals(USER_ID, sessions.beganUserId);
        assertEquals("sid-old", sessions.beganSid);
        assertEquals(ROTATED_CSRF_ID, sessions.beganCsrfId);
        assertEquals("ua|ip=10.1.2.3", sessions.beganClientLabel);
        // 旧的 CSRF 行必须作废（M03：改密轮换并撤销旧值）。
        assertEquals(CSRF_ID, csrf.revokedId);
        assertEquals("user.password_change", audit.last().action());
        assertEquals("SUCCESS", audit.last().result());
        assertFalse(audit.last().after().containsValue(NEW), "审计快照不能包含口令明文");
    }

    @Test
    void normalisesToNfcBeforeApplyingTheLengthRule() {
        // 每个 "e" + U+0301 是 2 个码点，NFC 合成 "é" 只有 1 个码点：15 个组合字符 = 30 码点。
        // 若先判长度，30 > 15 会通过；但真正要证明的是"先 NFC 再数码点"——规范化后是 15，
        // 恰好落在下限上，且混用三个基字符，不会命中"单字符重复"的弱口令规则。
        String decomposed = "a\u0301".repeat(5) + "e\u0301".repeat(5) + "i\u0301".repeat(5);
        assertEquals(30, decomposed.codePointCount(0, decomposed.length()));

        PasswordService.ChangeResult result = service.changePassword(session(), CURRENT, decomposed,
                KEY, "sid-old", "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1");

        assertTrue(result.accepted());
        String expected = java.text.Normalizer.normalize(decomposed, java.text.Normalizer.Form.NFC);
        assertEquals("H:" + expected, users.lastUpdatedHash);
        assertEquals(15, expected.codePointCount(0, expected.length()));
    }

    @Test
    void failsWithSessionExpiredWhenAccountIsDeactivatedDuringTheWrite() {
        users.updatePasswordReturns = 0;

        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, NEW, KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.AUTH_SESSION_EXPIRED, error.code());
        // 写库失败就不能轮换凭据：那等于把用户踢下线，而改密其实没成功。
        assertEquals(0, sessions.beginCalls);
        assertEquals(0, csrf.issueCalls);
    }

    @Test
    void replayedKeyReturnsAcceptedWithoutSideEffects() {
        idempotency.existing = new IdempotencyRequestRepository.LedgerRow(
                UUID.randomUUID(), PasswordService.requestDigest(CURRENT, NEW), "SUCCEEDED", 200,
                "{\"accepted\":true}");

        PasswordService.ChangeResult result = service.changePassword(session(), CURRENT, NEW, KEY,
                "sid-old", "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1");

        assertTrue(result.accepted());
        assertTrue(result.replayed());
        assertNull(result.session());
        assertNull(users.lastUpdatedHash);
        assertEquals(0, sessions.beginCalls);
    }

    @Test
    void sameKeyWithDifferentContentIsRejectedAsConflict() {
        idempotency.existing = new IdempotencyRequestRepository.LedgerRow(
                UUID.randomUUID(),
                PasswordService.requestDigest(CURRENT, "AnotherDifferentPassw0rd!"),
                "SUCCEEDED", 200, "{\"accepted\":true}");

        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, NEW, KEY, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, error.code());
        assertEquals(0, sessions.beginCalls);
    }

    @Test
    void missingIdempotencyKeyIsAValidationError() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.changePassword(session(), CURRENT, NEW, null, "sid-old",
                        "ua|ip=10.1.2.3", CSRF_ID, "10.1.2.3", "trace-1"));

        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertEquals("Idempotency-Key", error.details().get(0).field());
    }

    // ---- helpers ----

    private AuthenticatedSession session() {
        UserAccountRepository.UserAccountRow user = new UserAccountRepository.UserAccountRow(
                USER_ID, "U-0001", "admin", "系统管理员", CURRENT_HASH, "ACTIVE");
        return new AuthenticatedSession(USER_ID, SESSION_ID, OffsetDateTime.now().plusMinutes(10),
                OffsetDateTime.now().plusHours(8), user);
    }

    /** 只记录"有没有被调、被写成什么"，不校验口令强度（那是 PasswordService 的职责）。 */
    static final class StubUsers extends UserAccountRepository {
        String lastUpdatedHash;
        OffsetDateTime lastUpdatedAt;
        int updatePasswordReturns = 1;

        StubUsers() {
            super(new JdbcTemplate());
        }

        @Override
        public int updatePassword(UUID userId, String passwordHash, OffsetDateTime changedAt) {
            this.lastUpdatedHash = passwordHash;
            this.lastUpdatedAt = changedAt;
            return updatePasswordReturns;
        }

        @Override
        public Optional<UserAccountRow> findById(UUID userId) {
            return Optional.of(new UserAccountRow(USER_ID, "U-0001", "admin", "系统管理员",
                    CURRENT_HASH, "ACTIVE"));
        }
    }

    /** {@code "H:明文"} 形式的可逆桩：用于断言"写入的是规范化后的明文哈希"。 */
    static final class StubHasher extends PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "H:" + rawPassword;
        }

        @Override
        public boolean verify(String rawPassword, String stored) {
            return stored != null && stored.equals("H:" + rawPassword);
        }
    }

    /**
     * 会话签发桩：只记录入参并返回一对固定凭据。
     *
     * <p>父类构造要三个仓库 + 配置；它们都不会被用到（{@link #begin} 被整个覆盖），
     * 传 {@code new JdbcTemplate()} 只是为了让 {@code Assert.notNull} 过关。
     */
    static final class StubSessions extends SessionService {
        int beginCalls;
        UUID beganUserId;
        String beganClientLabel;
        String beganSid;
        UUID beganCsrfId;

        StubSessions() {
            super(new UserSessionRepository(new JdbcTemplate()),
                    new CsrfSessionRepository(new JdbcTemplate()),
                    new UserAccountRepository(new JdbcTemplate()),
                    new SessionProperties());
        }

        @Override
        public IssuedSession begin(UUID userId, String traceId, String clientLabel,
                                   String presentedSid, UUID csrfId) {
            beginCalls++;
            beganUserId = userId;
            beganClientLabel = clientLabel;
            beganSid = presentedSid;
            beganCsrfId = csrfId;
            return new IssuedSession("sid-new", ROTATED_SESSION_ID,
                    OffsetDateTime.now().plusMinutes(15), OffsetDateTime.now().plusHours(8));
        }
    }

    /** CSRF 轮换桩：记录签发次数与被作废的行 id。 */
    static final class StubCsrf extends CsrfTokenService {
        int issueCalls;
        UUID revokedId;

        StubCsrf() {
            super(new CsrfSessionRepository(new JdbcTemplate()), new CsrfProperties());
        }

        @Override
        public IssuedCsrf issue() {
            issueCalls++;
            return new IssuedCsrf(ROTATED_CSRF_ID, "csrf-cookie-new", "csrf-token-new",
                    OffsetDateTime.now().plusHours(2));
        }

        @Override
        public void revoke(UUID csrfId) {
            revokedId = csrfId;
        }
    }

    /** 幂等账本桩：可预置"已存在"行，记录插入。 */
    static final class StubIdempotency extends IdempotencyRequestRepository {
        LedgerRow existing;

        StubIdempotency() {
            super(new JdbcTemplate());
        }

        @Override
        public Optional<LedgerRow> find(UUID userId, String operationId, String idempotencyKey) {
            return Optional.ofNullable(existing);
        }

        @Override
        public void insertSucceeded(UUID userId, String operationId, String idempotencyKey,
                                    String requestDigest, String responseSnapshot) {
            // 无需落库：成功路径只要求调用不抛异常。
        }
    }

    /**
     * 记录事件的桩。
     *
     * <p>{@link AuditService} 的构造器要一个事务管理器；这里只让它通过参数校验，
     * 真正写库的 {@code record} 被整个覆盖掉，所以事务永远不会被使用。
     */
    static final class StubAudit extends AuditService {
        final List<AuditEvent> events = new ArrayList<>();

        StubAudit() {
            super((AuditEventRepository) null, NO_TRANSACTION);
        }

        @Override
        public void record(String action, String category, String resourceType, UUID resourceId,
                           Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                           String result, String failureCode,
                           Map<String, Object> before, Map<String, Object> after) {
            events.add(new AuditEvent(action, result, failureCode,
                    before == null ? Map.of() : new LinkedHashMap<>(before),
                    after == null ? Map.of() : new LinkedHashMap<>(after)));
        }

        AuditEvent last() {
            return events.get(events.size() - 1);
        }
    }

    /** 事件快照（只保留测试关心的字段）。 */
    record AuditEvent(String action, String result, String failureCode,
                      Map<String, Object> before, Map<String, Object> after) {
    }

    /** 最小事务管理器：本类的所有协作者都是桩，事务只是走过场。 */
    private static final PlatformTransactionManager NO_TRANSACTION = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // 桩写库没有真实事务可提交。
        }

        @Override
        public void rollback(TransactionStatus status) {
            // 同上。
        }
    };
}
