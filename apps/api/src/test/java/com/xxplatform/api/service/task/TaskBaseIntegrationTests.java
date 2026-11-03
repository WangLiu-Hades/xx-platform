package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 真库集成测试：用真实 PostgreSQL 验证"触发器 + 唯一约束 + 行锁"这三件单测替代不了的东西。
 *
 * <p>为什么默认跳过：{@code mvn test} 不该要求本机有库。设置 {@code XX_TASK_IT_DB_URL}
 * （例如 {@code jdbc:postgresql://127.0.0.1:55435/xxplatform}）并配上
 * {@code XX_TASK_IT_DB_USER}／{@code XX_TASK_IT_DB_PASSWORD} 才会执行。
 *
 * <p>为什么用真实迁移而不是建表脚本：{@code guard_task_transition()} 触发器与
 * {@code UNIQUE (owner_user_id, task_type, idempotency_key)} 是本任务依赖的契约的一部分；
 * 用一份手写的表结构测试等于测试了个假库。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class TaskBaseIntegrationTests {

    private static final UUID OWNER = UUID.fromString("0192f000-0000-7000-8000-00000000b001");

    private static ConfigurableApplicationContext context;
    private static TaskService service;
    private static TaskProperties properties;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startContext() {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        String user = System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", "");
        // reaper 关掉：测试自己调用 reapExpiredLeases，后台线程会让断言不可重复。
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("db")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + password,
                        "--xx.task.reaper-enabled=false");
        service = context.getBean(TaskService.class);
        properties = context.getBean(TaskProperties.class);
        jdbc = context.getBean(JdbcTemplate.class);
        // async_task.owner_user_id 有指向 auth.app_user 的外键：任务归属必须是一个真实账号。
        jdbc.update("""
                INSERT INTO auth.app_user
                    (id, user_code, login_name, display_name, password_hash, password_changed_at)
                VALUES (?, 'IT-TASK', 'it.task', '任务底座集成测试', 'x', now())
                ON CONFLICT (id) DO NOTHING
                """, OWNER);
        // M05/M14 之后提交/发布要复核"当前授权"：底座用例的账号也必须是真的有权账号，
        // 否则这条基线测的就不是状态机而是权限门。授权是夹具，不是断言。
        jdbc.update("""
                INSERT INTO auth.permission
                    (code, name, module_code, description, risk_level, assignable)
                VALUES ('task:manage', 'task:manage', 'task', '集成测试授权 task:manage', 'HIGH', false)
                ON CONFLICT (code) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO auth.role (id, role_code, name, management_all_sites, data_all_sites,
                                       own_only, scope_policy_version)
                VALUES ('0192f000-0000-7000-8000-00000000b002', 'IT-TASK-ROLE', '任务底座集成测试角色',
                        false, false, false, 'v1')
                ON CONFLICT (id) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO auth.role_permission (role_id, permission_code)
                VALUES ('0192f000-0000-7000-8000-00000000b002', 'task:manage')
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO auth.user_role (user_id, role_id)
                VALUES (?, '0192f000-0000-7000-8000-00000000b002')
                ON CONFLICT DO NOTHING
                """, OWNER);
        jdbc.update("""
                INSERT INTO auth.role_permission(role_id,permission_code)
                 VALUES ('0192f000-0000-7000-8000-00000000b002','capture:export') ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO auth.user_scope (user_id, management_all_sites, data_all_sites, own_only,
                                             scope_version, policy_version)
                VALUES (?, false, false, false, 1, 'v1')
                ON CONFLICT (user_id) DO NOTHING
                """, OWNER);
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 每个用例从空表开始：{@code claimNext} 是全局队列，若不清场，用例会领到别的用例留下的任务，
     * 断言"我领到的是我建的那条"就会随机失败。
     */
    @BeforeEach
    void cleanQueue() {
        jdbc.update("DELETE FROM analysis.task_event");
        jdbc.update("DELETE FROM analysis.task_attempt");
        // 领域子表有 task_id 外键指向 async_task；async_task.result_asset_id 又指向 file_asset。
        // 顺子清：子表 -> async_task -> file_asset。只删 async_task 会被 fk_analysis_export_job_task_id 挡住。
        jdbc.update("DELETE FROM analysis.export_job");
        jdbc.update("DELETE FROM analysis.import_job");
        jdbc.update("DELETE FROM analysis.analysis_job");
        jdbc.update("DELETE FROM analysis.set_job");
        jdbc.update("DELETE FROM analysis.face_search_job");
        jdbc.update("DELETE FROM analysis.model_execution");
        jdbc.update("DELETE FROM analysis.profile_revision_member");
        jdbc.update("DELETE FROM analysis.profile_revision");
        jdbc.update("DELETE FROM operation.report_job");
        jdbc.update("DELETE FROM capture.ingestion_batch");
        jdbc.update("DELETE FROM analysis.async_task");
        jdbc.update("DELETE FROM core.file_asset");
        jdbc.update("DELETE FROM core.idempotency_request");
    }

    @Test
    void duplicateIdempotencyKeyCreatesOneTaskAndDifferentPayloadConflicts() {
        String key = "it-idem-" + UUID.randomUUID();
        TaskCommand first = command(key, Map.of("format", "csv"));

        TaskRow created = service.create(first);
        TaskRow replayed = service.create(command(key, Map.of("format", "csv")));

        assertEquals(created.id(), replayed.id());
        assertEquals(1, countTasks(key));
        ApiException conflict = assertThrows(ApiException.class,
                () -> service.create(command(key, Map.of("format", "xlsx"))));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());
        assertEquals(1, countTasks(key));
    }

    @Test
    void claimAndFinishDriveTheTriggerBackedStateMachine() {
        TaskRow created = service.create(command("it-run-" + UUID.randomUUID(), Map.of()));
        AsyncTaskRepository.ClaimedTask claimed = service.claimNext("it-worker-1").orElseThrow();

        assertEquals(created.id(), claimed.task().id());
        assertEquals(TaskStatus.RUNNING, claimed.task().status());
        assertEquals(1L, claimed.task().currentAttemptNo());
        assertTrue(claimed.task().leaseUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));

        // M08：EXPORT 的成功结果必须挂 fileAssetId。这里补的是"任务真的产出了一个文件"这个
        // 领域事实（夹具），断言本身没有变——没有产物的 EXPORT 不允许发布 SUCCESS。
        UUID assetId = attachExportArtifact(created.id(), OWNER);

        TaskRow finished = service.finish(created.id(), "it-worker-1", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, assetId);

        assertEquals(TaskStatus.SUCCESS, finished.status());
        assertEquals(100, finished.progress());
        assertEquals(1, countAttempts(created.id(), "SUCCESS"));
    }

    @Test
    void finishedTaskCannotBeClaimedAgain() {
        TaskRow created = service.create(command("it-terminal-" + UUID.randomUUID(), Map.of()));
        AsyncTaskRepository.ClaimedTask claimed = service.claimNext("it-worker-2").orElseThrow();
        service.finish(created.id(), "it-worker-2", claimed.task().leaseGeneration(),
                TaskStatus.FAILED, "BOOM", "模拟失败", null);

        // 队列里可能还有别的测试留下的任务，但这条已终态，绝不能再被领走。
        assertTrue(jdbc.queryForObject(
                "SELECT status = 'FAILED' AND finished_at IS NOT NULL FROM analysis.async_task WHERE id = ?",
                Boolean.class, created.id()));
    }

    @Test
    void reaperReclaimsExpiredLeaseAndClosesTheAttempt() {
        TaskRow created = service.create(command("it-reap-" + UUID.randomUUID(), Map.of()));
        AsyncTaskRepository.ClaimedTask claimed = service.claimNext("it-worker-3").orElseThrow();
        expireLease(created.id());

        int reaped = service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));

        assertTrue(reaped >= 1);
        TaskRow after = service.get(created.id(), OWNER);
        assertEquals(TaskStatus.QUEUED, after.status());
        assertEquals(TaskService.LEASE_EXPIRED, after.errorCode());
        assertEquals(1, countAttempts(created.id(), "FAILED"));
        assertTrue(after.nextRunAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void reaperFailsTaskOnceAttemptsAreExhausted() {
        TaskRow created = service.create(command("it-exhaust-" + UUID.randomUUID(), Map.of()));
        AsyncTaskRepository.ClaimedTask claimed = service.claimNext("it-worker-4").orElseThrow();
        expireLease(created.id());

        int original = properties.getMaxAttempts();
        properties.setMaxAttempts(1);
        try {
            service.reapExpiredLeases(OffsetDateTime.now(ZoneOffset.UTC));
        } finally {
            properties.setMaxAttempts(original);
        }

        TaskRow after = service.get(created.id(), OWNER);
        assertEquals(TaskStatus.FAILED, after.status());
        assertEquals(TaskService.ATTEMPTS_EXHAUSTED, after.errorCode());
    }

    @Test
    void cancelAndRetryFollowTheContractSemantics() {
        TaskRow created = service.create(command("it-cancel-" + UUID.randomUUID(), Map.of()));
        TaskService.CancelOutcome cancelled = service.cancel(created.id(), OWNER);
        assertTrue(cancelled.immediate());
        assertEquals(TaskStatus.CANCELLED, cancelled.task().status());
        // 重复取消返回既有行，不再产生新的迁移（否则触发器会拒绝 CANCELLED -> CANCELLED 以外的写入）。
        assertEquals(TaskStatus.CANCELLED, service.cancel(created.id(), OWNER).task().status());

        TaskRow failing = service.create(command("it-retry-" + UUID.randomUUID(), Map.of()));
        AsyncTaskRepository.ClaimedTask claimed = service.claimNext("it-worker-5").orElseThrow();
        service.finish(failing.id(), "it-worker-5", claimed.task().leaseGeneration(),
                TaskStatus.FAILED, "BOOM", "模拟失败", null);

        TaskRow retried = service.retry(failing.id(), OWNER);
        assertEquals(TaskStatus.QUEUED, retried.status());
        assertEquals(1, retried.retryCount());
        assertNotEquals(created.id(), retried.id());
    }

    private static TaskCommand command(String key, Map<String, Object> parameters) {
        return new TaskCommand(OWNER, TaskType.EXPORT, "集成测试导出", "export.v1",
                new LinkedHashMap<>(parameters), key, "it-trace", null, null, null);
    }

    private static void expireLease(UUID taskId) {
        jdbc.update("UPDATE analysis.async_task SET lease_until = now() - interval '1 second' WHERE id = ?", taskId);
    }

    private static int countTasks(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.async_task WHERE idempotency_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    private static int countAttempts(UUID taskId, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.task_attempt WHERE task_id = ? AND result = ?",
                Integer.class, taskId, result);
        return count == null ? 0 : count;
    }

    /**
     * 给 EXPORT 任务补一条"真实产出"：领域行 {@code analysis.export_job} 指向一个 ACTIVE 的
     * {@code core.file_asset}。M08 要求 EXPORT 的公开结果必须带 fileAssetId，所以状态机用例
     * 也得有这份产物，否则测到的只是"发布被复核拦下"。
     */
    private static UUID attachExportArtifact(UUID taskId, UUID owner) {
        UUID assetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO core.file_asset
                    (id, storage_key, file_name, category, mime_type, byte_size, sha256,
                     owner_user_id, security_class, status, expires_at, trace_id)
                VALUES (?, ?, 'export.csv', 'EXPORT', 'text/csv', 1, repeat('a', 64),
                        ?, 'L3', 'ACTIVE', now() + interval '1 day', 'it-trace')
                """, assetId, "it-export-" + assetId, owner);
        jdbc.update("""
                INSERT INTO analysis.export_job
                    (task_id, source_type, source_schema_version, source_snapshot, format,
                     export_scope, output_file_id)
                VALUES (?, 'FILESET', '1', '{}'::jsonb, 'CSV', 'ALL', ?)
                ON CONFLICT (task_id) DO NOTHING
                """, taskId, assetId);
        return assetId;
    }
}
