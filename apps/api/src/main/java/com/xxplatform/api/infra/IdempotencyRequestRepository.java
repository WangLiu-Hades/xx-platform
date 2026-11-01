package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 幂等账本（{@code core.idempotency_request}）。
 *
 * <p>契约 api-conventions.md 要求创建/导入类的写请求按「账号 + operationId + 幂等键」去重：相同输入重放
 * 原响应，不同输入 409 {@code IDEMPOTENCY_CONFLICT}。表的唯一键 {@code (user_id, operation_id,
 * idempotency_key)} 是并发下的唯一裁决点，这里不做「先查后写」的乐观假设。
 *
 * <p>上传端用 {@link #insertProcessing} 抢坑：只有抢到的一方继续落盘，另一方拿不到坑位就按
 * 已存在记录处理，因此同一个键永远不会产生第二行 {@code core.file_asset}。
 */
@Repository
public class IdempotencyRequestRepository {

    private static final String SELECT_LEDGER =
            "SELECT id, request_digest, status, response_status, response_snapshot::text AS snapshot "
                    + "FROM core.idempotency_request "
                    + "WHERE user_id = ? AND operation_id = ? AND idempotency_key = ?";

    private final JdbcTemplate jdbc;

    public IdempotencyRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 账本行；{@code snapshot} 是首次响应的 JSON 文本（可为 null），重放直接回放它。 */
    public record LedgerRow(UUID id, String requestDigest, String status, Integer responseStatus,
                            String snapshot) {}

    public Optional<LedgerRow> find(UUID userId, String operationId, String idempotencyKey) {
        return jdbc.query(SELECT_LEDGER,
                        (rs, rowNum) -> new LedgerRow(
                                rs.getObject("id", UUID.class),
                                rs.getString("request_digest") == null
                                        ? null : rs.getString("request_digest").trim(),
                                rs.getString("status"),
                                (Integer) rs.getObject("response_status"),
                                rs.getString("snapshot")),
                        userId, operationId, idempotencyKey)
                .stream()
                .findFirst();
    }

    /** 读重放所需的响应快照（jsonb 原样文本）。 */
    public Optional<String> findSnapshot(UUID id) {
        return jdbc.query(
                        "SELECT response_snapshot::text FROM core.idempotency_request WHERE id = ?",
                        (rs, rowNum) -> rs.getString(1),
                        id)
                .stream()
                .findFirst();
    }

    /**
     * 抢占幂等键。返回 true 表示本次请求拿到处理权；false 表示同键记录已存在（并发请求或重放），
     * 调用方必须重新 {@link #find} 并按已有记录决定重放还是冲突。
     */
    public boolean insertProcessing(UUID id, UUID userId, String operationId, String idempotencyKey,
                                    String requestDigest, OffsetDateTime expiresAt) {
        int rows = jdbc.update(
                "INSERT INTO core.idempotency_request "
                        + "(id, user_id, operation_id, idempotency_key, request_digest, status, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?) "
                        + "ON CONFLICT (user_id, operation_id, idempotency_key) DO NOTHING",
                id, userId, operationId, idempotencyKey, requestDigest, expiresAt);
        return rows > 0;
    }

    public void markSucceeded(UUID id, int responseStatus, String responseSchemaVersion, String responseSnapshot) {
        jdbc.update(
                "UPDATE core.idempotency_request SET status = 'SUCCEEDED', response_status = ?, "
                        + "response_schema_version = ?, response_snapshot = ?::jsonb WHERE id = ?",
                responseStatus, responseSchemaVersion, responseSnapshot, id);
    }

    /**
     * 父工作区 {@code IdempotencyRequestRepository} 的兼容入口：直接把一次已完成请求记成 SUCCEEDED。
     * 上传端因为需要在落盘前抢占键位，走的是 {@code insertProcessing + markSucceeded}。
     */
    public void insertSucceeded(UUID userId, String operationId, String idempotencyKey,
                                String requestDigest, String responseSnapshot) {
        insertSucceeded(userId, operationId, idempotencyKey, requestDigest, 200, responseSnapshot);
    }

    /** 父工作区兼容签名：显式指定回放用的 HTTP 状态码。 */
    public void insertSucceeded(UUID userId, String operationId, String idempotencyKey,
                                String requestDigest, int responseStatus, String responseSnapshot) {
        jdbc.update(
                "INSERT INTO core.idempotency_request "
                        + "(id, user_id, operation_id, idempotency_key, request_digest, status, "
                        + "response_status, response_snapshot, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?::jsonb, now() + interval '24 hours') "
                        + "ON CONFLICT (user_id, operation_id, idempotency_key) DO NOTHING",
                UUID.randomUUID(), userId, operationId, idempotencyKey, requestDigest, responseStatus,
                responseSnapshot);
    }

    public void markFailed(UUID id) {
        jdbc.update("UPDATE core.idempotency_request SET status = 'FAILED' WHERE id = ?", id);
    }

    /** 只删自己刚插的 PROCESSING 坑位，绝不碰别人已完成的记录。 */
    public void deleteProcessing(UUID id) {
        jdbc.update("DELETE FROM core.idempotency_request WHERE id = ? AND status = 'PROCESSING'", id);
    }
}
