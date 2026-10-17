package com.xxplatform.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录频控参数（{@code xx.auth.throttle.*}）。
 *
 * <p><b>为什么要频控而不是直接上锁</b>：库里没有 {@code auth.login_attempt} 之类的
 * 失败计数/锁定表（auth schema 14 张表里没有任何登录尝试表），而本轮不改 DDL
 * （DDL 由契约与迁移统一管理）。所以频控做成**进程内**的内存计数：
 * <ul>
 *   <li>它防的是<b>在线爆破与撞库</b>，不是"账号永久锁死"。超过阈值只冷却一段时间，
 *       不会把真实用户永久挡在门外（永久锁定本身就是一种拒绝服务）。</li>
 *   <li>代价是<b>多实例部署时计数不共享</b>：N 个实例意味着实际阈值是 N 倍。这个限制
 *       必须写在这里而不是留在脑子里 —— 上多实例前需要换成共享存储（Redis）或落库，
 *       那是 P-021/运维阶段的事。</li>
 * </ul>
 *
 * <p>三个参数的关系：{@code window} 内失败达到 {@code maxFailures} 次即判定为限流；
 * 从**最后一次失败**起再等 {@code lockout} 才解除。冷却结束后计数清零，重新给机会。
 */
@ConfigurationProperties(prefix = "xx.auth.throttle")
public class LoginThrottleProperties {

    /** 窗口内允许的连续失败次数上限。取 10：正常用户手误远达不到，脚本爆破又太慢。 */
    private int maxFailures = 10;

    /** 计数窗口。超过窗口的失败记录会被丢弃（旧失败不该永久计数）。 */
    private Duration window = Duration.ofMinutes(15);

    /** 冷却时长：从最后一次失败起算，期间一律 429。 */
    private Duration lockout = Duration.ofMinutes(15);

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public Duration getLockout() {
        return lockout;
    }

    public void setLockout(Duration lockout) {
        this.lockout = lockout;
    }
}
