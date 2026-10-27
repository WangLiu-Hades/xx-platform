package com.xxplatform.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionRenewalTests {

    private static final Duration IDLE = Duration.ofMinutes(15);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-27T10:00:00Z");

    @Test
    void renewsWhenTheNewValueIsLater() {
        OffsetDateTime currentIdle = NOW.plusMinutes(5);
        OffsetDateTime absolute = NOW.plusHours(8);

        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(NOW, IDLE, currentIdle, absolute);

        assertThat(next).contains(NOW.plusMinutes(15));
    }

    @Test
    void neverPushesPastTheAbsoluteDeadline() {
        // 绝对期限只剩 10 分钟：续 15 分钟会被截到绝对期限，绝对期限本身不被推后。
        OffsetDateTime absolute = NOW.plusMinutes(10);
        OffsetDateTime currentIdle = NOW.plusMinutes(5);

        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(NOW, IDLE, currentIdle, absolute);

        assertThat(next).contains(absolute);
    }

    @Test
    void doesNotWriteWhenTheValueWouldNotMove() {
        // 刚登录过：空闲期限恰好是 now + 15m，算出来的候选值一样晚，不该产生 UPDATE。
        OffsetDateTime currentIdle = NOW.plusMinutes(15);

        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(NOW, IDLE, currentIdle, NOW.plusHours(8));

        assertThat(next).isEmpty();
    }

    @Test
    void doesNotWriteWhenTheClockWentBackwards() {
        OffsetDateTime currentIdle = NOW.plusMinutes(30);

        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(NOW, IDLE, currentIdle, NOW.plusHours(8));

        assertThat(next).isEmpty();
    }

    @Test
    void staysAtTheAbsoluteDeadlineOnceReached() {
        OffsetDateTime absolute = NOW.plusMinutes(10);
        OffsetDateTime currentIdle = absolute;

        Optional<OffsetDateTime> next = SessionRenewal.nextIdle(NOW, IDLE, currentIdle, absolute);

        assertThat(next).isEmpty();
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> SessionRenewal.nextIdle(null, IDLE, NOW, NOW.plusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionRenewal.nextIdle(NOW, null, NOW, NOW.plusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
