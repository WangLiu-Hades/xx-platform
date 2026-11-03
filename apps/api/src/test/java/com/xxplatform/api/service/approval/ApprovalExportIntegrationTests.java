package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.ApprovalProperties;
import com.xxplatform.api.infra.PasswordHasher;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.ExportFormat;
import com.xxplatform.api.service.ExportService;
import com.xxplatform.api.service.ExportService.ExportCommand;
import com.xxplatform.api.service.TaskCreatedView;
import com.xxplatform.api.service.task.TaskParameters;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 真库集成测试（M09–M14 / M20）。
 *
 * <p>这里验证的正是单测替身做不到的部分：审批人统计走 auth.* 多表 JOIN、设备集合由
 * core.device_group_member 解析、并发消费依赖 SELECT ... FOR UPDATE 与条件更新、
 * 拒绝审计依赖 REQUIRES_NEW 独立事务在业务回滚后仍然提交。
 *
 * <p>未设置 XX_TASK_IT_DB_URL 时整类跳过（与 TaskBaseIntegrationTests 一致）。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class ApprovalExportIntegrationTests {

    /** 测试密钥只经命令行注入，不落仓库配置（M12）。 */
    private static final String PAYLOAD_KEY = Base64.getEncoder()
            .encodeToString("it-approval-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String PAYLOAD_KEY_2 = Base64.getEncoder()
            .encodeToString("it-approval-key-fedcba9876543210".getBytes(StandardCharsets.UTF_8));
    private static final String PASSWORD = "ItPassw0rd!2026";
    private static final String TRACE = "it-approval-trace";

    private static final UUID REQUESTER = UUID.fromString("0192f100-0000-7000-8000-00000000a001");
    private static final UUID APPROVER1 = UUID.fromString("0192f100-0000-7000-8000-00000000a002");
    private static final UUID APPROVER2 = UUID.fromString("0192f100-0000-7000-8000-00000000a003");
    /** 有动作权限、在审批人名单内，但没有任何数据范围：证明“有权不等于能批”。 */
    private static final UUID APPROVER_NOSCOPE = UUID.fromString("0192f100-0000-7000-8000-00000000a004");
    /** 有动作权限、有范围，但不在部署审批人名单：证明“有权不等于有审批资格”。 */
    private static final UUID OUTSIDER = UUID.fromString("0192f100-0000-7000-8000-00000000a005");

    private static final UUID SITE = UUID.fromString("0192f100-0000-7000-8000-00000000b001");
    private static final UUID GROUP = UUID.fromString("0192f100-0000-7000-8000-00000000c001");
    private static final UUID ROLE_ALL = UUID.fromString("0192f100-0000-7000-8000-00000000d001");
    private static final UUID ROLE_NOSCOPE = UUID.fromString("0192f100-0000-7000-8000-00000000d002");
    private static final UUID[] DEVICES = new UUID[10];

    static {
        for (int i = 0; i < DEVICES.length; i++) {
            DEVICES[i] = UUID.fromString(String.format("0192f100-0000-7000-8000-0000000e%04d", i + 1));
        }
    }

    private static ConfigurableApplicationContext context;
    private static ApprovalService approvals;
    private static ExportService exports;
    private static ApprovalProperties properties;
    private static ApprovalPayloadCipher cipher;
    private static PasswordHasher hasher;
    private static UserAccountRepository users;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startContext() throws java.io.IOException {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        String user = System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", "");
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("db")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + password,
                        "--xx.file.storage-root=" + java.nio.file.Files.createTempDirectory("pr3-published-file-"),
                        // M12：部署密钥只经显式参数注入；未注入时审批必须 fail closed。
                        "--xx.approval.payload-key=" + PAYLOAD_KEY,
                        "--xx.approval.approver-logins=it.approval.approver1,"
                                + "it.approval.approver2,it.approval.noscope",
                        "--xx.task.reaper-enabled=false");
        approvals = context.getBean(ApprovalService.class);
        exports = context.getBean(ExportService.class);
        properties = context.getBean(ApprovalProperties.class);
        cipher = context.getBean(ApprovalPayloadCipher.class);
        hasher = context.getBean(PasswordHasher.class);
        users = context.getBean(UserAccountRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);
        seed();
        properties.setDeviceProfiles(List.of(new ApprovalProperties.DeviceProfile("SIMULATOR", "SIMULATOR",
                "SIMULATOR_V1", java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(i -> "IT-SN-" + i).toList(),
                List.of("RESET", "APPLY_PARAMETERS"), Map.of("powerClassCode", "STANDARD", "privateKey", "KEY"))));
        properties.setApproverLogins(List.of(loginName(APPROVER1), loginName(APPROVER2),
                loginName(APPROVER_NOSCOPE)));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            // 共用同一个真库：导出任务必须先删 export_job 再删 async_task，否则 TaskBaseIntegrationTests
            // 的 DELETE FROM analysis.async_task 会撞 fk_analysis_export_job_task_id。
            cleanupExportTasks();
            context.close();
        }
    }

    private static void cleanupExportTasks() {
        if (jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM analysis.task_event WHERE task_id IN"
                + " (SELECT id FROM analysis.async_task WHERE idempotency_key LIKE 'it-export-%')");
        jdbc.update("DELETE FROM analysis.task_attempt WHERE task_id IN"
                + " (SELECT id FROM analysis.async_task WHERE idempotency_key LIKE 'it-export-%')");
        jdbc.update("DELETE FROM analysis.export_job WHERE task_id IN"
                + " (SELECT id FROM analysis.async_task WHERE idempotency_key LIKE 'it-export-%')");
        jdbc.update("DELETE FROM analysis.async_task WHERE idempotency_key LIKE 'it-export-%'");
    }

    @BeforeEach
    void resetAllowlist() {
        cleanupExportTasks();
        properties.setApproverLogins(List.of(loginName(APPROVER1), loginName(APPROVER2),
                loginName(APPROVER_NOSCOPE)));
    }

    // ------------------------------------------------------------------
    // M09 审批资格与人数
    // ------------------------------------------------------------------

    @Test
    void deviceGroupIsResolvedServerSideAndDrivesTwoVoteRequirement() {
        // 客户端只报 1 台（并给 groupId），服务端必须按设备组真实成员数算票。
        Map<String, Object> body = deviceCommandBody();
        body.put("deviceIds", List.of(DEVICES[0].toString()));

        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), body), "批量重置设备参数申请说明");

        assertEquals(2, created.get("requiredApprovals"), "10 台设备必须要求 2 名审批人");
        List<Map<String, Object>> items = reviewItems(created);
        long deviceItems = items.stream()
                .filter(item -> String.valueOf(item.get("fieldCode")).startsWith("frozen.deviceIds["))
                .count();
        assertEquals(10, deviceItems, "复核清单必须列出服务端解析出的 10 台设备，而不是客户端自报的 1 台");
        assertTrue(items.stream().anyMatch(item -> "frozen.deviceCount".equals(item.get("fieldCode"))));
    }

    @Test
    void actionPermissionAloneIsNotEnoughToVote() {
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), deviceCommandBody()), "批量重置设备参数申请说明");
        UUID approvalId = (UUID) created.get("approvalId");

        // 有 device:command、有范围，但不在部署审批人名单：连详情都不能看（404 隐藏存在性）。
        ApiException hidden = assertThrows(ApiException.class,
                () -> approvals.get(session(OUTSIDER), approvalId));
        assertEquals(ErrorCode.DATA_NOT_FOUND, hidden.code());

        // 有 device:command、有范围，但不在部署审批人名单：不能投票。
        ApiException outsider = assertThrows(ApiException.class, () -> approvals.decide(
                session(OUTSIDER), approvalId, "APPROVE", "我同意这次设备重置操作", PASSWORD));
        assertEquals(ErrorCode.PERMISSION_DENIED, outsider.code());
        assertTrue(outsider.getMessage().contains("名单"), outsider.getMessage());

        // 在名单里、有权限，但数据范围不覆盖目标设备。
        ApiException noScope = assertThrows(ApiException.class, () -> approvals.decide(
                session(APPROVER_NOSCOPE), approvalId, "APPROVE", "我同意这次设备重置操作", PASSWORD));
        assertEquals(ErrorCode.PERMISSION_DATA_SCOPE, noScope.code());

        // 名单 + 权限 + 范围三者齐备才计票。
        Map<String, Object> voted = approvals.decide(session(APPROVER1), approvalId,
                "APPROVE", "我同意这次设备重置操作", PASSWORD);
        assertEquals("PENDING", voted.get("status"));
        assertEquals(1, ((List<?>) voted.get("decisions")).size());
    }

    @Test
    void requesterAndExecutorCannotApproveTheirOwnRequest() {
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(contextFor(REQUESTER, 5))), "导出审批自批拒绝验证");
        UUID approvalId = (UUID) created.get("approvalId");

        ApiException self = assertThrows(ApiException.class, () -> approvals.decide(
                session(REQUESTER), approvalId, "APPROVE", "我自己批准自己的导出", PASSWORD));
        assertEquals(ErrorCode.PERMISSION_DENIED, self.code());
        assertTrue(self.getMessage().contains("申请人"));
    }

    @Test
    void duplicateVoteBySameReviewerIsNotCountedTwice() {
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), deviceCommandBody()), "批量重置设备参数申请说明");
        UUID approvalId = (UUID) created.get("approvalId");

        approvals.decide(session(APPROVER1), approvalId, "APPROVE", "第一次复核意见说明通过", PASSWORD);
        Map<String, Object> again = approvals.decide(session(APPROVER1), approvalId,
                "APPROVE", "第一次复核意见说明通过", PASSWORD);
        assertEquals("PENDING", again.get("status"), "1 票不能满足 2 票要求");
        assertEquals(1, ((List<?>) again.get("decisions")).size());
        assertEquals(1, countDecisions(approvalId));

        Map<String, Object> approved = approvals.decide(session(APPROVER2), approvalId,
                "APPROVE", "第二名审批人复核同意", PASSWORD);
        assertEquals("APPROVED", approved.get("status"));
        assertNotNull(approved.get("executionGrantExpiresAt"));
    }

    @Test
    void insufficientEligibleApproversRejectsCreation() {
        properties.setApproverLogins(List.of(loginName(APPROVER1)));
        try {
            ApiException error = assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                    proposal("createDeviceCommand", Map.of(), deviceCommandBody()), "批量重置设备参数申请说明"));
            assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, error.code());
            assertTrue(error.getMessage().contains("审批人数不足"), error.getMessage());
        } finally {
            properties.setApproverLogins(List.of(loginName(APPROVER1), loginName(APPROVER2),
                    loginName(APPROVER_NOSCOPE)));
        }
    }

    // ------------------------------------------------------------------
    // M10 冻结与执行前复核
    // ------------------------------------------------------------------

    @Test
    void ifMatchMustMatchTheFrozenDeviceVersion() {
        UUID deviceId = DEVICES[0];
        Map<String, Object> stale = proposal("updateDeviceParameters",
                Map.of("deviceId", deviceId.toString()),
                Map.of("changes", List.of(Map.of("parameterCode", "powerClassCode", "valueType", "TEXT", "textValue", "P2")), "reason", "调整设备供电等级参数值"));
        stale.put("ifMatch", "\"99\"");
        ApiException error = assertThrows(ApiException.class,
                () -> approvals.create(session(REQUESTER), stale, "调整单台设备参数申请"));
        assertEquals(ErrorCode.RESOURCE_VERSION_CONFLICT, error.code());
    }

    @Test
    void unknownTargetDeviceCannotBeFrozenIntoAnApproval() {
        UUID missing = UUID.fromString("0192f100-0000-7000-8000-00000000ff99");
        Map<String, Object> proposal = proposal("updateDeviceParameters",
                Map.of("deviceId", missing.toString()),
                Map.of("changes", List.of(Map.of("parameterCode", "powerClassCode", "valueType", "TEXT", "textValue", "P2")), "reason", "调整设备供电等级参数值"));
        proposal.put("ifMatch", "\"0\"");

        long before = count("SELECT count(*) FROM operation.action_approval WHERE operation_id = ?"
                + " AND requester_user_id = ?", "updateDeviceParameters", REQUESTER);
        ApiException error = assertThrows(ApiException.class,
                () -> approvals.create(session(REQUESTER), proposal, "调整单台设备参数申请"));
        assertEquals(ErrorCode.DATA_NOT_FOUND, error.code());
        assertTrue(error.getMessage().contains("不存在"), error.getMessage());
        assertEquals(before, count("SELECT count(*) FROM operation.action_approval WHERE operation_id = ?"
                + " AND requester_user_id = ?", "updateDeviceParameters", REQUESTER), "未知资源不得生成申请");
    }

    @Test
    void executionIsRejectedAfterTargetVersionChanges() {
        UUID deviceId = DEVICES[1];
        Map<String, Object> proposal = proposal("updateDeviceParameters",
                Map.of("deviceId", deviceId.toString()),
                Map.of("changes", List.of(Map.of("parameterCode", "powerClassCode", "valueType", "TEXT", "textValue", "P3")), "reason", "调整设备供电等级参数值"));
        // 真库是复用的，设备版本可能已被上一轮抬高：以当前版本作为 If-Match，避免顺序依赖。
        Long current = jdbc.queryForObject("SELECT version FROM core.device WHERE id = ?", Long.class, deviceId);
        proposal.put("ifMatch", "\"" + current + "\"");
        UUID approvalId = (UUID) approvals.create(session(REQUESTER), proposal, "调整单台设备参数申请")
                .get("approvalId");
        approvals.decide(session(APPROVER1), approvalId, "APPROVE", "单台设备参数调整复核", PASSWORD);

        jdbc.update("UPDATE core.device SET version = ? WHERE id = ?", current + 7, deviceId);

        ApiException error = assertThrows(ApiException.class, () -> approvals.consume(
                approvalId, REQUESTER, "updateDeviceParameters", digestOf(proposal), TRACE));
        assertEquals(ErrorCode.RESOURCE_VERSION_CONFLICT, error.code());
        assertTrue(error.getMessage().contains("版本"), error.getMessage());
        assertEquals("APPROVED", approvalStatus(approvalId), "复核失败不能把批文烧掉");
    }

    @Test
    void expiredExecutionGrantIsGone() {
        UUID contextId = contextFor(REQUESTER, 5);
        Map<String, Object> proposal = proposal("createExport", Map.of(), exportBody(contextId));
        UUID approvalId = (UUID) approvals.create(session(REQUESTER), proposal, "导出审批许可过期验证")
                .get("approvalId");
        approvals.decide(session(APPROVER1), approvalId, "APPROVE", "导出申请复核同意并放行", PASSWORD);
        jdbc.update("UPDATE operation.action_approval"
                + " SET execution_grant_expires_at = now() - interval '1 minute' WHERE id = ?", approvalId);

        ApiException error = assertThrows(ApiException.class, () -> approvals.consume(
                approvalId, REQUESTER, "createExport", digestOf(proposal), TRACE));
        assertEquals(ErrorCode.CONTEXT_EXPIRED, error.code(), "许可过期是 410，不是可重试的 409");
    }

    // ------------------------------------------------------------------
    // M11 复核清单脱敏 / 密文不含明文
    // ------------------------------------------------------------------

    @Test
    void reviewItemsMaskNestedSecretsAndCiphertextCarriesNoPlaintext() {
        Map<String, Object> body = deviceCommandBody();
        body.put("commandType", "APPLY_PARAMETERS");
        body.put("parameterChanges", List.of(Map.of("parameterCode", "privateKey", "valueType", "TEXT", "textValue",
                "{\"password\":\"Hidden1!Secret\",\"secret\":\"Nested2!Secret\",\"token\":\"tok-3\"}")));

        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), body), "设备敏感参数变更审批");
        UUID approvalId = (UUID) created.get("approvalId");

        String rendered = reviewItems(created).toString();
        assertFalse(rendered.contains("Hidden1!Secret"), "顶层敏感值不得明文展示");
        assertFalse(rendered.contains("Nested2!Secret"), "序列化 JSON 字符串里的敏感值也不得明文展示");
        assertFalse(rendered.contains("tok-3"), "嵌套 token 必须掩码");
        assertTrue(rendered.contains("***"));
        assertTrue(reviewItems(created).stream()
                        .anyMatch(item -> String.valueOf(item.get("fieldCode")).endsWith("textValue")),
                "嵌套键要以 fieldCode 形式出现，审批人才能看出批的是哪个字段");

        String stored = jdbc.queryForObject(
                "SELECT encode(frozen_payload_ciphertext, 'escape') FROM operation.action_approval WHERE id = ?",
                String.class, approvalId);
        assertNotNull(stored);
        assertFalse(stored.contains("Hidden1!Secret"));
        assertFalse(stored.contains("Nested2!Secret"));

        byte[] second = cipher.seal("same-plaintext".getBytes(StandardCharsets.UTF_8));
        byte[] third = cipher.seal("same-plaintext".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(Base64.getEncoder().encodeToString(second),
                Base64.getEncoder().encodeToString(third), "每次加密必须用新的随机 nonce");
    }

    @Test
    void tamperedCiphertextAndWrongKeyFailClosed() {
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(contextFor(REQUESTER, 5))), "导出审批密文完整性验证");
        UUID approvalId = (UUID) created.get("approvalId");
        byte[] blob = jdbc.queryForObject(
                "SELECT frozen_payload_ciphertext FROM operation.action_approval WHERE id = ?", byte[].class,
                approvalId);
        byte[] tampered = blob.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> cipher.open(tampered));

        ApprovalProperties other = new ApprovalProperties();
        other.setPayloadKey(PAYLOAD_KEY_2);
        ApprovalPayloadCipher wrongKey = new ApprovalPayloadCipher(other);
        assertThrows(IllegalStateException.class, () -> wrongKey.open(blob));
    }

    // ------------------------------------------------------------------
    // M13 导出审批消费 / 幂等 / 并发
    // ------------------------------------------------------------------

    @Test
    void exportRequiresApprovalAndConsumesItExactlyOnce() {
        ApiException missing = assertThrows(ApiException.class,
                () -> exports.create(session(REQUESTER), command(contextFor(REQUESTER, 5),
                        "it-export-no-approval", null)));
        assertEquals(ErrorCode.PERMISSION_DENIED, missing.code());

        UUID contextId = contextFor(REQUESTER, 5);
        UUID approvalId = approvedExport(contextId);
        ExportCommand command = command(contextId, "it-export-" + UUID.randomUUID(), approvalId);

        TaskCreatedView created = exports.create(session(REQUESTER), command);
        assertNotNull(created.taskId());
        assertEquals("CONSUMED", approvalStatus(approvalId));
        assertEquals(1, count("SELECT count(*) FROM analysis.async_task WHERE id = ?", created.taskId()));
        assertEquals(1, count("SELECT count(*) FROM analysis.export_job WHERE task_id = ?", created.taskId()));

        // 同一个批文换一个幂等键：不能再执行一次。
        ApiException reused = assertThrows(ApiException.class, () -> exports.create(session(REQUESTER),
                command(contextId, "it-export-other-" + UUID.randomUUID(), approvalId)));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, reused.code());
        assertTrue(reused.getMessage().contains("已被使用"), reused.getMessage());

        // 同键重放：返回原结果，不因许可已消费而报错、也不产生第二个任务。
        TaskCreatedView replayed = exports.create(session(REQUESTER), command);
        assertEquals(created.taskId(), replayed.taskId());
        assertEquals(1, count("SELECT count(*) FROM analysis.async_task WHERE idempotency_key = ?",
                command.idempotencyKey()));
    }

    @Test
    void concurrentConsumptionAllowsExactlyOneSuccess() throws Exception {
        UUID contextId = contextFor(REQUESTER, 5);
        UUID approvalId = approvedExport(contextId);
        ExportCommand first = command(contextId, "it-export-conc-a-" + UUID.randomUUID(), approvalId);
        ExportCommand second = command(contextId, "it-export-conc-b-" + UUID.randomUUID(), approvalId);
        AuthenticatedSession session = session(REQUESTER);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (ExportCommand cmd : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        exports.create(session, cmd);
                        return null;
                    } catch (ApiException e) {
                        return e.code();
                    }
                }));
            }
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            long successes = results.stream().filter(code -> code == null).count();
            assertEquals(1, successes, "并发消费只允许一个成功，实际结果=" + results);
            assertEquals(1, results.stream().filter(code -> code == ErrorCode.RESOURCE_CONFLICT).count());
            assertEquals("CONSUMED", approvalStatus(approvalId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedExportLeavesApprovalUnconsumed() {
        UUID contextId = contextFor(REQUESTER, 5);
        UUID approvalId = approvedExport(contextId);
        // 权限版本在申请后被提升：导出必须在消费之前拒绝，批文保持 APPROVED 可重试。
        jdbc.update("UPDATE auth.app_user SET authz_version = 9 WHERE id = ?", REQUESTER);
        try {
            ApiException error = assertThrows(ApiException.class, () -> exports.create(session(REQUESTER),
                    command(contextId, "it-export-stale-" + UUID.randomUUID(), approvalId)));
            assertEquals(ErrorCode.PERMISSION_DENIED, error.code());
            assertEquals("APPROVED", approvalStatus(approvalId), "拒绝路径不能留下已消费未执行的假状态");
        } finally {
            jdbc.update("UPDATE auth.app_user SET authz_version = 5 WHERE id = ?", REQUESTER);
        }
    }

    // ------------------------------------------------------------------
    // M14 旧 Context 缩权
    // ------------------------------------------------------------------

    @Test
    void staleContextPermissionVersionIsRejected() {
        UUID staleContext = contextFor(REQUESTER, 4);
        ApiException error = assertThrows(ApiException.class, () -> exports.create(session(REQUESTER),
                command(staleContext, "it-export-stale-ctx-" + UUID.randomUUID(), null)));
        assertEquals(ErrorCode.PERMISSION_DENIED, error.code());
        assertTrue(error.getMessage().contains("权限范围"), error.getMessage());
    }

    @Test
    void contextDataTypeOutsideCurrentScopeIsRejected() {
        // 角色只剩 FENCE/PLATE 时，拿一个 FACE 的旧 Context 来导出必须拒绝（范围收缩，不只是版本号）。
        UUID faceContext = contextFor(REQUESTER, 5, "FACE");
        ApiException error = assertThrows(ApiException.class, () -> exports.create(session(REQUESTER),
                command(faceContext, "it-export-face-" + UUID.randomUUID(), null)));
        assertEquals(ErrorCode.PERMISSION_DATA_SCOPE, error.code());
        assertTrue(error.getMessage().contains("数据类型"), error.getMessage());

        // 正例：PLATE 在范围内，允许受理。
        UUID plateContext = contextFor(REQUESTER, 5, "PLATE");
        UUID approvalId = approvedExport(plateContext);
        assertNotNull(exports.create(session(REQUESTER),
                command(plateContext, "it-export-plate-" + UUID.randomUUID(), approvalId)).taskId());
    }

    @Test
    void rolledBackExportTransactionLeavesApprovalUnconsumedAndNoTask() {
        UUID contextId = contextFor(REQUESTER, 5);
        UUID approvalId = approvedExport(contextId);
        ExportCommand command = command(contextId, "it-export-rollback-" + UUID.randomUUID(), approvalId);
        PlatformTransactionManager transactions = context.getBean(PlatformTransactionManager.class);
        TransactionTemplate template = new TransactionTemplate(transactions);

        // 外层事务在 create 成功之后主动回滚：审批消费、任务行、导出意图、审计必须一起消失。
        assertThrows(IllegalStateException.class, () -> template.execute(status -> {
            exports.create(session(REQUESTER), command);
            throw new IllegalStateException("force rollback after domain write");
        }));

        assertEquals("APPROVED", approvalStatus(approvalId), "回滚后不能留下已消费未执行的假状态");
        assertEquals(0, count("SELECT count(*) FROM analysis.async_task WHERE idempotency_key = ?",
                command.idempotencyKey()));
        assertEquals(0, count("SELECT count(*) FROM analysis.export_job WHERE task_id IN"
                + " (SELECT id FROM analysis.async_task WHERE idempotency_key = ?)", command.idempotencyKey()));
    }

    // ------------------------------------------------------------------
    // M20 审计
    // ------------------------------------------------------------------

    @Test
    void approvalsAndExportRejectionsAreAudited() {
        // 审批与执行必须绑定同一个 contextId，否则摘要不同会先撞 409。
        UUID contextId = contextFor(REQUESTER, 5);
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(contextId)), "导出审批审计验证用途");
        UUID approvalId = (UUID) created.get("approvalId");
        approvals.decide(session(APPROVER1), approvalId, "APPROVE", "导出审批审计复核同意", PASSWORD);
        exports.create(session(REQUESTER), command(contextId,
                "it-export-audit-" + UUID.randomUUID(), approvalId));

        assertEquals(1, count("SELECT count(*) FROM audit.audit_event"
                + " WHERE action = 'approval.create' AND result = 'SUCCESS' AND resource_id = ?", approvalId));
        assertEquals(1, count("SELECT count(*) FROM audit.audit_event"
                + " WHERE action = 'approval.decide' AND result = 'SUCCESS' AND resource_id = ?", approvalId));
        assertEquals(1, count("SELECT count(*) FROM audit.audit_event"
                + " WHERE action = 'approval.consume' AND result = 'SUCCESS' AND resource_id = ?", approvalId));

        Map<String, Object> second = approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(contextFor(REQUESTER, 5))), "导出审批拒绝审计验证");
        UUID rejectedId = (UUID) second.get("approvalId");
        approvals.decide(session(APPROVER1), rejectedId, "REJECT", "目标范围过大不同意本次导出", PASSWORD);
        assertEquals(1, count("SELECT count(*) FROM audit.audit_event"
                + " WHERE action = 'approval.decide' AND result = 'SUCCESS' AND resource_id = ?", rejectedId));
        assertEquals("REJECTED", approvalStatus(rejectedId));
    }

    @Test
    void rejectionAuditSurvivesBusinessRollback() {
        Map<String, Object> invalid = proposal("createExport", Map.of(),
                Map.of("contextId", "not-a-uuid", "format", "PDF"));
        assertThrows(ApiException.class,
                () -> approvals.create(session(REQUESTER), invalid, "无效导出提案拒绝审计验证"));

        Integer approvalRejections = jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = 'approval.create'"
                        + " AND result = 'FAILURE' AND failure_code = 'APPROVAL_REJECTED' AND actor_user_id = ?",
                Integer.class, REQUESTER);
        assertTrue(approvalRejections != null && approvalRejections >= 1);

        assertThrows(ApiException.class, () -> exports.create(session(REQUESTER),
                command(contextFor(REQUESTER, 5), "it-export-deny-" + UUID.randomUUID(), null)));
        Integer exportRejections = jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE action = 'export.create'"
                        + " AND result = 'FAILURE' AND failure_code = 'EXPORT_REJECTED' AND actor_user_id = ?",
                Integer.class, REQUESTER);
        assertTrue(exportRejections != null && exportRejections >= 1);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private UUID approvedExport(UUID contextId) {
        Map<String, Object> created = approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(contextId)), "导出申请复核用途说明");
        UUID approvalId = (UUID) created.get("approvalId");
        approvals.decide(session(APPROVER1), approvalId, "APPROVE", "导出申请复核同意并放行", PASSWORD);
        return approvalId;
    }

    // ------------------------------------------------------------------
    // M15 幂等：创建申请 / 提交决定都落 core.idempotency_request 账本
    // ------------------------------------------------------------------

    @Test
    void approvalCreationIsIdempotentPerKeyAndRejectsDifferentContent() {
        String key = "it-approval-create-" + UUID.randomUUID();
        Map<String, Object> proposal = proposal("createDeviceCommand", Map.of(), deviceCommandBody());
        long before = count("SELECT count(*) FROM operation.action_approval WHERE operation_id = ?"
                + " AND requester_user_id = ?", "createDeviceCommand", REQUESTER);

        Map<String, Object> first = approvals.create(session(REQUESTER), proposal,
                "批量重置设备参数申请说明", key);
        Map<String, Object> replay = approvals.create(session(REQUESTER), proposal,
                "批量重置设备参数申请说明", key);

        assertEquals(String.valueOf(first.get("approvalId")), String.valueOf(replay.get("approvalId")),
                "同键同内容必须返回同一份申请");
        assertEquals(before + 1, count("SELECT count(*) FROM operation.action_approval"
                        + " WHERE operation_id = ? AND requester_user_id = ?",
                "createDeviceCommand", REQUESTER), "重放不得产生第二条申请");
        assertEquals(1, count("SELECT count(*) FROM core.idempotency_request WHERE user_id = ?"
                        + " AND operation_id = 'createActionApproval' AND idempotency_key = ?",
                REQUESTER, key), "同键只应有一条账本行");

        ApiException conflict = assertThrows(ApiException.class, () -> approvals.create(
                session(REQUESTER), proposal, "同一个键但申请理由不同内容", key));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.code(), "同键不同内容必须 409");
        assertEquals(1, count("SELECT count(*) FROM core.idempotency_request WHERE user_id = ?"
                        + " AND operation_id = 'createActionApproval' AND idempotency_key = ?",
                REQUESTER, key));
    }

    @Test
    void approvalDecisionIsIdempotentPerKeyAndStillCountsOneVotePerApprover() {
        UUID approvalId = (UUID) approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), deviceCommandBody()),
                "批量重置设备参数申请说明").get("approvalId");
        String key = "it-approval-decide-" + UUID.randomUUID();

        Map<String, Object> first = approvals.decide(session(APPROVER1), approvalId, "APPROVE",
                "第一名审批人复核同意", PASSWORD, key);
        assertEquals("PENDING", first.get("status"));

        Map<String, Object> replay = approvals.decide(session(APPROVER1), approvalId, "APPROVE",
                "第一名审批人复核同意", PASSWORD, key);
        assertEquals("PENDING", replay.get("status"));
        assertEquals(1, countDecisions(approvalId), "重放不得再记一票");
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(ApiException.class, () -> approvals.decide(
                session(APPROVER1), approvalId, "APPROVE", "第一名审批人复核同意", "wrong-current-password", key)).code());
        assertEquals(1, countDecisions(approvalId));

        ApiException conflict = assertThrows(ApiException.class, () -> approvals.decide(
                session(APPROVER1), approvalId, "APPROVE", "同一个键但申请理由不同内容", PASSWORD, key));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflict.code());

        // 换一个键、同一审批人、另一条理由说明：领域规则仍然只允许一票。
        ApiException second = assertThrows(ApiException.class, () -> approvals.decide(
                session(APPROVER1), approvalId, "APPROVE", "同一个键但申请理由不同内容", PASSWORD, key + "-b"));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, second.code());
        assertEquals(1, countDecisions(approvalId));
    }

    @Test
    void idempotencyKeyIsScopedToActor() {
        String key = "it-approval-scope-" + UUID.randomUUID();
        UUID approvalId = (UUID) approvals.create(session(REQUESTER),
                proposal("createDeviceCommand", Map.of(), deviceCommandBody()),
                "批量重置设备参数申请说明", key).get("approvalId");

        // 同一个键、另一个 actor：账本按 (user_id, operation_id, key) 分作用域，不应互相冲突。
        Map<String, Object> decided = approvals.decide(session(APPROVER1), approvalId, "APPROVE",
                "另一名审批人用相同键复核", PASSWORD, key);
        assertEquals("PENDING", decided.get("status"));
        assertEquals(1, countDecisions(approvalId));
        assertEquals(2, count("SELECT count(*) FROM core.idempotency_request WHERE idempotency_key = ?",
                key), "两个 actor 各一条账本行");
    }

    private static Map<String, Object> deviceCommandBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("commandType", "RESET");
        body.put("groupId", GROUP.toString());
        body.put("deviceIds", List.of(DEVICES[0].toString()));
        body.put("name", "SIMULATOR reset");
        body.put("reason", "批量重置设备参数前的复核申请");
        return body;
    }

    private static Map<String, Object> exportBody(UUID contextId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contextId", contextId.toString());
        body.put("format", "XLSX");
        body.put("columns", List.of("captureId", "dataType"));
        body.put("purpose", "导出用于安全审计核查");
        return body;
    }

    private static Map<String, Object> proposal(String operationId, Map<String, Object> path,
                                                Map<String, Object> body) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("operationId", operationId);
        if (!path.isEmpty()) {
            proposal.put("pathParameters", path);
        }
        if (!body.isEmpty()) {
            proposal.put("requestBody", body);
        }
        return proposal;
    }

    private static ExportCommand command(UUID contextId, String key, UUID approvalId) {
        return new ExportCommand(contextId, ExportFormat.XLSX, List.of("captureId", "dataType"),
                "导出用于安全审计核查", key, TRACE, approvalId);
    }

    /** 与 ApprovalProposals 相同的规范化 + SHA-256，供 consume 直接比对。 */
    private static String digestOf(Map<String, Object> proposal) {
        String canonical = TaskParameters.canonicalJson(proposal);
        return Sha256.hex(Sha256.ofBytes(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private AuthenticatedSession session(UUID userId) {
        UserAccountRepository.UserAccountRow row = users.findById(userId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new AuthenticatedSession(userId, UUID.randomUUID(), now.plusHours(1), now.plusHours(2), row);
    }

    private static String loginName(UUID userId) {
        return users.findById(userId).orElseThrow().loginName();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> reviewItems(Map<String, Object> view) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) view.get("reviewItems");
        return items == null ? List.of() : items;
    }

    private static String approvalStatus(UUID approvalId) {
        return jdbc.queryForObject("SELECT status FROM operation.action_approval WHERE id = ?",
                String.class, approvalId);
    }

    private static int countDecisions(UUID approvalId) {
        return count("SELECT count(*) FROM audit.action_approval_decision WHERE approval_id = ?", approvalId);
    }

    private static int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static UUID contextFor(UUID owner, long permissionVersion) {
        return contextFor(owner, permissionVersion, "PLATE");
    }

    private static UUID contextFor(UUID owner, long permissionVersion, String dataType) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String filter = "{\"filterType\":\"CAPTURE_SEARCH\",\"filterVersion\":1,\"captureFilter\":{\"timeRange\":{\"from\":\"2026-01-01T00:00:00Z\","
                + "\"to\":\"2026-02-01T00:00:00Z\"},\"dataType\":\"" + dataType + "\"}}";
        jdbc.update("""
                INSERT INTO analysis.analysis_context
                    (id, owner_user_id, source_module, filter_type, filter_schema_version, filter_snapshot,
                     permission_version, expires_at, trace_id, created_at)
                VALUES (?, ?, 'M06', 'CAPTURE_SEARCH', 1, ?::jsonb, ?, ?, ?, ?)
                """, id, owner, filter, permissionVersion, Timestamp.from(Instant.now().plusSeconds(1800)), TRACE,
                Timestamp.from(Instant.now()));
        return id;
    }

    // ------------------------------------------------------------------
    // 数据准备
    // ------------------------------------------------------------------

    private static void seed() {
        String hash = hasher.hash(PASSWORD);
        jdbc.update("INSERT INTO core.region (region_code, name, boundary_version)"
                + " VALUES ('IT-REGION', 'it-region', 'v1') ON CONFLICT (region_code) DO NOTHING");
        jdbc.update("INSERT INTO core.site (id, site_code, name, region_code)"
                + " VALUES (?, 'IT-SITE', 'it-site', 'IT-REGION') ON CONFLICT (id) DO NOTHING", SITE);

        seedUser(REQUESTER, "IT-APR-REQ", "it.approval.requester", "it requester");
        seedUser(APPROVER1, "IT-APR-A1", "it.approval.approver1", "it approver1");
        seedUser(APPROVER2, "IT-APR-A2", "it.approval.approver2", "it approver2");
        seedUser(APPROVER_NOSCOPE, "IT-APR-A3", "it.approval.noscope", "it noscope");
        seedUser(OUTSIDER, "IT-APR-OUT", "it.approval.outsider", "it outsider");
        jdbc.update("UPDATE auth.app_user SET password_hash = ?, authz_version = 5"
                + " WHERE id IN (?, ?, ?, ?, ?)", hash, REQUESTER, APPROVER1, APPROVER2, APPROVER_NOSCOPE, OUTSIDER);

        jdbc.update("INSERT INTO auth.permission (code, name, module_code, description, risk_level)"
                + " VALUES ('capture:export', 'it-capture-export', 'M13', 'it export', 'HIGH')"
                + " ON CONFLICT (code) DO NOTHING");
        jdbc.update("INSERT INTO auth.permission (code, name, module_code, description, risk_level)"
                + " VALUES ('device:command', 'it-device-command', 'M12', 'it command', 'HIGH')"
                + " ON CONFLICT (code) DO NOTHING");
        // M05/M14：任务提交与发布要按任务类型授权（task:run:<TYPE>）复核当前权限，
        // 导出用例的要求人必须有 EXPORT 的运行授权，否则连提交都到不了审批消费那一步。
        jdbc.update("INSERT INTO auth.permission (code, name, module_code, description, risk_level)"
                + " VALUES ('capture:export', 'it-task-run-export', 'M06', 'it task export', 'NORMAL')"
                + " ON CONFLICT (code) DO NOTHING");

        jdbc.update("INSERT INTO auth.role (id, role_code, name, management_all_sites, data_all_sites, own_only)"
                + " VALUES (?, 'IT_APPROVAL_ALL', 'it approval all', true, true, false)"
                + " ON CONFLICT (id) DO NOTHING", ROLE_ALL);
        jdbc.update("INSERT INTO auth.role (id, role_code, name, management_all_sites, data_all_sites, own_only)"
                + " VALUES (?, 'IT_APPROVAL_NOSCOPE', 'it approval noscope', false, false, true)"
                + " ON CONFLICT (id) DO NOTHING", ROLE_NOSCOPE);

        for (String permission : List.of("capture:export", "device:command", "device:reset")) {
            jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", ROLE_ALL, permission);
            jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", ROLE_NOSCOPE, permission);
        }
        // 数据范围按类型收窄到 FENCE/PLATE：M14 的"范围只剩两类"用例依赖这一行。
        for (String dataType : List.of("FENCE", "PLATE")) {
            jdbc.update("INSERT INTO auth.role_data_type (role_id, data_type) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", ROLE_ALL, dataType);
        }
        for (UUID user : List.of(REQUESTER, APPROVER1, APPROVER2, OUTSIDER)) {
            jdbc.update("INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", user, ROLE_ALL);
        }
        jdbc.update("INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                APPROVER_NOSCOPE, ROLE_NOSCOPE);

        jdbc.update("INSERT INTO core.device_group (id, site_id, name)"
                + " VALUES (?, ?, 'it-group') ON CONFLICT (id) DO NOTHING", GROUP, SITE);
        for (int i = 0; i < DEVICES.length; i++) {
            jdbc.update("INSERT INTO core.device (id, serial_number, name, site_id, category_code)"
                    + " VALUES (?, ?, ?, ?, 'CAMERA') ON CONFLICT (id) DO NOTHING",
                    DEVICES[i], "IT-SN-" + (i + 1), "it-device-" + (i + 1), SITE);
            jdbc.update("INSERT INTO core.device_group_member (group_id, device_id) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", GROUP, DEVICES[i]);
            jdbc.update("UPDATE core.device SET manufacturer='SIMULATOR',model='SIMULATOR',protocol_version='SIMULATOR_V1' WHERE id=?", DEVICES[i]);
        }
        for (String code : List.of("powerClassCode", "privateKey"))
            jdbc.update("""
                    INSERT INTO core.device_parameter_definition(id,manufacturer,model,protocol_version,parameter_code,label,value_type,read_only,enabled)
                     VALUES (?,'SIMULATOR','SIMULATOR','SIMULATOR_V1',?,?,'TEXT',false,true)
                     ON CONFLICT(manufacturer,model,protocol_version,parameter_code) DO UPDATE SET enabled=true,read_only=false
                    """, UUID.randomUUID(), code, code);
    }

    @Test
    void missingConfigurationAndOutOfScopeReviewersDisableCreation() {
        for (List<String> configured : List.of(List.<String>of(), List.of(loginName(APPROVER_NOSCOPE)))) {
            properties.setApproverLogins(configured);
            ApiException error = assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                    proposal("createDeviceCommand", Map.of(), deviceCommandBody()), "Synthetic approval qualification regression"));
            assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, error.code());
        }
    }

    @Test
    void outOfScopeReviewerCannotReadDetailsOrListTheApproval() {
        UUID id = (UUID) approvals.create(session(REQUESTER), proposal("createDeviceCommand", Map.of(), deviceCommandBody()),
                "Synthetic approval visibility regression").get("approvalId");
        assertEquals(ErrorCode.DATA_NOT_FOUND, assertThrows(ApiException.class,
                () -> approvals.get(session(APPROVER_NOSCOPE), id)).code());
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>)
                approvals.page(session(APPROVER_NOSCOPE), null, 100).get("items");
        assertTrue(items.stream().noneMatch(item -> String.valueOf(id).equals(String.valueOf(item.get("approvalId")))));
    }

    @Test
    void oneDeviceKeyChangeNeedsTwoIndependentVotesAndNeverDisplaysTheKey() {
        UUID device = DEVICES[2];
        Long version = jdbc.queryForObject("SELECT version FROM core.device WHERE id=?", Long.class, device);
        Map<String, Object> input = proposal("updateDeviceParameters", Map.of("deviceId", device.toString()),
                Map.of("changes", List.of(Map.of("parameterCode", "privateKey", "valueType", "TEXT", "textValue", "Synthetic-secret-material")),
                        "reason", "Synthetic key rotation regression"));
        input.put("ifMatch", "\"" + version + "\"");
        Map<String, Object> created = approvals.create(session(REQUESTER), input, "Synthetic key rotation regression");
        assertEquals(2, created.get("requiredApprovals"));
        assertFalse(created.toString().contains("Synthetic-secret-material"));
        UUID id = (UUID) created.get("approvalId");
        assertEquals("PENDING", approvals.decide(session(APPROVER1), id, "APPROVE", "First independent synthetic review", PASSWORD).get("status"));
        assertEquals("APPROVED", approvals.decide(session(APPROVER2), id, "APPROVE", "Second independent synthetic review", PASSWORD).get("status"));
    }

    @Test
    void nonexistentAndUnconnectedTargetsNeverProduceVerifiedApplications() {
        ApiException context = assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                proposal("createExport", Map.of(), exportBody(UUID.randomUUID())), "Synthetic missing context regression"));
        assertEquals(ErrorCode.DATA_NOT_FOUND, context.code());
        jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,'capture:decrypt') ON CONFLICT DO NOTHING", ROLE_ALL);
        try {
            ApiException decrypt = assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                    proposal("readDecryptionResult", Map.of("requestId", UUID.randomUUID().toString()), Map.of()),
                    "Synthetic unavailable decryption regression"));
            assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, decrypt.code());
        } finally { jdbc.update("DELETE FROM auth.role_permission WHERE role_id=? AND permission_code='capture:decrypt'", ROLE_ALL); }
        ApiException retry = assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                proposal("retryDeviceCommand", Map.of("taskId", UUID.randomUUID().toString()),
                        Map.of("failedDeviceIds", List.of(DEVICES[0].toString()), "reason", "Synthetic retry regression")),
                "Synthetic unavailable command retry regression"));
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, retry.code());
    }

    @Test
    void changedGroupMembershipInvalidatesTheFrozenApproval() {
        Map<String, Object> input = proposal("createDeviceCommand", Map.of(), deviceCommandBody());
        UUID id = (UUID) approvals.create(session(REQUESTER), input, "Synthetic group membership regression").get("approvalId");
        approvals.decide(session(APPROVER1), id, "APPROVE", "First synthetic group review", PASSWORD);
        approvals.decide(session(APPROVER2), id, "APPROVE", "Second synthetic group review", PASSWORD);
        jdbc.update("DELETE FROM core.device_group_member WHERE group_id=? AND device_id=?", GROUP, DEVICES[9]);
        try {
            assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(ApiException.class,
                    () -> approvals.consume(id, REQUESTER, "createDeviceCommand", digestOf(input), TRACE)).code());
            assertEquals("APPROVED", approvalStatus(id));
        } finally { jdbc.update("INSERT INTO core.device_group_member(group_id,device_id) VALUES (?,?) ON CONFLICT DO NOTHING", GROUP, DEVICES[9]); }
    }

    @Test
    void changedAuthorizationAndRemovedReviewerCannotConsumeApproval() {
        UUID contextId = contextFor(REQUESTER, 5);
        UUID id = approvedExport(contextId);
        Map<String, Object> input = proposal("createExport", Map.of(), exportBody(contextId));
        jdbc.update("UPDATE auth.app_user SET authz_version=6 WHERE id=?", REQUESTER);
        try {
            assertEquals(ErrorCode.RESOURCE_CONFLICT, assertThrows(ApiException.class,
                    () -> approvals.consume(id, REQUESTER, "createExport", digestOf(input), TRACE)).code());
        } finally { jdbc.update("UPDATE auth.app_user SET authz_version=5 WHERE id=?", REQUESTER); }
        properties.setApproverLogins(List.of(loginName(APPROVER2)));
        assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class,
                () -> approvals.consume(id, REQUESTER, "createExport", digestOf(input), TRACE)).code());
        assertEquals("APPROVED", approvalStatus(id));
    }

    @Test
    void concurrentIdenticalApprovalKeysCreateExactlyOneApplication() throws Exception {
        UUID contextId = contextFor(REQUESTER, 5);
        Map<String, Object> input = proposal("createExport", Map.of(), exportBody(contextId));
        String key = "it-approval-concurrent-" + UUID.randomUUID();
        int before = count("SELECT count(*) FROM operation.action_approval WHERE requester_user_id=?", REQUESTER);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                start.await();
                return String.valueOf(approvals.create(session(REQUESTER), input, "Synthetic concurrent approval regression", key).get("approvalId"));
            }));
            start.countDown();
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (Future<String> result : results) ids.add(result.get(30, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(before + 1, count("SELECT count(*) FROM operation.action_approval WHERE requester_user_id=?", REQUESTER));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void approvalReplayRechecksCurrentPermission() {
        Map<String, Object> input = proposal("createExport", Map.of(), exportBody(contextFor(REQUESTER, 5)));
        String key = "it-approval-revocation-" + UUID.randomUUID();
        approvals.create(session(REQUESTER), input, "Synthetic replay revocation regression", key);
        jdbc.update("DELETE FROM auth.role_permission WHERE role_id=? AND permission_code='capture:export'", ROLE_ALL);
        jdbc.update("UPDATE auth.app_user SET authz_version=6 WHERE id=?", REQUESTER);
        try {
            assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                    input, "Synthetic replay revocation regression", key)).code());
            assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> approvals.create(session(REQUESTER),
                    input, "Synthetic replay revocation regression", key + "-fresh")).code());
        } finally {
            jdbc.update("UPDATE auth.app_user SET authz_version=5 WHERE id=?", REQUESTER);
            jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,'capture:export') ON CONFLICT DO NOTHING", ROLE_ALL);
        }
    }

    @Test
    void exportWorksWithBusinessPermissionWithoutTaskManagement() {
        List<String> permissions = context.getBean(com.xxplatform.api.infra.RbacRepository.class).permissionCodes(REQUESTER);
        assertTrue(permissions.contains("capture:export"));
        assertFalse(permissions.contains("task:manage"));
        assertTrue(permissions.stream().noneMatch(p -> p.startsWith("task:run:")));
        UUID contextId = contextFor(REQUESTER, 5);
        assertNotNull(exports.create(session(REQUESTER), command(contextId, "it-export-business-" + UUID.randomUUID(), approvedExport(contextId))).taskId());
    }

    @Test
    void grantApplicationCannotExceedTheGrantorsCeiling() {
        UUID actor = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        seedUser(actor, "IT-BOUND-" + actor, "it.bound." + actor, "Synthetic limited grantor");
        jdbc.update("INSERT INTO auth.role(id,role_code,name) VALUES (?,?,?)", role, "IT_BOUND_" + role, "Synthetic limited role");
        jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,'user:write')", role);
        jdbc.update("INSERT INTO auth.user_role(user_id,role_id) VALUES (?,?)", actor, role);
        Map<String, Object> body = Map.of("userCode", "IT-NEW-" + UUID.randomUUID(), "loginName", "it.new." + UUID.randomUUID(),
                "displayName", "Synthetic attempted elevation", "initialPassword", "SyntheticLongPassword!2026", "roleIds", List.of(ROLE_ALL.toString()),
                "siteScope", Map.of("allSites", true, "siteIds", List.of()),
                "dataScope", Map.of("allSites", true, "siteIds", List.of(), "dataTypes", List.of("FENCE", "MAC", "PLATE", "FACE"), "ownOnly", false, "scopeVersion", 0));
        int before = count("SELECT count(*) FROM operation.action_approval WHERE requester_user_id=?", actor);
        assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> approvals.create(session(actor),
                proposal("createUser", Map.of(), body), "Synthetic grant upper bound regression")).code());
        assertEquals(before, count("SELECT count(*) FROM operation.action_approval WHERE requester_user_id=?", actor));
    }

    @Test
    void publishedExportFileRequiresPermitAndCurrentSourcePermission() throws Exception {
        jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,'file:download') ON CONFLICT DO NOTHING", ROLE_ALL);
        com.xxplatform.api.config.FileProperties fileProperties = context.getBean(com.xxplatform.api.config.FileProperties.class);
        fileProperties.setDownloadPermitKey(Base64.getEncoder().encodeToString(new byte[32]));
        UUID contextId = contextFor(REQUESTER, 5);
        Map<String, Object> body = exportBody(contextId);
        body.put("format", "CSV");
        UUID approval = (UUID) approvals.create(session(REQUESTER), proposal("createExport", Map.of(), body),
                "Synthetic published file authorization regression").get("approvalId");
        approvals.decide(session(APPROVER1), approval, "APPROVE", "Independent synthetic file export review", PASSWORD);
        UUID taskId = exports.create(session(REQUESTER), new ExportCommand(contextId, ExportFormat.CSV,
                List.of("captureId", "dataType"), String.valueOf(body.get("purpose")),
                "it-export-file-" + UUID.randomUUID(), TRACE, approval)).taskId();
        com.xxplatform.api.storage.FileStorage storage = context.getBean(com.xxplatform.api.storage.FileStorage.class);
        byte[] bytes = ("captureId,dataType\n" + UUID.randomUUID() + ",PLATE\n").getBytes(StandardCharsets.UTF_8);
        com.xxplatform.api.storage.FileStorage.StoredObject stored = storage.store(bytes, ".csv");
        UUID assetId = UUID.randomUUID();
        com.xxplatform.api.infra.FileAssetRepository assets = context.getBean(com.xxplatform.api.infra.FileAssetRepository.class);
        try {
            assets.insert(assetId, stored.storageKey(), "synthetic.csv", "EXPORT", "text/csv", stored.byteSize(),
                    stored.sha256Hex(), REQUESTER, TRACE, OffsetDateTime.now(ZoneOffset.UTC));
            assets.markStatus(assetId, "ACTIVE", null, com.xxplatform.api.service.file.FileRetention.expiresAt(OffsetDateTime.now(ZoneOffset.UTC)));
            jdbc.update("UPDATE analysis.export_job SET output_file_id=? WHERE task_id=?", assetId, taskId);
            com.xxplatform.api.service.task.TaskService tasks = context.getBean(com.xxplatform.api.service.task.TaskService.class);
            String worker = "it-file-publisher";
            var claimed = tasks.claimNext(worker, List.of(com.xxplatform.api.service.task.TaskType.EXPORT), 1);
            assertEquals(taskId, claimed.get(0).task().id());
            assertEquals(com.xxplatform.api.service.task.TaskStatus.SUCCESS,
                    tasks.finish(taskId, worker, claimed.get(0).task().leaseGeneration(),
                            com.xxplatform.api.service.task.TaskStatus.SUCCESS, null, null, assetId).status());
            com.xxplatform.api.service.file.FileService files = context.getBean(com.xxplatform.api.service.file.FileService.class);
            AuthenticatedSession current = session(REQUESTER);
            assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> files.download(current, assetId)).code());
            String permit = files.issueDownloadPermit(current, files.get(current, assetId));
            try (var stream = files.download(current, assetId, permit).stream()) {
                assertTrue(java.util.Arrays.equals(bytes, stream.readAllBytes()));
            }
            jdbc.update("UPDATE auth.role SET data_all_sites=false WHERE id=?", ROLE_ALL);
            int addedScope = jdbc.update("INSERT INTO auth.role_site(role_id,site_id,scope_kind) VALUES (?,?,'DATA') ON CONFLICT DO NOTHING", ROLE_ALL, SITE);
            jdbc.update("UPDATE auth.app_user SET authz_version=6 WHERE id=?", REQUESTER);
            try {
                assertEquals(ErrorCode.PERMISSION_DATA_SCOPE,
                        assertThrows(ApiException.class, () -> files.get(session(REQUESTER), assetId)).code());
            } finally {
                jdbc.update("UPDATE auth.app_user SET authz_version=5 WHERE id=?", REQUESTER);
                jdbc.update("UPDATE auth.role SET data_all_sites=true WHERE id=?", ROLE_ALL);
                if (addedScope > 0) jdbc.update("DELETE FROM auth.role_site WHERE role_id=? AND site_id=? AND scope_kind='DATA'", ROLE_ALL, SITE);
            }
            jdbc.update("DELETE FROM auth.role_permission WHERE role_id=? AND permission_code='capture:export'", ROLE_ALL);
            try {
                assertEquals(ErrorCode.PERMISSION_DENIED, assertThrows(ApiException.class, () -> files.download(current, assetId, permit)).code());
            } finally {
                jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,'capture:export') ON CONFLICT DO NOTHING", ROLE_ALL);
            }
        } finally {
            cleanupExportTasks();
            jdbc.update("DELETE FROM core.file_asset WHERE id=?", assetId);
            storage.delete(stored.storageKey());
            jdbc.update("DELETE FROM auth.role_permission WHERE role_id=? AND permission_code='file:download'", ROLE_ALL);
        }
    }

    private static void seedUser(UUID id, String code, String login, String display) {
        jdbc.update("INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash)"
                + " VALUES (?, ?, ?, ?, 'x') ON CONFLICT (id) DO NOTHING", id, code, login, display);
    }
}
