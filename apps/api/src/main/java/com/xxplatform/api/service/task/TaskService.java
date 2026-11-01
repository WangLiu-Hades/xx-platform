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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务底座（B-05）的用例层：创建（幂等）、领取、续租、完成、取消、重试、收回过期租约。
 *
 * <p><b>为什么"创建"的幂等落在任务表而不是 {@code core.idempotency_request}</b>：HTTP 请求级幂等要
 * 记录"这个请求回放什么响应"，而任务级幂等要回答"同一个业务意图是否已经在跑"。后者天然是
 * {@code (owner_user_id, task_type, idempotency_key)} 上的唯一约束——重复提交直接撞键，
 * 不需要额外的响应快照表，也不会出现"响应存了但任务没建"的两处不一致。**取消/重试**不同：
 * 它们有副作用但没有天然唯一键（同一任务可以被取消、重试多次），所以那两条路径走
 * {@code core.idempotency_request} 账本（见 {@code TaskWriteService} 的 {@code execute}）。
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

    /**
     * 一次 {@code claimNext} 里最多做多少轮“提升 → 领取”。
     *
     * <p>队列里可能排着本 worker 不支持类型的 PENDING 任务：每轮只提升一条，不循环的话
     * supportedTypes 里的任务会一直停在 PENDING（M06 要求 worker 只领 supportedTypes）。
     * 32 轮足以越过常见的类型混杂，同时避免一条 claim 调用里做无界工作。
     */
    private static final int MAX_PROMOTION_ROUNDS = 32;

    /**
     * 副作用任务的租约到期、结果未知（M07）。
     *
     * <p>为什么单独一个码：{@code TASK_LEASE_EXPIRED} 的语义是"没跑完、已重排"，而这里的
     * 语义是"可能已经跑了一部分外部动作、既不能重试也不能当成功"——两者混用会让对账人员
     * 按错误的剧本处理（重试 vs 人工确认）。
     */
    public static final String OUTCOME_UNKNOWN = "TASK_OUTCOME_UNKNOWN";

    private final AsyncTaskRepository repository;
    private final TaskProperties properties;

    /**
     * 发布/提交时的当前授权复核（P-002-05 / M05 / M14）。
     *
     * <p>为什么用 setter 注入而不是构造器：这个类有大量单测直接 {@code new TaskService(repo, props)}，
     * 构造器加参数会把"与权限无关的状态机单测"全部带上依赖。缺失时退化为"只做任务本地校验"，
     * 生产环境由 Spring 注入，不存在缺失。
     */
    private TaskScopeGuard scopeGuard;

    public TaskService(AsyncTaskRepository repository, TaskProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setScopeGuard(TaskScopeGuard scopeGuard) {
        this.scopeGuard = scopeGuard;
    }

    /**
     * 创建任务；同一 {@code (owner, taskType, idempotencyKey)} 重复提交返回**既有**任务。
     *
     * <p>参数不同则 409 {@code IDEMPOTENCY_CONFLICT}：幂等键承诺的是"同一个请求重放无害"，
     * 同一个键配不同参数说明调用方把键当成了普通字段，继续执行会让"重试"变成"改需求"。
     *
     * <p>为什么并发冲突靠"插入影响 0 行"而不是捕获唯一键异常：仓储的 INSERT 带
     * {@code ON CONFLICT DO NOTHING}（见 {@code AsyncTaskRepository} 的 {@code INSERT}）。
     * PostgreSQL 一旦把约束错误抛出，当前事务就进入 aborted，后面连回读赢家都会报
     * {@code 25P02}；所以冲突必须表现为"0 行"，再按同一作用域回读——这正是唯一约束
     * 存在的意义，先查后插只是快路径。
     */
    @Transactional
    public TaskRow create(TaskCommand command) {
        OffsetDateTime now = Instants.nowUtc();
        // 提交复核（M14）：账号可用 + 该任务类型仍在授权内 + Context 未过期/未收缩。
        // 放在幂等快路径之前，否则"重放一个已失效 Context 的旧请求"会绕过复核。
        requireSubmission(command, now);
        Optional<TaskRow> existing = repository.findByIdempotencyKey(
                command.ownerUserId(), command.taskType(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command);
        }

        enforceAdmission(command.ownerUserId());
        UUID id = UUID.randomUUID();
        if (repository.insert(id, command, now) == 0) {
            // 0 行 = 并发请求先插入成功：回读赢家，再按同一份参数指纹决定"重放"还是"冲突"。
            // 读不到说明赢家事务未提交或已被清理；此时不能假装成功，让调用方重试。
            TaskRow winner = repository.findByIdempotencyKey(
                            command.ownerUserId(), command.taskType(), command.idempotencyKey())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_CONFLICT,
                            "幂等键已被占用，但读取既有任务失败，请重试"));
            return replay(winner, command);
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
     * 领取一条待执行任务（兼容入口：不限制类型、只领 1 条）。
     *
     * <p>保留它是为了让既有 worker/集成测试不必都改成批量调用；语义与
     * {@link #claimNext(String, List, int)} 在 limit=1、无类型过滤时完全一致。
     */
    @Transactional
    public Optional<ClaimedTask> claimNext(String workerId) {
        return claimNext(workerId, List.of(), 1).stream().findFirst();
    }

    /**
     * 按 worker 能力领取任务：只领 {@code supportedTypes} 里声明过的类型，本轮最多 {@code limit} 条。
     *
     * <p>三件事必须在同一事务里：任务被置 {@code RUNNING}、attempt 行出现、事件行出现。
     * 否则进程在中间崩溃会留下"在跑但没有 attempt"的任务，重试计数与审计都会缺一块。
     *
     * <p>为什么先 {@code promoteNext}：数据库守卫只认 {@code PENDING -> QUEUED -> RUNNING}，
     * 直接置 {@code RUNNING} 会被拒绝；而"提升"本身就是一次可审计的状态迁移，所以单独记事件。
     * {@code PENDING} 保留给"已受理、尚未排期"的语义（比如需要等人审或等外部资源的任务）。
     *
     * <p>为什么类型过滤放在仓储的领取 SQL 里而不是这里先查再筛：先查会把"支持的类型"
     * 变成一次可能读到已过期候选的快照，且筛掉的行仍会被其他 worker 抢；下推到
     * {@code WHERE task_type = ANY (...)} 后，过滤与领取在同一条语句里完成。
     *
     * @param supportedTypes 支持的任务类型；空列表 = 不限制
     * @param limit          本轮最多领取条数，必须为正
     * @return 本轮领到的任务（0..limit 条）
     */
    @Transactional
    public List<ClaimedTask> claimNext(String workerId, List<TaskType> supportedTypes, int limit) {
        OffsetDateTime now = Instants.nowUtc();
        // 队列里可能排着本 worker 不支持类型的 PENDING 任务。只提升一条再领一次，会让
        // supportedTypes 里的任务一直停在 PENDING（M06 要求“worker 只领 supportedTypes 且不被饿死”）。
        // 因此循环“提升 → 领取”，直到领到东西或没有可提升的行。
        List<ClaimedTask> claimed = List.of();
        for (int round = 0; round < MAX_PROMOTION_ROUNDS; round++) {
            Optional<UUID> promoted = repository.promoteNext(now);
            promoted.ifPresent(taskId -> recordPromotion(taskId, now));
            claimed = repository.claimNext(
                    workerId, now, properties.getLease(), supportedTypes, limit);
            if (!claimed.isEmpty() || promoted.isEmpty()) {
                break;
            }
        }
        for (ClaimedTask c : claimed) {
            TaskRow task = c.task();
            repository.insertAttempt(task.id(), task.currentAttemptNo(), now, workerId, task.leaseGeneration());
            repository.insertEvent(UUID.randomUUID(), task.id(), task.currentAttemptNo(),
                    c.previousStatus(), TaskStatus.RUNNING, now,
                    "worker " + workerId + " 领取任务", task.traceId());
            log.info("任务已领取 taskId={} attempt={} worker={}", task.id(), task.currentAttemptNo(), workerId);
        }
        return claimed;
    }

    /** 记录一次 PENDING → QUEUED 的入队事件。 */
    private void recordPromotion(UUID taskId, OffsetDateTime now) {
        TaskRow promoted = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("提升后读不到任务: " + taskId));
        repository.insertEvent(UUID.randomUUID(), taskId, promoted.currentAttemptNo(),
                TaskStatus.PENDING, TaskStatus.QUEUED, now, "系统入队", promoted.traceId());
    }

    /**
     * 续租；{@code false} 表示租约已不属于该 worker（被收回、代际变化、已请求取消或已过期）。
     *
     * <p>返回布尔而不是抛 409：续租失败是 worker 主循环里的正常分支（该放手了），不是
     * 业务冲突；把它当异常会让"租约被收回"这种预期内的竞态变成需要 try/catch 的控制流。
     */
    public boolean renew(UUID taskId, String workerId, long generation) {
        OffsetDateTime now = Instants.nowUtc();
        OffsetDateTime leaseUntil = TaskLease.deadline(now, properties.getLease());
        TaskRow task = repository.findById(taskId).orElse(null);
        if (task != null && task.taskType() == TaskType.DEVICE_COMMAND && task.startedAt() != null) {
            // P-018-02：设备命令 TTL 120 秒，从实际开始执行算起。租约不能越过 TTL，
            // 否则 worker 能在"命令早已超时"之后继续续租并发布 SUCCESS——超时任务必须走
            // UNKNOWN + 读回/对账，而不是被续租续成"正常完成"。
            OffsetDateTime ttlDeadline = task.startedAt().plus(TaskProperties.COMMAND_TTL);
            if (ttlDeadline.isBefore(leaseUntil)) {
                leaseUntil = ttlDeadline;
            }
            if (!leaseUntil.isAfter(now)) {
                // TTL 已过：租约不可能再被延长到未来，直接告诉 worker "放手"，别让它以为
                // 续租成功、继续跑一条早该按 UNKNOWN 收尾的命令。
                return false;
            }
        }
        return repository.renewLease(taskId, workerId, generation, now, leaseUntil) > 0;
    }

    /**
     * 回报终态。租约栅栏不匹配时抛 409 而不是静默成功：结果没被采纳是一件必须让调用方知道的事，
     * 否则 worker 会以为任务已完成，而库里那条任务其实已经重新排队。
     *
     * <p>{@code 0} 行命中现在有三种原因（M05）：代际/owner 不匹配、已请求取消、租约已过期。
     * 三者都必须拒绝——取消后的成功结果落库会造成"用户以为已取消、任务却显示 SUCCESS"，
     * 过期租约的结果则可能与新持有者的结果互相覆盖。
     *
     * <p><b>P-002-05 已接线</b>（M05/M14）：SUCCESS 发布前先做当前授权/数据范围/结果引用复核
     * （{@link TaskScopeGuard#publishBlocker}），不通过时把本次尝试改判 FAILED 而不是公开结果；
     * 详见 {@link #rejectPublish}。
     */
    @Transactional
    public TaskRow finish(UUID taskId, String workerId, long generation, TaskStatus outcome,
                          String errorCode, String errorMessage, UUID resultAssetId) {
        TaskStateMachine.require(TaskStatus.RUNNING, outcome);
        OffsetDateTime now = Instants.nowUtc();
        if (outcome == TaskStatus.SUCCESS && scopeGuard != null) {
            TaskRow running = repository.findById(taskId)
                    .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "任务不存在"));
            String blocker = scopeGuard.publishBlocker(running, resultAssetId, now);
            if (blocker != null) {
                return rejectPublish(taskId, workerId, generation, blocker, now);
            }
        }
        int updated = repository.finish(taskId, workerId, generation, outcome,
                errorCode, errorMessage, resultAssetId, now);
        if (updated == 0) {
            throw new ApiException(ErrorCode.TASK_CONFLICT, "任务已取消或租约已失效，本次结果未被采纳");
        }
        TaskRow task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("任务完成但读不到: " + taskId));
        repository.closeAttempt(taskId, task.currentAttemptNo(), now, outcome, errorCode, errorMessage);
        repository.insertEvent(UUID.randomUUID(), taskId, task.currentAttemptNo(),
                TaskStatus.RUNNING, outcome, now, "worker 回报 " + outcome, task.traceId());
        return task;
    }

    /**
     * 发布复核未通过：把本次尝试改判 {@code FAILED}，绝不落成 SUCCESS。
     *
     * <p>为什么"改判"而不是抛 403 让事务回滚：回滚会把任务留在 {@code RUNNING} + 有效租约，
     * worker 会拿着同一份越权结果再调一次 {@code finish}，形成"每 10 秒被拒一次"的活锁；
     * 而且事务回滚意味着这条任务永远没有终态，reaper 只知道"租约过期"，丢失"为什么失败"。
     */
    private TaskRow rejectPublish(UUID taskId, String workerId, long generation, String blocker,
                                  OffsetDateTime now) {
        String message = scopeGuard.describe(blocker);
        int updated = repository.finish(taskId, workerId, generation, TaskStatus.FAILED,
                blocker, message, null, now);
        if (updated == 0) {
            throw new ApiException(ErrorCode.TASK_CONFLICT, "任务已取消或租约已失效，本次结果未被采纳");
        }
        TaskRow failed = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("任务改判后读不到: " + taskId));
        repository.closeAttempt(taskId, failed.currentAttemptNo(), now, TaskStatus.FAILED, blocker, message);
        repository.insertEvent(UUID.randomUUID(), taskId, failed.currentAttemptNo(),
                TaskStatus.RUNNING, TaskStatus.FAILED, now, message, failed.traceId());
        log.warn("发布复核未通过，结果不公开 taskId={} blocker={}", taskId, blocker);
        return failed;
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
        if (OUTCOME_UNKNOWN.equals(task.errorCode())) {
            // M07：外部动作处于 UNKNOWN 时只能先对账（或走 RESET 且 RESET 不自动重试），
            // 直接重试等于把"可能已经发生过的副作用"再发一遍。
            throw new ApiException(ErrorCode.TASK_CONFLICT,
                    "外部动作结果未知（TASK_OUTCOME_UNKNOWN），必须先对账，不能直接重试");
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
     * <p><b>副作用任务不重试</b>（M07）：{@code DEVICE_COMMAND}/{@code NOTIFICATION}/
     * {@code INGESTION}/{@code BACKFILL} 的租约到期时，SQL 已经把任务置为 {@code FAILED} +
     * {@code TASK_OUTCOME_UNKNOWN}。这里不再走"重排 + 尝试次数"那条路——重排意味着让设备
     * 命令或导入副作用再发生一次，而"结果未知"只能靠对账/人工确认，不是自动重试能解决的。
     *
     * @return 本轮收回的任务数
     */
    @Transactional
    public int reapExpiredLeases(OffsetDateTime now) {
        OffsetDateTime nextRunAt = now.plus(properties.getRetryDelay());
        List<ExpiredLease> expired = repository.expireLeases(now, nextRunAt, properties.getReaperBatchSize());
        for (ExpiredLease lease : expired) {
            if (lease.unknownOutcome()) {
                repository.closeAttempt(lease.taskId(), lease.attemptNo(), now, TaskStatus.FAILED,
                        OUTCOME_UNKNOWN, "租约到期且任务可能已产生外部副作用，结果未知，需人工对账");
                repository.insertEvent(UUID.randomUUID(), lease.taskId(), lease.attemptNo(),
                        TaskStatus.RUNNING, TaskStatus.FAILED, now,
                        "租约到期且结果未知，转人工对账", traceIdOf(lease.taskId()));
                log.warn("任务租约到期且结果未知，需人工对账 taskId={} attempt={}",
                        lease.taskId(), lease.attemptNo());
                continue;
            }
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
        int queuedTimeouts = failQueueTimeouts(now);
        int executionTimeouts = requestExecutionTimeouts(now);
        return expired.size() + queuedTimeouts + executionTimeouts;
    }

    /**
     * 排队时限（P-003-01）：普通 5 分钟、复杂 15 分钟，从 {@code submitted_at} 起算。
     *
     * <p>为什么"超时即 FAILED"而不是继续排队：排队时限是对外承诺；超时后即使跑完，
     * 结果也已经晚于用户被告知的时间，继续挂着只会让人看到一条永远不动的任务。
     *
     * @return 本轮判失败的任务数
     */
    private int failQueueTimeouts(OffsetDateTime now) {
        int handled = 0;
        handled += expireQueued(now, normalTaskTypes(), properties.getNormalQueueLimit());
        handled += expireQueued(now, properties.getComplexTaskTypes(), properties.getComplexQueueLimit());
        return handled;
    }

    private int expireQueued(OffsetDateTime now, java.util.Set<TaskType> types, java.time.Duration limit) {
        List<AsyncTaskRepository.TimedOutTask> timedOut = repository.failQueueTimeouts(
                types, now.minus(limit), now, properties.getReaperBatchSize());
        for (AsyncTaskRepository.TimedOutTask task : timedOut) {
            repository.insertEvent(UUID.randomUUID(), task.taskId(), task.attemptNo(),
                    task.previousStatus(), TaskStatus.FAILED, now,
                    "排队超过约定时限（" + limit.toMinutes() + " 分钟），已终止", traceIdOf(task.taskId()));
        }
        if (!timedOut.isEmpty()) {
            log.warn("排队超时终止 {} 条任务", timedOut.size());
        }
        return timedOut.size();
    }

    /**
     * 执行时限（P-003-01 / P-020-02）：普通 30 分钟、复杂 2 小时，从实际 {@code RUNNING} 起算。
     *
     * <p>只写"已请求取消"而不直接置终态：协议要求"超时告警并发起取消，未响应租约到期后终止发布"。
     * 直接改终态会绕过租约栅栏——worker 可能已经发出了副作用，必须由它确认或由租约到期收尾。
     */
    private int requestExecutionTimeouts(OffsetDateTime now) {
        int handled = 0;
        handled += requestTimeouts(now, normalTaskTypes(), properties.getNormalExecutionLimit());
        handled += requestTimeouts(now, properties.getComplexTaskTypes(),
                properties.getComplexExecutionLimit());
        return handled;
    }

    private int requestTimeouts(OffsetDateTime now, java.util.Set<TaskType> types, java.time.Duration limit) {
        List<AsyncTaskRepository.TimedOutTask> timedOut = repository.requestExecutionTimeouts(
                types, now.minus(limit), now, properties.getReaperBatchSize());
        for (AsyncTaskRepository.TimedOutTask task : timedOut) {
            repository.insertEvent(UUID.randomUUID(), task.taskId(), task.attemptNo(),
                    TaskStatus.RUNNING, TaskStatus.RUNNING, now,
                    "执行超过约定时限（" + limit.toMinutes() + " 分钟），已发起取消", traceIdOf(task.taskId()));
        }
        if (!timedOut.isEmpty()) {
            log.warn("执行超时已发起取消 {} 条任务", timedOut.size());
        }
        return timedOut.size();
    }

    /** 普通（非复杂）任务类型：复杂类型见 {@code xx.task.complex-task-types}。 */
    private java.util.Set<TaskType> normalTaskTypes() {
        java.util.Set<TaskType> normal = java.util.EnumSet.allOf(TaskType.class);
        normal.removeAll(properties.getComplexTaskTypes());
        return normal;
    }

    /**
     * 同进程 worker 的心跳续租（{@code TaskReaper#heartbeat} 每 10 秒调用）。
     *
     * <p>为什么心跳在服务端定时任务里做：协议规定的是"租约 60 秒、每 10 秒心跳"，续租动作
     * 是 {@code renew} 的批量版本；worker 进程若没有独立的调度器，就由本进程统一续。
     * 只续 {@code lease_owner} 在 {@code xx.task.worker-ids} 里的租约——不能续别人的，
     * 否则 reaper 永远收不到崩溃 worker 的租约。
     *
     * <p>未配置 {@code worker-ids} 时不做任何事：宁可让租约按 60 秒自然到期，
     * 也不要"猜一个身份"去续租。
     *
     * @return 实际被续租的任务数
     */
    @Transactional
    public int heartbeatLeases(OffsetDateTime now) {
        if (properties.getWorkerIds() == null || properties.getWorkerIds().isEmpty()) {
            return 0;
        }
        OffsetDateTime leaseUntil = TaskLease.deadline(now, properties.getLease());
        List<UUID> renewed = repository.renewLocalLeases(properties.getWorkerIds(), now, leaseUntil);
        if (!renewed.isEmpty()) {
            log.debug("心跳续租 {} 条任务", renewed.size());
        }
        return renewed.size();
    }

    /**
     * 提交配额（P-003-02）：每用户 5 个排队、系统全局 1000 个未终态任务。
     *
     * <p>为什么在提交入口就拦：{@code PROMOTE_PENDING} 只保证排队不超过 5 条，
     * {@code PENDING} 仍会无限堆积（用户看到的"已受理"任务越来越多、永远不会跑）。
     * 全局 1000 是系统容量底线，超过后应让调用方重试而不是把库压垮。
     *
     * <p>为什么先 {@code lockOwnerAdmission}：并发提交下"计数 < 上限"与 INSERT 之间
     * 没有锁就会一起越过上限；锁与领取共用全局 key，保证同一串行化序列。
     */
    private void enforceAdmission(UUID ownerUserId) {
        repository.lockOwnerAdmission(ownerUserId);
        long active = repository.countActiveGlobal();
        if (active >= properties.getMaxActiveTasks()) {
            throw ApiException.rateLimited("系统任务队列已满（上限 " + properties.getMaxActiveTasks()
                    + "），请稍后重试", java.time.Duration.ofSeconds(30));
        }
        long queued = repository.countQueuedByOwner(ownerUserId);
        if (queued >= TaskProperties.MAX_QUEUED_TASKS_PER_OWNER) {
            throw ApiException.rateLimited("该账号排队任务已满（上限 "
                    + TaskProperties.MAX_QUEUED_TASKS_PER_OWNER + "），请等待现有任务完成后再提交",
                    java.time.Duration.ofSeconds(30));
        }
    }

    /**
     * 提交复核（P-002-05 / P-009-01 / M14）：把 {@link TaskScopeGuard} 的判定翻译成契约 4xx。
     *
     * <p>为什么失败码要一一映射而不是统一 403：契约区分得清楚——Context 过期是 410
     * {@code CONTEXT_EXPIRED}，他人/不存在的 Context 是 404（不暴露存在性），范围收缩是 403
     * {@code PERMISSION_DATA_SCOPE}。统一成 403 会让客户端无法判断"重新建 Context 就好"还是
     * "这个请求根本不该发"。
     */
    private void requireSubmission(TaskCommand command, OffsetDateTime now) {
        if (scopeGuard == null) {
            return;
        }
        String blocker = scopeGuard.submissionBlocker(
                command.ownerUserId(), command.taskType(), command.parameters(), now);
        if (blocker == null) {
            return;
        }
        switch (blocker) {
            case TaskScopeGuard.PERMISSION_REVOKED -> throw new ApiException(ErrorCode.PERMISSION_DENIED,
                    "账号不可用或该任务类型已被收回授权，不能提交任务");
            case TaskScopeGuard.CONTEXT_NOT_FOUND -> throw new ApiException(ErrorCode.DATA_NOT_FOUND,
                    "上下文不存在或已失效");
            case TaskScopeGuard.CONTEXT_EXPIRED -> throw new ApiException(ErrorCode.CONTEXT_EXPIRED,
                    "上下文已过期（TTL 30 分钟按创建时间固定）");
            case TaskScopeGuard.SCOPE_SHRUNK -> throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE,
                    "数据范围已收缩，请重新构建上下文后再提交");
            default -> throw new ApiException(ErrorCode.PERMISSION_DENIED, "提交复核未通过: " + blocker);
        }
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
