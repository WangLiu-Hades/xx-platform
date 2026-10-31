package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 审批聚合与复核事实的数据访问。
 *
 * <p>两条不变式靠 SQL 兜底，而不是只靠服务层记得：
 *
 * <ul>
 *   <li>复核决定**只追加**——{@code audit.action_approval_decision} 上有 append-only 触发器，
 *       所以这里只有 INSERT，没有 UPDATE/DELETE；</li>
 *   <li>状态推进要带上版本号与前置状态——审批可能被两个人同时裁决，{@link #updateStatus}
 *       用 {@code version} 做乐观锁，{@link #expireIfPending} 用 {@code status='PENDING'} 做条件。</li>
 * </ul>
 *
 * <p>"可见性"放在 SQL 里一次算清：申请人看自己的；复核人看操作在权限范围内的、且自己**还没裁决**
 * 的（裁决过的从待办里消失，但仍可通过详情读到）。可复核的 operationId 用
 * {@code string_to_array} 传参，避免驱动层数组绑定的兼容问题（id 里不含逗号）。
 */
@Repository
public class ActionApprovalRepository {

    private static final String SELECT_COLUMNS = """
            a.id, a.requester_user_id, a.executor_user_id, a.operation_id, a.policy_version,
            a.payload_digest, a.frozen_payload_ciphertext, a.encryption_key_id, a.summary,
            a.status, a.required_approvals, a.created_at, a.expires_at,
            a.execution_grant_expires_at, a.consumed_at, a.version
            """;

    private static final String INSERT = """
            INSERT INTO operation.action_approval
              (id, requester_user_id, executor_user_id, operation_id, policy_version, payload_digest,
               frozen_payload_ciphertext, encryption_key_id, summary, status, required_approvals,
               created_at, expires_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, 1)
            """;

    private static final String SELECT_BY_ID = "SELECT " + SELECT_COLUMNS
            + " FROM operation.action_approval a WHERE a.id = ?";

    private static final String LOCK_BY_ID = SELECT_BY_ID + " FOR UPDATE";

    private static final String SELECT_VISIBLE = "SELECT " + SELECT_COLUMNS + """
             FROM operation.action_approval a
            WHERE (a.requester_user_id = ?
                   OR (a.executor_user_id <> ?
                       AND a.operation_id = ANY (string_to_array(?, ',')::text[])
                       AND NOT EXISTS (SELECT 1 FROM audit.action_approval_decision d
                                        WHERE d.approval_id = a.id AND d.reviewer_user_id = ?)))
            """;

    private static final String KEYSET = " AND (a.created_at, a.id) < (?, ?)";

    private static final String ORDER_AND_LIMIT = " ORDER BY a.created_at DESC, a.id DESC LIMIT ?";

    /** 没有任何可复核操作时用的哨兵，保证 {@code ANY} 恒假而不是语法错误。 */
    private static final String NO_OPERATIONS = "--none--";

    private static final String INSERT_DECISION = """
            INSERT INTO audit.action_approval_decision
              (id, approval_id, reviewer_user_id, decision, reason, decided_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_DECISIONS = """
            SELECT id, approval_id, reviewer_user_id, decision, reason, decided_at
              FROM audit.action_approval_decision
             WHERE approval_id = ?
             ORDER BY decided_at, id
            """;

    private static final String SELECT_DECISION_BY_REVIEWER = """
            SELECT id, approval_id, reviewer_user_id, decision, reason, decided_at
              FROM audit.action_approval_decision
             WHERE approval_id = ? AND reviewer_user_id = ?
            """;

    private static final String COUNT_APPROVE = """
            SELECT count(*) FROM audit.action_approval_decision
             WHERE approval_id = ? AND decision = 'APPROVE'
            """;

    private static final String UPDATE_STATUS = """
            UPDATE operation.action_approval
               SET status = ?, execution_grant_expires_at = ?, version = version + 1
             WHERE id = ? AND version = ?
            """;

    private static final String EXPIRE_PENDING = """
            UPDATE operation.action_approval
               SET status = 'EXPIRED', version = version + 1
             WHERE id = ? AND status = 'PENDING'
            """;

    private final JdbcTemplate jdbc;

    public ActionApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id,
                       UUID requesterUserId,
                       UUID executorUserId,
                       String operationId,
                       String policyVersion,
                       String payloadDigest,
                       byte[] ciphertext,
                       String encryptionKeyId,
                       String summary,
                       int requiredApprovals,
                       OffsetDateTime createdAt,
                       OffsetDateTime expiresAt) {
        requireNonNull(id, "id");
        requireNonNull(requesterUserId, "requesterUserId");
        requireNonNull(executorUserId, "executorUserId");
        requireNonNull(payloadDigest, "payloadDigest");
        requireNonNull(ciphertext, "ciphertext");
        jdbc.update(INSERT, id, requesterUserId, executorUserId, operationId, policyVersion, payloadDigest,
                ciphertext, encryptionKeyId, summary, requiredApprovals, createdAt, expiresAt);
    }

    public Optional<ApprovalRow> findById(UUID approvalId) {
        requireNonNull(approvalId, "approvalId");
        return jdbc.query(SELECT_BY_ID, ROW_MAPPER, approvalId).stream().findFirst();
    }

    /**
     * 裁决前锁定该行：同一审批的两个审批人并发提交时，第二个人会等在行锁上，
     * 拿到锁后重新读到的 {@code version} 已经变了，从而走 409 而不是双写。
     */
    public Optional<ApprovalRow> lockById(UUID approvalId) {
        requireNonNull(approvalId, "approvalId");
        return jdbc.query(LOCK_BY_ID, ROW_MAPPER, approvalId).stream().findFirst();
    }

    /**
     * 分页查询"本人可见"的审批。
     *
     * @param viewerId             当前用户
     * @param reviewableOperations 当前用户有权限复核的 operationId；空列表时只返回本人申请的
     * @param cursorCreatedAt      游标上界的创建时间；null 表示第一页
     * @param cursorId             游标上界的 id（与 createdAt 组成稳定序）
     * @param limit                页大小 + 1，多取一条用来判断还有没有下一页
     */
    public List<ApprovalRow> listVisible(UUID viewerId,
                                         List<String> reviewableOperations,
                                         OffsetDateTime cursorCreatedAt,
                                         UUID cursorId,
                                         int limit) {
        requireNonNull(viewerId, "viewerId");
        StringBuilder sql = new StringBuilder(SELECT_VISIBLE);
        List<Object> args = new ArrayList<>();
        args.add(viewerId);
        args.add(viewerId);
        args.add(reviewableOperations == null || reviewableOperations.isEmpty()
                ? NO_OPERATIONS
                : String.join(",", reviewableOperations));
        args.add(viewerId);
        if (cursorCreatedAt != null && cursorId != null) {
            sql.append(KEYSET);
            args.add(cursorCreatedAt);
            args.add(cursorId);
        }
        sql.append(ORDER_AND_LIMIT);
        args.add(limit);
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public void insertDecision(UUID id, UUID approvalId, UUID reviewerUserId, String decision,
                               String reason, OffsetDateTime decidedAt) {
        requireNonNull(id, "id");
        requireNonNull(approvalId, "approvalId");
        requireNonNull(reviewerUserId, "reviewerUserId");
        jdbc.update(INSERT_DECISION, id, approvalId, reviewerUserId, decision, reason, decidedAt);
    }

    public List<DecisionRow> listDecisions(UUID approvalId) {
        requireNonNull(approvalId, "approvalId");
        return jdbc.query(SELECT_DECISIONS, DECISION_MAPPER, approvalId);
    }

    public Optional<DecisionRow> findDecision(UUID approvalId, UUID reviewerUserId) {
        requireNonNull(approvalId, "approvalId");
        requireNonNull(reviewerUserId, "reviewerUserId");
        return jdbc.query(SELECT_DECISION_BY_REVIEWER, DECISION_MAPPER, approvalId, reviewerUserId)
                .stream().findFirst();
    }

    public long countApprovals(UUID approvalId) {
        requireNonNull(approvalId, "approvalId");
        Long count = jdbc.queryForObject(COUNT_APPROVE, Long.class, approvalId);
        return count == null ? 0 : count;
    }

    /** 乐观锁推进状态；返回 false 表示版本已被别人改过。 */
    public boolean updateStatus(UUID approvalId, String status, OffsetDateTime grantExpiresAt, long version) {
        requireNonNull(approvalId, "approvalId");
        return jdbc.update(UPDATE_STATUS, status, grantExpiresAt, approvalId, version) == 1;
    }

    public boolean expireIfPending(UUID approvalId) {
        requireNonNull(approvalId, "approvalId");
        return jdbc.update(EXPIRE_PENDING, approvalId) == 1;
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    private static final RowMapper<ApprovalRow> ROW_MAPPER = (rs, rowNum) -> new ApprovalRow(
            rs.getObject("id", UUID.class),
            rs.getObject("requester_user_id", UUID.class),
            rs.getObject("executor_user_id", UUID.class),
            rs.getString("operation_id"),
            rs.getString("policy_version"),
            rs.getString("payload_digest"),
            rs.getBytes("frozen_payload_ciphertext"),
            rs.getString("encryption_key_id"),
            rs.getString("summary"),
            rs.getString("status"),
            rs.getInt("required_approvals"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("execution_grant_expires_at", OffsetDateTime.class),
            rs.getObject("consumed_at", OffsetDateTime.class),
            rs.getLong("version"));

    private static final RowMapper<DecisionRow> DECISION_MAPPER = (rs, rowNum) -> new DecisionRow(
            rs.getObject("id", UUID.class),
            rs.getObject("approval_id", UUID.class),
            rs.getObject("reviewer_user_id", UUID.class),
            rs.getString("decision"),
            rs.getString("reason"),
            rs.getObject("decided_at", OffsetDateTime.class));

    public record ApprovalRow(UUID id,
                              UUID requesterUserId,
                              UUID executorUserId,
                              String operationId,
                              String policyVersion,
                              String payloadDigest,
                              byte[] ciphertext,
                              String encryptionKeyId,
                              String summary,
                              String status,
                              int requiredApprovals,
                              OffsetDateTime createdAt,
                              OffsetDateTime expiresAt,
                              OffsetDateTime executionGrantExpiresAt,
                              OffsetDateTime consumedAt,
                              long version) {

        public boolean isPending() {
            return "PENDING".equals(status);
        }
    }

    public record DecisionRow(UUID id,
                              UUID approvalId,
                              UUID reviewerUserId,
                              String decision,
                              String reason,
                              OffsetDateTime decidedAt) {
    }
}
