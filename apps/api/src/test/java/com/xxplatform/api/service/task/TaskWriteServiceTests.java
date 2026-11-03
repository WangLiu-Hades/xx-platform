package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link TaskWriteService}（取消 / 重试）的行为测试，W2 任务 8。
 *
 * <p>覆盖契约里的分叉：排队态取消立即生效、运行中取消只登记（状态仍是 RUNNING 且
 * {@code cancelRequestedAt} 已写）、终态取消 409 {@code TASK_NOT_CANCELLABLE}、
 * 非 FAILED 重试 409 {@code TASK_CONFLICT}、以及 {@code task:manage} 与归属判定。
 * 仓储用手写桩，因为沙箱里 Mockito 的 Byte Buddy 自附加起不来。
 */
class TaskWriteServiceTests {

    private static final UUID OWNER = UUID.fromString("0192f000-0000-7000-8000-00000000d001");
    private static final UUID OTHER = UUID.fromString("0192f000-0000-7000-8000-00000000d002");
    private static final UUID TASK_ID = UUID.fromString("0192f000-0000-7000-8000-00000000e001");
    private static final String TRACE = "trace-task-write-001";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 27, 9, 0, 0, 0, ZoneOffset.UTC);
    private static final String PARAMETERS = """
            {"contextId":"0192f000-0000-7000-8000-00000000c001","format":"XLSX"}
            """;

    private final StubTasks tasks = new StubTasks();
    private final StubRbac rbac = new StubRbac();
    private final TaskViewMapper mapper = new TaskViewMapper(new ObjectMapper());
    private TaskWriteService service;

    @BeforeEach
    void setUp() {
        tasks.current = row(TaskStatus.PENDING, OWNER);
        TaskProperties properties = new TaskProperties();
        properties.setLease(Duration.ofMinutes(2));
        properties.setMaxAttempts(3);
        properties.setRetryDelay(Duration.ofSeconds(30));
        properties.setReaperBatchSize(50);
        service = new TaskWriteService(rbac, new TaskService(tasks, properties), mapper);
        rbac.codes = List.of(TaskWriteService.PERMISSION);
    }

    @Test
    void cancelRequiresTaskManagePermission() {
        rbac.codes = List.of("task:read");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.cancel(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.findCalls);
    }

    @Test
    void retryRequiresTaskManagePermission() {
        rbac.codes = List.of("task:read");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.retry(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.findCalls);
    }

    @Test
    void cancelOfQueuedTaskBecomesCancelledImmediately() {
        tasks.current = row(TaskStatus.QUEUED, OWNER);

        Map<String, Object> task = service.cancel(session(OWNER), TASK_ID);

        assertEquals("CANCELLED", task.get("status"));
        assertEquals(1, tasks.cancelQueuedCalls);
        assertEquals(0, tasks.requestCancelCalls);
        assertEquals(1, tasks.events);
    }

    @Test
    void cancelOfRunningTaskOnlyRegistersRequest() {
        tasks.current = row(TaskStatus.RUNNING, OWNER);

        Map<String, Object> task = service.cancel(session(OWNER), TASK_ID);

        assertEquals("RUNNING", task.get("status"));
        assertNotNull(task.get("cancelRequestedAt"));
        assertEquals(1, tasks.requestCancelCalls);
        assertEquals(0, tasks.cancelQueuedCalls);
    }

    @Test
    void cancelOfCancelledTaskIsIdempotent() {
        tasks.current = row(TaskStatus.CANCELLED, OWNER);

        Map<String, Object> task = service.cancel(session(OWNER), TASK_ID);

        assertEquals("CANCELLED", task.get("status"));
        assertEquals(0, tasks.cancelQueuedCalls);
        assertEquals(0, tasks.events);
    }

    @Test
    void cancelOfTerminalTaskIsRejected() {
        tasks.current = row(TaskStatus.SUCCESS, OWNER);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.cancel(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.TASK_NOT_CANCELLABLE, failure.code());
    }

    @Test
    void cancelHidesAnotherUsersTask() {
        tasks.current = row(TaskStatus.QUEUED, OTHER);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.cancel(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
        assertEquals(0, tasks.cancelQueuedCalls);
    }

    @Test
    void retryOfFailedTaskRequeuesSameTaskAndKeepsSnapshot() {
        TaskRow failed = row(TaskStatus.FAILED, OWNER);
        tasks.current = failed;

        Map<String, Object> task = service.retry(session(OWNER), TASK_ID);

        assertEquals("QUEUED", task.get("status"));
        assertEquals(TASK_ID.toString(), task.get("taskId"));
        assertEquals(1L, task.get("retryCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) task.get("parameterSnapshot");
        assertEquals(failed.parameterHash(), snapshot.get("parameterHash"));
        assertEquals(1, tasks.requeueCalls);
    }

    @Test
    void retryOfNonFailedTaskIsRejected() {
        tasks.current = row(TaskStatus.QUEUED, OWNER);

        ApiException failure = assertThrows(ApiException.class,
                () -> service.retry(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.TASK_CONFLICT, failure.code());
        assertEquals(0, tasks.requeueCalls);
    }

    @Test
    void cancelOfRunningTaskWhoseStateChangedReportsConflict() {
        tasks.current = row(TaskStatus.RUNNING, OWNER);
        tasks.requestCancelResult = 0;

        ApiException failure = assertThrows(ApiException.class,
                () -> service.cancel(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.TASK_CONFLICT, failure.code());
    }

    private static AuthenticatedSession session(UUID userId) {
        return new AuthenticatedSession(userId, UUID.randomUUID(), NOW.plusMinutes(15), NOW.plusHours(8), null);
    }

    private static TaskRow row(TaskStatus status, UUID owner) {
        return new TaskRow(TASK_ID, "导出 2026-10-27", TaskType.EXPORT, owner, status,
                status == TaskStatus.SUCCESS ? 100 : 0, 5, "1", PARAMETERS, "a".repeat(64),
                "idem-key-000000000001", null, null, NOW.minusMinutes(5),
                status == TaskStatus.RUNNING ? NOW.minusMinutes(4) : null, null,
                null, null, 0L, NOW.minusMinutes(5), null, 0L,
                status == TaskStatus.RUNNING ? 1L : 0L,
                status == TaskStatus.FAILED ? "TASK_LEASE_EXPIRED" : null,
                status == TaskStatus.FAILED ? "租约到期未完成，任务被收回" : null,
                null, TRACE, false, 0L, NOW.minusMinutes(5));
    }

    /** 手写仓储桩：状态迁移直接改 {@link #current}，模拟 SQL 的原子更新结果。 */
    static final class StubTasks extends AsyncTaskRepository {

        TaskRow current;
        int findCalls;
        int cancelQueuedCalls;
        int requestCancelCalls;
        int requeueCalls;
        int events;
        int requestCancelResult = 1;

        StubTasks() {
            super(new JdbcTemplate());
        }

        @Override
        public Optional<TaskRow> findById(UUID id) {
            findCalls++;
            if (current == null || !current.id().equals(id)) {
                return Optional.empty();
            }
            return Optional.of(current);
        }

        @Override
        public int requestCancel(UUID taskId, OffsetDateTime at) {
            requestCancelCalls++;
            if (requestCancelResult == 0) {
                return 0;
            }
            current = copy(current, current.status(), at, current.retryCount());
            return 1;
        }

        @Override
        public int cancelQueued(UUID taskId, OffsetDateTime at) {
            cancelQueuedCalls++;
            current = copy(current, TaskStatus.CANCELLED, at, current.retryCount());
            return 1;
        }

        @Override
        public int requeueForRetry(UUID taskId, OffsetDateTime at) {
            requeueCalls++;
            current = copy(current, TaskStatus.QUEUED, null, current.retryCount() + 1);
            return 1;
        }

        @Override
        public void insertEvent(UUID id, UUID taskId, long attemptNo, TaskStatus previousStatus,
                                TaskStatus status, OffsetDateTime occurredAt, String reason, String traceId) {
            events++;
        }

        private static TaskRow copy(TaskRow row, TaskStatus status, OffsetDateTime cancelRequestedAt,
                                    long retryCount) {
            return new TaskRow(row.id(), row.name(), row.taskType(), row.ownerUserId(), status,
                    row.progress(), row.priority(), row.parameterSchemaVersion(), row.parameterSnapshot(),
                    row.parameterHash(), row.idempotencyKey(), row.resourceType(), row.resourceId(),
                    row.submittedAt(), row.startedAt(), row.finishedAt(), row.leaseOwner(), row.leaseUntil(),
                    row.leaseGeneration(), row.nextRunAt(),
                    cancelRequestedAt == null ? row.cancelRequestedAt() : cancelRequestedAt,
                    retryCount, row.currentAttemptNo(), row.errorCode(), row.errorMessage(),
                    row.resultAssetId(), row.traceId(), row.deleted(), row.version(), row.createdAt());
        }
    }

    /** 权限码桩：所有方法返回固定列表。 */
    static final class StubRbac extends RbacRepository {

        List<String> codes = List.of();

        StubRbac() {
            super(new JdbcTemplate());
        }

        @Override
        public List<String> permissionCodes(UUID userId) {
            return codes;
        }
    }
}
