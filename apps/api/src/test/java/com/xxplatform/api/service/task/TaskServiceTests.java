package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.ExpiredLease;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.AsyncTaskRepository.TimedOutTask;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link TaskService} 的行为测试：全部用手写桩替换仓储，不起 Spring 上下文。
 *
 * <p>为什么不用 Mockito：沙箱里 JVM 不能自附加，Byte Buddy 的 inline mock maker 初始化失败；
 * 仓库其余测试同样手写桩。手写桩还顺带把"服务层到底调了哪个 SQL 方法、参数是什么"钉死，
 * 这比 {@code verify(...)} 更直观。
 */
class TaskServiceTests {

    private static final UUID OWNER = UUID.fromString("0192f000-0000-7000-8000-00000000a001");
    private static final OffsetDateTime CREATED = OffsetDateTime.of(2026, 10, 27, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final String SCHEMA = "export.v1";
    private static final String TRACE = "trace-task-001";

    private final StubTasks tasks = new StubTasks();
    private final TaskProperties properties = new TaskProperties();
    private TaskService service;

    @BeforeEach
    void setUp() {
        properties.setLease(Duration.ofMinutes(2));
        properties.setMaxAttempts(3);
        properties.setRetryDelay(Duration.ofSeconds(30));
        properties.setReaperBatchSize(50);
        service = new TaskService(tasks, properties);
    }

    @Test
    void createPersistsPendingTaskAndCreationEvent() {
        TaskCommand command = command(Map.of("format", "csv"));

        TaskRow created = service.create(command);

        assertEquals(TaskStatus.PENDING, created.status());
        assertEquals(1, tasks.insertCalls);
        assertEquals(1, tasks.events.size());
        assertEquals(TaskStatus.PENDING, tasks.events.get(0).status());
        assertEquals(null, tasks.events.get(0).previousStatus());
    }

    @Test
    void createReplaysExistingTaskWhenParametersMatch() {
        TaskRow existing = row(TaskStatus.PENDING, 0, command(Map.of("format", "csv")).parameterHash());
        tasks.idempotent = Optional.of(existing);

        TaskRow created = service.create(command(Map.of("format", "csv")));

        assertSame(existing, created);
        assertEquals(0, tasks.insertCalls, "重放不应再插入任务");
    }

    @Test
    void createRejectsSameKeyWithDifferentParameters() {
        tasks.idempotent = Optional.of(row(TaskStatus.PENDING, 0, "0".repeat(64)));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(command(Map.of("format", "xlsx"))));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.code());
    }

    @Test
    void createTreatsIgnoredConflictAsIdempotentReplay() {
        TaskCommand command = command(Map.of("format", "csv"));
        // 并发赢家：第一次查不到、插入被 ON CONFLICT DO NOTHING 降成 0 行、回读命中同一份参数。
        tasks.insertResult = 0;
        tasks.idempotentResponses.add(Optional.empty());
        tasks.idempotentResponses.add(Optional.of(row(TaskStatus.PENDING, 0, command.parameterHash())));

        TaskRow created = service.create(command);

        assertEquals(OWNER, created.ownerUserId());
        assertEquals(1, tasks.insertCalls);
        assertEquals(0, tasks.events.size(), "幂等重放不应再写创建事件");
    }

    @Test
    void createRejectsConflictWhenStoredParametersDiffer() {
        // 同一幂等键、不同参数：0 行回读到的赢家指纹对不上，必须 409 而不是把旧任务当结果。
        tasks.insertResult = 0;
        tasks.idempotentResponses.add(Optional.empty());
        tasks.idempotentResponses.add(Optional.of(row(TaskStatus.PENDING, 0, "0".repeat(64))));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(command(Map.of("format", "csv"))));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.code());
    }

    @Test
    void createFailsWithConflictWhenIgnoredInsertWinnerCannotBeRead() {
        tasks.insertResult = 0;
        tasks.idempotentResponses.add(Optional.empty());
        tasks.idempotentResponses.add(Optional.empty());

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(command(Map.of("format", "csv"))));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, failure.code(),
                "插入被忽略又读不到既有任务时不能假装成功");
    }

    @Test
    void claimNextWritesAttemptAndRunningEvent() {
        TaskRow running = row(TaskStatus.RUNNING, 1, "h1");
        tasks.claimed = List.of(new ClaimedTask(running, TaskStatus.PENDING));

        Optional<ClaimedTask> claimed = service.claimNext("worker-1");

        assertTrue(claimed.isPresent());
        assertEquals(1, tasks.attempts.size());
        assertEquals(1L, tasks.attempts.get(0).attemptNo());
        assertEquals("worker-1", tasks.attempts.get(0).workerId());
        assertEquals(1, tasks.events.size());
        assertEquals(TaskStatus.RUNNING, tasks.events.get(0).status());
        assertEquals(TaskStatus.PENDING, tasks.events.get(0).previousStatus());
    }

    @Test
    void claimNextPromotesPendingTaskAndRecordsBothTransitions() {
        TaskRow pending = row(TaskStatus.PENDING, 0, "h1");
        tasks.byId = Optional.of(pending);
        tasks.promoteResult = Optional.of(pending.id());
        tasks.claimed = List.of(new ClaimedTask(row(TaskStatus.RUNNING, 1, "h1"), TaskStatus.QUEUED));

        service.claimNext("worker-1");

        assertEquals(2, tasks.events.size(), "PENDING->QUEUED 与 QUEUED->RUNNING 各一条");
        assertEquals(TaskStatus.QUEUED, tasks.events.get(0).status());
        assertEquals(TaskStatus.RUNNING, tasks.events.get(1).status());
    }

    @Test
    void claimNextReturnsEmptyWhenQueueIsEmpty() {
        tasks.claimed = List.of();

        assertTrue(service.claimNext("worker-1").isEmpty());
        assertEquals(0, tasks.attempts.size());
    }

    @Test
    void claimNextPassesWorkerCapabilitiesAndLimitToRepository() {
        TaskRow running = row(TaskStatus.RUNNING, 1, "h1");
        tasks.claimed = List.of(new ClaimedTask(running, TaskStatus.QUEUED));

        List<ClaimedTask> claimed = service.claimNext("worker-1",
                List.of(TaskType.EXPORT, TaskType.ANALYSIS), 3);

        assertEquals(1, claimed.size());
        assertEquals(List.of(TaskType.EXPORT, TaskType.ANALYSIS), tasks.lastSupportedTypes,
                "worker 声明的类型必须原样下推到领取 SQL");
        assertEquals(3, tasks.lastClaimLimit, "本轮上限必须下推，不能由仓储自行决定");
        assertEquals(1, tasks.attempts.size());
        assertEquals(1, tasks.events.size());
    }

    @Test
    void finishRejectsWhenLeaseWasStolen() {
        tasks.finishResult = 0;

        ApiException failure = assertThrows(ApiException.class,
                () -> service.finish(UUID.randomUUID(), "worker-1", 2L, TaskStatus.SUCCESS,
                        null, null, null));

        assertEquals(ErrorCode.TASK_CONFLICT, failure.code());
    }

    @Test
    void finishRejectsNonTerminalOutcome() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.finish(UUID.randomUUID(), "worker-1", 2L, TaskStatus.PENDING,
                        null, null, null));

        assertEquals(ErrorCode.TASK_CONFLICT, failure.code());
    }

    @Test
    void cancelQueuedTaskIsImmediate() {
        TaskRow queued = row(TaskStatus.QUEUED, 0, "h1");
        tasks.byId = Optional.of(queued);
        tasks.cancelQueuedResult = 1;
        tasks.afterCancel = row(TaskStatus.CANCELLED, 0, "h1");

        TaskService.CancelOutcome outcome = service.cancel(queued.id(), OWNER);

        assertTrue(outcome.immediate());
        assertEquals(TaskStatus.CANCELLED, outcome.task().status());
        assertEquals(1, tasks.cancelQueuedCalls);
        assertEquals(TaskStatus.CANCELLED, tasks.events.get(0).status());
    }

    @Test
    void cancelRunningTaskOnlyRecordsRequest() {
        TaskRow running = row(TaskStatus.RUNNING, 1, "h1");
        tasks.byId = Optional.of(running);
        tasks.requestCancelResult = 1;
        tasks.afterCancel = row(TaskStatus.RUNNING, 1, "h1");

        TaskService.CancelOutcome outcome = service.cancel(running.id(), OWNER);

        assertFalse(outcome.immediate());
        assertEquals(1, tasks.requestCancelCalls);
    }

    @Test
    void cancelTerminalTaskIsRejected() {
        TaskRow success = row(TaskStatus.SUCCESS, 1, "h1");
        tasks.byId = Optional.of(success);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.cancel(success.id(), OWNER));

        assertEquals(ErrorCode.TASK_NOT_CANCELLABLE, failure.code());
    }

    @Test
    void cancelAlreadyCancelledTaskIsIdempotent() {
        TaskRow cancelled = row(TaskStatus.CANCELLED, 1, "h1");
        tasks.byId = Optional.of(cancelled);

        TaskService.CancelOutcome outcome = service.cancel(cancelled.id(), OWNER);

        assertTrue(outcome.immediate());
        assertEquals(0, tasks.cancelQueuedCalls);
        assertEquals(0, tasks.events.size());
    }

    @Test
    void retryOnlyAcceptsFailedTask() {
        TaskRow success = row(TaskStatus.SUCCESS, 1, "h1");
        tasks.byId = Optional.of(success);
        assertEquals(ErrorCode.TASK_CONFLICT,
                assertThrows(ApiException.class, () -> service.retry(success.id(), OWNER)).code());

        TaskRow failed = row(TaskStatus.FAILED, 1, "h1");
        tasks.byId = Optional.of(failed);
        tasks.afterRetry = row(TaskStatus.QUEUED, 1, "h1");
        tasks.requeueResult = 1;

        TaskRow retried = service.retry(failed.id(), OWNER);

        assertEquals(TaskStatus.QUEUED, retried.status());
        assertEquals(1, tasks.requeueCalls);
        assertEquals(TaskStatus.QUEUED, tasks.events.get(0).status());
    }

    @Test
    void reapRequeuesExpiredLeaseAndClosesAttempt() {
        UUID taskId = UUID.randomUUID();
        tasks.expired = List.of(new ExpiredLease(taskId, TaskStatus.QUEUED, 1L, false));
        tasks.byId = Optional.of(row(taskId, TaskStatus.QUEUED, 1, "h1"));

        int reaped = service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(1, reaped);
        assertEquals(1, tasks.closedAttempts.size());
        assertEquals(TaskStatus.FAILED, tasks.closedAttempts.get(0).result());
        assertEquals(TaskService.LEASE_EXPIRED, tasks.closedAttempts.get(0).errorCode());
        assertEquals(0, tasks.failExhaustedCalls, "还有剩余尝试次数，不应改判失败");
    }

    @Test
    void reapFailsTaskWhenAttemptsAreExhausted() {
        UUID taskId = UUID.randomUUID();
        tasks.expired = List.of(new ExpiredLease(taskId, TaskStatus.QUEUED, 3L, false));
        tasks.byId = Optional.of(row(taskId, TaskStatus.QUEUED, 3, "h1"));

        service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(1, tasks.failExhaustedCalls);
        assertEquals(TaskService.ATTEMPTS_EXHAUSTED, tasks.failExhaustedCode);
        assertEquals(2, tasks.events.size(), "收回与放弃是两个状态迁移，各一条事件");
    }

    @Test
    void reapCancelsTaskWhoseCancellationWasRequested() {
        UUID taskId = UUID.randomUUID();
        tasks.expired = List.of(new ExpiredLease(taskId, TaskStatus.CANCELLED, 1L, false));
        tasks.byId = Optional.of(row(taskId, TaskStatus.CANCELLED, 1, "h1"));

        service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(TaskStatus.CANCELLED, tasks.closedAttempts.get(0).result());
        assertEquals(0, tasks.failExhaustedCalls);
    }

    @Test
    void reapFailsSideEffectTaskWithUnknownOutcomeAndDoesNotRetry() {
        UUID taskId = UUID.randomUUID();
        // DEVICE_COMMAND 这类任务租约到期时可能已经发出了命令：SQL 已置 FAILED，服务层不得再重排。
        tasks.expired = List.of(new ExpiredLease(taskId, TaskStatus.FAILED, 2L, true));
        tasks.byId = Optional.of(row(taskId, TaskStatus.FAILED, 2, "h1"));

        int reaped = service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));

        assertEquals(1, reaped);
        assertEquals(TaskStatus.FAILED, tasks.closedAttempts.get(0).result());
        assertEquals(TaskService.OUTCOME_UNKNOWN, tasks.closedAttempts.get(0).errorCode(),
                "attempt 必须记成结果未知，而不是普通租约过期");
        assertEquals(TaskStatus.FAILED, tasks.events.get(0).status(), "事件必须反映 FAILED 终态");
        assertEquals(0, tasks.failExhaustedCalls, "结果未知不能再按尝试次数走重排/改判");
    }

    @Test
    void renewReportsLeaseLoss() {
        tasks.renewResult = 0;
        assertFalse(service.renew(UUID.randomUUID(), "worker-1", 1L));

        tasks.renewResult = 1;
        assertTrue(service.renew(UUID.randomUUID(), "worker-1", 1L));
    }

    @Test
    void getHidesForeignTaskBehindNotFound() {
        TaskRow foreign = row(TaskStatus.PENDING, 0, "h1");
        tasks.byId = Optional.of(foreign);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.get(foreign.id(), UUID.randomUUID()));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
    }

    // ---------------------------------------------------------------- 夹具

    private static TaskCommand command(Map<String, Object> parameters) {
        return new TaskCommand(OWNER, TaskType.EXPORT, "导出 10 月报表", SCHEMA,
                new LinkedHashMap<>(parameters), "idem-001", TRACE, null, "EXPORT_JOB", UUID.randomUUID());
    }

    private static TaskRow row(TaskStatus status, long attemptNo, String hash) {
        return row(UUID.randomUUID(), status, attemptNo, hash);
    }

    private static TaskRow row(UUID id, TaskStatus status, long attemptNo, String hash) {
        return new TaskRow(id, "导出 10 月报表", TaskType.EXPORT, OWNER, status, 0, 5,
                SCHEMA, "{\"format\":\"csv\"}", hash, "idem-001", "EXPORT_JOB", UUID.randomUUID(),
                CREATED, CREATED, null, null, null, 0, CREATED, null, 0, attemptNo,
                null, null, null, TRACE, false, 0, CREATED);
    }

    /** 手写桩：只覆写测试关心的 SQL 方法，并记录调用。 */
    static final class StubTasks extends AsyncTaskRepository {

        int insertCalls;
        int insertResult = 1;
        Optional<TaskRow> idempotent = Optional.empty();
        Optional<TaskRow> byId = Optional.empty();
        List<ClaimedTask> claimed = List.of();
        List<TaskType> lastSupportedTypes = List.of();
        int lastClaimLimit;
        Optional<UUID> promoteResult = Optional.empty();
        TaskRow afterCancel;
        TaskRow afterRetry;
        int finishResult = 1;
        int renewResult = 1;
        int requestCancelResult;
        int cancelQueuedResult;
        int requeueResult;
        int cancelQueuedCalls;
        int requestCancelCalls;
        int requeueCalls;
        int failExhaustedCalls;
        String failExhaustedCode;
        List<ExpiredLease> expired = List.of();
        final List<AttemptWrite> attempts = new ArrayList<>();
        final List<EventWrite> events = new ArrayList<>();
        final List<AttemptClose> closedAttempts = new ArrayList<>();
        private final Deque<Optional<TaskRow>> idempotentResponses = new ArrayDeque<>();
        private boolean inserted;

        StubTasks() {
            super(new JdbcTemplate());
        }

        @Override
        public int insert(UUID id, TaskCommand command, OffsetDateTime now) {
            insertCalls++;
            if (insertResult == 0) {
                return 0;
            }
            inserted = true;
            byId = Optional.of(TaskServiceTests.row(id, TaskStatus.PENDING, 0, command.parameterHash()));
            return 1;
        }

        @Override
        public Optional<TaskRow> findById(UUID id) {
            return byId;
        }

        @Override
        public Optional<TaskRow> findByIdempotencyKey(UUID ownerUserId, TaskType taskType, String key) {
            if (!idempotentResponses.isEmpty()) {
                return idempotentResponses.removeFirst();
            }
            if (inserted) {
                return byId;
            }
            return idempotent;
        }

        @Override
        public List<ClaimedTask> claimNext(String workerId, OffsetDateTime now, Duration lease,
                                           List<TaskType> supportedTypes, int limit) {
            lastSupportedTypes = List.copyOf(supportedTypes);
            lastClaimLimit = limit;
            return claimed;
        }

        @Override
        public Optional<UUID> promoteNext(OffsetDateTime now) {
            return promoteResult;
        }

        @Override
        public int renewLease(UUID taskId, String workerId, long generation,
                              OffsetDateTime now, OffsetDateTime leaseUntil) {
            return renewResult;
        }

        @Override
        public int finish(UUID taskId, String workerId, long generation, TaskStatus outcome,
                          String errorCode, String errorMessage, UUID resultAssetId, OffsetDateTime finishedAt) {
            return finishResult;
        }

        @Override
        public int requestCancel(UUID taskId, OffsetDateTime at) {
            requestCancelCalls++;
            if (requestCancelResult > 0 && afterCancel != null) {
                byId = Optional.of(afterCancel);
            }
            return requestCancelResult;
        }

        @Override
        public int cancelQueued(UUID taskId, OffsetDateTime at) {
            cancelQueuedCalls++;
            if (cancelQueuedResult > 0 && afterCancel != null) {
                byId = Optional.of(afterCancel);
            }
            return cancelQueuedResult;
        }

        @Override
        public int requeueForRetry(UUID taskId, OffsetDateTime at) {
            requeueCalls++;
            if (requeueResult > 0 && afterRetry != null) {
                byId = Optional.of(afterRetry);
            }
            return requeueResult;
        }

        @Override
        public List<ExpiredLease> expireLeases(OffsetDateTime now, OffsetDateTime nextRunAt, int batchSize) {
            return expired;
        }

        @Override
        public int failExhausted(UUID taskId, OffsetDateTime at, String errorCode, String errorMessage) {
            failExhaustedCalls++;
            failExhaustedCode = errorCode;
            return 1;
        }

        @Override
        public int closeAttempt(UUID taskId, long attemptNo, OffsetDateTime at,
                                TaskStatus result, String errorCode, String errorMessage) {
            closedAttempts.add(new AttemptClose(taskId, attemptNo, result, errorCode));
            return 1;
        }

        @Override
        public void insertAttempt(UUID taskId, long attemptNo, OffsetDateTime startedAt,
                                  String workerId, long leaseGeneration) {
            attempts.add(new AttemptWrite(taskId, attemptNo, workerId));
        }

        @Override
        public void insertEvent(UUID id, UUID taskId, long attemptNo, TaskStatus previousStatus,
                                TaskStatus status, OffsetDateTime occurredAt, String reason, String traceId) {
            events.add(new EventWrite(taskId, previousStatus, status));
        }

        // 以下六个是 M06 配额/时限/心跳新增的仓储入口。单测只用内存队列验编排，
        // 用默认实现即可——不能让基类打到没有 DataSource 的 JdbcTemplate 上。

        @Override
        public void lockOwnerAdmission(UUID ownerUserId) {
        }

        @Override
        public long countActiveGlobal() {
            return 0;
        }

        @Override
        public long countQueuedByOwner(UUID ownerUserId) {
            return 0;
        }

        @Override
        public List<TimedOutTask> failQueueTimeouts(Set<TaskType> taskTypes, OffsetDateTime submittedBefore,
                                                    OffsetDateTime now, int batchSize) {
            return List.of();
        }

        @Override
        public List<TimedOutTask> requestExecutionTimeouts(Set<TaskType> taskTypes, OffsetDateTime startedBefore,
                                                           OffsetDateTime now, int batchSize) {
            return List.of();
        }

        @Override
        public List<UUID> renewLocalLeases(Set<String> workerIds, OffsetDateTime now, OffsetDateTime leaseUntil) {
            return List.of();
        }
    }

    record AttemptWrite(UUID taskId, long attemptNo, String workerId) {
    }

    record AttemptClose(UUID taskId, long attemptNo, TaskStatus result, String errorCode) {
    }

    record EventWrite(UUID taskId, TaskStatus previousStatus, TaskStatus status) {
    }
}
