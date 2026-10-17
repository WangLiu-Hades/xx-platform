package com.xxplatform.api.auth;

import com.xxplatform.api.common.CsrfProperties;
import com.xxplatform.api.common.Sha256;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSRF 令牌的签发与校验（同步器 token 模式）。
 *
 * <p>契约（{@code GET /auth/csrf}）要求：
 * 匿名/已登录**均可**获取；响应设置 {@code CSRFID=<opaque>; HttpOnly; Secure; SameSite=Lax;
 * Path=/api/v1}；body 返回与该 Cookie **绑定的同步器 token**；写请求须同时携带
 * Cookie 与 {@code X-CSRF-Token}。
 *
 * <h2>为什么 Cookie 值与 token 是两个不同的随机值</h2>
 * 这是双重提交（double-submit）与同步器模式的组合：
 * <ul>
 *   <li>Cookie 证明"这个浏览器确实先来领过 token"（攻击者跨站拿不到 HttpOnly Cookie）；</li>
 *   <li>body/header 里的 token 证明"调用方能读到同源的响应体"（攻击者能发出请求但读不到响应）；</li>
 *   <li>服务端把两者的哈希都存库并按 Cookie 关联，于是**即使攻击者能设置自己域下的同名
 *       Cookie**（Cookie 注入），也无法构造出与之匹配的 token —— 这正是单纯
 *       double-submit 的弱点，被服务端存储补上了。</li>
 * </ul>
 *
 * <h2>为什么不复用 Spring Security 的 CSRF</h2>
 * 见 {@link CsrfSessionRepository} 的类注释。核心原因：契约把 CSRF 失败明确映射到
 * {@code 403 AUTH_CSRF_INVALID}，且要求"CSRFID 不是身份"，这与 Spring Security 默认把
 * CSRF 与认证过滤器链绑定的模型不一致，绕过默认行为的成本高于直接实现。
 */
@Service
public class CsrfTokenService {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenService.class);

    /** token 原文的最小长度下界（契约 {@code CsrfToken} 的 minLength 为 16）。 */
    private static final int TOKEN_BYTES = 32;

    /**
     * 契约里 {@code headerName} 枚举只有这一个值（{@code CsrfTokenData.headerName}）。
     *
     * <p>写成常量并由测试断言，避免将来有人"顺手"改成别的名字导致前端对不上。
     */
    public static final String HEADER_NAME = "X-CSRF-Token";

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final CsrfSessionRepository repository;
    private final CsrfProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CsrfTokenService(CsrfSessionRepository repository, CsrfProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 签发一套新的 CSRF 凭据。
     *
     * <p>每次调用都新建一行并返回新的 Cookie 值 + token，**不复用旧值**：
     * 契约要求"登录后轮换、重新获取"，复用会让旧 token 长期有效。
     *
     * @return 需要写回浏览器的 Cookie 值与返回给前端的 token
     */
    @Transactional
    public IssuedCsrf issue() {
        // 顺带清理过期行：不需要调度框架，量级也很小
        int purged = repository.deleteExpired();
        if (purged > 0) {
            log.debug("清理过期 CSRF 会话 {} 条", purged);
        }

        String cookieValue = randomToken();
        String token = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.getTtl());

        repository.insert(
                UUID.randomUUID(),
                null, // 登录成功后由 SessionService 绑定；当前阶段还没有会话
                Sha256.of(cookieValue),
                Sha256.of(token),
                expiresAt);

        return new IssuedCsrf(cookieValue, token, expiresAt);
    }

    /**
     * 校验并把请求关联到具体的 CSRF 会话行。
     *
     * <p>为什么返回 {@link CsrfBinding} 而不只是 boolean：{@code POST /auth/login} 通过后
     * 要把 {@code auth.csrf_session.session_id} 绑到新签发的 SID 上
     * （契约"登录后轮换、重新获取 csrf"的前提是这行能被追踪），所以调用方需要拿到行 id。
     *
     * <p>任一步失败都返回 {@code Optional.empty()}，由调用方统一转成
     * {@code 403 AUTH_CSRF_INVALID}——**不要**区分"没 Cookie"和"token 不匹配"，
     * 那会给攻击者提供探测信息。
     *
     * <p>比较用常量时间（{@link Sha256#constantTimeEquals}），避免通过响应时间侧信道
     * 逐字节猜 token。
     *
     * @param cookieValue 请求携带的 {@code CSRFID} 原文
     * @param token       请求头 {@code X-CSRF-Token} 原文
     */
    @Transactional(readOnly = true)
    public Optional<CsrfBinding> resolve(String cookieValue, String token) {
        if (cookieValue == null || cookieValue.isBlank() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        return repository.findActiveByCookieHash(Sha256.of(cookieValue))
                .filter(row -> Sha256.constantTimeEquals(row.tokenHash(), Sha256.of(token)))
                .map(row -> new CsrfBinding(row.id()));
    }

    /**
     * 只需要"通过/不通过"的判断。
     *
     * @see #resolve(String, String)
     */
    @Transactional(readOnly = true)
    public boolean validate(String cookieValue, String token) {
        return resolve(cookieValue, token).isPresent();
    }

    /** 契约固定：Cookie 名、Path、SameSite 由属性给出，头名由常量给出。 */
    public String headerName() {
        return HEADER_NAME;
    }

    /** 让调用方拿到 Cookie 写回所需的属性值。 */
    public CsrfProperties properties() {
        return properties;
    }

    /**
     * 生成高熵随机值（32 字节 → base64url 43 字符，满足契约 minLength 16）。
     *
     * <p>用 {@link SecureRandom} 而不是 {@code Math.random}/{@code UUID.randomUUID().toString()}：
     * token 是安全凭据，必须来自密码学随机源。
     */
    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return B64URL.encodeToString(bytes);
    }

    /**
     * 签发结果。
     *
     * @param cookieValue 写回 {@code CSRFID} Cookie 的值（前端 JS 读不到，HttpOnly）
     * @param token        返回在响应体 {@code data.token} 里，前端存内存并放入请求头
     * @param expiresAt    该 CSRF 会话的过期时间
     */
    public record IssuedCsrf(String cookieValue, String token, OffsetDateTime expiresAt) {
    }

    /**
     * 校验通过后拿到的关联信息。
     *
     * @param csrfId {@code auth.csrf_session.id}，登录成功时用于绑定新会话
     */
    public record CsrfBinding(UUID csrfId) {
    }
}
