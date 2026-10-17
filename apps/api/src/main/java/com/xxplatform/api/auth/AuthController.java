package com.xxplatform.api.auth;

import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关端点（M01）。当前只实现 {@code GET /auth/csrf}。
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

    public AuthController(CsrfTokenService csrfTokenService) {
        this.csrfTokenService = csrfTokenService;
    }

    /**
     * {@code GET /auth/csrf}：签发 CSRF Cookie + 同步器 token。
     *
     * @param request  用于同源校验
     * @param response 用于写 {@code Set-Cookie}
     */
    @GetMapping("/auth/csrf")
    public ResponseEntity<ApiResponses> getCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        if (!isSameOrigin(request)) {
            // 契约把 CSRF/Origin 失败固定映射为 403 AUTH_CSRF_INVALID（不是 PERMISSION_DENIED）
            return ResponseEntity.status(ErrorCode.AUTH_CSRF_INVALID.httpStatus())
                    .body(ApiResponses.error(
                            ErrorCode.AUTH_CSRF_INVALID,
                            "请求来源不可信",
                            RequestIdFilter.currentTraceIdOrFallback(),
                            List.of()));
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
     * 同源校验。
     *
     * <p>规则：
     * <ul>
     *   <li>有 {@code Origin} → 其 host:port 必须等于本次请求的 Host（含代理头推导）；
     *   <li>无 {@code Origin} 但有 {@code Referer} → 取 Referer 的 origin 部分按同一规则比较；
     *   <li>两者都没有 → 视为非浏览器客户端，放行（curl/服务端调用不受 CSRF 攻击模型影响，
     *       真正防线是 Cookie + token 双要素本身）。</li>
     * </ul>
     *
     * <p>比较的是 {@code scheme://host:port} 三元组：只比 host 会放过 http→https 的降级攻击。
     * 由于本地是 http、生产是 https，这里用"请求自身的 scheme"作为期望值，
     * 而不是硬编码 https——否则本地联调全被拒。
     */
    private boolean isSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeader("Referer");
            if (referer == null || referer.isBlank()) {
                return true; // 非浏览器客户端
            }
            origin = originOf(referer);
            if (origin == null) {
                return false; // 有 Referer 但解析不出 origin（格式非法）
            }
        }
        String expected = requestOrigin(request);
        boolean ok = origin.equalsIgnoreCase(expected);
        if (!ok) {
            log.debug("拒绝 CSRF 请求：Origin={} 期望={}", origin, expected);
        }
        return ok;
    }

    /** 本次请求对外可见的 {@code scheme://host[:port]}。 */
    private String requestOrigin(HttpServletRequest request) {
        // 反向代理/网关后需要 ForwardedHeaderFilter 或已配置 server.forward-headers-strategy，
        // 这里直接信任容器解析后的值，不在代码里自行拼 X-Forwarded-*（避免可伪造）。
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    /** 从绝对 URL 中取出 {@code scheme://host[:port]}；失败返回 null。 */
    private String originOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && (port == -1 || port == 80))
                    || ("https".equalsIgnoreCase(scheme) && (port == -1 || port == 443));
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
