package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.infra.IdempotencyRequestRepository;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 自助改密（M04：{@code POST /me/password}）。
 *
 * <h2>为什么需要这个端点</h2>
 * <p>初始口令由管理员设置，必然经过 IM/邮件/口头传递，等于"半个公开凭据"。如果系统没有
 * 改密入口，{@code password_changed_at} 永远是 NULL —— 那把"初始口令不能做业务"的闸门
 * 就变成了"所有人永远不能做业务"。所以闸门与自助改密必须同时存在：前者堵住风险，
 * 后者给出唯一可行的出路。
 *
 * <h2>口令规则</h2>
 * <ul>
 *   <li><b>先做 NFC 规范化再判长度</b>：同一个视觉字符可能有组合形式（例如 emoji、带重音的
 *       拉丁字母），浏览器/输入法提交的码点数不同。不规范化就会出现"看起来 15 个字、实际
 *       20 个码点"通过校验，或者反过来把用户的合法口令拒掉。</li>
 *   <li><b>15–128 个字符</b>：下限取 15（P-021-01），上限 128 是防止 PBKDF2 被超长输入拖慢
 *       （DoS），同时足够容纳密码管理器生成的随机串。</li>
 *   <li><b>离线弱口令清单</b>：不连外网、不调用第三方服务（内网部署 + 口令不该离开进程）。
 *       清单命中的判定见 {@link #isWeak}：常见口令、包含登录名、单字符重复。</li>
 *   <li><b>必须与当前口令不同</b>：既比对明文，也用 {@link PasswordHasher#verify} 验一次。</li>
 * </ul>
 *
 * <h2>为什么改密的同时轮换 SID 与 CSRF（而不是只撤销会话）</h2>
 * <p>{@code api-conventions.md} 要求"本人改密须 currentPassword 并撤销其他会话"，
 * 而 P-001-02 要求"登录/改密/权限提升轮换并撤销旧值"。两条合起来的含义是：
 * <b>改密后旧 SID/CSRF 一律作废，当前用户拿到一对新凭据继续使用</b>。
 * 实现上直接复用 {@link SessionService#begin}——它本来就在做"锁账号行 → 撤销旧 SID →
 * 撤销该用户全部会话 → 签发新会话 → 绑定 CSRF"，与登录是同一份语义，不需要再写一套。
 * 这比"撤销全部会话、让用户重新登录"更好：用户改完密码不会被踢回登录页，
 * 但所有旧凭据（包括别的设备上的）已经失效。
 *
 * <h2>幂等（契约必填 Idempotency-Key）</h2>
 * <p>同一个 {@code (actor, "changeMyPassword", key)} + 相同请求内容只执行一次：
 * 第二次直接返回 {@code accepted}，不再改口令、不再撤销会话、不再轮换凭据；
 * 同一个 key 配不同内容返回 409 {@code IDEMPOTENCY_CONFLICT}。
 * 失败请求会让事务回滚，账本行随之消失，所以"改错当前口令后重试"不会被当成重放。
 */
@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    /** 新口令最短字符数（码点）。 */
    static final int MIN_LENGTH = 15;

    /** 新口令最长字符数（码点）；同时也是防 PBKDF2 被超长输入拖慢的上限。 */
    static final int MAX_LENGTH = 128;

    /** 幂等账本里的操作名；与契约 operationId 一致，便于按操作排查。 */
    static final String OPERATION_ID = "changeMyPassword";

    /**
     * 离线弱口令清单（小写、NFC）。
     *
     * <p>只是"最差的一批"，不是完整泄露库——完整库要几十 MB，放仓库里既不可评审也不好更新。
     * 生产环境应通过部署配置挂载更大的清单；这里的清单保证"123456 这种不会被设成管理口令"。
     */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "passw0rd", "p@ssw0rd", "password123", "password123456",
            "123456789012345", "1234567890123456", "qwertyuiopasdf", "1qaz2wsx3edc",
            "iloveyou123456", "welcome123456", "administrator", "changeme123456",
            "xxplatform", "xxplatform123", "letmein1234567", "adminadminadmin");

    private final UserAccountRepository users;
    private final PasswordHasher passwordHasher;
    private final AuditService audit;
    private final IdempotencyRequestRepository idempotency;
    private final SessionService sessionService;
    private final CsrfTokenService csrfTokenService;
    private final TransactionTemplate transaction;

    public PasswordService(UserAccountRepository users,
            PasswordHasher passwordHasher, AuditService audit,
            IdempotencyRequestRepository idempotency, SessionService sessionService,
            CsrfTokenService csrfTokenService, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.idempotency = idempotency;
        this.sessionService = sessionService;
        this.csrfTokenService = csrfTokenService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 校验当前口令、写入新口令，并轮换凭据。
     *
     * @param session        当前已认证会话（其 {@code user} 是本次改密的账号）
     * @param currentPassword 用户提交的当前口令
     * @param newPassword    用户提交的新口令（未规范化；本方法内做 NFC）
     * @param idempotencyKey 契约必填的 {@code Idempotency-Key}
     * @param presentedSid   当前请求带来的 SID（用于"撤销旧 SID"）
     * @param clientBinding  新会话的客户端绑定串（UA + IP，见 {@link LoginService#clientBinding}）
     * @param presentedCsrfId 当前请求使用的 CSRF 会话行（成功后作废）
     * @param clientIp       本次请求的客户端地址（M20：审计必须记为来源 IP，不能留空）
     * @param traceId        本次请求的 traceId
     * @throws ApiException {@code VALIDATION_ERROR}（口令规则/幂等头不合法）/
     *                      {@code AUTH_INVALID_CREDENTIALS}（当前口令错）/
     *                      {@code AUTH_SESSION_EXPIRED}（账号或会话在过程中失效）/
     *                      {@code IDEMPOTENCY_CONFLICT}（同 key 不同内容）
     */
    public ChangeResult changePassword(AuthenticatedSession session, String currentPassword,
            String newPassword, String idempotencyKey, String presentedSid, String clientBinding,
            UUID presentedCsrfId, String clientIp, String traceId) {
        String key = IdempotencyKeyHeader.require(idempotencyKey);
        try {
            return transaction.execute(status -> changePasswordInTransaction(
                    session, currentPassword, newPassword, key, presentedSid, clientBinding,
                    presentedCsrfId, clientIp, traceId));
        } catch (Rejection rejection) {
            // M20：失败/拒绝事件必须在业务事务之外落库，否则随回滚一起消失。
            // failure_code 用契约错误码名（error-codes.md），不另造内部码。
            audit.failure("user.password_change", "ACCOUNT", "USER", session.userId(),
                    new AuditService.Actor(session.user().id(), session.user().loginName(),
                            session.user().displayName()),
                    clientIp, null, traceId, rejection.code().name(), rejection.detail());
            throw new ApiException(rejection.code(), rejection.getMessage(),
                    rejection.fieldErrors(), null);
        }
    }

    private ChangeResult changePasswordInTransaction(AuthenticatedSession session,
            String currentPassword, String newPassword, String key, String presentedSid,
            String clientBinding, UUID presentedCsrfId, String clientIp, String traceId) {
        UserAccountRepository.UserAccountRow user = session.user();
        String current = currentPassword == null ? "" : currentPassword;
        String normalized = normalize(newPassword);
        String digest = requestDigest(current, normalized);

        Optional<IdempotencyRequestRepository.LedgerRow> existing =
                idempotency.find(user.id(), OPERATION_ID, key);
        if (existing.isPresent()) {
            if (!existing.get().requestDigest().equals(digest)) {
                log.info("改密被拒：同一 Idempotency-Key 的内容不同 userId={}", user.id());
                throw new Rejection(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key 已被不同的请求使用",
                        Map.of("reason", "IDEMPOTENCY_CONFLICT"));
            }
            log.info("改密重放（幂等命中） userId={}", user.id());
            return ChangeResult.replay();
        }

        if (!passwordHasher.verify(current, user.passwordHash())) {
            // 契约 openapi.yaml:16381 —— 当前口令错是 401 AUTH_INVALID_CREDENTIALS，
            // 不是 400 VALIDATION_ERROR（后者会被前端渲染成"表单没填对"，用户不会去重输旧口令）。
            log.info("改密失败：当前口令不正确 userId={}", user.id());
            throw new Rejection(ErrorCode.AUTH_INVALID_CREDENTIALS, "当前口令不正确",
                    Map.of("reason", "CURRENT_PASSWORD_MISMATCH"),
                    List.of(new FieldErrorDetail("currentPassword", "当前口令不正确")));
        }

        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            log.info("改密失败：新口令长度不合规 userId={} length={}", user.id(), length);
            throw new Rejection(ErrorCode.VALIDATION_ERROR,
                    "新口令长度必须为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 个字符",
                    Map.of("reason", "NEW_PASSWORD_LENGTH", "length", length),
                    List.of(new FieldErrorDetail("newPassword",
                            "长度必须为 " + MIN_LENGTH + "-" + MAX_LENGTH + " 个字符")));
        }
        if (isWeak(normalized, user)) {
            log.info("改密失败：新口令命中弱口令规则 userId={}", user.id());
            throw new Rejection(ErrorCode.VALIDATION_ERROR, "新口令过于常见，请更换",
                    Map.of("reason", "NEW_PASSWORD_WEAK"),
                    List.of(new FieldErrorDetail("newPassword", "新口令过于常见（离线弱口令清单命中）")));
        }
        if (normalized.equals(current) || passwordHasher.verify(normalized, user.passwordHash())) {
            log.info("改密失败：新口令与当前口令相同 userId={}", user.id());
            throw new Rejection(ErrorCode.VALIDATION_ERROR, "新口令不能与当前口令相同",
                    Map.of("reason", "NEW_PASSWORD_SAME"),
                    List.of(new FieldErrorDetail("newPassword", "新口令不能与当前口令相同")));
        }

        String hash = passwordHasher.hash(normalized);
        int updated = users.updatePassword(user.id(), hash, Instants.nowUtc());
        if (updated == 0) {
            // 校验口令到写库之间账号被停用/软删：不能给不可用账号写新口令。
            log.warn("改密时账号已不可用 userId={}", user.id());
            throw new Rejection(ErrorCode.AUTH_SESSION_EXPIRED, "账号不可用，请重新登录",
                    Map.of("reason", "ACCOUNT_DISABLED"));
        }

        // 轮换：新 CSRF → 新 SID（begin 内部会撤销旧 SID 与该用户全部其它会话）→ 作废旧 CSRF。
        CsrfTokenService.IssuedCsrf rotatedCsrf = csrfTokenService.issue();
        SessionService.IssuedSession rotated = sessionService.begin(user.id(), traceId,
                clientBinding, presentedSid, rotatedCsrf.id());
        if (presentedCsrfId != null) {
            csrfTokenService.revoke(presentedCsrfId);
        }

        boolean replayed;
        try {
            idempotency.insertSucceeded(user.id(), OPERATION_ID, key, digest, "{\"accepted\":true}");
            replayed = false;
        } catch (DuplicateKeyException race) {
            // 并发的同 key 请求已经先成功：本事务的写库与撤销都是等价的（同内容 → 同新口令），
            // 按重放返回即可，不算失败。
            log.info("改密并发同 key，按重放返回 userId={}", user.id());
            replayed = true;
        }

        log.info("改密成功并轮换凭据 userId={} sessionId={} replayed={}", user.id(), rotated.sessionId(), replayed);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sessionId", rotated.sessionId());
        detail.put("rotated", true);
        detail.put("replayed", replayed);
        audit.success("user.password_change", "ACCOUNT", "USER", user.id(),
                new AuditService.Actor(user.id(), user.loginName(), user.displayName()),
                clientIp, null, traceId, detail);
        return new ChangeResult(true, replayed, rotated, rotatedCsrf);
    }

    /**
     * 离线弱口令判定。
     *
     * <p>三条规则：命中清单、包含登录名（管理员给你起的名往往就是口令的一部分）、
     * 全部是同一个字符（{@code aaaaaaaaaaaaaaa}）。不做"必须含大小写数字符号"的组合要求——
     * P-021-01 明确不要字符组合规则，长度 + 清单才是当前 NIST 推荐的做法。
     */
    static boolean isWeak(String normalized, UserAccountRepository.UserAccountRow user) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            return true;
        }
        String loginName = user.loginName();
        if (loginName != null && !loginName.isBlank()
                && lower.contains(loginName.toLowerCase(Locale.ROOT))) {
            return true;
        }
        int first = lower.codePointAt(0);
        boolean singleChar = lower.codePoints().allMatch(cp -> cp == first);
        return singleChar;
    }

    /**
     * NFC 规范化。
     *
     * <p>为什么不用 NFKC：NFKC 会把全角字符、liga 连字"折叠"成别的字符，等于悄悄改掉用户的
     * 口令原文，既可能让用户下次无法用完全相同的方式登录，也削弱了字符集宽度。NFC 只合并
     * "同一个字符的不同码点表示"，语义上是无损的。
     */
    static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
    }

    /** 请求内容摘要：当前口令 + 新口令（NFC），用于区分"同 key 同内容"与"同 key 不同内容"。 */
    static String requestDigest(String currentPassword, String normalizedNewPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(currentPassword.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(normalizedNewPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，不可能缺失；真缺了也不该把口令原文写进日志。
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 改密结果。
     *
     * @param accepted   固定 true（成功才有结果）
     * @param replayed   是否命中幂等账本（命中时没有副作用，{@code session}/{@code csrf} 为 null）
     * @param session    轮换后的新会话（幂等重放时为 null）
     * @param csrf       轮换后的新 CSRF（幂等重放时为 null）
     */
    public record ChangeResult(boolean accepted, boolean replayed,
            SessionService.IssuedSession session, CsrfTokenService.IssuedCsrf csrf) {

        static ChangeResult replay() {
            return new ChangeResult(true, true, null, null);
        }
    }

    /**
     * 业务事务内的拒绝信号：带"契约错误码 + 失败原因 + 字段错误"。
     *
     * <p>与 {@code LoginService.Rejection} 同构，理由相同：需要"先回滚、再审计、再抛"三步，
     * 而 {@link ApiException} 不含内部原因。
     */
    private static final class Rejection extends RuntimeException {

        private final ErrorCode code;
        private final Map<String, Object> detail;
        private final List<FieldErrorDetail> fieldErrors;

        Rejection(ErrorCode code, String message, Map<String, Object> detail) {
            this(code, message, detail, List.of());
        }

        Rejection(ErrorCode code, String message, Map<String, Object> detail,
                List<FieldErrorDetail> fieldErrors) {
            super(message, null, false, false);
            this.code = code;
            this.detail = detail;
            this.fieldErrors = fieldErrors;
        }

        ErrorCode code() {
            return code;
        }

        Map<String, Object> detail() {
            return detail;
        }

        List<FieldErrorDetail> fieldErrors() {
            return fieldErrors;
        }
    }
}
