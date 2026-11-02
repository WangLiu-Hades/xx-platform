package com.xxplatform.api.service.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 任务真库集成测试的公共底座（M05/M06/M07/M08/M14/M15/M16）。
 *
 * <p><b>为什么每个类都自己 seed 账号与权限，而不是共用 {@link TaskBaseIntegrationTests} 的 OWNER</b>：
 * 权限复核是这一轮新增的行为——同一个 OWNER 被"清权限"的用例会影响别的类的成功路径。每个用例
 * 用一个随机 UUID 的 owner（并自建 role/user_role），既天然隔离，也能真实地"只收回某个人的权限"。
 *
 * <p><b>为什么清场要删全部任务</b>：{@code claimNext} 是全局队列，不清场会领到别的用例留下的任务，
 * 断言"我领到的是我建的那条"会随机失败。surefire 默认单线程跑测试类，删全表是安全的。
 */
abstract class TaskIntegrationTestSupport {

    private static final Map<String, ConfigurableApplicationContext> CONTEXTS = new java.util.HashMap<>();

    protected static ConfigurableApplicationContext context;
    protected static TaskService taskService;
    protected static TaskWriteService taskWriteService;
    protected static TaskViewMapper mapper;
    protected static TaskProperties properties;
    protected static JdbcTemplate jdbc;
    protected static AsyncTaskRepository repository;
    protected static ObjectMapper json;
    protected static int httpPort;

    /**
     * 启动（或复用）一个应用上下文。
     *
     * <p>同一个 JVM 里按参数 key 复用：不同类共享同一套配置时只启动一次，避免每个类 3–5 秒的
     * Spring 启动把真库测试拖成分钟级。
     */
    protected static synchronized void boot(String... extraArgs) {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("需要 XX_TASK_IT_DB_URL 指向真库");
        }
        String user = System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", "");
        String key = String.join("\u0000", extraArgs);
        context = CONTEXTS.get(key);
        if (context == null || !context.isActive()) {
            context = new SpringApplicationBuilder(ApiApplication.class)
                    .profiles("db")
                    .run(concat(new String[] {
                            "--server.port=0",
                            "--spring.datasource.url=" + url,
                            "--spring.datasource.username=" + user,
                            "--spring.datasource.password=" + password,
                            // reaper 关掉：各用例自己调用 reap/heartbeat，后台线程会让断言不可重复。
                            "--xx.task.reaper-enabled=false",
                            // 后台 profile 的 application.yml 仍写着 2m（待改配置）；这里先把契约值
                            // 60 秒钉住，避免"配置没改=测试跟着漂"。
                            "--xx.task.lease=60s"
                    }, extraArgs));
            CONTEXTS.put(key, context);
        }
        taskService = context.getBean(TaskService.class);
        taskWriteService = context.getBean(TaskWriteService.class);
        mapper = context.getBean(TaskViewMapper.class);
        properties = context.getBean(TaskProperties.class);
        jdbc = context.getBean(JdbcTemplate.class);
        repository = context.getBean(AsyncTaskRepository.class);
        json = context.getBean(ObjectMapper.class);
        httpPort = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }

    private static String[] concat(String[] left, String[] right) {
        String[] all = new String[left.length + right.length];
        System.arraycopy(left, 0, all, 0, left.length);
        System.arraycopy(right, 0, all, left.length, right.length);
        return all;
    }

    @BeforeEach
    void cleanQueue() {
        jdbc.update("DELETE FROM analysis.task_event");
        jdbc.update("DELETE FROM analysis.task_attempt");
        jdbc.update("DELETE FROM analysis.profile_revision_member");
        jdbc.update("DELETE FROM analysis.profile_revision");
        jdbc.update("DELETE FROM analysis.model_execution");
        jdbc.update("DELETE FROM analysis.analysis_job");
        jdbc.update("DELETE FROM analysis.set_job");
        jdbc.update("DELETE FROM analysis.face_search_job");
        jdbc.update("DELETE FROM analysis.import_job");
        jdbc.update("DELETE FROM analysis.export_job");
        jdbc.update("DELETE FROM operation.report_job");
        jdbc.update("DELETE FROM capture.ingestion_batch");
        // 顺序：先子表（export_job/import_job/... 有 task_id 外键）→ async_task（它有
        // result_asset_id 指向 file_asset）→ file_asset。反过来会撞
        // fk_analysis_export_job_task_id 或 fk_analysis_async_task_result_asset_id。
        jdbc.update("DELETE FROM analysis.async_task");
        jdbc.update("DELETE FROM core.file_asset");
        jdbc.update("DELETE FROM core.idempotency_request");
    }

    /**
     * 建一个带 {@code task:manage} 的账号，返回 userId。
     *
     * <p>登录态用 {@link #issueSession(UUID)} 单独发：任务用例大多走 service 层，只有"真实 HTTP
     * 响应"用例才需要 Cookie。
     */
    protected static UUID seedOwner(String tag) {
        java.util.Set<String> permissions = new java.util.HashSet<>(List.of(TaskScopeGuard.PERMISSION, "task:read"));
        for (TaskType type : TaskType.values()) permissions.add(TaskScopeGuard.permissionFor(type));
        return seedOwner(tag, List.copyOf(permissions));
    }

    /** 建一个只有指定权限码的账号（用于"缩权/无权限"用例）。 */
    protected static UUID seedOwner(String tag, List<String> permissions) {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        String suffix = userId.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO auth.app_user
                    (id, user_code, login_name, display_name, password_hash, password_changed_at)
                VALUES (?, ?, ?, ?, 'x', now())
                """, userId, "IT-" + tag + "-" + suffix, "it." + tag + "." + suffix, "集成测试 " + tag);
        jdbc.update("""
                INSERT INTO auth.role (id, role_code, name, management_all_sites, data_all_sites, own_only,
                                       scope_policy_version)
                VALUES (?, ?, ?, true, true, false, 'v1')
                """, roleId, "IT-" + tag + "-" + suffix, "集成测试角色 " + tag);
        for (String code : permissions) {
            jdbc.update("""
                    INSERT INTO auth.permission
                        (code, name, module_code, description, risk_level, assignable)
                    VALUES (?, ?, 'task', ?, 'HIGH', false)
                    ON CONFLICT (code) DO NOTHING
                    """, code, code, "集成测试授权 " + code);
            jdbc.update("""
                    INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """, roleId, code);
        }
        jdbc.update("INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        jdbc.update("UPDATE auth.app_user SET authz_version=1 WHERE id=?", userId);
        for (String type : List.of("FENCE", "MAC", "PLATE", "FACE"))
            jdbc.update("INSERT INTO auth.role_data_type(role_id,data_type) VALUES (?,?)", roleId, type);
        jdbc.update("""
                INSERT INTO auth.user_scope (user_id, management_all_sites, data_all_sites, own_only,
                                             scope_version, policy_version)
                VALUES (?, false, false, false, 1, 'v1')
                """, userId);
        return userId;
    }

    /** 追加一条授权码（缩权用例的反向操作：先有再收回）。 */
    protected static void grant(UUID userId, String permissionCode) {
        UUID roleId = jdbc.queryForObject(
                "SELECT role_id FROM auth.user_role WHERE user_id = ?", UUID.class, userId);
        jdbc.update("""
                INSERT INTO auth.permission
                    (code, name, module_code, description, risk_level, assignable)
                VALUES (?, ?, 'task', ?, 'HIGH', false) ON CONFLICT (code) DO NOTHING
                """, permissionCode, permissionCode, "集成测试授权 " + permissionCode);
        jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", roleId, permissionCode);
    }

    /** 收回授权码（"claim 之后缩权"的用例用）。 */
    protected static void revoke(UUID userId, String permissionCode) {
        jdbc.update("""
                DELETE FROM auth.role_permission
                 WHERE permission_code = ? AND role_id IN (SELECT role_id FROM auth.user_role WHERE user_id = ?)
                """, permissionCode, userId);
    }

    /** 发一个登录态，返回 Cookie 头值（{@code SID=...}）。 */
    protected static String issueSession(UUID userId) {
        UUID sessionId = UUID.randomUUID();
        String sid = "it-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auth.user_session (id, user_id, sid_hash, last_seen_at, idle_expires_at,
                                               absolute_expires_at, client_label, trace_id)
                VALUES (?, ?, ?, now(), now() + interval '1 hour', now() + interval '1 day', NULL, 'it-trace')
                """, sessionId, userId, com.xxplatform.api.common.Sha256.of(sid));
        jdbc.update("UPDATE auth.user_session SET client_label=? WHERE id=?",
                com.xxplatform.api.service.LoginService.clientBinding("Java-http-client/" + System.getProperty("java.version"), "127.0.0.1"), sessionId);
        return "SID=" + sid;
    }

    /** 构造一条任务命令；优先取默认优先级（50），需要排序用例时用重载。 */
    protected static TaskCommand command(UUID owner, TaskType type, Map<String, Object> parameters, String key) {
        return command(owner, type, parameters, key, null);
    }

    protected static TaskCommand command(UUID owner, TaskType type, Map<String, Object> parameters,
                                         String key, Integer priority) {
        String schema = switch (type) {
            case EXPORT -> "export.v1";
            case ANALYSIS -> "analysis.v1";
            case DEVICE_COMMAND -> "device-command.v1";
            default -> type.name().toLowerCase(java.util.Locale.ROOT) + ".v1";
        };
        return new TaskCommand(owner, type, "集成测试 " + type, schema,
                parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters),
                key, "it-trace", priority, null, null);
    }

    protected static TaskCommand command(UUID owner, Map<String, Object> parameters, String key) {
        return command(owner, TaskType.EXPORT, parameters, key);
    }

    protected static String newKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * 领取指定任务。
     *
     * <p>{@code claimNext} 是全局队列（按 priority/submittedAt 排序），并行测试留下的待执行任务
     * 可能被先领到。这里把"顺手领到的别人任务"改成 CANCELLED 再继续——集成测试库是独占的，
     * 这样既不会让用例依赖执行顺序，也不会让领错的任务一直占着 RUNNING 配额。
     */
    protected static ClaimedTask claim(UUID taskId, String workerId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            ClaimedTask claimed = taskService.claimNext(workerId).orElseThrow(
                    () -> new AssertionError("期望领到任务 " + taskId + "，实际队列为空"));
            if (claimed.task().id().equals(taskId)) {
                return claimed;
            }
            taskService.finish(claimed.task().id(), workerId, claimed.task().leaseGeneration(),
                    TaskStatus.CANCELLED, "TEST_NOISE", "集成测试清理：非目标任务", null);
        }
        throw new AssertionError("连续 200 次都没领到任务 " + taskId + "，队列可能被其他用例占满");
    }

    /** 把租约改成"已过期"，模拟 worker 崩溃后时间流逝。 */
    protected static void expireLease(UUID taskId) {
        jdbc.update("UPDATE analysis.async_task SET lease_until = now() - interval '1 second' WHERE id = ?",
                taskId);
    }

    protected static void backdateSubmitted(UUID taskId, Duration age) {
        jdbc.update("UPDATE analysis.async_task SET submitted_at = now() - make_interval(secs => ?) WHERE id = ?",
                (double) age.toSeconds(), taskId);
    }

    protected static void backdateStarted(UUID taskId, Duration age) {
        jdbc.update("""
                UPDATE analysis.async_task
                   SET started_at = now() - make_interval(secs => ?),
                       lease_until = now() + interval '60 seconds'
                 WHERE id = ?
                """, (double) age.toSeconds(), taskId);
    }

    protected static TaskRow reload(UUID taskId) {
        return repository.findById(taskId).orElseThrow();
    }

    protected static String taskStatus(UUID taskId) {
        return jdbc.queryForObject("SELECT status FROM analysis.async_task WHERE id = ?", String.class, taskId);
    }

    protected static String taskErrorCode(UUID taskId) {
        return jdbc.queryForObject("SELECT error_code FROM analysis.async_task WHERE id = ?", String.class, taskId);
    }

    protected static UUID taskResultAsset(UUID taskId) {
        return jdbc.queryForObject("SELECT result_asset_id FROM analysis.async_task WHERE id = ?", UUID.class, taskId);
    }

    protected static int countAttempts(UUID taskId, String result) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.task_attempt WHERE task_id = ? AND result = ?",
                Integer.class, taskId, result);
        return count == null ? 0 : count;
    }

    protected static int countEvents(UUID taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.task_event WHERE task_id = ?", Integer.class, taskId);
        return count == null ? 0 : count;
    }

    protected static int countTasksWithKey(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.async_task WHERE idempotency_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    protected static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 插一个当前用户拥有的可下载文件资产（EXPORT 结果发布用）。 */
    protected static UUID seedFileAsset(UUID owner, String status) {
        UUID assetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO core.file_asset (id, storage_key, file_name, category, mime_type, byte_size,
                                             sha256, owner_user_id, security_class, status, trace_id, expires_at)
                VALUES (?, ?, 'export.xlsx', 'EXPORT', 'application/vnd.openxmlformats-officedocument'
                        || '.spreadsheetml.sheet', 1024, ?, ?, 'INTERNAL', ?, 'it-trace', now()+interval '12 months')
                """, assetId, "it/" + assetId, "0".repeat(64), owner, status);
        return assetId;
    }

    /** 直接改任务优先级（创建时参数里没有 priority，排序用例用它造不同的值）。 */
    protected static void setPriority(UUID taskId, int priority) {
        jdbc.update("UPDATE analysis.async_task SET priority = ? WHERE id = ?", priority, taskId);
    }

    /** 让 {@code QUEUED} 任务"看起来已经排了一会儿"（排队时限用例用）。 */
    protected static void setSubmittedAt(UUID taskId, OffsetDateTime at) {
        jdbc.update("UPDATE analysis.async_task SET submitted_at = ? WHERE id = ?", at, taskId);
    }

    protected static void setStartedAt(UUID taskId, OffsetDateTime at) {
        jdbc.update("UPDATE analysis.async_task SET started_at = ? WHERE id = ?", at, taskId);
    }

    /** 该 owner 当前 RUNNING 的重任务条数（配额断言用，口径与仓储的 HEAVY_TASK_TYPES 一致）。 */
    protected static long runningHeavy(UUID owner) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM analysis.async_task
                 WHERE owner_user_id = ? AND deleted = false AND status = 'RUNNING'
                   AND task_type IN ('EXPORT','INGESTION','BACKFILL','ANALYSIS','DATASET_IMPORT',
                                     'MODEL_RUN','PROFILE_MERGE','PROFILE_SPLIT','SET_OPERATION','REPORT')
                """, Long.class, owner);
        return count == null ? 0 : count;
    }

    protected static long queuedOf(UUID owner) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM analysis.async_task
                 WHERE owner_user_id = ? AND deleted = false AND status = 'QUEUED'
                """, Long.class, owner);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------- 领域行（M08 结果引用 / M14 范围）

    /**
     * 清掉任务及其领域行。配额/并发用例必须从干净队列出发：上一个用例留下的 RUNNING 任务会
     * 占住每用户与系统槽位，导致后面的断言测到的是"被前面用例挤住"而不是被测逻辑。
     */
    protected static void resetTasks() {
        for (String table : List.of(
                "operation.delivery",
                "operation.notification_execution",
                "operation.notification_plan",
                "operation.device_command_job",
                "operation.report_job",
                "analysis.model_execution",
                "analysis.face_search_job",
                "analysis.profile_revision",
                "analysis.set_job",
                "analysis.analysis_job",
                "capture.ingestion_batch",
                "analysis.export_job",
                "analysis.import_job",
                "analysis.task_event",
                "analysis.task_attempt")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM analysis.async_task");
    }

    /** 插一个 Context；{@code permissionVersion} 与 {@code auth.user_scope.scope_version} 比对。 */
    protected static UUID seedContext(UUID owner, long permissionVersion, OffsetDateTime expiresAt) {
        UUID contextId = UUID.randomUUID();
        // analysis_context 的校验约束：source_module 必须是 M04..M13，filter_type 必须是
        // GENERAL/CAPTURE_SEARCH/DATASET/ANALYSIS_RESULTS/PROFILE，trace_id 必填，
        // 且 expires_at > created_at（过期用例要把 created_at 一起往前挪）。
        OffsetDateTime createdAt = expiresAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : expiresAt.minusMinutes(1);
        jdbc.update("""
                INSERT INTO analysis.analysis_context
                    (id, owner_user_id, source_module, filter_type, filter_schema_version,
                     filter_snapshot, permission_version, expires_at, trace_id, created_at)
                VALUES (?, ?, 'M09', 'GENERAL', 1,
                  '{"filterType":"GENERAL","filterVersion":1,"dataTypes":["PLATE"],"timeRange":{"from":"2026-10-01T00:00:00Z","to":"2026-10-02T00:00:00Z"}}'::jsonb,
                  ?, ?, 'it-trace', ?)
                """, contextId, owner, permissionVersion, expiresAt, createdAt);
        return contextId;
    }

    /** 把账号的授权版本推高，模拟"缩权后 Context 的 permissionVersion 已落后"。 */
    protected static void bumpScopeVersion(UUID owner, long version) {
        jdbc.update("UPDATE auth.user_scope SET scope_version = ? WHERE user_id = ?", version, owner);
        jdbc.update("UPDATE auth.app_user SET authz_version=? WHERE id=?", version, owner);
    }

    /** 改 Context 的授权版本（模拟管理员收缩该 Context 的范围）。 */
    protected static void setContextPermissionVersion(UUID contextId, long version) {
        jdbc.update("""
                UPDATE analysis.analysis_context SET permission_version = ? WHERE id = ?
                """, version, contextId);
    }

    protected static UUID seedSnapshot(UUID owner) {
        UUID snapshotId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis.input_snapshot
                    (id, owner_user_id, source_kind, source_schema_version, filter_snapshot,
                     status, storage_mode, permission_version)
                VALUES (?, ?, 'CONTEXT', '1', '{}'::jsonb, 'PREPARING', 'REFERENCES', 1)
                """, snapshotId, owner);
        return snapshotId;
    }

    /** 造一条 INGESTION 批次，返回 batchId（契约要求 INGESTION 的结果引用带 batchId）。 */
    protected static UUID seedIngestionBatch(UUID taskId) {
        // source_mapping_version 与 ingestion_batch 都有 FK 指向 capture.ingestion_source，先建源定义。
        jdbc.update("""
                INSERT INTO capture.ingestion_source
                    (source_code, name, source_kind, adapter_code, protocol_version, enabled)
                VALUES ('IT_SOURCE', 'IT 源', 'FILE', 'csv', 'v1', true)
                ON CONFLICT (source_code) DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO capture.source_mapping_version
                    (source_code, mapping_version, schema_version, mapping_definition,
                     dedup_rule_version, effective_at, enabled)
                VALUES ('IT_SOURCE', 'v1', '1', '{}'::jsonb, 'd1', now(), true)
                ON CONFLICT (source_code, mapping_version) DO NOTHING
                """);
        UUID batchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO capture.ingestion_batch
                    (id, task_id, source_code, mapping_version, dedup_rule_version, trace_id)
                VALUES (?, ?, 'IT_SOURCE', 'v1', 'd1', 'it-trace')
                """, batchId, taskId);
        return batchId;
    }

    /** 造一条集合运算作业，返回 setTaskId（契约要求 SET_OPERATION 的 resultId 是它）。 */
    protected static UUID seedSetJob(UUID taskId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis.set_job
                    (id, task_id, name, operator, element_type, dedup_rule_version)
                VALUES (?, ?, 'it-set', 'UNION', 'CAPTURE', 'd1')
                """, id, taskId);
        return id;
    }

    /** 造一条报表作业，返回 reportId（契约要求 REPORT 的 resultId 是它，不是 taskId）。 */
    protected static UUID seedReportJob(UUID owner, UUID taskId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO operation.report_job
                    (id, task_id, name, mode, input_snapshot_id, output_format)
                VALUES (?, ?, 'it-report', 'SINGLE', ?, 'XLSX')
                """, id, taskId, seedSnapshot(owner));
        return id;
    }

    /** 造一条分析作业，返回 analysisTaskId（契约要求 ANALYSIS 的 resultId 是它）。 */
    protected static UUID seedAnalysisJob(UUID owner, UUID taskId, UUID contextId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis.analysis_job
                    (id, task_id, name, kind, context_id, input_snapshot_id)
                VALUES (?, ?, 'it-analysis', 'COMPANION', ?, ?)
                """, id, taskId, contextId, seedSnapshot(owner));
        return id;
    }

    protected static void seedFaceSearchJob(UUID owner, UUID taskId) {
        jdbc.update("""
                INSERT INTO analysis.face_search_job
                    (task_id, image_asset_id, input_snapshot_id, time_from, time_to, candidate_limit)
                VALUES (?, ?, ?, now() - interval '1 day', now(), 100)
                """, taskId, seedFileAsset(owner, "ACTIVE"), seedSnapshot(owner));
    }

    /** 造一条 MODEL_RUN 执行行，返回 modelId（契约要求 MODEL_RUN 必须返回 modelId）。 */
    protected static UUID seedModelRun(UUID owner, UUID taskId) {
        UUID modelId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis.trend_model
                    (id, code, name, model_type, effective_data_filter)
                VALUES (?, ?, 'it-model', 'RESIDENCE', '{}'::jsonb)
                """, modelId, "IT_MODEL_" + modelId.toString().substring(0, 8));
        UUID parameterVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO analysis.model_parameter_version
                    (id, model_id, version_no, effective_at, created_by, reason)
                VALUES (?, ?, 1, now(), ?, 'it')
                """, parameterVersionId, modelId, owner);
        jdbc.update("""
                INSERT INTO analysis.model_execution
                    (task_id, model_id, parameter_version_id, input_snapshot_id)
                VALUES (?, ?, ?, ?)
                """, taskId, modelId, parameterVersionId, seedSnapshot(owner));
        return modelId;
    }

    /** 造一条导出作业并挂上输出文件（EXPORT 结果引用必须是 fileAssetId）。 */
    protected static void seedExportJob(UUID taskId, UUID outputFileId) {
        jdbc.update("""
                INSERT INTO analysis.export_job
                    (task_id, source_type, source_schema_version, source_snapshot,
                     format, export_scope, output_file_id)
                VALUES (?, 'FILESET', '1', '{}'::jsonb, 'XLSX', 'CURRENT_VIEW', ?)
                """, taskId, outputFileId);
    }

    /** 造一条 PROFILE_MERGE/SPLIT 的 profile_revision（契约要求返回 profileIds）。 */
    protected static void seedProfileRevision(UUID owner, UUID taskId, List<UUID> profileIds, String action) {
        long revisionNo = 1;
        for (UUID profileId : profileIds) {
            jdbc.update("""
                    INSERT INTO analysis.profile (id, profile_type, display_label, source)
                    VALUES (?, 'PERSON', ?, 'IT')
                    """, profileId, "it-profile-" + profileId.toString().substring(0, 8));
            jdbc.update("""
                    INSERT INTO analysis.profile_revision
                        (id, profile_id, task_id, revision_no, action, reason, actor_user_id,
                         before_snapshot, after_snapshot, occurred_at, trace_id)
                    VALUES (?, ?, ?, ?, ?, 'it', ?, '{}'::jsonb, '{}'::jsonb, now(), 'it-trace')
                    """, UUID.randomUUID(), profileId, taskId, revisionNo++, action, owner);
        }
    }

    /**
     * 把所有非终态任务收成 CANCELLED（测试隔离用）。
     *
     * <p>{@code claimNext} 是全局队列：别的用例遗留的 RUNNING 会占住系统级并发槽位（尤其长 I/O），
     * 让本用例“本该领到”的任务变成空。每个 claim 类用例先清场，断言才可重复。
     */
    protected static void abandonAllNonTerminalTasks() {
        jdbc.update("""
                UPDATE analysis.async_task
                   SET status = 'CANCELLED', finished_at = now(), lease_owner = NULL, lease_until = NULL
                 WHERE status IN ('PENDING', 'QUEUED', 'RUNNING')
                """);
    }

    /** 断言抛出的 ApiException 的错误码。 */
    protected static com.xxplatform.api.common.ApiException assertCode(
            com.xxplatform.api.common.ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        com.xxplatform.api.common.ApiException failure =
                org.junit.jupiter.api.Assertions.assertThrows(com.xxplatform.api.common.ApiException.class, action);
        org.junit.jupiter.api.Assertions.assertEquals(expected, failure.code(),
                () -> "错误码不符: " + failure.getMessage());
        return failure;
    }
}
