package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.ActionApprovalRepository;
import com.xxplatform.api.infra.ActionApprovalRepository.ApprovalRow;
import com.xxplatform.api.infra.ActionApprovalRepository.DecisionRow;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuthenticatedSession;

/** 审批生命周期：创建、可见性、裁决分支与幂等。 */
class ApprovalServiceTests {

    private static final String PASSWORD = "CorrectHorse1!";

    /** 32 字节全零密钥，仅测试用。 */
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static String passwordHash;

    private UUID requesterId;
    private UUID reviewerId;
    private UUID strangerId;
    private List<String> requesterPermissions;
    private List<String> reviewerPermissions;
    private StubApprovals approvals;
    private StubRbac rbac;
    private StubUsers users;
    private ApprovalService service;

    @BeforeAll
    static void hashOnce() {
        // PBKDF2 600k 迭代约 0.6 秒，只算一次
        passwordHash = new PasswordHasher().hash(PASSWORD);
    }

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        requesterPermissions = new ArrayList<>(List.of("capture:export"));
        reviewerPermissions = new ArrayList<>(List.of("capture:export"));
        approvals = new StubApprovals();
        rbac = new StubRbac();
        rbac.grant(requesterId, requesterPermissions);
        rbac.grant(reviewerId, reviewerPermissions);
        rbac.grant(strangerId, List.of("task:read"));
        users = new StubUsers();
        users.add(requesterId, passwordHash);
        users.add(reviewerId, passwordHash);
        service = new ApprovalService(approvals, cipher(TEST_KEY), properties(), rbac, users,
                new PasswordHasher());
    }

    @Test
    void createFreezesProposalWithDigestAndPendingStatus() {
        Map<String, Object> view = service.create(session(requesterId), exportProposal(), "申请导出采集数据用于核查");

        assertEquals("PENDING", view.get("status"));
        assertEquals("createExport", view.get("operationId"));
        assertEquals(requesterId, view.get("requesterUserId"));
        assertEquals(1, approvals.inserted.size());
        StubApprovals.Inserted inserted = approvals.inserted.get(0);
        assertEquals(64, inserted.payloadDigest().length());
        assertTrue(inserted.ciphertext().length > 0, "冻结载荷必须是密文");
        assertEquals("local-dev-v1", inserted.encryptionKeyId());
        assertEquals(1, inserted.requiredApprovals());
    }

    @Test
    void createRequiresTheOperationPermission() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.create(session(strangerId), exportProposal(), "申请导出采集数据用于核查"));
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code());
        assertTrue(approvals.inserted.isEmpty());
    }

    @Test
    void createFailsClosedWhenNoPayloadKeyIsConfigured() {
        ApprovalService withoutKey = new ApprovalService(approvals, cipher(""), properties(), rbac, users,
                new PasswordHasher());
        ApiException error = assertThrows(ApiException.class,
                () -> withoutKey.create(session(requesterId), exportProposal(), "申请导出采集数据用于核查"));
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, error.code());
        assertTrue(approvals.inserted.isEmpty());
    }

    @Test
    void createRejectsShortReason() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.create(session(requesterId), exportProposal(), "太短"));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertEquals("reason", error.details().get(0).field());
    }

    @Test
    void strangerCannotReadApproval() {
        UUID approvalId = createApproval();
        ApiException error = assertThrows(ApiException.class,
                () -> service.get(session(strangerId), approvalId));
        assertEquals(ErrorCode.DATA_NOT_FOUND, error.code());
    }

    @Test
    void reviewerWithPermissionCanReadAndSeeReviewItems() {
        UUID approvalId = createApproval();
        Map<String, Object> view = service.get(session(reviewerId), approvalId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) view.get("reviewItems");
        assertFalse(items.isEmpty());
        assertEquals(64, ((String) view.get("requestDigest")).length());
        assertNull(view.get("executionGrantExpiresAt"));
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        UUID approvalId = createApproval();
        ApiException error = assertThrows(ApiException.class,
                () -> service.decide(session(requesterId), approvalId, "APPROVE",
                        "我自己批自己的申请看看", PASSWORD));
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code());
        assertTrue(approvals.decisions.isEmpty());
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutRecordingDecision() {
        UUID approvalId = createApproval();
        ApiException error = assertThrows(ApiException.class,
                () -> service.decide(session(reviewerId), approvalId, "APPROVE",
                        "独立复核后同意该导出申请", "wrong-password"));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertEquals("currentPassword", error.details().get(0).field());
        assertTrue(approvals.decisions.isEmpty());
    }

    @Test
    void rejectionEndsTheApprovalImmediately() {
        UUID approvalId = createApproval();
        Map<String, Object> view = service.decide(session(reviewerId), approvalId, "REJECT",
                "用途描述不足，先驳回再补充说明", PASSWORD);

        assertEquals("REJECTED", view.get("status"));
        assertNull(view.get("executionGrantExpiresAt"));
        assertEquals(1, approvals.decisions.size());
    }

    @Test
    void twoApprovalsAreCollectedThenGranted() {
        requesterPermissions.add("capture:decrypt");
        reviewerPermissions.add("capture:decrypt");
        UUID secondReviewer = UUID.randomUUID();
        rbac.grant(secondReviewer, List.of("capture:decrypt"));
        users.add(secondReviewer, passwordHash);
        UUID approvalId = createApproval("readDecryptionResult", Map.of(
                "pathParameters", Map.of("requestId", "00000000-0000-4000-8000-000000000009")));

        Map<String, Object> afterFirst = service.decide(session(reviewerId), approvalId, "APPROVE",
                "第一位复核人同意该解密申请", PASSWORD);
        assertEquals("PENDING", afterFirst.get("status"));

        Map<String, Object> afterSecond = service.decide(session(secondReviewer), approvalId, "APPROVE",
                "第二位复核人同意该解密申请", PASSWORD);
        assertEquals("APPROVED", afterSecond.get("status"));
        assertNotNull(afterSecond.get("executionGrantExpiresAt"));
    }

    @Test
    void approvingACompletedApprovalIsConflict() {
        UUID approvalId = createApproval();
        service.decide(session(reviewerId), approvalId, "REJECT", "用途描述不足，先驳回再补充说明", PASSWORD);

        ApiException error = assertThrows(ApiException.class,
                () -> service.decide(session(reviewerId), approvalId, "APPROVE",
                        "想改主意，重新批准这条申请", PASSWORD));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, error.code());
    }

    @Test
    void expiredPendingApprovalIsMarkedAndRejected() {
        UUID approvalId = createApproval();
        ApprovalRow row = approvals.rows.get(approvalId);
        approvals.rows.put(approvalId, new ApprovalRow(row.id(), row.requesterUserId(), row.executorUserId(),
                row.operationId(), row.policyVersion(), row.payloadDigest(), row.ciphertext(),
                row.encryptionKeyId(), row.summary(), row.status(), row.requiredApprovals(),
                row.createdAt(), OffsetDateTime.now().minusMinutes(1), null, null, row.version()));

        ApiException error = assertThrows(ApiException.class,
                () -> service.decide(session(reviewerId), approvalId, "APPROVE",
                        "超时之后再批准这条申请", PASSWORD));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, error.code());
        assertEquals("EXPIRED", approvals.rows.get(approvalId).status());
    }

    @Test
    void repeatingTheSameDecisionIsIdempotent() {
        UUID approvalId = createApproval();
        service.decide(session(reviewerId), approvalId, "APPROVE",
                "复核人同意该导出申请并记录理由", PASSWORD);
        Map<String, Object> again = service.decide(session(reviewerId), approvalId, "APPROVE",
                "复核人同意该导出申请并记录理由", PASSWORD);

        assertEquals("APPROVED", again.get("status"));
        assertEquals(1, approvals.decisions.size());
    }

    @Test
    void listReturnsOwnApplicationAndReviewableItemsWithCursor() {
        createApproval();
        createApproval();
        Map<String, Object> page = service.page(session(reviewerId), null, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertEquals(2, items.size());
        assertNull(page.get("nextCursor"));
        assertEquals(20, page.get("pageSize"));

        Map<String, Object> firstPage = service.page(session(reviewerId), null, 1);
        assertEquals(1, ((List<?>) firstPage.get("items")).size());
        String cursor = (String) firstPage.get("nextCursor");
        assertNotNull(cursor);
        Map<String, Object> second = service.page(session(reviewerId), cursor, 1);
        assertEquals(1, ((List<?>) second.get("items")).size());
    }

    @Test
    void invalidCursorIsValidationError() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.page(session(reviewerId), "not-a-cursor", 20));
        assertEquals(ErrorCode.VALIDATION_ERROR, error.code());
        assertEquals("cursor", error.details().get(0).field());
    }

    @Test
    void pageSizeOutOfRangeIsValidationError() {
        assertThrows(ApiException.class, () -> service.page(session(reviewerId), null, 0));
        assertThrows(ApiException.class, () -> service.page(session(reviewerId), null, 101));
    }

    @Test
    void versionConflictOnConcurrentDecisionIsReported() {
        UUID approvalId = createApproval();
        approvals.failNextUpdate = true;

        ApiException error = assertThrows(ApiException.class,
                () -> service.decide(session(reviewerId), approvalId, "REJECT",
                        "另一名审批人已经处理，本次推进失败", PASSWORD));
        assertEquals(ErrorCode.RESOURCE_VERSION_CONFLICT, error.code());
    }

    // ---- helpers ----

    private UUID createApproval() {
        return createApproval("createExport", Map.of("requestBody", Map.of("format", "XLSX", "contextId", "ctx-1")));
    }

    private UUID createApproval(String operationId, Map<String, Object> payload) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("operationId", operationId);
        proposal.putAll(payload);
        Map<String, Object> view = service.create(session(requesterId), proposal, "申请执行受控操作并冻结完整载荷");
        return (UUID) view.get("approvalId");
    }

    private static Map<String, Object> exportProposal() {
        return Map.of("operationId", "createExport",
                "requestBody", Map.of("contextId", "00000000-0000-4000-8000-000000000001", "format", "XLSX"));
    }

    private static ApprovalPayloadCipher cipher(String key) {
        ApprovalProperties properties = properties();
        properties.setPayloadKey(key);
        return new ApprovalPayloadCipher(properties);
    }

    private static ApprovalProperties properties() {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setPayloadKey("");
        properties.setTtl(Duration.ofHours(24));
        properties.setGrantTtl(Duration.ofMinutes(10));
        properties.setDecryptionGrantTtl(Duration.ofMinutes(5));
        return properties;
    }

    private static AuthenticatedSession session(UUID userId) {
        return new AuthenticatedSession(userId, UUID.randomUUID(), null, null, null);
    }

    private static final class StubRbac extends RbacRepository {

        private final Map<UUID, List<String>> grants = new HashMap<>();

        StubRbac() {
            super(new JdbcTemplate());
        }

        void grant(UUID userId, List<String> permissions) {
            grants.put(userId, permissions);
        }

        @Override
        public List<String> permissionCodes(UUID userId) {
            return grants.getOrDefault(userId, List.of());
        }
    }

    private static final class StubUsers extends UserAccountRepository {

        private final Map<UUID, String> hashes = new HashMap<>();

        StubUsers() {
            super(new JdbcTemplate());
        }

        void add(UUID userId, String hash) {
            hashes.put(userId, hash);
        }

        @Override
        public Optional<UserAccountRow> findById(UUID userId) {
            String hash = hashes.get(userId);
            if (hash == null) {
                return Optional.empty();
            }
            return Optional.of(new UserAccountRow(userId, "U", "login", "name", hash, "ACTIVE"));
        }
    }

    private static final class StubApprovals extends ActionApprovalRepository {

        record Inserted(UUID id, UUID requesterId, String operationId, String payloadDigest,
                        byte[] ciphertext, String encryptionKeyId, int requiredApprovals) {
        }

        private final Map<UUID, ApprovalRow> rows = new HashMap<>();
        private final List<Inserted> inserted = new ArrayList<>();
        private final List<DecisionRow> decisions = new ArrayList<>();
        private OffsetDateTime expiresAt;
        private int requiredApprovals = 1;
        private boolean failNextUpdate;
        private long clock = 0;

        StubApprovals() {
            super(new JdbcTemplate());
        }

        @Override
        public void insert(UUID id, UUID requesterUserId, UUID executorUserId, String operationId,
                           String policyVersion, String payloadDigest, byte[] ciphertext,
                           String encryptionKeyId, String summary, int required,
                           OffsetDateTime createdAt, OffsetDateTime expiresAt) {
            inserted.add(new Inserted(id, requesterUserId, operationId, payloadDigest, ciphertext,
                    encryptionKeyId, required));
            this.expiresAt = expiresAt;
            rows.put(id, new ApprovalRow(id, requesterUserId, executorUserId, operationId, policyVersion,
                    payloadDigest, ciphertext, encryptionKeyId, summary, "PENDING", required,
                    createdAt, expiresAt, null, null, 1));
        }

        @Override
        public Optional<ApprovalRow> findById(UUID approvalId) {
            return Optional.ofNullable(rows.get(approvalId));
        }

        @Override
        public Optional<ApprovalRow> lockById(UUID approvalId) {
            return findById(approvalId);
        }

        @Override
        public List<ApprovalRow> listVisible(UUID viewerId, List<String> reviewableOperations,
                                             OffsetDateTime cursorCreatedAt, UUID cursorId, int limit) {
            List<ApprovalRow> visible = new ArrayList<>();
            for (ApprovalRow row : rows.values()) {
                boolean own = row.requesterUserId().equals(viewerId);
                boolean reviewable = !row.executorUserId().equals(viewerId)
                        && reviewableOperations.contains(row.operationId())
                        && decisions.stream().noneMatch(d -> d.approvalId().equals(row.id())
                        && d.reviewerUserId().equals(viewerId));
                if (!own && !reviewable) {
                    continue;
                }
                if (cursorCreatedAt != null && cursorId != null) {
                    if (!(row.createdAt().isBefore(cursorCreatedAt)
                            || (row.createdAt().isEqual(cursorCreatedAt) && row.id().compareTo(cursorId) < 0))) {
                        continue;
                    }
                }
                visible.add(row);
            }
            visible.sort((a, b) -> {
                int byTime = b.createdAt().compareTo(a.createdAt());
                return byTime != 0 ? byTime : b.id().compareTo(a.id());
            });
            return visible.size() > limit ? visible.subList(0, limit) : visible;
        }

        @Override
        public void insertDecision(UUID id, UUID approvalId, UUID reviewerUserId, String decision,
                                   String reason, OffsetDateTime decidedAt) {
            decisions.add(new DecisionRow(id, approvalId, reviewerUserId, decision, reason, decidedAt));
        }

        @Override
        public List<DecisionRow> listDecisions(UUID approvalId) {
            return decisions.stream().filter(d -> d.approvalId().equals(approvalId)).toList();
        }

        @Override
        public Optional<DecisionRow> findDecision(UUID approvalId, UUID reviewerUserId) {
            return decisions.stream()
                    .filter(d -> d.approvalId().equals(approvalId) && d.reviewerUserId().equals(reviewerUserId))
                    .findFirst();
        }

        @Override
        public long countApprovals(UUID approvalId) {
            return listDecisions(approvalId).stream().filter(d -> "APPROVE".equals(d.decision())).count();
        }

        @Override
        public boolean updateStatus(UUID approvalId, String status, OffsetDateTime grantExpiresAt, long version) {
            if (failNextUpdate) {
                failNextUpdate = false;
                return false;
            }
            ApprovalRow row = rows.get(approvalId);
            if (row == null) {
                return false;
            }
            rows.put(approvalId, new ApprovalRow(row.id(), row.requesterUserId(), row.executorUserId(),
                    row.operationId(), row.policyVersion(), row.payloadDigest(), row.ciphertext(),
                    row.encryptionKeyId(), row.summary(), status, row.requiredApprovals(),
                    row.createdAt(), row.expiresAt(), grantExpiresAt, row.consumedAt(), row.version() + 1));
            return true;
        }

        @Override
        public boolean expireIfPending(UUID approvalId) {
            ApprovalRow row = rows.get(approvalId);
            if (row == null || !"PENDING".equals(row.status())) {
                return false;
            }
            rows.put(approvalId, new ApprovalRow(row.id(), row.requesterUserId(), row.executorUserId(),
                    row.operationId(), row.policyVersion(), row.payloadDigest(), row.ciphertext(),
                    row.encryptionKeyId(), row.summary(), "EXPIRED", row.requiredApprovals(),
                    row.createdAt(), row.expiresAt(), null, row.consumedAt(), row.version() + 1));
            return true;
        }
    }
}
