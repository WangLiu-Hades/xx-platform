package com.xxplatform.api.auth;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code auth.user_session} 的最小读写面（任务 5：签发与轮换 SID）。
 *
 * <p>库里存的是 {@code sid_hash}（SHA-256 摘要），不存 SID 原文：SID 是 256 位随机值、
 * 本身高熵，无盐 SHA-256 已足以让"拖库后直接拿值当 Cookie 用"失效。
 * 代价是**无法反查某个值对应哪条会话**，所以所有查询都从"客户端提交的值"出发算摘要。
 */
@Repository
public class UserSessionRepository {

    /** sid_hash 固定 32 字节（SHA-256）。 */
    private static final int SHA256_LEN = 32;

    private static final String INSERT = """
            INSERT INTO auth.user_session
                (id, user_id, sid_hash, last_seen_at, idle_expires_at, absolute_expires_at, client_label, trace_id)
            VALUES (?, ?, ?, now(), ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public UserSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条会话。
     *
     * <p>{@code created_at} 走表默认值 {@code now()}；{@code revoked_at} 留空。
     * 表上有 {@code CHECK (idle_expires_at <= absolute_expires_at)} 与
     * {@code CHECK (created_at < absolute_expires_at)}，所以 TTL 配反了会在**插入时**直接
     * 报错（23514），而不是等到会话该过期的那一刻才暴露——这是好事，别在应用层再做一遍校验。
     */
    public void insert(UUID id, UUID userId, byte[] sidHash, OffsetDateTime idleExpiresAt,
                       OffsetDateTime absoluteExpiresAt, String clientLabel, String traceId) {
        if (sidHash == null || sidHash.length != SHA256_LEN) {
            throw new IllegalArgumentException("sid_hash 必须是 SHA-256（32 字节）");
        }
        jdbc.update(INSERT, id, userId, sidHash, idleExpiresAt, absoluteExpiresAt, clientLabel, traceId);
    }

    /**
     * 按 SID 摘要撤销会话（登录轮换用）。
     *
     * <p>条件是 {@code revoked_at IS NULL}：已经是撤销状态的会话不需要重复写时间戳，
     * 否则审计里会出现"同一条会话被撤销多次"的假象。返回 0 行是**正常情况**
     * （新登录时客户端没有旧 SID），不能当错误。
     */
    public int revokeBySidHash(byte[] sidHash) {
        if (sidHash == null || sidHash.length != SHA256_LEN) {
            throw new IllegalArgumentException("sid_hash 必须是 SHA-256（32 字节）");
        }
        return jdbc.update(
                "UPDATE auth.user_session SET revoked_at = now() WHERE sid_hash = ? AND revoked_at IS NULL",
                (Object) sidHash);
    }
}
