package com.xxplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AnalysisContextRepository;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.ExportJobRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.task.TaskCommand;
import com.xxplatform.api.service.task.TaskService;
import com.xxplatform.api.service.task.TaskStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link ExportService} 的行为测试：手写桩替换三个仓储，不起 Spring 上下文。
 *
 * <p>覆盖的是"提交端能拦住什么"：权限、上下文可见性与过期、范围有界、列白名单、幂等。
 * 真正的行数/字节上限属于 worker 执行期，无法在提交端用单测证明——这里只断言预算被冻结
 * 进了任务参数。
 */
class ExportServiceTests {

    private static final UUID OWNER = UUID.fromString("0192f000-0000-7000-8000-00000000b001");
    private static final UUID OTHER = UUID.fromString("0192f000-0000-7000-8000-00000000b002");
    private static final UUID CONTEXT_ID = UUID.fromString("0192f000-0000-7000-8000-00000000c001");
    private static final String KEY = "idem-key-000000000001";
    private static final String TRACE = "trace-export-001";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 27, 8, 0, 0, 0, ZoneOffset.UTC);

    private static final String PLATE_FILTER = """
            {"filterType":"CAPTURE_SEARCH","filterVersion":1,
             "captureFilter":{"dataType":"PLATE",
                              "timeRange":{"from":"2026-10-27T00:00:00Z","to":"2026-10-27T12:00:00Z"}}}
            """;

    private static final String GENERAL_FILTER = """
            {"filterType":"GENERAL","filterVersion":1,"dataTypes":["FENCE","PLATE"],
             "timeRange":{"from":"2026-10-27T00:00:00Z","to":"2026-10-27T12:00:00Z"}}
            """;

    private static final String UNBOUNDED_GENERAL_FILTER = """
            {"filterType":"GENERAL","filterVersion":1,"dataTypes":["FACE"]}
            """;

    private final StubTasks tasks = new StubTasks();
    private final StubContexts contexts = new StubContexts();
    private final StubJobs jobs = new StubJobs();
    private final StubRbac rbac = new StubRbac();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExportService service;

    @BeforeEach
    void setUp() {
        TaskProperties properties = new TaskProperties();
        properties.setLease(Duration.ofMinutes(2));
        properties.setMaxAttempts(3);
        properties.setRetryDelay(Duration.ofSeconds(30));
        properties.setReaperBatchSize(50);
        TaskService taskService = new TaskService(tasks, properties);
        service = new ExportService(taskService, contexts, jobs, rbac, objectMapper);
        contexts.row = Optional.of(context("CAPTURE_SEARCH", PLATE_FILTER, OWNER, Instants.nowUtc().plusHours(1)));
        rbac.codes = List.of(ExportService.PERMISSION);
    }

    @Test
    void createFreezesTaskAndExportJobForPlateExport() {
        TaskCreatedView created = service.create(session(OWNER), command(ExportFormat.XLSX, null, "例行检查"));

        assertEquals(TaskStatus.PENDING.name(), created.status());
        assertEquals(ExportService.RESOURCE_TYPE, created.resourceType());
        assertNotNull(created.taskId());
        assertEquals(1, tasks.insertCalls);
        assertEquals(1, jobs.inserts.size());

        StubJobs.Insert intent = jobs.inserts.get(0);
        assertEquals(created.taskId(), intent.taskId());
        assertEquals("CAPTURE", intent.sourceType());
        assertEquals("XLSX", intent.format());
        assertEquals("CAPTURE_SEARCH", intent.exportScope());
        assertEquals("例行检查", intent.purpose());
        assertTrue(intent.columns().contains("plateMasked"), "PLATE 的默认列应包含 plateMasked");
        assertTrue(intent.columns().contains("captureId"), "公共列应始终出现");
        assertTrue(!intent.columns().contains("faceImageAssetId"), "PLATE 不应带人像专有列");
    }

    @Test
    void createStoresFrozenLimitsInTaskParameters() {
        service.create(session(OWNER), command(ExportFormat.CSV, null, null));

        Map<String, Object> parameters = tasks.insertedCommand.parameters();
        assertEquals("CAPTURE_SEARCH", parameters.get("filterType"));
        assertEquals("M05", parameters.get("sourceModule"));
        @SuppressWarnings("unchecked")
        Map<String, Object> limits = (Map<String, Object>) parameters.get("limits");
        assertEquals(ExportService.MAX_ROWS, limits.get("maxRows"));
        assertEquals(ExportService.MAX_BYTES, limits.get("maxBytes"));
        assertEquals(ExportService.SHARD_ROWS, limits.get("shardRows"));
        assertEquals(ExportService.RETENTION_MONTHS, limits.get("retentionMonths"));
        assertEquals(ExportService.DOWNLOAD_TTL.toSeconds(), limits.get("downloadTtlSeconds"));
        assertNotNull(parameters.get("filterSnapshot"), "筛选快照必须冻结进任务参数");
    }

    @Test
    void createRejectsMissingPermission() {
        rbac.codes = List.of("capture:read");

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.PERMISSION_DENIED, failure.code());
        assertEquals(0, tasks.insertCalls);
    }

    @Test
    void createHidesContextOwnedBySomeoneElse() {
        contexts.row = Optional.of(context("CAPTURE_SEARCH", PLATE_FILTER, OTHER, NOW.plusHours(1)));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
    }

    @Test
    void createRejectsUnknownContext() {
        contexts.row = Optional.empty();

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.DATA_NOT_FOUND, failure.code());
    }

    @Test
    void createRejectsExpiredContext() {
        contexts.row = Optional.of(context("CAPTURE_SEARCH", PLATE_FILTER, OWNER, Instants.nowUtc().minusMinutes(1)));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.CONTEXT_EXPIRED, failure.code());
    }

    @Test
    void createRejectsUnboundedGeneralRange() {
        contexts.row = Optional.of(context("GENERAL", UNBOUNDED_GENERAL_FILTER, OWNER, Instants.nowUtc().plusHours(1)));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.DATA_QUERY_TOO_BROAD, failure.code());
        assertEquals(0, tasks.insertCalls);
    }

    @Test
    void createAcceptsBoundedGeneralRangeAcrossDataTypes() {
        contexts.row = Optional.of(context("GENERAL", GENERAL_FILTER, OWNER, Instants.nowUtc().plusHours(1)));

        TaskCreatedView created = service.create(session(OWNER), command(ExportFormat.ZIP, null, null));

        assertNotNull(created.taskId());
        List<String> columns = jobs.inserts.get(0).columns();
        assertTrue(columns.contains("imsiMasked"), "FENCE 专有列应在并集里");
        assertTrue(columns.contains("plateMasked"), "PLATE 专有列应在并集里");
        assertTrue(!columns.contains("faceImageAssetId"), "未声明的 FACE 不应出现");
    }

    @Test
    void createRejectsUnsupportedContextType() {
        contexts.row = Optional.of(context("PROFILE", "{\"filterType\":\"PROFILE\",\"filterVersion\":1}",
                OWNER, Instants.nowUtc().plusHours(1)));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, null, null)));

        assertEquals(ErrorCode.DATA_INVALID_FILTER, failure.code());
    }

    @Test
    void createRejectsUnknownColumn() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, List.of("fullPlate"), null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
        assertEquals("columns", failure.details().get(0).field());
    }

    @Test
    void createRejectsColumnFromAnotherDataType() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, List.of("faceImageAssetId"), null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
    }

    @Test
    void createRejectsEmptyColumnList() {
        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.XLSX, List.of(), null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
    }

    @Test
    void createKeepsRequestedColumnOrderAndDeduplicates() {
        TaskCreatedView created = service.create(session(OWNER),
                command(ExportFormat.XLSX, List.of("plateMasked", "captureId", "plateMasked"), null));

        assertNotNull(created.taskId());
        assertEquals(List.of("plateMasked", "captureId"), jobs.inserts.get(0).columns());
    }

    @Test
    void createReplaysSameKeyWithSameParameters() {
        TaskCreatedView first = service.create(session(OWNER), command(ExportFormat.XLSX, null, null));
        TaskCreatedView second = service.create(session(OWNER), command(ExportFormat.XLSX, null, null));

        assertEquals(first.taskId(), second.taskId());
        assertEquals(1, tasks.insertCalls, "重复提交不应再插任务");
    }

    @Test
    void createRejectsSameKeyWithDifferentParameters() {
        service.create(session(OWNER), command(ExportFormat.XLSX, null, null));

        ApiException failure = assertThrows(ApiException.class,
                () -> service.create(session(OWNER), command(ExportFormat.CSV, null, null)));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.code());
    }

    private static AuthenticatedSession session(UUID userId) {
        return new AuthenticatedSession(userId, UUID.randomUUID(), NOW.plusMinutes(15), NOW.plusHours(8), null);
    }

    private static ContextRow context(String filterType, String snapshot, UUID owner, OffsetDateTime expiresAt) {
        return new ContextRow(CONTEXT_ID, owner, "M05", filterType, 1L, snapshot, 7L, expiresAt);
    }

    private static ExportService.ExportCommand command(ExportFormat format, List<String> columns, String purpose) {
        return new ExportService.ExportCommand(CONTEXT_ID, format, columns, purpose, KEY, TRACE);
    }

    static TaskRow row(UUID id, TaskStatus status, TaskCommand command) {
        return new TaskRow(id, command.name(), command.taskType(), command.ownerUserId(), status, 0, 5,
                command.parameterSchemaVersion(), command.parameterJson(), command.parameterHash(),
                command.idempotencyKey(), command.resourceType(), command.resourceId(),
                NOW, null, null, null, null, 0L, NOW, null, 0L, 0L,
                null, null, null, command.traceId(), false, 0L, NOW);
    }

    static final class StubTasks extends AsyncTaskRepository {

        int insertCalls;
        TaskCommand insertedCommand;
        Optional<TaskRow> byId = Optional.empty();
        final List<EventWrite> events = new ArrayList<>();

        StubTasks() {
            super(new JdbcTemplate());
        }

        @Override
        public void insert(UUID id, TaskCommand command, OffsetDateTime now) {
            insertCalls++;
            insertedCommand = command;
            byId = Optional.of(row(id, TaskStatus.PENDING, command));
        }

        @Override
        public Optional<TaskRow> findById(UUID id) {
            return byId;
        }

        @Override
        public Optional<TaskRow> findByIdempotencyKey(UUID ownerUserId, com.xxplatform.api.service.task.TaskType taskType, String key) {
            return byId;
        }

        @Override
        public void insertEvent(UUID id, UUID taskId, long attemptNo, TaskStatus previousStatus,
                                TaskStatus status, OffsetDateTime occurredAt, String reason, String traceId) {
            events.add(new EventWrite(taskId, previousStatus, status));
        }
    }

    static final class StubContexts extends AnalysisContextRepository {

        Optional<ContextRow> row = Optional.empty();

        StubContexts() {
            super(new JdbcTemplate());
        }

        @Override
        public Optional<ContextRow> findById(UUID contextId) {
            return row;
        }
    }

    static final class StubJobs extends ExportJobRepository {

        final List<Insert> inserts = new ArrayList<>();

        StubJobs() {
            super(new JdbcTemplate());
        }

        @Override
        public void insert(UUID taskId, String sourceType, String sourceSchemaVersion, String sourceSnapshotJson,
                           String format, List<String> columns, String exportScope, String purpose,
                           OffsetDateTime createdAt) {
            inserts.add(new Insert(taskId, sourceType, sourceSchemaVersion, sourceSnapshotJson, format,
                    List.copyOf(columns), exportScope, purpose, createdAt));
        }

        record Insert(UUID taskId, String sourceType, String sourceSchemaVersion, String sourceSnapshot,
                      String format, List<String> columns, String exportScope, String purpose,
                      OffsetDateTime createdAt) {
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

    record EventWrite(UUID taskId, TaskStatus previousStatus, TaskStatus status) {
    }
}
