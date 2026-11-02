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
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.ActionApprovalRepository;
import com.xxplatform.api.infra.ActionApprovalRepository.ApprovalRow;
import com.xxplatform.api.infra.ActionApprovalRepository.DecisionRow;
import com.xxplatform.api.infra.IdempotencyRequestRepository;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuditService;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.task.TaskParameters;

/** Main SENSITIVE_ACTION_PROTOCOL: configured independent reviewers, frozen real targets, live reauthorization, transaction-bound single consumption and idempotency. */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private static final String APPROVE = "APPROVE";
    private static final String REJECT = "REJECT";
    private static final int REASON_MIN = 10;
    private static final int REASON_MAX = 1000;
    private static final int PASSWORD_MAX = 128;

    /** 幂等账本里的操作名；与契约 {@code operationId} 一致，便于按操作排查。 */
    static final String CREATE_LEDGER_OPERATION = "createActionApproval";
    static final String DECIDE_LEDGER_OPERATION = "decideActionApproval";

    private static final com.fasterxml.jackson.databind.ObjectMapper LEDGER_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ActionApprovalRepository approvals;
    private final ApprovalPayloadCipher cipher;
    private final ApprovalProperties properties;
    private final RbacRepository rbac;
    private final UserAccountRepository users;
    private final PasswordHasher passwordHasher;
    /** 审计（M20）；允许为 null：单测直接构造本服务时不必再搭一个审计桩。 */
    private final AuditService audit;
    /** 目标解析（M09/M10）；允许为 null：单测不查库。 */
    private final ApprovalTargetResolver resolver;
    /** 拒绝/失败审计（M20），独立事务提交；允许为 null。 */
    private final com.xxplatform.api.service.FailureAudit failureAudit;
    /** 请求级幂等账本（M15）；允许为 null：单测直接构造本服务时不查库。 */
    private final IdempotencyRequestRepository idempotency;

    /**
     * 单测/旧调用用的构造器：不带审计。
     *
     * <p>为什么保留它而不是强制注入审计：本服务的行为（票数、状态机、摘要比对）与"是否记账"
     * 正交，测试这些行为时搭一个审计桩只会增加噪音。生产注入走下面那个构造器。
     */
    public ApprovalService(ActionApprovalRepository approvals,
                           ApprovalPayloadCipher cipher,
                           ApprovalProperties properties,
                           RbacRepository rbac,
                           UserAccountRepository users,
                           PasswordHasher passwordHasher) {
        this(approvals, cipher, properties, rbac, users, passwordHasher, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalService(ActionApprovalRepository approvals,
                           ApprovalPayloadCipher cipher,
                           ApprovalProperties properties,
                           RbacRepository rbac,
                           UserAccountRepository users,
                           PasswordHasher passwordHasher,
                           AuditService audit,
                           ApprovalTargetResolver resolver,
                           com.xxplatform.api.service.FailureAudit failureAudit,
                           IdempotencyRequestRepository idempotency) {
        this.approvals = approvals;
        this.cipher = cipher;
        this.properties = properties;
        this.rbac = rbac;
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.audit = audit;
        this.resolver = resolver;
        this.failureAudit = failureAudit;
        this.idempotency = idempotency;
    }

    /** 创建审批：冻结提案、算摘要、落 PENDING。 */
    @Transactional
    public Map<String, Object> create(AuthenticatedSession session,
                                      Map<String, Object> proposal,
                                      String reason) {
        try {
            return doCreate(session, proposal, reason);
        } catch (RuntimeException e) {
            // M20：被拒的申请也要留痕。FailureAudit 用独立事务提交，业务回滚不会吞掉它。
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("operationId", proposal == null ? null : proposal.get("operationId"));
            failure(actorOf(session), null, "approval.create", "APPROVAL_REJECTED", e, detail);
            throw e;
        }
    }

    /** Lock the idempotency key, reauthorize, then replay or create within one transaction. */
    @Transactional
    public Map<String, Object> create(AuthenticatedSession session,
                                      Map<String, Object> proposal,
                                      String reason,
                                      String idempotencyKey) {
        if (idempotencyKey == null || idempotency == null) {
            return create(session, proposal, reason);
        }
        String key = IdempotencyKeyHeader.require(idempotencyKey);
        idempotency.lockKey(session.userId(), CREATE_LEDGER_OPERATION, key);
        authorizeCreate(session, proposal);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("operationId", CREATE_LEDGER_OPERATION);
        String digest = ledgerDigest(identity, proposal, reason);
        Optional<IdempotencyRequestRepository.LedgerRow> seen =
                idempotency.find(session.userId(), CREATE_LEDGER_OPERATION, key);
        if (seen.isPresent()) {
            return replayAuthorized(session, null, "approval.create", seen.get(), digest);
        }
        Map<String, Object> result = create(session, proposal, reason);
        idempotency.insertSucceeded(session.userId(), CREATE_LEDGER_OPERATION, key, digest, 201,
                toLedgerJson(result));
        return result;
    }

    private Map<String, Object> doCreate(AuthenticatedSession session,
                                         Map<String, Object> proposal,
                                         String reason) {
        requireReason(reason);
        ApprovalProposals.Proposal frozen = ApprovalProposals.parse(proposal);
        if (!permissionsOf(session.userId()).contains(frozen.operation().permission())) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED,
                    "当前账号不具备 " + frozen.operation().permission() + " 权限，不能申请该操作");
        }
        // M09/M10：目标集合由服务端解析，票数按解析后的真实设备数算；解析结果与
        // 资源版本/授权版本/策略版本一起冻结进密文快照，执行前还要再比对一次。
        ApprovalTargetResolver.Frozen resolved = null;
        int required = frozen.requiredApprovals();
        if (resolver != null) {
            resolved = resolver.freeze(frozen.operation(), frozen.pathParameters(),
                    frozen.requestBody(), resolver.scopeOf(session.userId()), proposal.get("ifMatch"), session.userId());
            required = resolved.keyChange() ? 2 : frozen.operation().requiredApprovals(resolved.deviceIds().size());
        }
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime expiresAt = now.plus(properties.getTtl());
        requireEnoughApprovers(frozen.operation(), required, session.userId(), resolved);
        byte[] sealed = cipher.seal(sealPayload(frozen, resolved, session.userId()));
        UUID id = UUID.randomUUID();
        approvals.insert(id, session.userId(), session.userId(), frozen.operation().operationId(),
                properties.getPolicyVersion(), frozen.digestHex(), sealed, cipher.keyId(),
                frozen.summary(), required, now, expiresAt);
        log.info("创建敏感动作审批 approvalId={} operation={} requester={} requiredApprovals={}",
                id, frozen.operation().operationId(), session.userId(), required);
        if (audit != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("operationId", frozen.operation().operationId());
            detail.put("requiredApprovals", required);
            detail.put("requestDigest", frozen.digestHex());
            if (resolved != null) {
                detail.put("resolvedDeviceCount", resolved.deviceIds().size());
            }
            audit.success("approval.create", "APPROVAL", "ACTION_APPROVAL", id, actorOf(session), null,
                    null, com.xxplatform.api.common.RequestIdFilter.currentTraceIdOrFallback(), detail);
        }
        return view(approvals.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "审批不存在")));
    }

    /** 快照明文：有服务端解析结果时用信封（request + frozen），否则退回裸 canonicalJson。 */
    private byte[] sealPayload(ApprovalProposals.Proposal frozen, ApprovalTargetResolver.Frozen resolved, UUID actorId) {
        Map<String, Object> facts = resolved == null ? Map.of() : new LinkedHashMap<>(resolved.toMap());
        if (resolver != null) facts.put("actorAuthorization", resolver.authorizationStamp(actorId));
        String plaintext = resolved == null
                ? frozen.canonicalJson()
                : ApprovalEnvelope.encode(frozen.canonicalJson(), facts);
        return plaintext.getBytes(StandardCharsets.UTF_8);
    }

    /** No configuration or too few currently qualified independent reviewers makes the action unavailable. */
    private void requireEnoughApprovers(ApprovalOperation operation, int required, UUID requesterId,
                                        ApprovalTargetResolver.Frozen resolved) {
        List<String> configured = properties.getApproverLogins();
        if (configured == null || configured.isEmpty()) {
            throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "未配置审批人，该动作不可用");
        }
        long eligible = configured.stream().distinct().map(users::findByLoginName).flatMap(Optional::stream)
                .filter(user -> user.isActive() && !requesterId.equals(user.id()))
                .filter(user -> permissionsOf(user.id()).contains(operation.permission()))
                .filter(user -> resolver == null || resolved != null && resolver.reviewerCovers(user.id(), operation, resolved.toMap()))
                .map(UserAccountRepository.UserAccountRow::id).distinct().count();
        if (eligible < required) {
            throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "当前范围内的独立审批人数不足，该动作不可用");
        }
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
        List<ApprovalRow> rows = new ArrayList<>();
        Cursor scan = decoded;
        while (rows.size() <= size) {
            List<ApprovalRow> candidates = approvals.listVisible(session.userId(), reviewable,
                    scan.createdAt(), scan.id(), 100);
            if (candidates.isEmpty()) break;
            for (ApprovalRow candidate : candidates) {
                if (canView(candidate, session.userId())) rows.add(candidate);
                if (rows.size() > size) break;
            }
            if (rows.size() > size || candidates.size() < 100) break;
            ApprovalRow last = candidates.get(candidates.size() - 1);
            scan = new Cursor(last.createdAt(), last.id());
        }
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
        try {
            return doDecide(session, approvalId, decision, reason, currentPassword);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("decision", decision);
            failure(actorOf(session), approvalId, "approval.decide", "APPROVAL_DECISION_REJECTED", e, detail);
            throw e;
        }
    }

    /**
     * 提交决定（带 {@code Idempotency-Key}，M15）。
     *
     * <p>同 key + 同内容（审批、决定、理由、当前口令）→ 返回首次响应快照；
     * 同 key + 不同内容 → 409。不同 key 但同一审批人重复投票仍由领域规则拦（一票）。
     */
    @Transactional(noRollbackFor = ApprovalExpiredException.class)
    public Map<String, Object> decide(AuthenticatedSession session,
                                      UUID approvalId,
                                      String decision,
                                      String reason,
                                      String currentPassword,
                                      String idempotencyKey) {
        if (idempotencyKey == null || idempotency == null) {
            return decide(session, approvalId, decision, reason, currentPassword);
        }
        String key = IdempotencyKeyHeader.require(idempotencyKey);
        idempotency.lockKey(session.userId(), DECIDE_LEDGER_OPERATION, key);
        authorizeDecisionReplay(session, approvalId, currentPassword);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("operationId", DECIDE_LEDGER_OPERATION);
        identity.put("approvalId", approvalId == null ? null : approvalId.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        // currentPassword is a fresh authentication proof, not persisted business input.
        String digest = ledgerDigest(identity, body, reason);
        Optional<IdempotencyRequestRepository.LedgerRow> seen =
                idempotency.find(session.userId(), DECIDE_LEDGER_OPERATION, key);
        if (seen.isPresent()) {
            return replayAuthorized(session, approvalId, "approval.decide", seen.get(), digest);
        }
        Map<String, Object> result = decide(session, approvalId, decision, reason, currentPassword);
        idempotency.insertSucceeded(session.userId(), DECIDE_LEDGER_OPERATION, key, digest, 200,
                toLedgerJson(result));
        return result;
    }

    /**
     * 摘要：规范化 JSON（键递归排序）后的 SHA-256。
     *
     * <p>为什么不用客户端自报摘要：客户端可以自报一个"正确"的摘要而内容不同，
     * 于是同一个 key 能产生两套副作用；这里服务端按收到的内容自己算。
     */
    private static String ledgerDigest(Map<String, Object> identity,
                                       Map<String, Object> body,
                                       String text) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.putAll(identity);
        canonical.put("requestBody", body);
        canonical.put("reason", text);
        String json = TaskParameters.canonicalJson(canonical);
        return Sha256.hex(Sha256.ofBytes(json.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> replay(IdempotencyRequestRepository.LedgerRow row, String digest) {
        if (!digest.equals(row.requestDigest())) {
            log.info("审批幂等冲突：同一 Idempotency-Key 的内容不同");
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key 已被不同的请求使用");
        }
        if (row.snapshot() == null || row.snapshot().isBlank()) {
            throw new ApiException(ErrorCode.SYSTEM_INTERNAL, "幂等账本缺少首次响应，无法重放");
        }
        log.info("审批幂等重放 ledgerId={}", row.id());
        return ApprovalProposals.JsonMaps.parse(row.snapshot());
    }

    private static String toLedgerJson(Map<String, Object> value) {
        try {
            return LEDGER_MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("审批响应无法序列化进幂等账本", e);
        }
    }

    private Map<String, Object> doDecide(AuthenticatedSession session,
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
        // M09：有动作权限 ≠ 有审批资格。部署配置了审批人名单时，只有名单内的人能投票；
        // 名单外的人即使持有权限码、能看详情，也不能把自己的票算进去。
        requireConfiguredApprover(session);
        requireReviewScope(session.userId(), row);

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
        if (audit != null) {
            // 裁决是最需要留痕的动作：谁、批还是驳、当时累计几票。理由（reason）已在
            // action_approval_decision 里，正文摘要只放可检索的结构化字段。
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("operationId", row.operationId());
            detail.put("decision", normalizedDecision);
            detail.put("approveCount", approvals.countApprovals(row.id()));
            detail.put("requiredApprovals", row.requiredApprovals());
            audit.success("approval.decide", "APPROVAL", "ACTION_APPROVAL", row.id(), actorOf(session),
                    null, null, RequestIdFilter.currentTraceIdOrFallback(), detail);
        }
        return view(approvals.findById(row.id()).orElseThrow());
    }

    /**
     * 审计用的操作者快照。
     *
     * <p>为什么按 id 再查一次而不是直接传 {@code session}：{@code consume} 的签名只带
     * {@code executorUserId}（导出链路手里只有它），而审计表需要登录名/显示名快照。
     * 这次查询走主键、结果确定，代价可以接受。
     */
    private AuditService.Actor actorOf(UUID userId) {
        return users.findById(userId)
                .map(row -> new AuditService.Actor(row.id(), row.loginName(), row.displayName()))
                .orElseGet(AuditService.Actor::unknown);
    }

    private AuditService.Actor actorOf(AuthenticatedSession session) {
        if (session == null || session.user() == null) {
            return AuditService.Actor.unknown();
        }
        return new AuditService.Actor(session.userId(), session.user().loginName(),
                session.user().displayName());
    }

    /**
     * 消费一次审批（M13）：把 {@code APPROVED} 推进到 {@code CONSUMED} 并校验执行上下文。
     *
     * <p>这是"审批真的能被用掉"的唯一入口。为什么必须在同一事务里锁行：两个并发的执行请求
     * 若都读到 {@code APPROVED}，就会各自往下执行——审批变成了可重放的门票。{@code FOR UPDATE}
     * + {@code status='APPROVED'} 条件更新把并发收敛为"一个成功、另一个 409"。
     *
     * <p>校验项与顺序：
     * <ol>
     *   <li>状态必须是 {@code APPROVED}；已经是 {@code CONSUMED} 说明重放，回 409；</li>
     *   <li>执行许可未过期（{@code execution_grant_expires_at > now}）；</li>
     *   <li>{@code operation_id} 与调用方声明的一致——防止"拿 A 的批文执行 B"；</li>
     *   <li>执行人必须等于 {@code executor_user_id}；申请人不必是执行人；</li>
     *   <li>请求摘要必须等于冻结的 {@code payload_digest}——批的和跑的是同一份内容。</li>
     * </ol>
     *
     * <p>失败一律 409 {@code RESOURCE_CONFLICT}（除执行人不符是 403）：对调用方而言这些都是
     * "这个批文现在不能用"，而不是"参数写错了"。
     */
    @Transactional
    public void consume(UUID approvalId,
                        UUID executorUserId,
                        String operationId,
                        String requestDigest,
                        String traceId) {
        try {
            doConsume(approvalId, executorUserId, operationId, requestDigest, traceId);
        } catch (RuntimeException e) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("operationId", operationId);
            detail.put("requestDigest", requestDigest);
            failure(actorOf(executorUserId), approvalId, "approval.consume",
                    "APPROVAL_CONSUME_REJECTED", e, detail);
            throw e;
        }
    }

    private void doConsume(UUID approvalId,
                           UUID executorUserId,
                           String operationId,
                           String requestDigest,
                           String traceId) {
        if (approvalId == null) {
            throw fieldError("approvalId", "approvalId 必填");
        }
        ApprovalRow row = approvals.lockById(approvalId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批不存在或不可用"));
        if ("CONSUMED".equals(row.status())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批已被使用，不能重复执行");
        }
        if (!"APPROVED".equals(row.status())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批当前状态为 " + row.status() + "，不能执行");
        }
        OffsetDateTime now = Instants.nowUtc();
        if (row.executionGrantExpiresAt() == null || !row.executionGrantExpiresAt().isAfter(now)) {
            // 规则原文：审批许可过期 = 410（与 Context 过期同属"这段授权窗口已经不存在"）。
            // 409 会让调用方以为"重试一下就好"，而实际上必须重新申请。
            throw new ApiException(ErrorCode.CONTEXT_EXPIRED, "审批的执行许可已过期，请重新申请");
        }
        if (!java.util.Objects.equals(row.operationId(), operationId)) {
            // 用 A 的批文执行 B：这是最容易发生的越权路径，必须硬拒。
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "批文与操作不匹配");
        }
        if (!row.executorUserId().equals(executorUserId)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "你不是该审批指定的执行人");
        }
        if (!java.util.Objects.equals(row.payloadDigest(), requestDigest)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "请求内容与审批冻结的内容不一致");
        }
        ApprovalOperation operation = ApprovalOperation.parse(row.operationId()).orElse(null);
        if (operation != null) {
            // 执行前复核"当前"权限与目标：审批是 10 分钟前的快照，期间执行人可能被缩权，
            // 目标设备可能被删除/改版本。两者任一变化都必须拒绝，而不是照着旧快照执行。
            if (!permissionsOf(executorUserId).contains(operation.permission())) {
                throw new ApiException(ErrorCode.PERMISSION_DENIED, "执行人已不具备该动作权限");
            }
            if (resolver != null) {
                java.util.Set<UUID> actors = new java.util.TreeSet<>();
                actors.add(row.requesterUserId());
                actors.add(executorUserId);
                approvals.listDecisions(row.id()).forEach(v -> actors.add(v.reviewerUserId()));
                resolver.lockAuthorizationUsers(actors);
                if (!resolver.active(row.requesterUserId()) || !resolver.active(executorUserId)
                        || !permissionsOf(row.requesterUserId()).contains(operation.permission())) {
                    throw new ApiException(ErrorCode.PERMISSION_DENIED, "申请人或执行人当前已无操作资格");
                }
                Map<String, Object> facts = frozenFacts(row);
                if (!(facts.get("actorAuthorization") instanceof String)) {
                    throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "旧审批缺少完整冻结事实，请重新申请");
                }
                resolver.verifyUnchanged(operation, facts, row.requesterUserId());
                if (!row.requesterUserId().equals(executorUserId)) resolver.verifyUnchanged(operation, facts, executorUserId);
                long validVotes = approvals.listDecisions(row.id()).stream()
                        .filter(vote -> APPROVE.equals(vote.decision()))
                        .filter(vote -> currentReviewer(vote.reviewerUserId(), row))
                        .map(DecisionRow::reviewerUserId).distinct().count();
                if (validVotes < row.requiredApprovals()) {
                    throw new ApiException(ErrorCode.PERMISSION_DENIED, "原审批人的当前资格或范围已变化，请重新申请");
                }
            }
        }
        boolean updated = approvals.markConsumed(row.id(), now, row.version());
        if (!updated) {
            // 锁行之后仍失败只有一种解释：并发事务抢先提交了。对调用方就是 409。
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "审批已被使用，不能重复执行");
        }
        log.info("消费审批 approvalId={} operation={} executor={}", row.id(), operationId, executorUserId);
        if (audit != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("operationId", operationId);
            detail.put("requestDigest", requestDigest);
            audit.success("approval.consume", "APPROVAL", "ACTION_APPROVAL", row.id(),
                    actorOf(executorUserId), null, null,
                    traceId == null ? RequestIdFilter.currentTraceIdOrFallback() : traceId, detail);
        }
    }

    private ApprovalRow requireVisible(UUID approvalId, UUID viewerId) {
        ApprovalRow row = approvals.findById(approvalId)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "Approval not found"));
        if (!canView(row, viewerId)) throw new ApiException(ErrorCode.DATA_NOT_FOUND, "Approval not found");
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
        // M11：清单从冻结快照生成，客户端请求字段在前，服务端解析出的目标/版本/范围在后。
        List<ApprovalProposals.ReviewItem> items = ApprovalEnvelope.reviewItems(
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
        if (!isConfiguredApprover(userId)) {
            // 名单外的人没有复核资格；自己在别的操作上的权限码不能让他看到别人的待办。
            return List.of();
        }
        List<String> permissions = permissionsOf(userId);
        List<String> operations = new ArrayList<>();
        for (ApprovalOperation operation : ApprovalOperation.values()) {
            if (permissions.contains(operation.permission())) {
                operations.add(operation.operationId());
            }
        }
        return operations;
    }

    /** 部署是否配置了审批人名单，以及当前用户是否在名单内（M09）。 */
    private boolean isConfiguredApprover(UUID userId) {
        List<String> allowlist = properties.getApproverLogins();
        if (allowlist == null || allowlist.isEmpty()) {
            return false;
        }
        String loginName = users.findById(userId)
                .filter(UserAccountRepository.UserAccountRow::isActive)
                .map(UserAccountRepository.UserAccountRow::loginName)
                .orElse(null);
        return loginName != null && allowlist.contains(loginName);
    }

    private void requireConfiguredApprover(AuthenticatedSession session) {
        if (!isConfiguredApprover(session.userId())) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED,
                    "当前账号不在部署配置的审批人名单中，不能复核该申请");
        }
    }

    /**
     * 审批人自己的数据范围必须覆盖申请目标（M09"不能越过自身范围"）。
     *
     * <p>用冻结快照里的 siteIds 判断，而不是重新解析 client 目标：审批人批的就是这份快照。
     */
    private void requireReviewScope(UUID reviewerId, ApprovalRow row) {
        if (resolver == null) {
            return;
        }
        Map<String, Object> facts = frozenFacts(row);
        ApprovalOperation operation = ApprovalOperation.parse(row.operationId()).orElseThrow();
        if (!resolver.reviewerCovers(reviewerId, operation, facts)) {
            throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE,
                    "审批目标不在你的当前数据范围内，不能复核");
        }
    }

    private void authorizeCreate(AuthenticatedSession session, Map<String, Object> raw) {
        try {
        ApprovalProposals.Proposal proposal = ApprovalProposals.parse(raw);
        if (!permissionsOf(session.userId()).contains(proposal.operation().permission()))
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "当前已无该操作权限");
        if (resolver != null) resolver.freeze(proposal.operation(), proposal.pathParameters(), proposal.requestBody(),
                resolver.scopeOf(session.userId()), raw.get("ifMatch"), session.userId());
        } catch (RuntimeException rejected) {
            failure(actorOf(session), null, "approval.create", "APPROVAL_REJECTED", rejected, Map.of());
            throw rejected;
        }
    }

    private void authorizeDecisionReplay(AuthenticatedSession session, UUID approvalId, String password) {
        try {
            ApprovalRow row = requireVisible(approvalId, session.userId());
            requireCurrentReviewer(session.userId(), row);
            requirePassword(password);
            requirePasswordMatches(session.userId(), password);
        } catch (RuntimeException rejected) {
            failure(actorOf(session), approvalId, "approval.decide", "APPROVAL_DECISION_REJECTED", rejected, Map.of());
            throw rejected;
        }
    }

    private Map<String, Object> replayAuthorized(AuthenticatedSession session, UUID approvalId, String action,
                                                 IdempotencyRequestRepository.LedgerRow ledger, String digest) {
        try { return replay(ledger, digest); }
        catch (RuntimeException rejected) {
            failure(actorOf(session), approvalId, action, "APPROVAL_REPLAY_REJECTED", rejected, Map.of());
            throw rejected;
        }
    }

    private boolean currentReviewer(UUID reviewer, ApprovalRow row) {
        if (reviewer.equals(row.requesterUserId()) || reviewer.equals(row.executorUserId())) return false;
        ApprovalOperation operation = ApprovalOperation.parse(row.operationId()).orElseThrow();
        return isConfiguredApprover(reviewer) && permissionsOf(reviewer).contains(operation.permission())
                && (resolver == null || resolver.reviewerCovers(reviewer, operation, frozenFacts(row)));
    }

    private void requireCurrentReviewer(UUID reviewer, ApprovalRow row) {
        if (!currentReviewer(reviewer, row)) throw new ApiException(ErrorCode.PERMISSION_DENIED, "当前无审批资格或目标范围已变化");
    }

    private boolean canView(ApprovalRow row, UUID viewer) {
        ApprovalOperation operation = ApprovalOperation.parse(row.operationId()).orElseThrow();
        if (!permissionsOf(viewer).contains(operation.permission())) return false;
        if (row.requesterUserId().equals(viewer) || row.executorUserId().equals(viewer))
            return resolver == null || resolver.viewerCovers(viewer, operation, frozenFacts(row));
        return currentReviewer(viewer, row);
    }

    private Map<String, Object> frozenFacts(ApprovalRow row) {
        String plaintext = new String(cipher.open(row.ciphertext()), StandardCharsets.UTF_8);
        return ApprovalEnvelope.decode(plaintext).frozen();
    }

    private static java.util.Set<UUID> siteIds(Map<String, Object> facts) {
        Object raw = facts.get("siteIds");
        if (!(raw instanceof List<?> list)) {
            return java.util.Set.of();
        }
        java.util.Set<UUID> sites = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            sites.add(UUID.fromString(String.valueOf(item)));
        }
        return sites;
    }

    /**
     * 写一条拒绝/失败审计（M20）。{@link com.xxplatform.api.service.FailureAudit} 用独立事务
     * 提交，所以外层业务回滚不会把"这次被拒了"一起抹掉。
     */
    private void failure(AuditService.Actor actor,
                         UUID approvalId,
                         String action,
                         String failureCode,
                         RuntimeException error,
                         Map<String, Object> detail) {
        if (failureAudit == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(detail);
        payload.put("errorCode", error instanceof ApiException api ? api.code().name() : "SYSTEM_INTERNAL");
        payload.put("error", error.getMessage());
        failureAudit.failure(action, "APPROVAL", "ACTION_APPROVAL", approvalId, actor, null,
                RequestIdFilter.currentTraceIdOrFallback(), failureCode, payload);
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
