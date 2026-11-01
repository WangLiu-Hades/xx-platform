package com.xxplatform.api.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话 TTL、Cookie 属性与登录来源网段（{@code xx.session.*}）。
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

    /**
     * 允许登录的来源网段（CIDR）。登录时按 {@code request.getRemoteAddr()} 判定，
     * 不在列表内一律 403 {@code PERMISSION_DENIED}（{@code "登录来源不在允许的内网范围"}）。
     *
     * <p><b>为什么默认只含回环 + 私网</b>：本平台部署在内网，默认值必须"安全开箱"——
     * 若默认放行任意来源，一次忘记配置就等于把管理后台暴露到公网。列出私网段是为了让
     * 容器/反向代理（同网段）也能登录。
     *
     * <p><b>默认只信 {@code getRemoteAddr()}，不解析 {@code X-Forwarded-For}</b>：那个头由
     * 客户端随意伪造，默认信任它等于让攻击者用一个头就绕过来源限制（和登录频控的分桶）。
     * 只有在部署方显式声明了 {@link #trustedProxies}（代理自己的地址）之后，
     * {@code ClientNetworks.resolveClientIp} 才从右往左跳过可信代理、取第一个不可信地址；
     * 也就是说"采信转发头"的前提是"这一跳确实来自我们配置的代理"。
     *
     * <p>因此反向代理部署有两种等价做法：在容器层把可信头解析进 remoteAddr
     * （Tomcat {@code RemoteIpValve} / {@code server.forward-headers-strategy=native}），
     * 或者配置 {@link #trustedProxies} 让业务层按同一规则解析——两者都不要"无条件信任"。
     */
    private List<String> allowedNetworks = List.of(
            "127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

    /**
     * 可信反向代理的地址/网段（CIDR 或单个 IP）。
     *
     * <p><b>默认空 = 永不采信 {@code X-Forwarded-For}</b>。只有 {@code remoteAddr} 命中本列表时，
     * 才把转发头里的地址当作客户端真实地址；解析规则见
     * {@code ClientNetworks.resolveClientIp}（从右往左跳过可信代理，取第一个不可信地址）。
     * 这是防"伪造 XFF 绕过内网来源限制"的关键：直连客户端即使自带
     * {@code X-Forwarded-For: 10.0.0.1}，由于它的 remoteAddr 不在本列表里，这个头会被整个忽略。
     */
    private List<String> trustedProxies = List.of();

    /**
     * 服务端可判定的"不续命"路径（健康探针、轮询读接口）。
     *
     * <p>契约要求"探针/轮询/资源预加载不续期"。只靠客户端自报的
     * {@code X-Background-Request} 头不够（攻击者或写错的轮询器可以不带），所以这里再加一份
     * **服务端白名单**：命中即不滑动空闲期。支持单个 {@code *} 匹配一段路径，
     * 例如 {@code /tasks/*} 命中 {@code GET /tasks/{id}} 这类轮询读。
     *
     * <p>刻意不含 {@code /me}：契约把 {@code GET /me} 当作"主动确认身份"的前端调用，
     * 且它是登录态探活的主入口；续期与否由客户端头/显式 renew 决定更可控。
     */
    private List<String> nonRenewingPaths = List.of(
            "/health", "/ready", "/client-config", "/tasks/*", "/tasks/*/attempts");

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

    public List<String> getAllowedNetworks() {
        return allowedNetworks;
    }

    public void setAllowedNetworks(List<String> allowedNetworks) {
        this.allowedNetworks = allowedNetworks == null ? List.of() : List.copyOf(allowedNetworks);
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }

    public List<String> getNonRenewingPaths() {
        return nonRenewingPaths;
    }

    public void setNonRenewingPaths(List<String> nonRenewingPaths) {
        this.nonRenewingPaths = nonRenewingPaths == null ? List.of() : List.copyOf(nonRenewingPaths);
    }
}
