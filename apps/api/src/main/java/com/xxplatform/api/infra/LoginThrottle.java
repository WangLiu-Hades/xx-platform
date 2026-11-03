package com.xxplatform.api.infra;

import com.xxplatform.api.config.LoginThrottleProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Deque;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 登录失败频控（进程内滑动窗口）。
 *
 * <p>键是 {@code loginName + "|" + clientIp} 的组合，不是其中任何一个单独：
 * <ul>
 *   <li>只按登录名 → 别人可以故意刷爆某个账号，把真实用户锁在门外（拒绝服务）。</li>
 *   <li>只按 IP → 同一出口 NAT 后的所有人共享配额，一人手误全公司被限流。</li>
 * </ul>
 * 组合键把影响面限制在"同一账号 + 同一来源"。
 *
 * <p><b>内存上限</b>：键数超过 {@value #MAX_KEYS} 时清扫一遍过期项，避免被随机登录名
 * 打爆内存（每次尝试都会创建一个新键）。每个键内部只保留最近
 * {@code maxFailures} 条记录，所以单个键的体积也是有界的。
 *
 * <p><b>已知限制</b>：多实例部署时各算各的，实际阈值 = 实例数 × maxFailures。
 * 详见 {@link LoginThrottleProperties} 的类注释。
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    /** 键数上限，超过就清扫（只在新增失败时判断，读路径无锁）。 */
    private static final int MAX_KEYS = 10_000;

    private final LoginThrottleProperties properties;
    private final Clock clock;

    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /**
     * 生产用构造器。
     *
     * <p>必须标 {@code @Autowired}：本类有两个构造器，Spring 无法自行判断用哪个，
     * 会报 {@code No default constructor found}（实测启动即失败），而不是"选参数最多的那个"。
     */
    @Autowired
    public LoginThrottle(LoginThrottleProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** 供测试注入固定时钟（包私有，不对外暴露）。 */
    LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** 组合键。登录名原样使用（大小写敏感，与数据库唯一索引一致）。 */
    public static String key(String loginName, String clientIp) {
        return (loginName == null ? "" : loginName) + "|" + (clientIp == null ? "" : clientIp);
    }

    /**
     * 判断当前键是否处于限流中。
     *
     * @return 需要等待的时长；{@code Optional.empty()} 表示允许尝试
     */
    public Optional<Duration> retryAfter(String key) {
        Deque<Instant> bucket = failures.get(key);
        if (bucket == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        synchronized (bucket) {
            pruneExpired(bucket, now);
            if (bucket.isEmpty()) {
                // 窗口内的失败已全部过期：桶里没有信息了，顺手删掉，
                // 否则"每个失败过的键"会永久占一个 map 条目（长跑进程里就是内存泄漏）
                failures.remove(key, bucket);
                return Optional.empty();
            }
            if (bucket.size() < properties.getMaxFailures()) {
                return Optional.empty();
            }
            Instant releaseAt = bucket.peekLast().plus(properties.getLockout());
            if (!now.isBefore(releaseAt)) {
                // 冷却结束：清掉整个桶，让用户重新获得完整配额
                failures.remove(key, bucket);
                log.debug("登录限流冷却结束，计数清零 key={}", mask(key));
                return Optional.empty();
            }
            return Optional.of(Duration.between(now, releaseAt));
        }
    }

    /** 记录一次失败（口令错误、账号不存在、账号停用都算）。 */
    public void recordFailure(String key) {
        Instant now = clock.instant();
        Deque<Instant> bucket = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            pruneExpired(bucket, now);
            bucket.addLast(now);
            // 只留最近 maxFailures 条：更早的失败对判断"是否限流"没有影响，留着只是占内存
            int cap = Math.max(1, properties.getMaxFailures());
            while (bucket.size() > cap) {
                bucket.removeFirst();
            }
        }
        if (failures.size() > MAX_KEYS) {
            sweep(now);
        }
    }

    /** 登录成功后清空该键的失败历史。 */
    public void clear(String key) {
        failures.remove(key);
    }

    /** 当前被记录的键数量（仅用于测试与诊断）。 */
    int trackedKeys() {
        return failures.size();
    }

    private void pruneExpired(Deque<Instant> bucket, Instant now) {
        Instant windowStart = now.minus(properties.getWindow());
        while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(windowStart)) {
            bucket.removeFirst();
        }
    }

    private void sweep(Instant now) {
        int before = failures.size();
        failures.entrySet().removeIf(entry -> {
            Deque<Instant> bucket = entry.getValue();
            synchronized (bucket) {
                pruneExpired(bucket, now);
                return bucket.isEmpty();
            }
        });
        // 只清扫"已经完全过期"的键；仍在限流中的键必须保留，否则等于绕过限流
        log.debug("登录频控键数 {} -> {}（上限 {}）", before, failures.size(), MAX_KEYS);
    }

    /** 日志里不直接打登录名全文：它是账号标识的一半，落盘后等于收集用户名清单。 */
    private static String mask(String key) {
        int idx = key.indexOf('|');
        if (idx <= 0) {
            return "***";
        }
        String loginName = key.substring(0, idx);
        String masked = loginName.length() <= 2
                ? "*".repeat(loginName.length())
                : loginName.charAt(0) + "*".repeat(loginName.length() - 2) + loginName.charAt(loginName.length() - 1);
        return masked + key.substring(idx);
    }
}
