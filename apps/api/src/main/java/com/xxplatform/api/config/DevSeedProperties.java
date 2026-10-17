package com.xxplatform.api.config;

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

    /** 管理员种子账号登录名，例如 {@code demo.admin}（与前端 Mock 账号同名，便于打通链路）。 */
    private String loginName;

    /** 操作员种子账号登录名（只读权限、仅 FENCE/PLATE、{@code ownOnly=true}）。 */
    private String operatorLoginName = "demo.operator";

    /** 涉密岗种子账号登录名（操作员权限 + 解密/审计阅读、四种数据类型）。 */
    private String classifiedLoginName = "demo.classified";

    /** 三个种子账号共用的口令原文（仅用于启动时计算哈希，不会存库、不会写日志）。 */
    private String password;

    /**
     * 账号已存在时是否把口令重置为 {@link #getPassword()} 当前值，并撤销该账号的会话。
     *
     * <p><b>为什么默认 false</b>：正常重启不应该把人手工改过的口令冲掉。
     *
     * <p><b>为什么需要这个开关</b>：没有它会出现"口令漂移"——第一次用口令 A 建了账号，
     * 之后把环境变量改成 B 重启，新账号用 B、老账号仍是 A，于是同一次启动里三个演示账号
     * 口令不一致，联调时表现为"有的能登有的不能"，而且没有任何提示。需要换口令时显式打开
     * 本开关启动一次，再关掉。
     *
     * <p>重置口令同时撤销该账号所有未撤销会话：这与契约
     * {@code api-conventions.md} §4"管理员重置口令须撤销该用户会话"是同一口径，
     * 否则改了口令别人手里的旧会话照样能用。
     */
    private boolean resetPassword = false;

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

    public String getOperatorLoginName() {
        return operatorLoginName;
    }

    public void setOperatorLoginName(String operatorLoginName) {
        this.operatorLoginName = operatorLoginName;
    }

    public String getClassifiedLoginName() {
        return classifiedLoginName;
    }

    public void setClassifiedLoginName(String classifiedLoginName) {
        this.classifiedLoginName = classifiedLoginName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isResetPassword() {
        return resetPassword;
    }

    public void setResetPassword(boolean resetPassword) {
        this.resetPassword = resetPassword;
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
