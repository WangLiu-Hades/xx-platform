package com.xxplatform.api.auth;

import com.xxplatform.api.common.Sha256;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话签发与轮换（契约 {@code api-conventions.md} L47："login 轮换 SID"）。
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
    private final SessionProperties properties;

    public SessionService(UserSessionRepository sessionRepository,
                          CsrfSessionRepository csrfSessionRepository,
                          SessionProperties properties) {
        this.sessionRepository = sessionRepository;
        this.csrfSessionRepository = csrfSessionRepository;
        this.properties = properties;
    }

    /**
     * 签发新会话（并撤销提交上来的旧 SID）。
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
        if (presentedSid != null && !presentedSid.isBlank()) {
            int revoked = sessionRepository.revokeBySidHash(Sha256.of(presentedSid));
            // 0 行是正常情况（旧 SID 本来就已经撤销或不存在），只在 >0 时记 info
            if (revoked > 0) {
                log.info("登录轮换：撤销旧会话 {} 条 userId={} traceId={}", revoked, userId, traceId);
            }
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
