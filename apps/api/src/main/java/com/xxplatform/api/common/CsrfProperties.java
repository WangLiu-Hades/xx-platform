package com.xxplatform.api.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSRF 与 Cookie 的策略参数（`xx.csrf.*` / `xx.cookie.*`）。
 *
 * <p>为什么做成配置而不是常量：契约与设计对这几个值有**未签认**的部分——
 * {@code OPENAPI_PENDING.md} P-001 明确"空闲/绝对超时、跨域部署边界待签认"，
 * P-009 提到 Context TTL 类似问题。把这些值写死在代码里，等于用实现替代了
 * 尚未做出的业务决定；做成配置后，签认结果只需改配置。
 *
 * <p><b>当前默认值不代表已签认</b>，只是让链路可运行的保守初始值，已在
 * {@code application.yml} 与 README 注明。
 */
@ConfigurationProperties(prefix = "xx.csrf")
public class CsrfProperties {

    /**
     * CSRF 会话（{@code auth.csrf_session.expires_at}）存活时长。
     *
     * <p>默认 2 小时：与"登录页停留一段时间后仍能提交"的常见体验一致；
     * 真正的空闲/绝对超时要等 P-001 签认，届时改这里即可。
     */
    private Duration ttl = Duration.ofHours(2);

    /**
     * Cookie 是否带 {@code Secure} 属性。
     *
     * <p>契约写的是 {@code HttpOnly; Secure; SameSite=Lax; Path=/api/v1}，生产必须为
     * {@code true}。但本地用 {@code http://127.0.0.1} 调试时浏览器**不会**回传
     * Secure Cookie，会让整条链路无法联调。
     *
     * <p>因此默认跟随请求协议（HTTPS 才加 Secure），并在非 HTTPS 时打 WARN；
     * 生产部署应显式设为 {@code true} 并强制 TLS（见 SD06）。
     */
    private boolean secureCookie = false;

    /** Cookie 名，契约固定 {@code CSRFID}。 */
    private String cookieName = "CSRFID";

    /**
     * Cookie 的 {@code Path}，契约固定 {@code /api/v1}。
     *
     * <p>写成 {@code /api/v1} 而不是 {@code /}，是为了不让 CSRF Cookie 泄露给
     * 同域的其他应用。
     */
    private String cookiePath = "/api/v1";

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public boolean isSecureCookie() {
        return secureCookie;
    }

    public void setSecureCookie(boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }
}
