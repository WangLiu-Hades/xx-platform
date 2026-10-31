package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link TaskReadService} 的行为测试：手写桩替换仓储与 RBAC，不起 Spring 上下文。
 *
 * <p>重点覆盖三件契约要求：可见性（只看自己的、别人的按 404）、分页参数边界（page/pageSize/
 * 时间窗），以及响应形状（{@code parameterSnapshot} 只暴露摘要、失败任务的错误码折成
 * {@code TASK_FAILED}、导出任务推导 {@code resourceType}）。
 */
class TaskReadServiceTests {

    private static final UUID OWNER = UUID.fromString("0192f000-0000-7000-8000-00000000d001");
    private static final UUID OTHER = UUID.fromString("0192f000-0000-7000-8000-00000000d002");
    private static final UUID TASK_ID = UUID.fromString("0192f000-0000-7000-8000-00000000e001");
    private static final UUID EVENT_ID = UUID.fromString("0192f000-0000-7000-8000-00000000e002");
    private static final String TRACE = "trace-tasks-001";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 27, 9, 0, 0, 0, ZoneOffset.UTC);

    private static final String PARAMETERS = """
            {"contextId":"0192f000-0000-7000-8000-00000000c001","format":"XLSX",
             "columns":["captureId","plateMasked"],"purpose":"例行检查",
             "limits":{"maxRows":1000000,"maxBytes":1073741824}}
            """;

    private final StubTasks tasks = new StubTasks();
    private final StubRbac rbac = new StubRbac();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TaskReadService service;

    @BeforeEach
    void setUp() {
        TaskPropertiesFactory properties = new TaskPropertiesFactory();
        TaskService taskService = new TaskService(tasks, properties.properties());
        service = new TaskReadService(tasks, rbac, taskService,
                new TaskViewMapper(objectMapper));
        rbac.codes = List.of(TaskReadService.PERMISSION);
        tasks.rows = List.of(row(TASK_ID, TaskStatus.PENDING, OWNER));
    }

    @Test
    void listRequiresTaskReadPermission() {
        rbac.codes = List.of("capture:read");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.list(session(OWNER), defaultQuery()));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.pageCalls);
    }

    @Test
    void listRejectsPageBelowOne() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.list(session(OWNER), query(0, 20, null, null, null, null, false)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
        assertEquals("page", failure.details().get(0).field());
    }

    @Test
    void listRejectsPageSizeAboveHundred() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.list(session(OWNER), query(1, 101, null, null, null, null, false)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
        assertEquals("pageSize", failure.details().get(0).field());
    }

    @Test
    void listRejectsInvertedCreatedWindow() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.list(session(OWNER),
                        query(1, 20, null, null, NOW, NOW.minusHours(1), false)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
        assertEquals("createdFrom", failure.details().get(0).field());
    }

    @Test
    void listPassesFiltersAndPagingToRepository() {
        tasks.total = 42;
        TaskListQuery query = query(3, 25, TaskStatus.FAILED,
                List.of(TaskType.EXPORT, TaskType.REPORT), NOW.minusDays(1), NOW, true);

        service.list(session(OWNER), query);

        assertEquals(OWNER, tasks.lastOwner);
        assertEquals(List.of(TaskType.EXPORT, TaskType.REPORT), tasks.lastTaskTypes);
        assertEquals(TaskStatus.FAILED, tasks.lastStatus);
        assertEquals(NOW.minusDays(1), tasks.lastFrom);
        assertEquals(NOW, tasks.lastTo);
        assertEquals(TaskSortBy.CREATED_AT, tasks.lastSortBy);
        assertTrue(tasks.lastDescending);
        assertEquals(25, tasks.lastLimit);
        assertEquals(50L, tasks.lastOffset, "第 3 页、每页 25 条时偏移必须是 50");
        assertEquals(42L, tasks.total);
        assertEquals(OWNER, tasks.lastTotalOwner, "计数查询必须与分页查询用同一个 owner 过滤");
    }

    @Test
    void listReturnsContractPageShape() {
        tasks.total = 1;

        TaskPageView page = service.list(session(OWNER), defaultQuery());

        assertEquals(1, page.page());
        assertEquals(20, page.pageSize());
        assertEquals(1L, page.total());
        assertEquals(1, page.items().size());
        Map<String, Object> item = page.items().get(0);
        assertEquals(TASK_ID.toString(), item.get("taskId"));
        assertEquals("PENDING", item.get("status"));
        assertEquals("EXPORT", item.get("taskType"));
        assertEquals(0, item.get("progress"));
        assertEquals(OWNER.toString(), item.get("ownerUserId"));
        assertEquals(TRACE, item.get("traceId"));
    }

    @Test
    void listHidesSensitiveParametersBehindSnapshotSummary() {
        Map<String, Object> item = service.list(session(OWNER), defaultQuery()).items().get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) item.get("parameterSnapshot");
        assertEquals("1", snapshot.get("schemaVersion"));
        assertEquals("0192f000-0000-7000-8000-00000000c001", snapshot.get("contextId"));
        assertTrue(snapshot.get("submittedAt") instanceof String);
        assertEquals("a".repeat(64), snapshot.get("parameterHash"));
        assertFalse(snapshot.containsKey("columns"), "导出列属于领域详情，不应出现在通用任务列表");
        assertFalse(snapshot.containsKey("purpose"));
        assertFalse(item.containsKey("idempotencyKey"));
    }

    @Test
    void listDerivesExportResourceTypeWithoutResourceId() {
        Map<String, Object> item = service.list(session(OWNER), defaultQuery()).items().get(0);

        assertEquals("EXPORT", item.get("resourceType"));
        assertFalse(item.containsKey("resourceId"));
        assertFalse(item.containsKey("resultReference"), "没有结果文件时不应凭空造 resultReference");
    }

    @Test
    void listMapsFailedTaskToContractErrorCode() {
        tasks.rows = List.of(row(TASK_ID, TaskStatus.FAILED, OWNER));

        Map<String, Object> item = service.list(session(OWNER), defaultQuery()).items().get(0);

        assertEquals("TASK_FAILED", item.get("errorCode"));
        assertEquals("TASK_LEASE_EXPIRED", tasks.rows.get(0).errorCode(), "库里保留排障码");
        assertTrue(String.valueOf(item.get("errorMessage")).contains("租约"));
    }

    @Test
    void listAddsResultReferenceWhenResultAssetExists() {
        tasks.rows = List.of(row(TASK_ID, TaskStatus.SUCCESS, OWNER));

        Map<String, Object> item = service.list(session(OWNER), defaultQuery()).items().get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> reference = (Map<String, Object>) item.get("resultReference");
        assertEquals("EXPORT", reference.get("resultType"));
        assertEquals(EVENT_ID.toString(), reference.get("fileAssetId"));
        assertFalse(item.containsKey("errorCode"));
    }

    @Test
    void getReturnsOwnTask() {
        Map<String, Object> view = service.get(session(OWNER), TASK_ID);

        assertEquals(TASK_ID.toString(), view.get("taskId"));
    }

    @Test
    void getHidesAnotherUsersTaskAsNotFound() {
        tasks.rows = List.of(row(TASK_ID, TaskStatus.PENDING, OTHER));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.get(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
    }

    @Test
    void getUnknownTaskIsNotFound() {
        tasks.rows = List.of();

        ApiException failure = assertThrows(ApiException.class,
                () -> service.get(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
    }

    @Test
    void getRequiresPermissionBeforeTouchingRows() {
        rbac.codes = List.of("task:manage");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.get(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.findCalls);
    }

    @Test
    void attemptsRequireTaskReadPermission() {
        rbac.codes = List.of("task:manage");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.attempts(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.findCalls);
    }

    @Test
    void attemptsHideAnotherUsersTaskAsNotFound() {
        tasks.rows = List.of(row(TASK_ID, TaskStatus.FAILED, OTHER));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.attempts(session(OWNER), TASK_ID));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
        assertEquals(0, tasks.attemptCalls);
    }

    @Test
    void attemptsReturnRowsInAttemptOrderWithNullableFinish() {
        tasks.attempts = List.of(
                new AsyncTaskRepository.AttemptRow(1, NOW.minusMinutes(9), NOW.minusMinutes(7),
                        "SUCCESS", null),
                new AsyncTaskRepository.AttemptRow(2, NOW.minusMinutes(5), null,
                        null, "TASK_LEASE_EXPIRED"));

        List<Map<String, Object>> items = service.attempts(session(OWNER), TASK_ID);

        assertEquals(2, items.size());
        assertEquals(1L, items.get(0).get("attemptNo"));
        assertEquals("SUCCESS", items.get(0).get("result"));
        assertNull(items.get(0).get("errorCode"));
        assertEquals(2L, items.get(1).get("attemptNo"));
        assertNull(items.get(1).get("finishedAt"));
        assertFalse(items.get(1).containsKey("result"));
        assertEquals("TASK_FAILED", items.get(1).get("errorCode"));
        assertEquals(TASK_ID, tasks.lastAttemptTaskId);
    }

    @Test
    void attemptsForTaskWithoutAttemptsReturnEmptyList() {
        tasks.attempts = List.of();

        assertTrue(service.attempts(session(OWNER), TASK_ID).isEmpty());
    }

    private static AuthenticatedSession session(UUID userId) {
        return new AuthenticatedSession(userId, UUID.randomUUID(), NOW.plusMinutes(15), NOW.plusHours(8), null);
    }

    private static TaskListQuery defaultQuery() {
        return query(1, 20, null, null, null, null, false);
    }

    private static TaskListQuery query(int page, int pageSize, TaskStatus status, List<TaskType> types,
                                       OffsetDateTime from, OffsetDateTime to, boolean descending) {
        return new TaskListQuery(page, pageSize, TaskSortBy.CREATED_AT, descending,
                types == null ? List.of() : types, status, from, to);
    }

    private static TaskRow row(UUID id, TaskStatus status, UUID owner) {
        return new TaskRow(id, "导出 2026-10-27", TaskType.EXPORT, owner, status,
                status == TaskStatus.SUCCESS ? 100 : 0, 5, "1", PARAMETERS, "a".repeat(64),
                "idem-key-000000000001", null, null, NOW.minusMinutes(5), null, null,
                null, null, 0L, NOW.minusMinutes(5), null, 0L, 0L,
                status == TaskStatus.FAILED ? "TASK_LEASE_EXPIRED" : null,
                status == TaskStatus.FAILED ? "租约到期未完成，任务被收回" : null,
                status == TaskStatus.SUCCESS ? EVENT_ID : null, TRACE, false, 0L, NOW.minusMinutes(5));
    }

    /** 与生产配置同形的 {@link com.xxplatform.api.config.TaskProperties}（避开构造器特判）。 */
    private static final class TaskPropertiesFactory {

        com.xxplatform.api.config.TaskProperties properties() {
            var properties = new com.xxplatform.api.config.TaskProperties();
            properties.setLease(java.time.Duration.ofMinutes(2));
            properties.setMaxAttempts(3);
            properties.setRetryDelay(java.time.Duration.ofSeconds(30));
            properties.setReaperBatchSize(50);
            return properties;
        }
    }

    static final class StubTasks extends AsyncTaskRepository {

        List<TaskRow> rows = List.of();
        long total;
        int pageCalls;
        int findCalls;
        UUID lastOwner;
        List<TaskType> lastTaskTypes;
        TaskStatus lastStatus;
        OffsetDateTime lastFrom;
        OffsetDateTime lastTo;
        TaskSortBy lastSortBy;
        boolean lastDescending;
        int lastLimit;
        long lastOffset;
        UUID lastTotalOwner;
        List<AsyncTaskRepository.AttemptRow> attempts = List.of();
        int attemptCalls;
        UUID lastAttemptTaskId;

        StubTasks() {
            super(new JdbcTemplate());
        }

        @Override
        public List<TaskRow> findPageByOwner(UUID ownerUserId, List<TaskType> taskTypes, TaskStatus status,
                                             OffsetDateTime createdFrom, OffsetDateTime createdTo,
                                             TaskSortBy sortBy, boolean descending, int limit, long offset) {
            pageCalls++;
            lastOwner = ownerUserId;
            lastTaskTypes = List.copyOf(taskTypes);
            lastStatus = status;
            lastFrom = createdFrom;
            lastTo = createdTo;
            lastSortBy = sortBy;
            lastDescending = descending;
            lastLimit = limit;
            lastOffset = offset;
            return rows;
        }

        @Override
        public long countByOwner(UUID ownerUserId, List<TaskType> taskTypes, TaskStatus status,
                                 OffsetDateTime createdFrom, OffsetDateTime createdTo) {
            lastTotalOwner = ownerUserId;
            return total;
        }

        @Override
        public Optional<TaskRow> findById(UUID id) {
            findCalls++;
            return rows.stream().filter(row -> row.id().equals(id)).findFirst();
        }

        @Override
        public List<AsyncTaskRepository.AttemptRow> listAttempts(UUID taskId) {
            attemptCalls++;
            lastAttemptTaskId = taskId;
            return attempts;
        }
    }

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
