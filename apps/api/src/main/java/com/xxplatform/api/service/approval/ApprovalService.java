package com.xxplatform.api.service.approval;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.ActionApprovalRepository;
import com.xxplatform.api.infra.ActionApprovalRepository.ApprovalRow;
import com.xxplatform.api.infra.ActionApprovalRepository.DecisionRow;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuthenticatedSession;

/**
 * PD-2026-10-19.v1（暂定）的敏感动作审批：冻结申请、独立复核、按票数放行。
 *
 * <p>三条业务规则来自协议文档，而不是接口形状：
 *
 * <ul>
 *   <li><b>不能自批</b>：申请人自己就是 {@code requester_user_id}，所以任何申请人都不能裁决自己的
 *       申请；执行人也一样（当前实现里 {@code executor_user_id} 与申请人相同，属于临时口径，
 *       真正的"指定执行人"要等甲方确认）。</li>
 *   <li><b>复核人要真的有权限</b>：审批人必须持有该操作对应的权限码，否则"批了也执行不了"，
 *       只会制造一条无效审批。申请人同样要持有——自己都做不了的动作不该先申请。</li>
 *   <li><b>当前密码只做即时校验</b>：{@link #decide} 用 {@link PasswordHasher} 对当前用户口令做
 *       一次校验，明文不落库、不写日志、不回显；这是"二次确认"的最低成本实现。</li>
 * </ul>
 *
 * <p>幂等：契约的决策接口带 {@code Idempotency-Key}，但库里没有对应列（V3 未建，且本轮不改
 * schema 以避免破坏迁移 parity 契约）。因此退化为"同一审批人、同一决定、同一理由 → 返回既有
 * 决定"，语义上等价于重复提交不产生新事实；不同决定仍回 409。这条差异在接口 Javadoc 与周报
 * 证据里都会写明。
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";
    private static final int REASON_MIN = 10;
    private static final int REASON_MAX = 1000;
    private static final int PASSWORD_MAX = 128;

    private final ActionApprovalRepository approvals;
    private final ApprovalPayloadCipher cipher;
    private final ApprovalProperties properties;
    private final RbacRepository rbac;
    private final UserAccountRepository users;
    private final PasswordHasher passwordHasher;

    public ApprovalService(ActionApprovalRepository approvals,
                           ApprovalPayloadCipher cipher,
                           ApprovalProperties properties,
                           RbacRepository rbac,
                           UserAccountRepository users,
                           PasswordHasher passwordHasher) {
        this.approvals = approvals;
        this.cipher = cipher;
        this.properties = properties;
        this.rbac = rbac;
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    /** 创建审批：冻结提案、算摘要、落 PENDING。 */
    @Transactional
    public Map<String, Object> create(AuthenticatedSession session,
                                      Map<String, Object> proposal,
                                      String reason) {
        String normalizedReason = requireReason(reason);
        ApprovalProposals.Proposal frozen = ApprovalProposals.parse(proposal);
        if (!permissionsOf(session.userId()).contains(frozen.operation().permission())) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED,
                    "当前账号不具备 " + frozen.operation().permission() + " 权限，不能申请该操作");
        }
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime expiresAt = now.plus(properties.getTtl());
        byte[] sealed = cipher.seal(frozen.canonicalJson().getBytes(StandardCharsets.UTF_8));
        UUID id = UUID.randomUUID();
        approvals.insert(id, session.userId(), session.userId(), frozen.operation().operationId(),
                properties.getPolicyVersion(), frozen.digestHex(), sealed, cipher.keyId(),
                frozen.summary(), frozen.requiredApprovals(), now, expiresAt);
        log.info("创建敏感动作审批 approvalId={} operation={} requester={} requiredApprovals={}",
                id, frozen.operation().operationId(), session.userId(), frozen.requiredApprovals());
        return view(approvals.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "审批不存在")));
    }

    /** 详情：申请人或有权复核的人可见，其余一律 404 隐藏存在性。 */
    @Transactional
    public Map<String, Object> get(AuthenticatedSession session, UUID approvalId) {
        ApprovalRow row = requireVisible(approvalId, session.userId());
        return view(row);
    }

    /** 列表：本人申请的 + 待本人复核的，keyset 游标分页。 */
    public Map<String, Object> page(AuthenticatedSession session, String cursor, Integer pageSize) {
        int size = pageSize == null ? properties.getPageSizeDefault() : pageSize;
        if (size < 1 || size > properties.getPageSizeMax()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "pageSize 必须在 1 到 " + properties.getPageSizeMax() + " 之间",
                    List.of(new ApiResponses.FieldErrorDetail("pageSize",
                            "pageSize 必须在 1 到 " + properties.getPageSizeMax() + " 之间")), null);
        }
        Cursor decoded = Cursor.decode(cursor);
        List<String> reviewable = reviewableOperations(session.userId());
        List<ApprovalRow> rows = approvals.listVisible(session.userId(), reviewable,
                decoded.createdAt(), decoded.id(), size + 1);
        boolean hasMore = rows.size() > size;
        List<ApprovalRow> pageRows = hasMore ? rows.subList(0, size) : rows;
        List<Map<String, Object>> items = new ArrayList<>(pageRows.size());
        pageRows.forEach(row -> items.add(view(row)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("nextCursor", hasMore ? Cursor.encode(pageRows.get(pageRows.size() - 1)) : null);
        data.put("pageSize", size);
        return data;
    }

    /**
     * 提交复核决定。
     *
     * <p>顺序是有讲究的：先校验"能不能看"（404 隐藏），再校验"能不能批"（自批 403、无权限 403），
     * 然后才校验状态与口令。这样越权者拿不到任何状态信息（例如"这条已经被驳回了"）。
     */
    @Transactional(noRollbackFor = ApprovalExpiredException.class)
    public Map<String, Object> decide(AuthenticatedSession session,
                                      UUID approvalId,
                                      String decision,
                                      String reason,
                                      String currentPassword) {
        String normalizedDecision = requireDecision(decision);
        String normalizedReason = requireReason(reason);
        requirePassword(currentPassword);

        ApprovalRow row = approvals.lockById(approvalId)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "审批不存在"));
        if (session.userId().equals(row.requesterUserId()) || session.userId().equals(row.executorUserId())) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "申请人或执行人不能复核自己的申请");
        }
        ApprovalOperation operation = ApprovalOperation.parse(row.operationId())
                .orElseThrow(() -> new IllegalStateException("审批引用了未登记操作：" + row.operationId()));
        if (!permissionsOf(session.userId()).contains(operation.permission())) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED,
                    "当前账号不具备 " + operation.permission() + " 权限，不能复核该操作");
        }

        OffsetDateTime now = Instants.nowUtc();
        requirePasswordMatches(session.userId(), currentPassword);

        // 幂等重放要排在"状态不是 PENDING"之前：第一次批准会把状态推进到 APPROVED，
        // 同一个人重复提交同一决定时应当拿回同一结果，而不是 409。
        Optional<DecisionRow> existing = approvals.findDecision(row.id(), session.userId());
        if (existing.isPresent()) {
            DecisionRow previous = existing.get();
            if (previous.decision().equals(normalizedDecision) && previous.reason().equals(normalizedReason)) {
                return view(approvals.findById(row.id()).orElseThrow());
            }
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "你已经对该申请提交过决定");
        }

        if (row.isPending() && !row.expiresAt().isAfter(now)) {
            // 先把状态写成 EXPIRED 再抛 409：noRollbackFor 让这次更新随事务提交，
            // 否则异常会把"已过期"这个事实一起回滚，库里留下永久 PENDING 的过期记录。
            approvals.expireIfPending(row.id());
            throw new ApprovalExpiredException("审批已过期");
        }
        if (!row.isPending()) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批当前状态为 " + row.status() + "，不能再复核");
        }

        approvals.insertDecision(UUID.randomUUID(), row.id(), session.userId(), normalizedDecision,
                normalizedReason, now);

        if (REJECT.equals(normalizedDecision)) {
            boolean updated = approvals.updateStatus(row.id(), "REJECTED", null, row.version());
            if (!updated) {
                throw new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT, "审批状态已被其他人更新，请重新读取");
            }
            log.info("审批被驳回 approvalId={} reviewer={}", row.id(), session.userId());
        } else {
            long approveCount = approvals.countApprovals(row.id());
            if (approveCount >= row.requiredApprovals()) {
                Duration grantTtl = operation == ApprovalOperation.READ_DECRYPTION_RESULT
                        ? properties.getDecryptionGrantTtl()
                        : properties.getGrantTtl();
                OffsetDateTime grantExpiresAt = now.plus(grantTtl);
                if (grantExpiresAt.isAfter(row.expiresAt())) {
                    grantExpiresAt = row.expiresAt();
                }
                boolean updated = approvals.updateStatus(row.id(), "APPROVED", grantExpiresAt, row.version());
                if (!updated) {
                    throw new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT, "审批状态已被其他人更新，请重新读取");
                }
                log.info("审批通过 approvalId={} approveCount={}/{}", row.id(), approveCount, row.requiredApprovals());
            }
        }
        return view(approvals.findById(row.id()).orElseThrow());
    }

    private ApprovalRow requireVisible(UUID approvalId, UUID viewerId) {
        ApprovalRow row = approvals.findById(approvalId)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "审批不存在"));
        if (row.requesterUserId().equals(viewerId)) {
            return row;
        }
        boolean reviewer = !row.executorUserId().equals(viewerId)
                && reviewableOperations(viewerId).contains(row.operationId());
        if (!reviewer) {
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "审批不存在");
        }
        return row;
    }

    private Map<String, Object> view(ApprovalRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("approvalId", row.id());
        view.put("operationId", row.operationId());
        view.put("requesterUserId", row.requesterUserId());
        view.put("executorUserId", row.executorUserId());
        view.put("requestDigest", row.payloadDigest());
        view.put("summary", row.summary());
        view.put("status", row.status());
        view.put("requiredApprovals", row.requiredApprovals());
        List<Map<String, Object>> decisions = new ArrayList<>();
        for (DecisionRow decision : approvals.listDecisions(row.id())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("decisionId", decision.id());
            item.put("reviewerUserId", decision.reviewerUserId());
            item.put("decision", decision.decision());
            item.put("reason", decision.reason());
            item.put("decidedAt", Instants.iso(decision.decidedAt()));
            decisions.add(item);
        }
        view.put("decisions", decisions);
        view.put("createdAt", Instants.iso(row.createdAt()));
        view.put("expiresAt", Instants.iso(row.expiresAt()));
        view.put("executionGrantExpiresAt", row.executionGrantExpiresAt() == null
                ? null
                : Instants.iso(row.executionGrantExpiresAt()));
        view.put("version", row.version());
        view.put("reviewItems", reviewItems(row));
        return view;
    }

    private List<Map<String, Object>> reviewItems(ApprovalRow row) {
        byte[] plaintext = cipher.open(row.ciphertext());
        List<ApprovalProposals.ReviewItem> items = ApprovalProposals.reviewItems(
                new String(plaintext, StandardCharsets.UTF_8));
        List<Map<String, Object>> result = new ArrayList<>(items.size());
        for (ApprovalProposals.ReviewItem item : items) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("fieldCode", item.fieldCode());
            mapped.put("label", item.label());
            mapped.put("displayValue", item.displayValue());
            result.add(mapped);
        }
        return result;
    }

    private List<String> reviewableOperations(UUID userId) {
        List<String> permissions = permissionsOf(userId);
        List<String> operations = new ArrayList<>();
        for (ApprovalOperation operation : ApprovalOperation.values()) {
            if (permissions.contains(operation.permission())) {
                operations.add(operation.operationId());
            }
        }
        return operations;
    }

    private List<String> permissionsOf(UUID userId) {
        return rbac.permissionCodes(userId);
    }

    private void requirePasswordMatches(UUID userId, String currentPassword) {
        String stored = users.findById(userId).map(UserAccountRepository.UserAccountRow::passwordHash).orElse(null);
        if (stored == null || !passwordHasher.verify(currentPassword, stored)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "当前口令不正确",
                    List.of(new ApiResponses.FieldErrorDetail("currentPassword", "当前口令不正确")), null);
        }
    }

    private static String requireDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            throw fieldError("decision", "decision 必填");
        }
        String normalized = decision.trim().toUpperCase(java.util.Locale.ROOT);
        if (!APPROVE.equals(normalized) && !REJECT.equals(normalized)) {
            throw fieldError("decision", "decision 只能是 APPROVE 或 REJECT");
        }
        return normalized;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw fieldError("reason", "reason 必填");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < REASON_MIN || trimmed.length() > REASON_MAX) {
            throw fieldError("reason", "reason 长度必须在 " + REASON_MIN + " 到 " + REASON_MAX + " 之间");
        }
        return trimmed;
    }

    private static void requirePassword(String password) {
        if (password == null || password.isEmpty() || password.length() > PASSWORD_MAX) {
            throw fieldError("currentPassword", "currentPassword 必须是 1 到 " + PASSWORD_MAX + " 个字符");
        }
    }

    private static ApiException fieldError(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message,
                List.of(new ApiResponses.FieldErrorDetail(field, message)), null);
    }

    /**
     * 列表游标：base64url({@code createdAt|id})。用 keyset 而不是 offset，避免翻页时被新申请挤动。
     * 解析失败一律 400，而不是悄悄退回第一页——静默回退会让客户端无限翻同一页。
     */
    record Cursor(OffsetDateTime createdAt, UUID id) {

        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new Cursor(null, null);
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", 2);
                return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException e) {
                throw fieldError("cursor", "cursor 无法解析");
            }
        }

        static String encode(ApprovalRow row) {
            String raw = Instants.iso(row.createdAt()) + "|" + row.id();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }
}
