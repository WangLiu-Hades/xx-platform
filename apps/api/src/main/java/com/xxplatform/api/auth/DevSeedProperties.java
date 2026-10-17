package com.xxplatform.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 开发种子账号开关（{@code xx.auth.dev-seed.*}）。
 *
 * <p><b>为什么要三重闸门</b>：种子账号是一个"知道口令就能登录"的后门，只要它在生产环境
 * 被启用一次，整个鉴权体系就形同虚设。所以：
 * <ol>
 *   <li>Bean 本身只在 {@code db} profile 下创建（没有数据库时无从插入）；</li>
 *   <li>{@link #isEnabled()} 默认 <b>false</b>，必须显式改；</li>
 *   <li>登录名与口令必须**由外部显式提供**，代码里没有任何默认口令——
 *       这样即使误开开关，也不会得到一个"文档里写着 demo/demo"的公开账号。</li>
 * </ol>
 *
 * <p>口令**不进 yml**（yml 是要提交的），走环境变量 {@code XX_AUTH_DEVSEED_PASSWORD}；
 * Spring 的 relaxed binding 会把 {@code dev-seed} 映射成 {@code DEVSEED}。
 */
@ConfigurationProperties(prefix = "xx.auth.dev-seed")
public class DevSeedProperties {

    /** 是否创建种子账号。默认关闭；关闭时 {@link DevUserSeeder} 什么也不做。 */
    private boolean enabled = false;

    /** 种子账号登录名，例如 {@code demo.admin}（与前端 Mock 账号同名，便于打通链路）。 */
    private String loginName;

    /** 种子账号口令原文（仅用于启动时计算哈希，不会存库、不会写日志）。 */
    private String password;

    /** 显示名。留空时用登录名。 */
    private String displayName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** 口令最短长度（种子账号也强制最小复杂度，避免出现 1 位口令）。 */
    public static final int MIN_PASSWORD_LENGTH = 8;
}
