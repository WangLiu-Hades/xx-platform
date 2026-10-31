package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code auth.csrf_session} 的读写（M01 登录/CSRF 表）。
 *
 * <p>表定义见 {@code contracts/database/schema.sql}：
 * {@code id uuid PK}、{@code session_id uuid NULL}、{@code cookie_hash bytea NOT NULL UNIQUE}、
 * {@code token_hash bytea NOT NULL}、{@code expires_at timestamptz NOT NULL}、
 * {@code created_at DEFAULT now()}、{@code CHECK (created_at < expires_at)}、
 * 索引 {@code ix_csrf_expiry (expires_at)}。
 *
 * <p><b>只存哈希，不存原文</b>：Cookie 里的 opaque 值与会话内的 token 都在服务端做
 * SHA-256 后入库。这样即使库被读，也无法反推出可用的 Cookie/Token（与
 * {@code auth.user_session.sid_hash} 的处置一致）。
 *
 * <p><b>异步绑定会话</b>：{@code session_id} 允许为 null，因为 CSRF 必须在**登录前**
 * 就能拿到（登录请求本身要带 CSRF Token）。登录成功后由会话服务把 {@code session_id}
 * 回填到该行；登出时随会话一起删除。当前阶段还没有会话，故写入 null。
 *
 * <p>为什么没有用 {@code org.springframework.security}：契约要求的是
 * "同步器 token + 可信 Origin"两件事，而引入 Spring Security 会带来默认登录页、
 * 过滤器链顺序、CSRF 与 SID Cookie 的耦合等一整套需要反配置的默认行为；
 * 在本项目的"服务端 SID Cookie + 每写请求校验"模型下，这点逻辑直接写更可控，
 * 也更容易与契约的 {@code AUTH_CSRF_INVALID} 错误码对齐。
 */
@Repository
public class CsrfSessionRepository {

    /** 表里只存 32 字节 SHA-256。 */
    private static final int SHA256_LEN = 32;

    private final JdbcTemplate jdbc;

    public CsrfSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条 CSRF 会话。
     *
     * @param id         行主键（应用生成 UUID）
     * @param sessionId  关联的登录会话；登录前为 null
     * @param cookieHash Cookie 原文的 SHA-256
     * @param tokenHash  Token 原文的 SHA-256
     * @param expiresAt  过期时间，必须晚于 now()
     */
    public void insert(UUID id, UUID sessionId, byte[] cookieHash, byte[] tokenHash, OffsetDateTime expiresAt) {
        if (cookieHash.length != SHA256_LEN || tokenHash.length != SHA256_LEN) {
            // 防御性检查：写错长度会让 bytea 比较永远不相等，表现为"CSRF 永远校验失败"，
            // 那种 bug 很难查，所以在入口直接拒绝。
            throw new IllegalArgumentException("cookieHash/tokenHash 必须是 32 字节 SHA-256");
        }
        jdbc.update(
                """
                INSERT INTO auth.csrf_session (id, session_id, cookie_hash, token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                sessionId,
                cookieHash,
                tokenHash,
                expiresAt);
    }

    /**
     * 按 Cookie 哈希查一条**未过期**记录。
     *
     * <p>用 {@code expires_at > now()} 而不是在应用侧比较时间：一律以数据库时钟为准，
     * 避免多实例时钟漂移造成"某台机器认为还有效"。
     */
    public Optional<CsrfSessionRow> findActiveByCookieHash(byte[] cookieHash) {
        return jdbc.query(
                        """
                        SELECT id, session_id, token_hash, expires_at
                        FROM auth.csrf_session
                        WHERE cookie_hash = ? AND expires_at > now()
                        """,
                        (rs, rowNum) -> new CsrfSessionRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("session_id", UUID.class),
                                rs.getBytes("token_hash"),
                                rs.getObject("expires_at", OffsetDateTime.class)),
                        cookieHash)
                .stream()
                .findFirst();
    }

    /** 登录成功后把会话绑定到该 CSRF 行（P-001 签认前保持可选）。 */
    public int bindSession(UUID csrfId, UUID sessionId) {
        return jdbc.update("UPDATE auth.csrf_session SET session_id = ? WHERE id = ?", sessionId, csrfId);
    }

    /** 按 Cookie 哈希删除（登出或校验失败后轮换时用）。 */
    public int deleteByCookieHash(byte[] cookieHash) {
        return jdbc.update("DELETE FROM auth.csrf_session WHERE cookie_hash = ?", cookieHash);
    }

    /**
     * 清理已过期记录。
     *
     * <p>没有定时任务（首版不引入调度框架），由 {@code GET /auth/csrf} 顺带清理，
     * 量级很小（每次只删过期行，走 {@code ix_csrf_expiry}）。真正的定期清理应随
     * 任务底座一起做。
     */
    public int deleteExpired() {
        return jdbc.update("DELETE FROM auth.csrf_session WHERE expires_at <= now()");
    }

    /**
     * 查询结果行。
     *
     * @param id        行主键
     * @param sessionId 关联会话，登录前为 null
     * @param tokenHash token 的 SHA-256
     * @param expiresAt 过期时间
     */
    public record CsrfSessionRow(UUID id, UUID sessionId, byte[] tokenHash, OffsetDateTime expiresAt) {
    }
}
