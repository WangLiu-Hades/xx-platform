package com.xxplatform.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话 TTL 与 Cookie 属性（{@code xx.session.*}）。
 *
 * <p><b>为什么 TTL 是配置项而不是常量</b>：契约里 {@code SessionData} 带
 * {@code x-design-status: "provisional"}，取值仍待甲方确认 —— 把 15 分钟/8 小时写进代码，
 * 等于替产品做了一个未授权的决定。做成配置后，签认结果只需改 yml，不用改代码、不用重新走一遍评审。
 *
 * <p><b>默认值取自 PD-2026-10-19.v1 暂定政策</b>：{@code docs/project-governance/provisional-policy.json}
 * 的 {@code settings.auth.idleSeconds=900}、{@code absoluteSeconds=28800}。空闲 15 分钟对
 * "后台运维系统"够处理一次故障排查，又不会让离开工位的终端一直可写；绝对 8 小时覆盖一个工作日，
 * 跨天必须重新登录。两者都不是安全边界，真正的边界是 SID 轮换 + 改密/停用即撤销会话。
 */
@ConfigurationProperties(prefix = "xx.session")
public class SessionProperties {

    /** 空闲超时：每次请求刷新，超过即失效（{@code renew} 只延长它）。 */
    private Duration idleTtl = Duration.ofMinutes(15);

    /** 绝对超时：从登录时刻起算，任何活动都不延长它。 */
    private Duration absoluteTtl = Duration.ofHours(8);

    /** SID Cookie 的 Path；契约 {@code api-conventions.md} 硬约定 {@code /api/v1}。 */
    private String cookiePath = "/api/v1";

    /**
     * 是否给 SID 加 {@code Secure}。
     *
     * <p>默认 false 只为让本地 {@code http://127.0.0.1:8080} 能联调（浏览器不会回传
     * Secure Cookie）。**生产必须为 true 并强制 TLS**，该默认值挂在 P-001 名下追踪。
     */
    private boolean secureCookie = false;

    public Duration getIdleTtl() {
        return idleTtl;
    }

    public void setIdleTtl(Duration idleTtl) {
        this.idleTtl = idleTtl;
    }

    public Duration getAbsoluteTtl() {
        return absoluteTtl;
    }

    public void setAbsoluteTtl(Duration absoluteTtl) {
        this.absoluteTtl = absoluteTtl;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public boolean isSecureCookie() {
        return secureCookie;
    }

    public void setSecureCookie(boolean secureCookie) {
        this.secureCookie = secureCookie;
    }
}
