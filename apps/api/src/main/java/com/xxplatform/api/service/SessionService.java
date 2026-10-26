package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.infra.CsrfSessionRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话签发、轮换与终结（契约 {@code api-conventions.md} L47："login 轮换 SID"；
 * {@code POST /auth/logout} 撤销当前会话）。
 *
 * <h2>为什么轮换</h2>
 * 登录前客户端可能已经带着一个旧 SID（换账号、或会话被劫持后重新登录）。
 * 如果不撤销旧会话，**换账号时旧会话仍然有效**——A 在共用终端上退出后 B 登录，
 * 攻击者手里的 A 的 SID 还能继续用。所以新会话签发前先把提交上来的旧 SID 撤销。
 *
 * <h2>为什么"撤销 + 插入 + 绑定 CSRF"要在同一个事务里</h2>
 * 三条语句描述的是同一个状态迁移。中途失败会留下两种坏状态：旧会话已撤销但新会话没建
 * （用户被踢出且拿不到新身份）、新会话已建但 CSRF 没绑定（下次请求必 403，用户会以为是
 * 网络问题）。放在一个事务里，失败就整体回滚，用户看到 500 重新登录即可。
 *
 * <h2>为什么慢哈希不在这里</h2>
 * 口令校验要 ~619 ms（见 {@link PasswordHasher}）。如果把校验放进这个事务，
 * 每个登录请求都会占用一个数据库连接近 1 秒（连接池只有 10 个）。所以
 * {@link LoginService} 先在事务外完成校验，再调用本类的方法——本方法内部**只有
 * 数据库操作**，事务时间在毫秒级。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /**
     * SID Cookie 名由契约固定，不做配置项。
     *
     * <p>依据：{@code openapi.yaml} 的 {@code securitySchemes.sessionCookie} 写死
     * {@code type: apiKey, in: cookie, name: "SID"}；{@code api-conventions.md} L45 也写明
     * "唯一身份方案 SID Cookie Session"。允许配置会让前后端各配一个名字，然后调试半天。
     */
    public static final String COOKIE_NAME = "SID";

    /** SID 熵：256 位（与 CSRF token 同级；base64url 后 43 字符）。 */
    private static final int SID_BYTES = 32;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    private final UserSessionRepository sessionRepository;
    private final CsrfSessionRepository csrfSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final SessionProperties properties;

    public SessionService(UserSessionRepository sessionRepository,
                          CsrfSessionRepository csrfSessionRepository,
                          UserAccountRepository userAccountRepository,
                          SessionProperties properties) {
        this.sessionRepository = sessionRepository;
        this.csrfSessionRepository = csrfSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.properties = properties;
    }

    /**
     * 签发新会话：锁账号 → 复核状态 → 撤销旧会话（含该账号的全部其他会话）→ 插入 → 绑定 CSRF。
     *
     * <p><b>为什么先锁 {@code auth.app_user}</b>（A02）：契约要求"单账号 1 会话，新登录撤销旧会话"。
     * 只撤销客户端提交的 SID 只能覆盖"同一浏览器重复登录"，另一台设备首次登录时没有 SID 可提交，
     * 旧会话会活着。锁住账号行让"撤销全部旧会话 + 插入新会话"对同一账号串行，多实例部署下也成立
     * （进程内锁只在单实例内有效，不能用作保证）。
     *
     * <p><b>为什么锁里要再判一次状态</b>：口令校验在事务外（慢哈希不占连接），从校验通过到拿到锁
     * 之间账号可能已被停用或软删。对外仍抛与登录失败一致的 401，避免暴露"账号存在但被停用"。
     *
     * @param userId      已通过口令校验的用户
     * @param traceId     本次请求的 traceId（落库用于把会话与请求日志串起来）
     * @param clientLabel 客户端标识（User-Agent 截断），仅用于"我的会话"列表展示
     * @param presentedSid 客户端本次带来的 SID（可能为 null；null 表示首次登录）
     * @param csrfId      本次请求使用的 CSRF 会话行 id（用于把 CSRF 会话绑定到 SID）
     */
    @Transactional
    public IssuedSession begin(UUID userId, String traceId, String clientLabel,
                               String presentedSid, UUID csrfId) {
        UserAccountRepository.UserAccountRow account = userAccountRepository.lockById(userId)
                .orElseThrow(SessionService::accountUnavailable);
        if (!account.isActive()) {
            log.warn("登录竞态：会话签发前账号已不可用 status={} userId={} traceId={}",
                    account.status(), userId, traceId);
            throw accountUnavailable();
        }

        if (presentedSid != null && !presentedSid.isBlank()) {
            // 提交上来的旧 SID 未必属于本账号（共用终端上换账号登录），所以这条必须单独做：
            // revokeAllForUser 只覆盖同一 user_id 的行。
            int revoked = sessionRepository.revokeBySidHash(Sha256.of(presentedSid));
            // 0 行是正常情况（旧 SID 本来就已经撤销或不存在），只在 >0 时记 info
            if (revoked > 0) {
                log.info("登录轮换：撤销旧会话 {} 条 userId={} traceId={}", revoked, userId, traceId);
            }
        }

        // 单账号单会话：把该账号在其他浏览器/设备上的有效会话一并撤销（A02）。
        int kicked = sessionRepository.revokeAllForUser(userId);
        if (kicked > 0) {
            log.info("单会话策略：撤销该账号其他有效会话 {} 条 userId={} traceId={}",
                    kicked, userId, traceId);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime absoluteExpiresAt = now.plus(properties.getAbsoluteTtl());
        OffsetDateTime idleExpiresAt = now.plus(properties.getIdleTtl());
        if (idleExpiresAt.isAfter(absoluteExpiresAt)) {
            // 配置把空闲 TTL 设得比绝对 TTL 还长。表上有 CHECK 会直接拒（23514），
            // 但那样用户看到的是 500。这里夹住并告警：配置要改，但登录仍能成功。
            log.warn("xx.session.idle-ttl({}) 大于 absolute-ttl({})，已按绝对超时截断",
                    properties.getIdleTtl(), properties.getAbsoluteTtl());
            idleExpiresAt = absoluteExpiresAt;
        }

        byte[] sidBytes = new byte[SID_BYTES];
        random.nextBytes(sidBytes);
        String sidValue = B64.encodeToString(sidBytes);

        UUID sessionId = UUID.randomUUID();
        sessionRepository.insert(sessionId, userId, Sha256.of(sidValue), idleExpiresAt,
                absoluteExpiresAt, clientLabel, traceId);

        if (csrfId != null) {
            int bound = csrfSessionRepository.bindSession(csrfId, sessionId);
            if (bound != 1) {
                // 事务里回来 0 行说明这行 CSRF 刚被并发 deleteExpired 清掉了。
                // 不中止登录：CSRF 会话缺失只会让下一次请求重新取 token，不应把用户挡在门外。
                log.warn("CSRF 会话绑定失败（csrfId={}），会话已签发，待前端重新取 csrf", csrfId);
            }
        }

        return new IssuedSession(sidValue, sessionId, idleExpiresAt, absoluteExpiresAt);
    }

    /**
     * 会话签发时账号已不可用：对外与登录失败完全一致（401 {@code AUTH_INVALID_CREDENTIALS}）。
     *
     * <p>不复用 {@code AUTH_SESSION_EXPIRED}：这条路径根本没有会话可过期，而且登录接口返回
     * "会话过期"会诱导客户端去调 renew。文案也必须与 {@link LoginService} 的失败提示一致，
     * 否则攻击者能靠错误文案区分"账号存在但被停用"。
     */
    private static ApiException accountUnavailable() {
        return new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, LoginService.FAILURE_MESSAGE);
    }

    /**
     * 结束（撤销）一个会话（任务 11：{@code POST /auth/logout}）。
     *
     * <p><b>为什么只写 {@code revoked_at}、不删行</b>：{@code auth.user_session} 同时承担
     * 审计职责（谁在什么时间从哪个 UA 登录过）。物理删除会让"退出"变成不可追溯的事件，
     * 而撤销只加一列时间戳，既不破坏历史也不影响认证判定——{@code SessionAuthenticator}
     * 看到 {@code revoked_at} 非空即判 {@code AUTH_SESSION_EXPIRED}。
     *
     * <p><b>为什么不在这里删 CSRF 会话行</b>：CSRF 行单独不构成身份（必须同时持有 SID），
     * 到期由 {@code CsrfTokenService.issue} 顺带清理。如果在退出时删掉，客户端一旦重试
     * 退出（双击、网络重试）就会从"401 会话已终止"变成"403 来源不可信"，把一次正常退出
     * 变成让人怀疑被攻击的报错。
     *
     * <p><b>为什么按 {@code sessionId} 而不是重新解析 SID</b>：本次请求已经通过
     * {@link SessionAuthenticator#require} 认证，那个 {@link AuthenticatedSession} 里带着
     * 会话行 id；再拿 SID 算一次摘要既多余又引入"两次查询之间会话被换掉"的窗口。
     *
     * @param sessionId 已认证会话的行 id
     * @param traceId   本次请求的 traceId（只用于日志串联）
     * @return {@code true} 本次调用真的撤销了一行；{@code false} 表示已经被撤销过（并发/重试）
     */
    @Transactional
    public boolean end(UUID sessionId, String traceId) {
        int revoked = sessionRepository.revokeById(sessionId);
        if (revoked == 0) {
            log.debug("退出来自已撤销的会话，忽略 sessionId={} traceId={}", sessionId, traceId);
            return false;
        }
        log.info("会话已撤销 sessionId={} traceId={}", sessionId, traceId);
        return true;
    }

    /**
     * 刚签发成功的会话。
     *
     * @param sidValue           SID 原文——**只在这里出现一次**，写进 Set-Cookie 后不再保留
     * @param sessionId          会话行 id
     * @param idleExpiresAt      空闲超时（响应体返回给前端）
     * @param absoluteExpiresAt  绝对超时（响应体与 Cookie Max-Age 都基于它）
     */
    public record IssuedSession(String sidValue, UUID sessionId,
                                OffsetDateTime idleExpiresAt, OffsetDateTime absoluteExpiresAt) {
    }
}
