package com.xxplatform.api.infra;

import com.xxplatform.api.config.LoginThrottleProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginThrottle} 的滑动窗口语义。
 *
 * <p>纯 JUnit（无 Spring、无 Mockito）：把时钟换成可控实现，就不需要真的等 15 分钟。
 * 频控的正确性几乎全在"时间边界"上，所以每个用例都要显式推进时钟并断言边界，
 * 而不是"调 10 次然后看看有没有 429"。
 */
class LoginThrottleTests {

    private MutableClock clock;
    private LoginThrottleProperties properties;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-17T08:00:00Z"));
        properties = new LoginThrottleProperties();
        properties.setMaxFailures(3);
        properties.setWindow(Duration.ofMinutes(15));
        properties.setLockout(Duration.ofMinutes(15));
        throttle = new LoginThrottle(properties, clock);
    }

    @Test
    @DisplayName("未达阈值：允许尝试")
    void allowsBelowThreshold() {
        String key = LoginThrottle.key("demo.admin", "127.0.0.1");

        throttle.recordFailure(key);
        throttle.recordFailure(key);

        assertThat(throttle.retryAfter(key)).isEmpty();
    }

    @Test
    @DisplayName("达到阈值：返回剩余冷却时长，且随时间递减")
    void blocksAtThreshold() {
        String key = LoginThrottle.key("demo.admin", "127.0.0.1");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(key);
        }

        assertThat(throttle.retryAfter(key)).contains(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(5));
        assertThat(throttle.retryAfter(key)).contains(Duration.ofMinutes(10));

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertThat(throttle.retryAfter(key)).contains(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("冷却结束后自动恢复，且计数清零（不把用户永久锁死）")
    void recoversAfterLockout() {
        String key = LoginThrottle.key("demo.admin", "127.0.0.1");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(key);
        }

        clock.advance(Duration.ofMinutes(15));

        assertThat(throttle.retryAfter(key)).isEmpty();
        assertThat(throttle.trackedKeys()).isZero();

        // 恢复后再失败两次仍在阈值内
        throttle.recordFailure(key);
        throttle.recordFailure(key);
        assertThat(throttle.retryAfter(key)).isEmpty();
    }

    @Test
    @DisplayName("成功登录会清空失败历史")
    void clearResetsHistory() {
        String key = LoginThrottle.key("demo.admin", "127.0.0.1");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(key);
        }
        assertThat(throttle.retryAfter(key)).isPresent();

        throttle.clear(key);

        assertThat(throttle.retryAfter(key)).isEmpty();
        assertThat(throttle.trackedKeys()).isZero();
    }

    @Test
    @DisplayName("窗口外的旧失败被丢弃：分散在窗口外的失败不累计成限流")
    void failuresOutsideWindowArePruned() {
        String key = LoginThrottle.key("demo.admin", "127.0.0.1");

        // 每 10 分钟失败一次（窗口 15 分钟），永远不会累积到 3 次
        throttle.recordFailure(key);
        clock.advance(Duration.ofMinutes(10));
        throttle.recordFailure(key);
        clock.advance(Duration.ofMinutes(10));
        throttle.recordFailure(key);

        // 第一次失败已超出窗口，当前窗口内只有 2 次
        assertThat(throttle.retryAfter(key)).isEmpty();
    }

    @Test
    @DisplayName("不同来源 IP 各自计数：NAT 后的其他用户不受影响")
    void differentIpHasSeparateBucket() {
        String victim = LoginThrottle.key("demo.admin", "10.0.0.1");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(victim);
        }

        assertThat(throttle.retryAfter(victim)).isPresent();
        assertThat(throttle.retryAfter(LoginThrottle.key("demo.admin", "10.0.0.2"))).isEmpty();
    }

    @Test
    @DisplayName("大小写不同的登录名各自计数（与数据库唯一索引的大小写敏感一致）")
    void loginNameIsCaseSensitive() {
        String lower = LoginThrottle.key("demo.admin", "127.0.0.1");
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure(lower);
        }

        assertThat(throttle.retryAfter(LoginThrottle.key("Demo.Admin", "127.0.0.1"))).isEmpty();
    }

    @Test
    @DisplayName("键的组合形态：登录名 + | + IP")
    void keyShape() {
        assertThat(LoginThrottle.key("demo.admin", "127.0.0.1")).isEqualTo("demo.admin|127.0.0.1");
        // null 不应抛出 NPE（HttpServletRequest 的 remoteAddr 理论上有 null 的可能）
        assertThat(LoginThrottle.key(null, null)).isEqualTo("|");
    }

    /** 可推进的时钟。 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
