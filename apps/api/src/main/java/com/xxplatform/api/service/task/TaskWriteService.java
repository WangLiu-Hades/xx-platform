package com.xxplatform.api.service.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.IdempotencyRequestRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务的写操作：取消与重试（{@code POST /tasks/{taskId}/cancel}、
 * {@code POST /tasks/{taskId}/retry}，W2 任务 8）。
 *
 * <p><b>权限是 {@code task:manage}</b>：契约给这两个操作标了该权限码。取消会停掉别人的执行、
 * 重试会再排一次队，两者都能改变任务对外的结果。
 *
 * <p><b>幂等（M15）</b>：带 {@code Idempotency-Key} 时把"作用域 = 账号 + operationId + 键"
 * 落进既有表 {@code core.idempotency_request}：首跑先插 PROCESSING 行抢坑（唯一约束
 * {@code (user_id, operation_id, idempotency_key)} 做互斥），执行成功后写 SUCCEEDED + 202 + 响应快照；
 * 同键同内容直接回放快照，同键不同内容 409 {@code IDEMPOTENCY_CONFLICT}。
 * 失败时整个事务回滚，PROCESSING 行一并消失，同键可以重试。
 */
@Service
public class TaskWriteService {

    /** 契约 {@code x-permission}。 */
    public static final String PERMISSION = "task:manage";

    /** 幂等作用域里的 operationId。 */
    public static final String OPERATION_CANCEL = "task:cancel";
    public static final String OPERATION_RETRY = "task:retry";

    private final RbacRepository rbac;
    private final TaskService tasks;
    private final TaskViewMapper mapper;

    /**
     * 请求级幂等账本（M15）。setter 注入：单测直接 new 三个参数，不被强迫构造 JdbcTemplate；
     * 缺失时退化为"只做状态机"，生产环境由 Spring 注入。
     */
    private IdempotencyRequestRepository ledger;

    /** 快照序列化；缺失时用 {@code new ObjectMapper()} 兜底（单测场景）。 */
    private ObjectMapper objectMapper = new ObjectMapper();

    public TaskWriteService(RbacRepository rbac, TaskService tasks, TaskViewMapper mapper) {
        this.rbac = rbac;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLedger(IdempotencyRequestRepository ledger) {
        this.ledger = ledger;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setObjectMapper(ObjectMapper objectMapper) {
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
        }
    }

    /** 取消；返回取消后的任务行（运行中时 {@code cancelRequestedAt} 已写、状态仍是 RUNNING）。 */
    public Map<String, Object> cancel(AuthenticatedSession session, UUID taskId) {
        return cancel(session, taskId, null);
    }

    /**
     * 取消（带 {@code Idempotency-Key}）。
     *
     * <p>权限复核在进幂等账本之前：契约要求"所有幂等回放仍重校权限"，缩权后的重放拿到 403
     * 而不是旧响应。
     */
    @Transactional
    public Map<String, Object> cancel(AuthenticatedSession session, UUID taskId, String idempotencyKey) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        return idempotent(session.userId(), OPERATION_CANCEL, taskId, idempotencyKey,
                () -> mapper.view(tasks.cancel(taskId, session.userId()).task()));
    }

    /** 重试失败任务；返回重新排队后的行（{@code retryCount} 已加一，{@code attemptNo} 待下次领取递增）。 */
    public Map<String, Object> retry(AuthenticatedSession session, UUID taskId) {
        return retry(session, taskId, null);
    }

    /** 重试（带 {@code Idempotency-Key}）；权限与回放语义同 {@link #cancel(AuthenticatedSession, UUID, String)}。 */
    @Transactional
    public Map<String, Object> retry(AuthenticatedSession session, UUID taskId, String idempotencyKey) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        requirePermission(session.userId());
        return idempotent(session.userId(), OPERATION_RETRY, taskId, idempotencyKey,
                () -> mapper.view(tasks.retry(taskId, session.userId())));
    }

    /**
     * 幂等执行主体。
     *
     * <p>顺序是：抢坑 → 执行 → 落账本。抢坑用 {@code ON CONFLICT DO NOTHING} 插入 PROCESSING 行，
     * 由唯一约束做互斥，赢的一方执行副作用，输的一方读已有账本行：内容相同回放首次响应，
     * 内容不同 409。失败时事务回滚，PROCESSING 行一并消失。
     */
    private Map<String, Object> idempotent(UUID userId, String operationId, UUID taskId,
            String idempotencyKey, java.util.function.Supplier<Map<String, Object>> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || ledger == null) {
            return action.get();
        }
        String digest = digest(operationId + "|" + taskId);
        UUID ledgerId = UUID.randomUUID();
        boolean claimed = ledger.insertProcessing(ledgerId, userId, operationId, idempotencyKey, digest,
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusHours(24));
        if (!claimed) {
            IdempotencyRequestRepository.LedgerRow existing =
                    ledger.find(userId, operationId, idempotencyKey)
                            .orElseThrow(() -> new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                                    "同一 Idempotency-Key 正在处理中，本次请求未重复执行"));
            return replay(existing, digest);
        }
        Map<String, Object> response = action.get();
        ledger.markSucceeded(ledgerId, 202, "v1", snapshotOf(response));
        return response;
    }

    private Map<String, Object> replay(IdempotencyRequestRepository.LedgerRow row, String digest) {
        if (!row.requestDigest().equals(digest)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 对应了不同的任务或操作");
        }
        String snapshot = row.snapshot();
        if (snapshot == null || snapshot.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshot, new TypeReference<Map<String, Object>>() { });
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("幂等响应快照无法解析", failure);
        }
    }

    private String snapshotOf(Map<String, Object> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("幂等响应快照无法序列化", failure);
        }
    }

    private static String digest(String canonical) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM 缺少 SHA-256", impossible);
        }
    }

    private void requirePermission(UUID userId) {
        if (!rbac.permissionCodes(userId).contains(PERMISSION)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "无权操作任务（缺少 " + PERMISSION + "）");
        }
    }
}
