package com.xxplatform.api.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 一次已认证请求要不要续空闲期（A02：常规主动请求续期，探针/轮询不续期）。
 *
 * <p>契约 {@code SessionData} 规定"空闲 15 分钟、绝对 8 小时、单账号单会话"。
 * 空闲期限要随真实使用前移，否则用户连续工作 15 分钟后会被踢；但续期**不能**推过绝对期限，
 * 否则"绝对 8 小时"就变成了空话。这两条规则放在一处纯函数里，好被单测直接钉住，
 * 不必为了验证一条算术规则去起数据库。
 *
 * <p>为什么返回 {@link Optional} 而不是直接算一个值：续期要写库，而"新值不更晚"的情形
 * （同一秒的连点、并发请求、时钟回拨）不应该产生 UPDATE。把判断留在调用方之外，
 * 调用方就只剩"有值就写"这一句。
 */
public final class SessionRenewal {

    private SessionRenewal() {
    }

    /**
     * 计算本次请求应当写入的新空闲期限。
     *
     * @param now               当前时间
     * @param idleTtl           空闲期限长度（配置项 {@code xx.session.idle-ttl}）
     * @param currentIdle       会话当前的空闲期限
     * @param absoluteExpiresAt 会话的绝对期限（续期上限）
     * @return 需要推进时返回新值；不推进时返回 {@link Optional#empty()}
     */
    public static Optional<OffsetDateTime> nextIdle(
            OffsetDateTime now, Duration idleTtl, OffsetDateTime currentIdle, OffsetDateTime absoluteExpiresAt) {
        if (now == null || idleTtl == null || currentIdle == null || absoluteExpiresAt == null) {
            throw new IllegalArgumentException("续期参数不能为空");
        }
        OffsetDateTime candidate = now.plus(idleTtl);
        if (candidate.isAfter(absoluteExpiresAt)) {
            candidate = absoluteExpiresAt;
        }
        if (!candidate.isAfter(currentIdle)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
