package com.xxplatform.api.service.task;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.ExpiredLease;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务底座（B-05）的用例层：创建（幂等）、领取、续租、完成、取消、重试、收回过期租约。
 *
 * <p><b>为什么"幂等"落在任务表而不是 {@code core.idempotency_request}</b>：HTTP 请求级幂等要记录
 * "这个请求回放什么响应"，而任务级幂等要回答"同一个业务意图是否已经在跑"。后者天然是
 * {@code (owner_user_id, task_type, idempotency_key)} 上的唯一约束——重复提交直接撞键，
 * 不需要额外的响应快照表，也不会出现"响应存了但任务没建"的两处不一致。
 *
 * <p><b>为什么状态机校验在 Java 里、迁移守卫在数据库里各来一遍</b>：数据库触发器保证任何写入
 * （包括手工运维 SQL）都不会产生非法状态；Java 侧校验让调用方拿到 409 {@code TASK_CONFLICT}
 * 而不是 {@code SQLException: 23514}。两处规则必须同步，所以 {@link TaskStateMachine} 的
 * {@code ALLOWED} 表与迁移里的触发器按同一份列表维护。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /** 任务被回收时写入的错误码：与 {@code ErrorCode} 不同层，它是任务自己的失败原因。 */
    public static final String LEASE_EXPIRED = "TASK_LEASE_EXPIRED";
    public static final String ATTEMPTS_EXHAUSTED = "TASK_ATTEMPTS_EXHAUSTED";

    private final AsyncTaskRepository repository;
    private final TaskProperties properties;

    public TaskService(AsyncTaskRepository repository, TaskProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 创建任务；同一 {@code (owner, taskType, idempotencyKey)} 重复提交返回**既有**任务。
     *
     * <p>参数不同则 409 {@code IDEMPOTENCY_CONFLICT}：幂等键承诺的是"同一个请求重放无害"，
     * 同一个键配不同参数说明调用方把键当成了普通字段，继续执行会让"重试"变成"改需求"。
     *
     * <p>为什么还要捕获 {@link DuplicateKeyException}：两个并发请求可能同时查不到、同时插入，
     * 唯一约束让其中一个失败。此时回读赢家的行，按同样规则判断——这正是唯一约束存在的意义，
     * 先查后插只是快路径。
     */
    @Transactional
    public TaskRow create(TaskCommand command) {
        Optional<TaskRow> existing = repository.findByIdempotencyKey(
                command.ownerUserId(), command.taskType(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        OffsetDateTime now = Instants.nowUtc();
        UUID id = UUID.randomUUID();
        try {
            repository.insert(id, command, now);
        } catch (DuplicateKeyException conflict) {
            Optional<TaskRow> winner = repository.findByIdempotencyKey(
                    command.ownerUserId(), command.taskType(), command.idempotencyKey());
            if (winner.isEmpty()) {
                throw conflict;
            }
            return replay(winner.get(), command);
        }
        repository.insertEvent(UUID.randomUUID(), id, 0, null, TaskStatus.PENDING, now,
                "任务已创建", command.traceId());
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("任务创建后不可见: " + id));
    }

    /** 读单个任务；不是自己的任务按"不存在"处理，避免用 404/403 的差异探测他人任务是否存在。 */
    public TaskRow get(UUID taskId, UUID ownerUserId) {
        TaskRow row = repository.findById(taskId)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "任务不存在"));
        if (!row.ownerUserId().equals(ownerUserId)) {
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "任务不存在");
        }
        return row;
    }

    /**
     * 领取下一条待执行任务，并同步写下 attempt 与事件行。
     *
     * <p>三件事必须在同一事务里：任务被置 {@code RUNNING}、attempt 行出现、事件行出现。
     * 否则进程在中间崩溃会留下"在跑但没有 attempt"的任务，重试计数与审计都会缺一块。
     *
     * <p>为什么先 {@code promoteNext}：数据库守卫只认 {@code PENDING -> QUEUED -> RUNNING}，
     * 直接置 {@code RUNNING} 会被拒绝；而"提升"本身就是一次可审计的状态迁移，所以单独记事件。
     * {@code PENDING} 保留给"已受理、尚未排期"的语义（比如需要等人审或等外部资源的任务）。
     */
    @Transactional
    public Optional<ClaimedTask> claimNext(String workerId) {
        OffsetDateTime now = Instants.nowUtc();
        repository.promoteNext(now).ifPresent(taskId -> {
            TaskRow promoted = repository.findById(taskId)
                    .orElseThrow(() -> new IllegalStateException("提升后读不到任务: " + taskId));
            repository.insertEvent(UUID.randomUUID(), taskId, promoted.currentAttemptNo(),
                    TaskStatus.PENDING, TaskStatus.QUEUED, now, "系统入队", promoted.traceId());
        });

        Optional<ClaimedTask> claimed = repository.claimNext(workerId, now, properties.getLease());
        claimed.ifPresent(c -> {
            TaskRow task = c.task();
            repository.insertAttempt(task.id(), task.currentAttemptNo(), now, workerId, task.leaseGeneration());
            repository.insertEvent(UUID.randomUUID(), task.id(), task.currentAttemptNo(),
                    c.previousStatus(), TaskStatus.RUNNING, now,
                    "worker " + workerId + " 领取任务", task.traceId());
            log.info("任务已领取 taskId={} attempt={} worker={}", task.id(), task.currentAttemptNo(), workerId);
        });
        return claimed;
    }

    /** 续租；{@code false} 表示租约已不属于该 worker（被收回、代际变化或已请求取消）。 */
    public boolean renew(UUID taskId, String workerId, long generation) {
        OffsetDateTime leaseUntil = TaskLease.deadline(Instants.nowUtc(), properties.getLease());
        return repository.renewLease(taskId, workerId, generation, leaseUntil) > 0;
    }

    /**
     * 回报终态。租约栅栏不匹配时抛 409 而不是静默成功：结果没被采纳是一件必须让调用方知道的事，
     * 否则 worker 会以为任务已完成，而库里那条任务其实已经重新排队。
     */
    @Transactional
    public TaskRow finish(UUID taskId, String workerId, long generation, TaskStatus outcome,
                          String errorCode, String errorMessage, UUID resultAssetId) {
        TaskStateMachine.require(TaskStatus.RUNNING, outcome);
        OffsetDateTime now = Instants.nowUtc();
        int updated = repository.finish(taskId, workerId, generation, outcome,
                errorCode, errorMessage, resultAssetId, now);
        if (updated == 0) {
            throw new ApiException(ErrorCode.TASK_CONFLICT, "任务租约已失效，本次结果未被采纳");
        }
        TaskRow task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("任务完成但读不到: " + taskId));
        repository.closeAttempt(taskId, task.currentAttemptNo(), now, outcome, errorCode, errorMessage);
        repository.insertEvent(UUID.randomUUID(), taskId, task.currentAttemptNo(),
                TaskStatus.RUNNING, outcome, now, "worker 回报 " + outcome, task.traceId());
        return task;
    }

    /**
     * 取消任务，返回"是否已立即取消"。
     *
     * <p>三个分支对应契约：{@code PENDING}/{@code QUEUED} 直接 {@code CANCELLED}（同步完成，202 也可）；
     * {@code RUNNING} 只写 {@code cancel_requested_at}，返回 {@code false}——真正的收尾由 worker
     * 或 reaper 做，因为已经发出的副作用无法撤回；终态抛 409 {@code TASK_NOT_CANCELLABLE}。
     * 已取消的任务重复取消返回既有行（幂等），不报错。
     */
    @Transactional
    public CancelOutcome cancel(UUID taskId, UUID ownerUserId) {
        TaskRow task = get(taskId, ownerUserId);
        OffsetDateTime now = Instants.nowUtc();
        switch (task.status()) {
            case SUCCESS, FAILED -> throw new ApiException(ErrorCode.TASK_NOT_CANCELLABLE,
                    "任务已进入终态，不能取消");
            case CANCELLED -> {
                return new CancelOutcome(task, true);
            }
            case RUNNING -> {
                if (repository.requestCancel(taskId, now) == 0) {
                    throw new ApiException(ErrorCode.TASK_CONFLICT, "任务状态已变化，请刷新后重试");
                }
                repository.insertEvent(UUID.randomUUID(), taskId, task.currentAttemptNo(),
                        TaskStatus.RUNNING, TaskStatus.RUNNING, now, "已请求取消", task.traceId());
                return new CancelOutcome(repository.findById(taskId).orElse(task), false);
            }
            default -> {
                if (repository.cancelQueued(taskId, now) == 0) {
                    throw new ApiException(ErrorCode.TASK_CONFLICT, "任务状态已变化，请刷新后重试");
                }
                repository.insertEvent(UUID.randomUUID(), taskId, task.currentAttemptNo(),
                        task.status(), TaskStatus.CANCELLED, now, "排队中取消", task.traceId());
                return new CancelOutcome(repository.findById(taskId).orElse(task), true);
            }
        }
    }

    /** 重试失败任务：同 taskId 重新排队，下一次领取时 {@code attemptNo} 自增。 */
    @Transactional
    public TaskRow retry(UUID taskId, UUID ownerUserId) {
        TaskRow task = get(taskId, ownerUserId);
        if (task.status() != TaskStatus.FAILED) {
            throw new ApiException(ErrorCode.TASK_CONFLICT, "只有失败的任务可以重试");
        }
        OffsetDateTime now = Instants.nowUtc();
        if (repository.requeueForRetry(taskId, now) == 0) {
            throw new ApiException(ErrorCode.TASK_CONFLICT, "任务状态已变化，请刷新后重试");
        }
        repository.insertEvent(UUID.randomUUID(), taskId, task.currentAttemptNo(),
                TaskStatus.FAILED, TaskStatus.QUEUED, now, "重试", task.traceId());
        return repository.findById(taskId).orElse(task);
    }

    /**
     * 收回过期租约（reaper 每轮调用）。
     *
     * <p>"尝试次数用尽"在这里判定而不是在 SQL 里：阈值来自配置，不该在迁移里再存一份。
     * 收回到 {@code QUEUED} 后再改判 {@code FAILED}，走的是触发器允许的
     * {@code RUNNING -> QUEUED -> FAILED} 两跳，而不是伪造一个 {@code RUNNING -> FAILED} 之外的路径。
     *
     * @return 本轮收回的任务数
     */
    @Transactional
    public int reapExpiredLeases(OffsetDateTime now) {
        OffsetDateTime nextRunAt = now.plus(properties.getRetryDelay());
        List<ExpiredLease> expired = repository.expireLeases(now, nextRunAt, properties.getReaperBatchSize());
        for (ExpiredLease lease : expired) {
            TaskStatus result = lease.status() == TaskStatus.CANCELLED ? TaskStatus.CANCELLED : TaskStatus.FAILED;
            repository.closeAttempt(lease.taskId(), lease.attemptNo(), now, result,
                    LEASE_EXPIRED, "租约到期未完成，任务被收回");
            repository.insertEvent(UUID.randomUUID(), lease.taskId(), lease.attemptNo(),
                    TaskStatus.RUNNING, lease.status(), now, "租约到期，reaper 收回",
                    traceIdOf(lease.taskId()));

            if (lease.status() == TaskStatus.QUEUED && lease.attemptNo() >= properties.getMaxAttempts()) {
                repository.failExhausted(lease.taskId(), now, ATTEMPTS_EXHAUSTED, "尝试次数用尽");
                repository.insertEvent(UUID.randomUUID(), lease.taskId(), lease.attemptNo(),
                        TaskStatus.QUEUED, TaskStatus.FAILED, now, "尝试次数用尽", traceIdOf(lease.taskId()));
            }
        }
        if (!expired.isEmpty()) {
            log.info("reaper 收回过期租约 {} 条", expired.size());
        }
        return expired.size();
    }

    private String traceIdOf(UUID taskId) {
        return repository.findById(taskId).map(TaskRow::traceId).orElse("unknown-trace");
    }

    private TaskRow replay(TaskRow existing, TaskCommand command) {
        if (!existing.parameterHash().equals(command.parameterHash())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "同一幂等键提交了不同的请求参数");
        }
        log.debug("幂等重放 taskId={} key={}", existing.id(), command.idempotencyKey());
        return existing;
    }

    /** 取消结果：行 + 是否已立即取消（{@code RUNNING} 的取消只是"已登记"）。 */
    public record CancelOutcome(TaskRow task, boolean immediate) {
    }
}
