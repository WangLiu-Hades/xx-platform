package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestCookies;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.config.CsrfProperties;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.CsrfTokenService;
import com.xxplatform.api.service.LoginService;
import com.xxplatform.api.service.PasswordService;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自助改密端点（M04）。
 *
 * <p>路径是 {@code /me/password} 而不是 {@code /users/{id}/password}：它只能改**自己**的
 * 口令，作用对象由会话决定，不接受路径参数。这样即使有人把路径参数拼成别人的 id，
 * 也没有可注入的位置。
 *
 * <p><b>为什么单独一个 Controller 而不是塞进 {@code AuthController}</b>：
 * {@code AuthController} 已经承载登录/退出/续期/CSRF 四个端点，而改密是"登录后的账号操作"，
 * 与"认证流程"是两类关注点；拆开后 {@code /me} 系列将来加"我的会话列表"也有地方放。
 *
 * <p>CSRF 校验不在本类里重复：{@code CsrfFilter} 对所有非安全方法统一校验
 * （{@code X-CSRF-Token} + Cookie 对）。这里再解析一次只是为了拿到**当前那一行**的 id，
 * 成功后把它作废（M03：改密要轮换并撤销旧凭据），规则仍只有
 * {@link CsrfTokenService#resolve} 一份。
 *
 * <p>契约要求（{@code openapi.yaml} → {@code /me/password}）：{@code Idempotency-Key} 必填、
 * 成功 200 体 {@code ActionResult}（只有 {@code accepted}）、当前口令错 401
 * {@code AUTH_INVALID_CREDENTIALS}、规则不满足 400 {@code VALIDATION_ERROR}。
 */
@RestController
@RequestMapping("/me")
public class PasswordController {

    private final SessionAuthenticator sessionAuthenticator;
    private final PasswordService passwordService;
    private final CsrfTokenService csrfTokenService;
    private final SessionProperties sessionProperties;

    public PasswordController(SessionAuthenticator sessionAuthenticator, PasswordService passwordService,
            CsrfTokenService csrfTokenService, SessionProperties sessionProperties) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.passwordService = passwordService;
        this.csrfTokenService = csrfTokenService;
        this.sessionProperties = sessionProperties;
    }

    /**
     * {@code POST /me/password}：校验当前口令、写入新口令，并轮换 SID/CSRF。
     *
     * <p>成功返回 {@code 200} 与 {@code {"accepted": true}}（契约 {@code ActionResult}），
     * 同时通过 {@code Set-Cookie} 下发**新**的 SID 与 CSRFID、通过响应头 {@code X-CSRF-Token}
     * 下发新的同步器 token。
     *
     * <p><b>为什么不再返回 {@code reLoginRequired}</b>：契约的响应 schema 是
     * {@code ActionResult}（{@code additionalProperties:false}，只有 {@code accepted}），
     * 多一个字段会让严格校验的前端/契约测试失败；而且语义上也不需要——成功响应已经带着
     * 可用的新凭据，用户不需要重新登录。
     *
     * <p><b>幂等重放</b>：命中 {@code Idempotency-Key} 账本时直接返回 {@code accepted}，
     * 不再下发新 Cookie（第一次响应已经给过），也不产生第二次副作用。
     */
    @PostMapping("/password")
    public ResponseEntity<ApiResponses> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                       @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false)
                                                       String idempotencyKey,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        AuthenticatedSession session = sessionAuthenticator.require(request);

        String cookieValue = RequestCookies.value(request, csrfTokenService.properties().getCookieName());
        CsrfTokenService.CsrfBinding binding = csrfTokenService.resolve(
                        cookieValue, request.getHeader(CsrfTokenService.HEADER_NAME))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_CSRF_INVALID, "请求来源不可信"));

        String clientIp = sessionAuthenticator.clientIp(request);
        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        PasswordService.ChangeResult result = passwordService.changePassword(
                session,
                body.currentPassword(),
                body.newPassword(),
                idempotencyKey,
                RequestCookies.value(request, SessionService.COOKIE_NAME),
                LoginService.clientBinding(request.getHeader(HttpHeaders.USER_AGENT), clientIp),
                binding.csrfId(),
                clientIp,
                traceId);

        if (!result.replayed()) {
            // M03：改密属于"凭据变更"，必须轮换 SID 与 CSRF 并撤销旧值。新值只能通过
            // Set-Cookie/响应头下发——ActionResult 的 body 里没有地方放它们。
            response.addHeader(HttpHeaders.SET_COOKIE, sidCookie(result.session()).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(result.csrf().cookieValue()).toString());
            response.addHeader(CsrfTokenService.HEADER_NAME, result.csrf().token());
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);
        return ResponseEntity.ok(ApiResponses.success(data, traceId));
    }

    /** 构造新的 SID Cookie：属性与登录时完全一致，Max-Age 绑到新的绝对期限。 */
    private ResponseCookie sidCookie(SessionService.IssuedSession session) {
        Duration remaining = Duration.between(Instants.nowUtc(), session.absoluteExpiresAt());
        return ResponseCookie.from(SessionService.COOKIE_NAME, session.sidValue())
                .httpOnly(true)
                .secure(sessionProperties.isSecureCookie())
                .sameSite("Lax")
                .path(sessionProperties.getCookiePath())
                .maxAge(remaining.isNegative() ? Duration.ZERO : remaining)
                .build();
    }

    /** 构造新的 CSRF Cookie；属性取自 {@code xx.csrf.*}，与 {@code GET /auth/csrf} 一致。 */
    private ResponseCookie csrfCookie(String cookieValue) {
        CsrfProperties props = csrfTokenService.properties();
        return ResponseCookie.from(props.getCookieName(), cookieValue)
                .httpOnly(true)
                .secure(props.isSecureCookie())
                .sameSite("Lax")
                .path(props.getCookiePath())
                .build();
    }

    /**
     * 改密请求体。
     *
     * <p>Bean Validation 只做"必填 + 不超长"的粗筛（防止把 1MB 字符串送进 PBKDF2）；
     * 长度规则（15–128 码点）在 {@link PasswordService} 里判——那里才知道 NFC 规范化之后
     * 的真实字符数，也才能给出字段级错误。
     */
    public record ChangePasswordRequest(
            @NotBlank @Size(max = 256) String currentPassword,
            @NotBlank @Size(max = 256) String newPassword) {
    }
}
