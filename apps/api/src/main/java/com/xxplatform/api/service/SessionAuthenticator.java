package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestCookies;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.infra.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 把"请求里的 SID Cookie"解析成 {@link AuthenticatedSession}（任务 6：{@code GET /me}）。
 *
 * <p>所有需要"已登录"的端点都应当经由这里拿身份，而不是各自读 Cookie 查库——
 * 会话失效判定（撤销/空闲超时/绝对超时/账号停用）只有一份实现，才不会出现
 * "某个端点漏判了 revoked_at"这种越权口子。
 *
 * <p>判定顺序与对应的错误码：
 * <table border="1">
 *   <caption>失败分支</caption>
 *   <tr><th>情况</th><th>HTTP</th><th>code</th><th>理由</th></tr>
 *   <tr><td>没有 SID Cookie</td><td>401</td><td>{@code AUTH_REQUIRED}</td>
 *       <td>客户端从未登录，前端应引导登录页</td></tr>
 *   <tr><td>摘要查不到会话</td><td>401</td><td>{@code AUTH_REQUIRED}</td>
 *       <td>Cookie 是伪造/来自别处（换库、清表）——等价于没登录</td></tr>
 *   <tr><td>已撤销（登出或登录轮换）</td><td>401</td><td>{@code AUTH_SESSION_EXPIRED}</td>
 *       <td>会话确实存在过，前端按"清登录态、不自动重放写请求"处理</td></tr>
 *   <tr><td>空闲或绝对超时</td><td>401</td><td>{@code AUTH_SESSION_EXPIRED}</td>
 *       <td>同上；两者都要求重新认证</td></tr>
 *   <tr><td>账号被停用/软删</td><td>401</td><td>{@code AUTH_SESSION_EXPIRED}</td>
 *       <td>不返回 403：403 会让前端显示"无权限"并保留登录态，
 *           而禁用用户的正确表现是踢回登录页</td></tr>
 * </table>
 *
 * <p><b>为什么"查不到"用 {@code AUTH_REQUIRED} 而不是 {@code AUTH_SESSION_EXPIRED}</b>：
 * 后者会让前端认为"我的会话刚过期"，可能触发自动续期逻辑；而查不到意味着这个
 * SID 从来不是有效会话，续期也不可能成功。区分开能少一轮无意义的 renew 请求。
 */
@Component
public class SessionAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticator.class);

    private final UserSessionRepository sessions;
    private final UserAccountRepository users;

    public SessionAuthenticator(UserSessionRepository sessions, UserAccountRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    /**
     * 解析并校验当前请求的会话。
     *
     * @throws ApiException 任一步失败；{@code code} 见类注释的表格
     */
    public AuthenticatedSession require(HttpServletRequest request) {
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

        return new AuthenticatedSession(session.userId(), session.id(), session.idleExpiresAt(),
                session.absoluteExpiresAt(), user);
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
