package com.xxplatform.api.service.file;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 受控文件的保留期计算（P-003-03 / RETENTION_POLICY）。
 *
 * <p>规则原文："同类数据保存一年（12 个自然月）……按 Asia/Shanghai 从起算时刻增加 12 个
 * 自然月，同日不存在则取该月最后一日（如 2024-02-29 至 2025-02-28），不固定换算 365 天。"
 *
 * <p><b>为什么不能用 365 天</b>：365 天让闰年的 2 月 29 日漂成 2 月 28 日或 3 月 1 日，
 * 而同一天在不同年份的"12 个月后"日期并不相同；用 {@link java.time.OffsetDateTime#plusMonths}
 * 才能得到"同月同日、不存在则月末"的日历语义。
 *
 * <p><b>为什么必须在 Asia/Shanghai 下计算再转回 UTC</b>：起算时刻存的是 UTC，
 * 但"月末"是按甲方时区的日历判断的。例如 UTC 2026-01-31T16:00Z 实际是上海 2026-02-01 00:00，
 * 保留期应到 2027-02-01，而不是按 UTC 的 1 月 31 日算成 2027-01-31。
 */
public final class FileRetention {

    /** 保留期规则使用的日历时区（甲方口径）。 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 文件（及其他同类数据）的保留月数。 */
    public static final int MONTHS = 12;

    private FileRetention() {
    }

    /**
     * 从 ACTIVE 时刻推算到期时刻。
     *
     * @param activeAt 文件进入 ACTIVE 的时刻（UTC）
     * @return 同一时刻加 12 个自然月后的 UTC 时刻
     */
    public static OffsetDateTime expiresAt(OffsetDateTime activeAt) {
        if (activeAt == null) {
            throw new IllegalArgumentException("activeAt 不能为空");
        }
        return activeAt.atZoneSameInstant(ZONE)
                .plusMonths(MONTHS)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
    }
}
