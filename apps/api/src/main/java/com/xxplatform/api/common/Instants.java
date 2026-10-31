package com.xxplatform.api.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 时间格式与"现在"的统一出口。
 *
 * <p>契约（{@code api-conventions.md} §3）要求 {@code date-time} 是带偏移的 ISO8601/RFC3339，
 * 服务端按 UTC 存储与返回，即形如 {@code 2026-10-17T05:57:04Z}。
 *
 * <p><b>为什么不让 Jackson 自动序列化 {@code OffsetDateTime}</b>：默认行为取决于
 * {@code ObjectMapper} 的日期配置（是否写时间戳、是否带纳秒、偏移是 {@code Z} 还是
 * {@code +00:00}），任何一次全局配置调整都会静默改变对外契约，而前端可能按字符串前缀
 * 解析。统一走 {@link #iso(OffsetDateTime)} 后，对外格式只有一处定义。
 *
 * <p><b>为什么统一 {@code truncatedTo(SECONDS)}</b>：契约示例与前端断言都不含小数秒；
 * PostgreSQL {@code timestamptz} 有微秒精度，写入后读回会带小数部分（{@code ...:04.123456Z}），
 * 与契约示例不一致。写库前截到秒，读回后再格式化就不会出现小数。
 */
public final class Instants {

    private Instants() {
    }

    /** 当前 UTC 时间，截到秒（写库与比较都用它，避免"同一逻辑内出现两个不同的 now"）。 */
    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    /** 契约格式：带偏移的 ISO8601，UTC 以 {@code Z} 结尾。 */
    public static String iso(OffsetDateTime value) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
    }
}
