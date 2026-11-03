package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ClientNetworks;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.LoginThrottle;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserAccountRepository.UserAccountRow;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 登录用例（任务 5）。
 *
 * <p>口令校验本身在 {@link PasswordHasher}（PBKDF2 + 常量时间比较）；本类负责编排
 * "来源网段 → 限流 → 账号状态 → 口令 → 签发会话 → 清理限流桶"这条链，并把失败统一成
 * 同一个对外 401。对外不区分"账号不存在"与"口令错"，是为了避免撞库时枚举账号；
 * 区分只存在于日志与审计里。
 *
 * <p><b>M02：登录来源网段校验放在最前面</b>。不在 {@code xx.session.allowed-networks} 内直接
 * 403，且**不消耗**登录限流桶——否则外网扫描器可以用一个用户名把管理员锁死（限流是按
 * 登录名+IP 分桶的，扫不进来的人不该影响桶）。
 *
 * <p><b>客户端地址怎么取</b>：控制器用 {@link ClientNetworks#resolveClientIp} 解析——
 * 默认只信 {@code request.getRemoteAddr()}；只有对端命中 {@code xx.session.trusted-proxies}
 * （部署方声明的代理）时才从右往左解析 {@code X-Forwarded-For}。所以"伪造 XFF 绕过内网来源"
 * 在默认配置下不成立。
 *
 * <p><b>为什么失败审计写在事务外面</b>：{@link AuditService} 用 NESTED 传播加入调用方事务
 * （避免行锁自死锁，见其类注释）。如果失败审计写在 {@code @Transactional} 方法内部，
 * 随后抛出的业务异常会把整个事务回滚——**审计事件跟着一起没了**，正是 M20 要禁止的
 * "拒绝/失败事件被事务吞掉"。所以这里用 {@link TransactionTemplate} 包住业务主体，
 * 让失败路径先回滚业务、再在独立事务里落审计，最后抛契约错误。
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    /**
     * 对外统一的失败文案。
     *
     * <p>为什么不用"账号不存在"/"密码错误"两种：那等于提供账号枚举接口。这个字符串同时被
     * 写进契约的错误响应示例，改它要同步改 {@code contracts/api/openapi.yaml} 与前端文案。
     */
    static final String FAILURE_MESSAGE = "登录名或密码不正确";

    /** 来源不在允许网段时的文案（M02）；前端按"网络环境不允许"展示。 */
    static final String SOURCE_DENIED_MESSAGE = "登录来源不在允许的内网范围";

    /** {@code client_label} 列宽 {@code varchar(200)}；超出截断，避免插入失败。 */
    private static final int CLIENT_LABEL_MAX = 200;

    /** {@code client_label} 里 UA 部分的上限：留出 {@code |ip=} + 45 字符 IPv6 的位置。 */
    private static final int UA_PART_MAX = 150;

    /** {@code client_label} 中 IP 的固定分隔符（见 {@link #clientBinding}）。 */
    private static final String IP_SEPARATOR = "|ip=";

    /** 会话绑定里"客户端没给地址"的占位；解析时按"没有 IP 可绑"处理。 */
    private static final String UNKNOWN = "unknown";

    private final UserAccountRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final LoginThrottle throttle;
    private final SessionProperties sessionProperties;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public LoginService(UserAccountRepository users, PasswordHasher passwordHasher,
            SessionService sessionService, LoginThrottle throttle,
            SessionProperties sessionProperties, AuditService audit,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.throttle = throttle;
        this.sessionProperties = sessionProperties;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 校验口令并签发新会话。
     *
     * <p>业务主体在一个事务里：{@link SessionService#begin} 内部要"锁账号行 + 撤销旧会话 +
     * 插入新会话"，必须原子。口令校验是 CPU 计算（PBKDF2），放在事务里会让行锁多持有
     * 约 50–100ms；这是有意的取舍——先锁行再校验可以让"两个并发登录"严格串行，
     * 避免两边都校验通过、各自撤销、最后两条会话并存。
     *
     * <p>失败审计在事务提交/回滚**之后**写（见类注释），因此即使业务事务回滚，
     * {@code audit.audit_event} 里仍有这次拒绝的记录。
     *
     * @param loginName  提交的登录名（未清洗，查询用原文）
     * @param password   提交的口令原文
     * @param presentedSid 客户端带来的旧 SID（可为 null；登录轮换用）
     * @param csrfId     本次请求使用的 CSRF 会话行 id（绑定到新 SID）
     * @param clientIp   已解析的客户端地址（可信代理规则见 {@link ClientNetworks#resolveClientIp}）
     * @param clientLabel 客户端绑定串（UA + IP，见 {@link #clientBinding}），写入会话供逐请求比对
     * @param traceId    本次请求的 traceId
     * @throws ApiException {@code RATE_LIMITED} / {@code PERMISSION_DENIED}（来源网段）/
     *                      {@code AUTH_INVALID_CREDENTIALS}
     */
    public LoginResult login(String loginName, String password, String presentedSid, UUID csrfId,
            String clientIp, String clientLabel, String traceId) {
        try {
            return transaction.execute(status -> loginInTransaction(
                    loginName, password, presentedSid, csrfId, clientIp, clientLabel, traceId));
        } catch (Rejection rejection) {
            audit.failure("auth.login", "AUTH", "SESSION", null, rejection.actor(), clientIp, null,
                    traceId, rejection.code().name(), Map.of("reason", rejection.reason()));
            throw rejection.toApiException();
        }
    }

    private LoginResult loginInTransaction(String loginName, String password, String presentedSid,
            UUID csrfId, String clientIp, String clientLabel, String traceId) {
        // M02：来源网段判定用已解析的客户端地址（可信代理才采信转发头）。
        if (!ClientNetworks.allowed(clientIp, sessionProperties.getAllowedNetworks())) {
            log.warn("登录被拒：来源不在允许网段 clientIp={} loginName={}", clientIp, sanitize(loginName));
            throw new Rejection(ErrorCode.PERMISSION_DENIED, SOURCE_DENIED_MESSAGE,
                    "SOURCE_NETWORK_DENIED", AuditService.Actor.ofLoginName(sanitize(loginName)), null);
        }

        String key = LoginThrottle.key(loginName, clientIp);
        Optional<Duration> retryAfter = throttle.retryAfter(key);
        if (retryAfter.isPresent()) {
            log.info("登录被限流 loginName={} clientIp={} retryAfter={}s",
                    sanitize(loginName), clientIp, retryAfter.get().toSeconds());
            throw new Rejection(ErrorCode.RATE_LIMITED, "登录失败次数过多，请稍后再试", "THROTTLED",
                    AuditService.Actor.ofLoginName(sanitize(loginName)), retryAfter.get());
        }

        Optional<UserAccountRow> found = users.findByLoginName(loginName);
        if (found.isEmpty()) {
            throttle.recordFailure(key);
            log.info("登录失败：账号不存在 loginName={} clientIp={}", sanitize(loginName), clientIp);
            throw new Rejection(ErrorCode.AUTH_INVALID_CREDENTIALS, FAILURE_MESSAGE, "UNKNOWN_ACCOUNT",
                    AuditService.Actor.ofLoginName(sanitize(loginName)), null);
        }

        UserAccountRow user = found.get();
        // 停用/软删账号在查询层是查得到的（见 UserAccountRepository.findById 的注释），
        // 这里显式判一次，并用与"口令错"相同的响应，避免泄露"这个账号存在但被停用"。
        if (!user.isActive()) {
            throttle.recordFailure(key);
            log.info("登录失败：账号不可用 loginName={} status={} clientIp={}",
                    sanitize(loginName), user.status(), clientIp);
            throw new Rejection(ErrorCode.AUTH_INVALID_CREDENTIALS, FAILURE_MESSAGE, "ACCOUNT_DISABLED",
                    new AuditService.Actor(user.id(), user.loginName(), user.displayName()), null);
        }

        if (!passwordHasher.verify(password, user.passwordHash())) {
            throttle.recordFailure(key);
            log.info("登录失败：口令不匹配 loginName={} clientIp={}", sanitize(loginName), clientIp);
            throw new Rejection(ErrorCode.AUTH_INVALID_CREDENTIALS, FAILURE_MESSAGE, "BAD_PASSWORD",
                    new AuditService.Actor(user.id(), user.loginName(), user.displayName()), null);
        }

        SessionService.IssuedSession session = sessionService.begin(
                user.id(), traceId, clientLabel, presentedSid, csrfId);

        users.touchLastLogin(user.id(), Instants.nowUtc());
        throttle.clear(key);
        log.info("登录成功 userId={} loginName={} clientIp={} sessionId={}",
                user.id(), sanitize(user.loginName()), clientIp, session.sessionId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sessionId", session.sessionId());
        detail.put("clientIp", clientIp);
        audit.success("auth.login", "AUTH", "SESSION", session.sessionId(),
                new AuditService.Actor(user.id(), user.loginName(), user.displayName()), clientIp, null,
                traceId, detail);
        return new LoginResult(user.id(), user.displayName(), session);
    }

    /**
     * 把 User-Agent 收敛成 {@code client_label}（M02：会话绑定到客户端）。
     *
     * <p>为什么原样保留 UA 而不做哈希：审计与"我的会话"将来要能显示"这是什么设备"，
     * 哈希之后就没法展示了；UA 本身不是凭据，泄露它不构成越权。代价是它可被伪造——
     * 所以它只是"防止 SID 被搬到别的客户端"的弱绑定，真正的安全边界仍是 SID 高熵 + 短 TTL。
     *
     * <p>没有 UA 时写 {@code "unknown"}（而不是 null）：null 在认证层表示"跳过绑定比对"，
     * 而"客户端没给 UA"应该是一种**确定的**绑定值，这样它也不能被别的客户端冒用。
     */
    public static String clientLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() <= CLIENT_LABEL_MAX ? trimmed : trimmed.substring(0, CLIENT_LABEL_MAX);
    }

    /** Bind the full normalized UA digest and validated client IP within the existing varchar(200) column. Legacy partial bindings require reauthentication. */
    public static String clientBinding(String userAgent, String clientIp) {
        // Bind the complete normalized UA, including characters beyond the column's display budget.
        String ua = "v2:" + com.xxplatform.api.common.Sha256.hex(
                com.xxplatform.api.common.Sha256.ofBytes(
                        (userAgent == null || userAgent.isBlank() ? UNKNOWN : userAgent.trim())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String ip = clientIp == null || clientIp.isBlank() ? UNKNOWN : clientIp.trim();
        return ua + IP_SEPARATOR + ip;
    }

    /** 从绑定串里取 UA 部分；没有 {@code |ip=} 的历史行原样返回。 */
    public static String boundUserAgent(String clientLabel) {
        if (clientLabel == null) {
            return null;
        }
        int index = clientLabel.lastIndexOf(IP_SEPARATOR);
        return index < 0 ? clientLabel : clientLabel.substring(0, index);
    }

    /** 从绑定串里取 IP 部分；没有 IP（历史行、或登录时地址缺失）返回 null。 */
    public static String boundIp(String clientLabel) {
        if (clientLabel == null) {
            return null;
        }
        int index = clientLabel.lastIndexOf(IP_SEPARATOR);
        if (index < 0) {
            return null;
        }
        String ip = clientLabel.substring(index + IP_SEPARATOR.length()).trim();
        return ip.isEmpty() || UNKNOWN.equals(ip) ? null : ip;
    }

    /**
     * 日志里的登录名清洗：去掉控制字符并截断。
     *
     * <p>为什么要清洗：登录名来自请求体，直接进日志就是日志注入（CRLF 伪造行、超长行打爆
     * 日志采集）。审计快照不做这层清洗——审计是结构化入库，不需要"可读日志"的约束，
     * 但入库前同样会截断到列宽。
     */
    static String sanitize(String loginName) {
        if (loginName == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(Math.min(loginName.length(), 64));
        for (int i = 0; i < loginName.length() && builder.length() < 64; i++) {
            char c = loginName.charAt(i);
            builder.append(Character.isISOControl(c) ? '?' : c);
        }
        return builder.toString();
    }

    /**
     * 登录成功的结果。
     *
     * @param userId      账号 id
     * @param displayName 显示名（{@code LoginResponse.displayName}）
     * @param session     已签发的会话（含 SID/CSRF 原文，只在这里出现一次）
     */
    public record LoginResult(UUID userId, String displayName, SessionService.IssuedSession session) {
    }

    /**
     * 业务事务内部的拒绝信号：带上审计需要的"契约错误码 + 失败原因 + 主体快照"。
     *
     * <p>为什么不用 {@link ApiException} 直接当信号：{@code ApiException} 是"对外错误"，
     * 不含内部原因（原因只进审计），且它会被控制器的兜底逻辑当成已完成的事件。
     * 这里需要"先回滚、再审计、再抛"三步，必须有一个只在本类内部流转的类型。
     */
    private static final class Rejection extends RuntimeException {

        private final ErrorCode code;
        private final String reason;
        private final AuditService.Actor actor;
        private final Duration retryAfter;

        Rejection(ErrorCode code, String message, String reason, AuditService.Actor actor,
                Duration retryAfter) {
            super(message, null, false, false);
            this.code = code;
            this.reason = reason;
            this.actor = actor;
            this.retryAfter = retryAfter;
        }

        ErrorCode code() {
            return code;
        }

        String reason() {
            return reason;
        }

        AuditService.Actor actor() {
            return actor;
        }

        ApiException toApiException() {
            return new ApiException(code, getMessage(), List.of(), retryAfter);
        }
    }
}
