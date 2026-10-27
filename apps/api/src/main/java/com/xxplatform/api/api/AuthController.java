package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestCookies;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.common.SameOriginVerifier;
import com.xxplatform.api.config.CsrfProperties;
import com.xxplatform.api.config.SessionProperties;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.CsrfTokenService;
import com.xxplatform.api.service.LoginService;
import com.xxplatform.api.service.MeService;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关端点（M01）。已实现 {@code GET /auth/csrf}（任务 4）、
 * {@code POST /auth/login}（任务 5）、{@code GET /me}（任务 6）与
 * {@code POST /auth/logout}（任务 11，让"登录 → 进入 → 退出"在真实接口下闭环）。
 *
 * <p>契约要点（{@code openapi.yaml} → {@code /auth/csrf}）：
 * {@code x-permission: PUBLIC}、{@code security: []}（匿名也能取）、同源且校验
 * Origin/Referer、设置 {@code CSRFID=<opaque>; HttpOnly; Secure; SameSite=Lax; Path=/api/v1}
 * 的 HttpOnly Cookie，并在 body 返回与之绑定的同步器 token。
 *
 * <p><b>为什么 GET 也要校验 Origin</b>：攻击者可以用 {@code <img>}/{@code <script>} 发起
 * 跨站 GET，把我们签发的新 CSRF Cookie 写入受害者浏览器（"CSRF token 固定"攻击的变种）。
 * 校验 Origin 能挡住浏览器发起的跨站请求；非浏览器客户端通常不带 Origin，按"非
 * 浏览器请求"放行（它们不受跨站攻击模型影响）。
 */
@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 契约里 {@code CsrfTokenData.headerName} 的唯一枚举值。 */
    private static final String HEADER_NAME = CsrfTokenService.HEADER_NAME;

    private final CsrfTokenService csrfTokenService;
    private final LoginService loginService;
    private final SessionProperties sessionProperties;
    private final SessionAuthenticator sessionAuthenticator;
    private final MeService meService;
    private final SessionService sessionService;

    public AuthController(CsrfTokenService csrfTokenService,
                          LoginService loginService,
                          SessionProperties sessionProperties,
                          SessionAuthenticator sessionAuthenticator,
                          MeService meService,
                          SessionService sessionService) {
        this.csrfTokenService = csrfTokenService;
        this.loginService = loginService;
        this.sessionProperties = sessionProperties;
        this.sessionAuthenticator = sessionAuthenticator;
        this.meService = meService;
        this.sessionService = sessionService;
    }

    /**
     * {@code GET /auth/csrf}：签发 CSRF Cookie + 同步器 token。
     *
     * @param request  用于同源校验
     * @param response 用于写 {@code Set-Cookie}
     */
    @GetMapping("/auth/csrf")
    public ResponseEntity<ApiResponses> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        if (!SameOriginVerifier.isSameOrigin(request)) {
            // 契约把 CSRF/Origin 失败固定映射为 403 AUTH_CSRF_INVALID（不是 PERMISSION_DENIED）
            throw new ApiException(ErrorCode.AUTH_CSRF_INVALID, "请求来源不可信");
        }

        CsrfTokenService.IssuedCsrf issued = csrfTokenService.issue();
        var props = csrfTokenService.properties();

        ResponseCookie cookie = ResponseCookie.from(props.getCookieName(), issued.cookieValue())
                .httpOnly(true)          // 契约：HttpOnly —— 前端 JS 读不到，才挡得住 XSS 窃取
                .secure(props.isSecureCookie()) // 契约要求 Secure；本地 http 调试由配置放开，见 CsrfProperties
                .sameSite("Lax")         // 契约：SameSite=Lax —— 跨站 POST 不带 Cookie
                .path(props.getCookiePath()) // 契约：Path=/api/v1，不泄露给同域其他应用
                .build();
        // 用 header 追加而不是 response.addCookie：ResponseCookie 才能精确控制 SameSite
        // （Servlet 的 Cookie API 至今没有 SameSite，需要容器私有 API，不可移植）
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", issued.token());
        data.put("headerName", HEADER_NAME);

        if (!props.isSecureCookie()) {
            // 生产必须为 true（SD06 要求 HTTPS/TLS）。降级为 WARN 是因为本地联调无法用 https，
            // 不能因此让开发环境完全跑不起来。
            log.warn("CSRF Cookie 未带 Secure 属性（xx.csrf.secure-cookie=false）。"
                    + "生产环境必须开启 HTTPS 并设为 true。");
        }

        return ResponseEntity.ok(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }

    /**
     * {@code POST /auth/login}：口令登录，签发 SID 会话 Cookie。
     *
     * <p>执行顺序（顺序本身就是安全要求）：
     * <ol>
     *   <li><b>先校验 CSRF</b>。契约把这个操作声明为 {@code security: []}（匿名可访问），
     *       但它仍然 {@code $ref: CsrfToken}，因为登录本身是"改变状态"的写请求：
     *       没有 CSRF 防线，攻击者可以拿受害者的浏览器去撞口令（login CSRF），
     *       把受害者登进攻击者的账号，后续操作全落在攻击者账号里。</li>
     *   <li>再走 {@link LoginService#login}（频控 → 口令校验 → 签发会话）。失败统一 401。</li>
     *   <li>最后写 SID Cookie。</li>
     * </ol>
     *
     * <p>Origin 校验不在这里重复：{@code developmentCorsFilter}（{@code WebConfiguration}）
     * 与 {@link com.xxplatform.api.common.CsrfFilter} 都在过滤器阶段按 {@code Origin}
     * 拒绝跨源请求，且拒绝体已是契约错误壳；{@link SameOriginVerifier} 是两边共用的同一份规则。
     * 在控制器再写一遍只会得到两套可能不一致的规则。
     *
     * <p>响应体只返回 {@code userId/idleExpiresAt/absoluteExpiresAt}——契约 {@code SessionData}
     * 就这三个字段（{@code additionalProperties:false}）。显示名、权限等留给 {@code GET /me}。
     */
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponses> login(@Valid @RequestBody LoginRequest body,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        String csrfCookieValue = RequestCookies.value(request, csrfTokenService.properties().getCookieName());
        String csrfToken = request.getHeader(HEADER_NAME);
        CsrfTokenService.CsrfBinding binding = csrfTokenService.resolve(csrfCookieValue, csrfToken)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_CSRF_INVALID, "请求来源不可信"));

        LoginService.LoginResult result = loginService.login(
                body.loginName(),
                body.password(),
                RequestCookies.value(request, SessionService.COOKIE_NAME),
                binding.csrfId(),
                clientIp(request),
                LoginService.clientLabel(request.getHeader(HttpHeaders.USER_AGENT)),
                RequestIdFilter.currentTraceIdOrFallback());

        SessionService.IssuedSession session = result.session();
        response.addHeader(HttpHeaders.SET_COOKIE, sidCookie(session).toString());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", result.userId().toString());
        data.put("idleExpiresAt", Instants.iso(session.idleExpiresAt()));
        data.put("absoluteExpiresAt", Instants.iso(session.absoluteExpiresAt()));

        return ResponseEntity.ok(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }

    /**
     * {@code POST /auth/logout}：撤销当前会话并清掉身份 Cookie（任务 11）。
     *
     * <p>契约要点（{@code openapi.yaml} → {@code /auth/logout}）：{@code x-permission: AUTHENTICATED}、
     * {@code security: [{sessionCookie: []}]}、参数 {@code $ref CsrfToken}（必须带
     * {@code X-CSRF-Token}）、响应 200/400/401/403/500、200 体 {@code ActionResult}
     * 只有 {@code accepted}（{@code additionalProperties:false}）。
     *
     * <p><b>为什么这里还留着一次 CSRF 校验</b>：{@link com.xxplatform.api.common.CsrfFilter}
     * 已经在过滤器里拦下了"无 token 的写请求"，正常路径走不到这里。保留这一次是防御纵深：
     * 过滤器的白名单/路径匹配将来若被改动（例如误把 logout 加进豁免），认证后的这道检查
     * 仍然保证"撤销会话必须先证明来源可信"，不会退化成跨站强制下线。
     *
     * <p><b>失败顺序</b>：闸门在认证之前，所以"未登录 + 无 token"现在得到 403 而不是 401；
     * 但"未登录 + 有效 token 对"仍是 401（闸门放行、认证拒绝），前端的 401 → 清身份逻辑不变。
     *
     * <p><b>为什么响应里还要清 Cookie</b>：SID 是 HttpOnly，前端 JS 删不掉；服务端不清，
     * 浏览器会一直带着一个已撤销的 SID，下一个请求得到 401 而不是重新取 CSRF 后的干净状态。
     * {@code Max-Age=0} 才是"让浏览器立刻丢弃"的唯一手段。CSRFID 一并清掉：
     * 它绑定的会话已经终止，留着只会让人误以为还能用。
     *
     * <p><b>幂等性</b>：重复退出返回 401 而不是 200——第二次请求带来的 SID 已经撤销，
     * 认证层判定 {@code AUTH_SESSION_EXPIRED}。这是契约允许的（401 在响应列表里），
     * 且前端 {@code logout()} 对失败只提示不清状态，不影响"已经退出"的事实。
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponses> logout(HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedSession session = sessionAuthenticator.require(request);

        String csrfCookieValue = RequestCookies.value(request, csrfTokenService.properties().getCookieName());
        csrfTokenService.resolve(csrfCookieValue, request.getHeader(HEADER_NAME))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_CSRF_INVALID, "请求来源不可信"));

        sessionService.end(session.sessionId(), RequestIdFilter.currentTraceIdOrFallback());

        CsrfProperties csrfProperties = csrfTokenService.properties();
        response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie(
                SessionService.COOKIE_NAME,
                sessionProperties.getCookiePath(),
                sessionProperties.isSecureCookie()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie(
                csrfProperties.getCookieName(),
                csrfProperties.getCookiePath(),
                csrfProperties.isSecureCookie()).toString());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);

        return ResponseEntity.ok(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }

    /**
     * {@code GET /me}：返回当前登录用户的身份、权限、菜单与数据范围。
     *
     * <p>契约 {@code x-permission: AUTHENTICATED}，只要求有效会话，不再叠加权限码——
     * 任何登录用户都必须能读到自己是谁，否则前端无法决定首屏渲染什么。
     *
     * <p><b>为什么没有 {@code X-CSRF-Token} 参数</b>：本操作是 {@code GET}，契约
     * 未给它声明 {@code CsrfToken} 参数。读操作不改状态，跨站读也读不到响应
     * （浏览器同源策略拦住），因此不需要同步器 token。
     *
     * <p>响应 200 只有 {@code X-Trace-Id} 一个头，**没有 ETag**：契约没声明条件请求，
     * 而 {@code /me} 的内容（权限、范围）会随管理员改角色而变化，缓存收益不稳定。
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponses> getMe(HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        return ResponseEntity.ok(ApiResponses.success(
                meService.load(session), RequestIdFilter.currentTraceIdOrFallback()));
    }

    /**
     * 构造 SID Cookie。
     *
     * <p>四个属性各有依据：
     * <ul>
     *   <li>{@code HttpOnly}：JS 读不到 SID，XSS 无法直接把会话带走；</li>
     *   <li>{@code SameSite=Lax}：跨站 POST 不带 Cookie（契约明文要求）；</li>
     *   <li>{@code Path=/api/v1}：不泄露给同域的其他应用（{@code api-conventions.md} L45）；</li>
     *   <li>{@code Max-Age}=到 {@code absoluteExpiresAt} 的剩余秒数。**为什么不用会话 Cookie**
     *       （不设 Max-Age）：那样"关浏览器即失效"，与契约声明的绝对超时语义矛盾——
     *       用户会以为会话还在、实际已被浏览器丢掉。用 Max-Age 让浏览器与服务端对齐。</li>
     * </ul>
     */
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

    /**
     * 构造"立刻作废"的 Cookie（{@code Max-Age=0}）。
     *
     * <p>属性必须与签发时**逐项一致**（同名、同 Path、同 SameSite、同 Secure），否则浏览器
     * 认为是另一个 Cookie，原 Cookie 会继续存在——这是清 Cookie 最常见的坑。
     * 值用空串：内容不再重要，重要的是让浏览器把它删掉。
     */
    private static ResponseCookie clearedCookie(String name, String path, boolean secure) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 来源 IP。
     *
     * <p>直接用 {@code getRemoteAddr()}，**不解析** {@code X-Forwarded-For}：那个头默认由客户端
     * 随意伪造，信任它等于让攻击者随意绕过频控（每次换一个 IP 就是一个新桶）。
     * 部署到反向代理之后，应通过容器级配置（如 Tomcat 的 {@code RemoteIpValve} /
     * {@code server.forward-headers-strategy}）让容器自己把可信头解析进 {@code getRemoteAddr()}，
     * 而不是在业务代码里猜。
     */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

}
