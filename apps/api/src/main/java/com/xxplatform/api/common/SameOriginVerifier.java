package com.xxplatform.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 同源校验规则（Origin → Referer → 非浏览器放行）。
 *
 * <p>这段规则原先写在 {@code AuthController#isSameOrigin}。W2 任务 3 把它做成写请求的统一
 * 闸门（{@link CsrfFilter}）后，控制器与过滤器必须用<b>同一份</b>判断：两处各写一份，
 * 迟早会演化成"过滤器放行、控制器拒绝"或者反过来，而这类差异只在特定 Origin 组合下
 * 才复现，排查成本极高。
 *
 * <p>规则：
 * <ul>
 *   <li>有 {@code Origin} → 其 {@code scheme://host[:port]} 必须等于本次请求的同一三元组；</li>
 *   <li>无 {@code Origin} 但有 {@code Referer} → 取 Referer 的 origin 部分按同一规则比较；</li>
 *   <li>两者都没有 → 视为非浏览器客户端，放行（curl/服务端调用不受 CSRF 攻击模型影响，
 *       真正的防线仍是 Cookie + token 双要素）。</li>
 * </ul>
 *
 * <p>比较 {@code scheme} 而不只比 host：只比 host 会放过 {@code http} → {@code https}
 * 的降级攻击。期望值用"请求自身的 scheme"而不是硬编码 {@code https}，否则本地 http
 * 联调会全被拒；生产侧的 TLS 强制由部署层保证（SD06）。
 */
public final class SameOriginVerifier {

    private static final Logger log = LoggerFactory.getLogger(SameOriginVerifier.class);

    private SameOriginVerifier() {
    }

    /**
     * 判断请求是否来自本站（或非浏览器客户端）。
     *
     * @param request 当前请求，不能为 null
     * @return {@code true} 表示允许继续；{@code false} 表示必须按
     *         {@code 403 AUTH_CSRF_INVALID} 拒绝
     */
    public static boolean isSameOrigin(HttpServletRequest request) {
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
            log.debug("同源校验拒绝：Origin={} 期望={}", origin, expected);
        }
        return ok;
    }

    /** 本次请求对外可见的 {@code scheme://host[:port]}。 */
    static String requestOrigin(HttpServletRequest request) {
        // 反向代理/网关后需要 ForwardedHeaderFilter 或已配置 server.forward-headers-strategy；
        // 这里直接信任容器解析后的值，不在代码里自行拼 X-Forwarded-*（可被客户端伪造）。
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    /** 从绝对 URL 中取出 {@code scheme://host[:port]}；失败返回 null。 */
    static String originOf(String url) {
        try {
            URI uri = URI.create(url);
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
