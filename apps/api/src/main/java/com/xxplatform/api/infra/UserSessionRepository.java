package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

    /**
     * 按摘要取会话（任务 6：{@code GET /me} 的身份来源）。
     *
     * <p><b>故意不带 {@code revoked_at IS NULL} 与过期条件</b>：认证层需要区分三种失败
     * （会话不存在 / 已撤销 / 已过期），对外虽然都是 401，但日志与排障完全不同。
     * 如果 SQL 里就过滤掉，接收方只能拿到"查不到"，无法判断是"用户点了退出"还是
     * "空闲超时被清理"。条件判断统一放在 {@code SessionAuthenticator} 一处。
     */
    private static final String SELECT_BY_SID_HASH = """
            SELECT id, user_id, revoked_at, idle_expires_at, absolute_expires_at
              FROM auth.user_session
             WHERE sid_hash = ?
            """;

    /**
     * 按会话行 id 撤销（任务 11：{@code POST /auth/logout}）。
     *
     * <p>为什么按 {@code id} 而不是像登录轮换那样按 {@code sid_hash} 撤销：退出时
     * 认证层已经把 {@code sid_hash} 换成了 {@link SessionRow}，再算一次摘要属于多余动作，
     * 而且"按已认证的会话行 id 撤销"语义更直接——撤销的正是本次请求通过认证的那一行。
     */
    private static final String REVOKE_BY_ID =
            "UPDATE auth.user_session SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL";

    /**
     * 撤销某账号的全部有效会话（A02：单账号单会话）。
     *
     * <p>契约 {@code SessionData} 写明"单账号 1 会话，新登录撤销旧会话"。旧实现只撤销
     * 客户端**提交上来的那个 SID**，所以另一台浏览器（或另一台机器）首次登录时没有 SID 可提交，
     * 旧会话就活着——同一个人可以同时有多条有效会话。
     *
     * <p>调用方必须已持有 {@code auth.app_user} 行锁（{@link UserAccountRepository#lockById}），
     * 否则两个并发登录会互相看不见对方刚插入的行。
     */
    private static final String REVOKE_ALL_FOR_USER =
            "UPDATE auth.user_session SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL";

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

    /**
     * 按会话行 id 撤销会话（{@code POST /auth/logout}）。
     *
     * <p>返回 0 行不是错误：并发下同一个会话被两次退出（双击、重试）时，第二次看到的
     * 已经是 {@code revoked_at IS NOT NULL}。调用方据此只记 debug 日志，不向用户报错。
     */
    public int revokeById(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        return jdbc.update(REVOKE_BY_ID, sessionId);
    }

    /**
     * 按账号撤销全部有效会话（A02：新登录踢掉同一账号的其他会话）。
     *
     * <p>返回行数是**被踢掉的旧会话条数**，正常为 0（首次登录）或 1（换设备登录）；
     * 不做"必须恰好 1 行"的断言——多行说明历史数据里本来就有并发会话，撤销它们正是目的。
     */
    public int revokeAllForUser(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return jdbc.update(REVOKE_ALL_FOR_USER, userId);
    }

    /** 按 SID 摘要取会话（含已撤销/已过期的，好让调用方区分失败原因）。 */
    public Optional<SessionRow> findBySidHash(byte[] sidHash) {
        if (sidHash == null || sidHash.length != SHA256_LEN) {
            throw new IllegalArgumentException("sid_hash 必须是 SHA-256（32 字节）");
        }
        List<SessionRow> rows = jdbc.query(SELECT_BY_SID_HASH, (rs, rowNum) -> new SessionRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("revoked_at", OffsetDateTime.class),
                rs.getObject("idle_expires_at", OffsetDateTime.class),
                rs.getObject("absolute_expires_at", OffsetDateTime.class)), (Object) sidHash);
        return rows.stream().findFirst();
    }

    /** 认证判定所需的会话列集；{@code revokedAt} 为 {@code null} 表示未撤销。 */
    public record SessionRow(
            UUID id,
            UUID userId,
            OffsetDateTime revokedAt,
            OffsetDateTime idleExpiresAt,
            OffsetDateTime absoluteExpiresAt) {
    }
}
