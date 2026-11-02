package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ClientNetworks;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestCookies;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** SID authentication checks current account/session authorization, source networks, complete UA/IP binding, expiry and initial-password restrictions before use or renewal. */
@Component
public class SessionAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticator.class);

    /**
     * 兜底的不续期探针路径（相对 context path）。
     *
     * <p>实际上这些端点在 {@code WebConfiguration} 里是公开的、走不到认证层；名单的主来源是
     * {@code xx.session.non-renewing-paths}（部署方可增补）。这里保留一份兜底，
     * 是为了"配置被清空"时轮询也不会把会话续成永不超时。
     */
    private static final Set<String> FALLBACK_PROBE_PATHS = Set.of("/health", "/ready");

    /**
     * 客户端标记"这是后台轮询、不是用户操作"的请求头（P-001-02 / M01）。
     *
     * <p>为什么需要显式标记而不是只靠路径：轮询可能打在任意读接口上（任务状态、文件状态、
     * 列表刷新），而同一个接口也可能是用户点的；但反过来，路径是可以被服务端识别的——
     * 所以 M01 要求"服务端可判定的不续期端点集合"（见 {@code xx.session.non-renewing-paths}）
     * 作为主判据，本请求头只是补充手段，不能当唯一依据。
     */
    public static final String BACKGROUND_HEADER = "X-Background-Request";

    /**
     * 浏览器/前端预加载类的用途头（Chrome 的 {@code Sec-Purpose: prefetch}、
     * 旧版 {@code X-Purpose: preview}）。值与 {@code prefetch}/{@code preload}/{@code preview}
     * 相关时不续期——这类请求同样不是用户主动操作。
     */
    private static final String[] PURPOSE_HEADERS = {"Sec-Purpose", "X-Purpose"};

    /**
     * 初始口令状态下仍允许访问的端点（{@code 方法 + 路径}）。
     *
     * <p><b>为什么把闸门放在认证层</b>：{@link #require} 是"已登录"的唯一入口，
     * 新增端点不需要记得在每个 Controller 里再判一次——漏判一次就是一个"初始口令可做业务"的
     * 洞。反过来，这里必须用**白名单**（默认拒绝）而不是黑名单：将来新增的端点自动受限，
     * 不会因为有人忘了加规则而穿透。
     *
     * <p>清单里的每一项都有理由：改密（唯一出路）、{@code /me}（前端要知道"我是谁"才能渲染
     * 改密页）、退出（否则用户被困住）、续期（不让改密页在填写时被 15 分钟超时踢出）、
     * CSRF（改密是写请求，必须能取到 token）。
     */
    private static final Set<String> INITIAL_PASSWORD_ALLOWLIST = Set.of(
            "GET /me",
            "POST /me/password",
            "POST /auth/logout",
            "POST /auth/session/renew",
            "GET /auth/csrf");

    /** 初始口令提示文案：前端可直接展示。 */
    static final String INITIAL_PASSWORD_MESSAGE = "首次登录必须先修改初始密码";

    private final UserSessionRepository sessions;
    private final UserAccountRepository users;
    private final SessionProperties properties;
    private final AuditService audit;

    public SessionAuthenticator(UserSessionRepository sessions, UserAccountRepository users,
            SessionProperties properties, AuditService audit) {
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * 解析并校验当前请求的会话，顺带推进空闲期。
     *
     * @throws ApiException 任一步失败；{@code code} 见类注释的表格
     */
    public AuthenticatedSession require(HttpServletRequest request) {
        return authenticate(request, true);
    }

    /**
     * 解析并校验当前请求的会话，但**不**推进空闲期。
     *
     * <p>给 {@code POST /auth/session/renew} 用：那个端点自己要在事务里严格续期（写库失败必须
     * 报 500，不能像常规请求那样"续不上就算了"），所以认证阶段只做判定，不顺手改库。若这里也
     * 推一次，续期端点会对同一行做两次 UPDATE，第二次必然命中 0 行，反而要额外解释。
     */
    public AuthenticatedSession requireWithoutRenewing(HttpServletRequest request) {
        return authenticate(request, false);
    }

    private AuthenticatedSession authenticate(HttpServletRequest request, boolean renewIdle) {
        String sid = RequestCookies.value(request, SessionService.COOKIE_NAME);
        if (sid == null || sid.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED, "未登录或会话已失效");
        }

        UserSessionRepository.SessionRow session = sessions.findBySidHash(Sha256.of(sid))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "未登录或会话已失效"));

        OffsetDateTime now = Instants.nowUtc();
        if (session.revokedAt() != null) {
            log.debug("会话已撤销 sessionId={}", session.id());
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "会话已失效，请重新登录");
        }
        if (!session.idleExpiresAt().isAfter(now)) {
            log.debug("会话空闲超时 sessionId={} idleExpiresAt={}", session.id(), session.idleExpiresAt());
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "会话已过期，请重新登录");
        }
        if (!session.absoluteExpiresAt().isAfter(now)) {
            log.debug("会话绝对超时 sessionId={} absoluteExpiresAt={}", session.id(),
                    session.absoluteExpiresAt());
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "会话已过期，请重新登录");
        }

        UserAccountRepository.UserAccountRow user = users.findById(session.userId())
                .orElseThrow(() -> {
                    log.warn("会话存在但账号已不存在 sessionId={} userId={}", session.id(), session.userId());
                    return new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "账号不可用，请重新登录");
                });
        if (!user.isActive()) {
            log.debug("账号不可用 userId={} status={}", user.id(), user.status());
            throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "账号不可用，请重新登录");
        }

        requireBoundClient(request, session, user);

        OffsetDateTime idleExpiresAt = renewIdle
                ? renewIfActiveRequest(request, session, now)
                : session.idleExpiresAt();

        // M04：闸门放在"会话已成立、账号可用"之后——否则一个被停用的账号会因为 403 而
        // 保留登录态（前端对 403 的处理是"提示无权限"，对 401 才是回登录页）。
        requirePasswordChanged(request, user, session);

        return new AuthenticatedSession(session.userId(), session.id(), idleExpiresAt,
                session.absoluteExpiresAt(), user);
    }

    /**
     * 解析客户端地址（M02）：默认只用 {@code remoteAddr}；只有对端是
     * {@code xx.session.trusted-proxies} 中的代理时才采信 {@code X-Forwarded-For}。
     *
     * <p>控制器与认证层共用这一个入口，避免"登录按 XFF 判来源、会话按 remoteAddr 绑 IP"
     * 这种两套取址互相打架的情况。
     */
    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        List<String> hops = forwarded == null || forwarded.isBlank()
                ? List.of()
                : Arrays.asList(forwarded.split(","));
        return ClientNetworks.resolveClientIp(request.getRemoteAddr(), hops,
                properties.getTrustedProxies());
    }

    /** Reject absent, legacy or mismatched UA/IP bindings before renewal; record the failure without accepting an unbound SID. */
    private void requireBoundClient(HttpServletRequest request, UserSessionRepository.SessionRow session,
                                    UserAccountRepository.UserAccountRow user) {
        String bound = session.clientLabel();
        String currentIp = clientIp(request);
        String boundUa = LoginService.boundUserAgent(bound);
        String boundIp = LoginService.boundIp(bound);
        String currentUa = LoginService.boundUserAgent(LoginService.clientBinding(
                request.getHeader(HttpHeaders.USER_AGENT), currentIp));

        String reason = null;
        if (bound == null || !bound.startsWith("v2:") || boundIp == null) {
            reason = "CLIENT_BINDING_MISSING";
        } else if (!Objects.equals(boundUa, currentUa)) {
            reason = "USER_AGENT_MISMATCH";
        } else if (!ClientNetworks.sameAddress(boundIp, currentIp)) {
            reason = "CLIENT_IP_MISMATCH";
        }
        if (reason == null) {
            return;
        }
        log.warn("会话客户端绑定不匹配 sessionId={} userId={} reason={} boundIp={} currentIp={}",
                session.id(), session.userId(), reason, boundIp, currentIp);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sessionId", session.id());
        detail.put("reason", reason);
        detail.put("boundIp", boundIp);
        detail.put("currentIp", currentIp);
        audit.failure("auth.session_rejected", "AUTH", "SESSION", session.id(),
                new AuditService.Actor(user.id(), user.loginName(), user.displayName()),
                currentIp, null, traceOf(request),
                ErrorCode.AUTH_SESSION_EXPIRED.name(), detail);
        throw new ApiException(ErrorCode.AUTH_SESSION_EXPIRED, "会话绑定信息已变化，请重新登录");
    }

    /**
     * 初始口令闸门（M04）：{@code password_changed_at IS NULL} 时只放行白名单端点。
     *
     * <p>为什么必须在认证层集中实现：这是"账号级"限制，不是"某个接口的业务规则"。
     * 分散到各 Controller 就会出现遗漏，而且新端点默认是放开的（黑名单思维）。
     * 这里用方法+路径的白名单，默认拒绝，新增端点自动受限。
     */
    private void requirePasswordChanged(HttpServletRequest request,
                                        UserAccountRepository.UserAccountRow user,
                                        UserSessionRepository.SessionRow session) {
        if (!user.hasInitialPassword()) {
            return;
        }
        if (isAllowedWithInitialPassword(request)) {
            return;
        }
        log.info("初始口令访问业务端点被拒 userId={} target={} {}", user.id(), request.getMethod(), request.getRequestURI());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("path", relativePath(request));
        detail.put("method", request.getMethod());
        audit.failure("auth.initial_password_blocked", "AUTH", "SESSION", session.id(),
                new AuditService.Actor(user.id(), user.loginName(), user.displayName()),
                clientIp(request), null, traceOf(request),
                ErrorCode.PERMISSION_DENIED.name(), detail);
        throw new ApiException(ErrorCode.PERMISSION_DENIED, INITIAL_PASSWORD_MESSAGE);
    }

    private static boolean isAllowedWithInitialPassword(HttpServletRequest request) {
        return INITIAL_PASSWORD_ALLOWLIST.contains(request.getMethod() + " " + relativePath(request));
    }

    /** 去掉 context path 的请求路径；{@code null} 时退化成 {@code ""}（不匹配任何白名单项）。 */
    private static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        return contextPath != null && uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
    }

    private static String traceOf(HttpServletRequest request) {
        return com.xxplatform.api.common.RequestIdFilter.currentTraceIdOrFallback();
    }

    /**
     * 常规主动请求把空闲期限往前推；探针与轮询不推（A02）。
     *
     * <p>续期上限由 SQL 的 {@code LEAST(?, absolute_expires_at)} 与这里的
     * {@link SessionRenewal#nextIdle} 双重保证，绝对期限不会被推后。写库失败只记 warn、
     * 不影响本次请求：续期是"让用户少登录一次"的优化，不该把已经通过认证的请求变成 500。
     *
     * @return 本请求生效的空闲期限（续期成功取新值，否则取原值）
     */
    private OffsetDateTime renewIfActiveRequest(
            HttpServletRequest request, UserSessionRepository.SessionRow session, OffsetDateTime now) {
        if (isNonRenewing(request)) {
            return session.idleExpiresAt();
        }
        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(
                now, properties.getIdleTtl(), session.idleExpiresAt(), session.absoluteExpiresAt());
        if (next.isEmpty()) {
            return session.idleExpiresAt();
        }
        try {
            sessions.touch(session.id(), next.get());
        } catch (RuntimeException e) {
            log.warn("会话续期失败，本次请求按原空闲期限继续 sessionId={} error={}", session.id(), e.toString());
            return session.idleExpiresAt();
        }
        return next.get();
    }

    /**
     * 探针、预加载与后台轮询都不推进空闲期（M01）。
     *
     * <p>判据三条，任一成立即不续期：
     * <ol>
     *   <li>路径命中服务端配置的 {@code xx.session.non-renewing-paths}（默认健康/就绪检查、
     *       客户端配置下发、任务状态轮询路径）——这是"服务端可判定"的主判据，
     *       客户端不带任何头也生效；</li>
     *   <li>请求头 {@code X-Background-Request: true}；</li>
     *   <li>预加载用途头 {@code Sec-Purpose}/{@code X-Purpose} 含 prefetch/preload/preview。</li>
     * </ol>
     *
     * <p><b>为什么不把"未知轮询默认不续期"做成全局默认</b>：续期只发生在
     * {@code require} 里，而"哪些读请求是轮询"服务端无法凭路径完全判断（同一个列表接口
     * 既可能被用户手动刷新也可能被定时器调用）。把**未识别**的请求一律不续期，等于让
     * 常规用户的正常操作也不续期，会话退化成固定 15 分钟——那是可用性事故。
     * 因此这里采取"已知轮询路径 + 显式标记"的组合，并让路径清单可部署配置扩展。
     * 真正兜底的是绝对期限与空闲期限的严格判定（60 秒写节流不参与过期判定）。
     */
    private boolean isNonRenewing(HttpServletRequest request) {
        if (isProbe(request) || hasBackgroundHeader(request) || isPreload(request)) {
            return true;
        }
        return false;
    }

    private boolean isProbe(HttpServletRequest request) {
        String path = relativePath(request);
        for (String pattern : properties.getNonRenewingPaths()) {
            if (pathMatches(pattern, path)) {
                return true;
            }
        }
        return FALLBACK_PROBE_PATHS.contains(path);
    }

    private static boolean hasBackgroundHeader(HttpServletRequest request) {
        String background = request.getHeader(BACKGROUND_HEADER);
        return background != null && "true".equalsIgnoreCase(background.trim());
    }

    private static boolean isPreload(HttpServletRequest request) {
        for (String header : PURPOSE_HEADERS) {
            String value = request.getHeader(header);
            if (value == null) {
                continue;
            }
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("prefetch") || lower.contains("preload") || lower.contains("preview")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单段通配匹配：{@code /tasks/*} 命中 {@code /tasks/123}，不命中 {@code /tasks/1/attempts}。
     *
     * <p>只支持整段 {@code *}（不引入 Ant 风格 {@code **}）：路径清单是给运维配置的安全相关
     * 白名单，语义越少越不容易配错；{@code /tasks/*}{@code /attempts} 这类多段规则直接写全。
     */
    private static boolean pathMatches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        String[] parts = pattern.trim().split("/", -1);
        String[] actual = path.split("/", -1);
        if (parts.length != actual.length) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            if ("*".equals(parts[i])) {
                continue;
            }
            if (!parts[i].equals(actual[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 尝试解析，失败返回 {@code Optional.empty()} 而不是抛异常。
     *
     * <p>给"登录态可有可无"的端点用（例如将来公开页面上要显示"已登录则显示用户名"）。
     * 现在没有调用点，但保留它是为了让调用方**不必**写
     * {@code try { require(...) } catch (ApiException e) { ... }}——那种写法会把
     * 真正的 500 也一起吞成"未登录"。
     */
    public Optional<AuthenticatedSession> tryResolve(HttpServletRequest request) {
        try {
            return Optional.of(require(request));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }
}
