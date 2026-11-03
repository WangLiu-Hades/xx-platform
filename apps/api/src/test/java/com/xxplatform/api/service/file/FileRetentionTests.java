package com.xxplatform.api.service.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 保留期必须是"Asia/Shanghai 下的 12 个自然月"，不是 365 天，也不是按 UTC 日期算。
 */
class FileRetentionTests {

    private static OffsetDateTime utc(String iso) {
        return OffsetDateTime.parse(iso).withOffsetSameInstant(ZoneOffset.UTC);
    }

    @Test
    void addsTwelveCalendarMonths() {
        assertEquals(utc("2027-03-15T02:00:00Z"),
                FileRetention.expiresAt(utc("2026-03-15T02:00:00Z")));
    }

    @Test
    void clampsToLastDayOfMonthWhenDayDoesNotExist() {
        // 上海 2026-01-31 18:00 → 2027-01-31；若跨闰年 2 月则收敛到月末
        assertEquals(utc("2027-02-28T02:00:00Z"),
                FileRetention.expiresAt(utc("2026-02-28T02:00:00Z")));
        assertEquals(utc("2025-02-28T04:00:00Z"),
                FileRetention.expiresAt(utc("2024-02-29T04:00:00Z")));
    }

    @Test
    void usesShanghaiCalendarEvenWhenUtcIsPreviousDay() {
        // UTC 2026-01-31T16:30Z 在上海是 2026-02-01 00:30，保留期应到 2027-02-01 00:30（UTC 2027-01-31T16:30Z）
        assertEquals(utc("2027-01-31T16:30:00Z"),
                FileRetention.expiresAt(utc("2026-01-31T16:30:00Z")));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> FileRetention.expiresAt(null));
    }
}
