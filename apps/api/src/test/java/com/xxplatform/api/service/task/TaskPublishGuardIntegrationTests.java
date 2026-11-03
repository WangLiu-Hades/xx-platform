package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * M05 + M14（任务侧）：租约栅栏与"发布/提交时复核当前授权与数据范围"。
 *
 * <p>这些用例全部用真库跑，因为被验证的条件（{@code lease_until > now}、{@code cancel_requested_at IS NULL}、
 * 生成号匹配）都写在 UPDATE 的 WHERE 里，mock 掉仓储等于把要证明的东西一起 mock 掉。
 *
 * <p><b>关于"请求取消受理 ≠ 已取消完成"</b>：{@code cancel()} 对 {@code RUNNING} 只登记
 * {@code cancel_requested_at}，任务仍是 RUNNING；此时 worker 回报 SUCCESS 必须被 409 拒绝，
 * 直到租约到期由 reaper 收成 CANCELLED。下面 {@link #cancelRequestIsNotACompletedCancellation()}
 * 把这三步都断言出来。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class TaskPublishGuardIntegrationTests extends TaskIntegrationTestSupport {

    @BeforeAll
    static void start() {
        boot();
    }

    @Test
    void exportsUseTheirOwnDomainPermissionRatherThanCapturePermissionForEveryExport() {
        UUID owner = seedOwner("audit-export", List.of("audit:export"));
        assertNotNull(taskService.create(command(owner, Map.of("operationId", "exportAuditEvents"), newKey("audit-export"))));
        assertCode(ErrorCode.PERMISSION_DENIED, () -> taskService.create(command(owner,
                Map.of("operationId", "createExport"), newKey("audit-cannot-capture"))));
    }

    @Test
    void contextScopeNeverMultipliesTypesAndSitesAcrossRoles() {
        UUID owner = seedOwner("scope-cross");
        UUID firstRole = jdbc.queryForObject("SELECT role_id FROM auth.user_role WHERE user_id=?", UUID.class, owner);
        UUID secondRole = UUID.randomUUID();
        UUID siteA = UUID.randomUUID();
        UUID siteB = UUID.randomUUID();
        jdbc.update("INSERT INTO core.region(region_code,name,boundary_version) VALUES ('IT_CROSS','Synthetic region','v1') ON CONFLICT DO NOTHING");
        for (UUID site : List.of(siteA, siteB))
            jdbc.update("INSERT INTO core.site(id,site_code,name,region_code) VALUES (?,?,?,'IT_CROSS')", site, "IT-" + site, "Synthetic scope site");
        jdbc.update("UPDATE auth.role SET data_all_sites=false,management_all_sites=false WHERE id=?", firstRole);
        jdbc.update("DELETE FROM auth.role_data_type WHERE role_id=?", firstRole);
        jdbc.update("INSERT INTO auth.role_data_type(role_id,data_type) VALUES (?,'FENCE')", firstRole);
        jdbc.update("INSERT INTO auth.role_site(role_id,site_id,scope_kind) VALUES (?,?,'DATA')", firstRole, siteA);
        jdbc.update("INSERT INTO auth.role(id,role_code,name,own_only) VALUES (?,?,?,false)", secondRole, "IT-CROSS-" + secondRole, "Synthetic FACE role");
        jdbc.update("INSERT INTO auth.role_data_type(role_id,data_type) VALUES (?,'FACE')", secondRole);
        jdbc.update("INSERT INTO auth.role_site(role_id,site_id,scope_kind) VALUES (?,?,'DATA')", secondRole, siteB);
        jdbc.update("INSERT INTO auth.user_role(user_id,role_id) VALUES (?,?)", owner, secondRole);
        UUID contextId = seedContext(owner, 1, now().plusMinutes(30));
        String filter = "{\"filterType\":\"GENERAL\",\"filterVersion\":1,\"dataTypes\":[\"FACE\"],\"siteIds\":[\"" + siteA
                + "\"],\"timeRange\":{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-10-02T00:00:00Z\"}}";
        jdbc.update("UPDATE analysis.analysis_context SET filter_snapshot=?::jsonb WHERE id=?", filter, contextId);
        assertCode(ErrorCode.PERMISSION_DATA_SCOPE, () -> taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("cross-denied"))));
        jdbc.update("UPDATE analysis.analysis_context SET filter_snapshot=?::jsonb WHERE id=?", filter.replace(siteA.toString(), siteB.toString()), contextId);
        assertNotNull(taskService.create(command(owner, Map.of("contextId", contextId.toString()), newKey("matching-tuple"))));
    }

    @Test
    void matchingVersionsDoNotOverrideTheCurrentTypedScope() {
        UUID owner = seedOwner("typed-scope");
        jdbc.update("DELETE FROM auth.role_data_type WHERE role_id IN (SELECT role_id FROM auth.user_role WHERE user_id=?) AND data_type<>'FENCE'", owner);
        UUID contextId = seedContext(owner, 1, now().plusMinutes(30));
        assertCode(ErrorCode.PERMISSION_DATA_SCOPE, () -> taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("typed-scope"))));
    }

    @Test
    void activeFileWithoutAnExpiryCannotBePublished() {
        UUID owner = seedOwner("null-expiry");
        UUID file = seedFileAsset(owner, "ACTIVE");
        jdbc.update("UPDATE core.file_asset SET expires_at=NULL WHERE id=?", file);
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("null-expiry")));
        ClaimedTask claimed = claim(task.id(), "it-null-expiry");
        seedExportJob(task.id(), file);
        assertEquals(TaskStatus.FAILED, taskService.finish(task.id(), "it-null-expiry", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, file).status());
        assertNull(reload(task.id()).resultAssetId());
    }

    @Test
    void validLeaseAndScopePublishesSuccess() {
        UUID owner = seedOwner("guard-ok");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-ok")));
        ClaimedTask claimed = claim(task.id(), "it-guard-ok");
        seedExportJob(task.id(), asset);

        TaskRow done = taskService.finish(task.id(), "it-guard-ok", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, asset);

        assertEquals(TaskStatus.SUCCESS, done.status());
        assertEquals(asset, done.resultAssetId());
        assertEquals(1, countAttempts(task.id(), "SUCCESS"));
        // 事件：创建 + PENDING→QUEUED + 领取 + 完成 = 4 条；发布成功不得多写"改判"事件。
        assertEquals(4, countEvents(task.id()));
    }

    @Test
    void permissionRevokedAfterClaimBlocksPublishAndFailsAttempt() {
        UUID owner = seedOwner("guard-revoke");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-revoke")));
        ClaimedTask claimed = claim(task.id(), "it-guard-revoke");
        revoke(owner, "capture:export");
        int eventsBefore = countEvents(task.id());

        TaskRow outcome = taskService.finish(task.id(), "it-guard-revoke", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, asset);

        // 缩权后不得公开结果：终态是 FAILED + 明确原因，结果指针为空。
        assertEquals(TaskStatus.FAILED, outcome.status());
        assertEquals(TaskScopeGuard.PERMISSION_REVOKED, outcome.errorCode());
        assertNull(reload(task.id()).resultAssetId());
        assertEquals(1, countAttempts(task.id(), "FAILED"));
        assertEquals(eventsBefore + 1, countEvents(task.id()));
        assertEquals("TASK_PERMISSION_REVOKED", taskErrorCode(task.id()));
    }

    @Test
    void dataScopeShrinkAfterClaimBlocksPublish() {
        UUID owner = seedOwner("guard-scope");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        UUID contextId = seedContext(owner, 1, now().plusMinutes(30));
        TaskRow task = taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("guard-scope")));
        ClaimedTask claimed = claim(task.id(), "it-guard-scope");
        seedExportJob(task.id(), asset);
        // 管理员把账号的授权版本推高：Context 里记的 permissionVersion=1 已落后，等于范围收缩。
        bumpScopeVersion(owner, 2);

        TaskRow outcome = taskService.finish(task.id(), "it-guard-scope", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, asset);

        assertEquals(TaskStatus.FAILED, outcome.status());
        assertEquals(TaskScopeGuard.SCOPE_SHRUNK, outcome.errorCode());
        assertNull(reload(task.id()).resultAssetId());
    }

    @Test
    void expiredContextCannotBeUsedToSubmit() {
        UUID owner = seedOwner("guard-expired-ctx");
        UUID contextId = seedContext(owner, 1, now().minusSeconds(1));

        assertCode(ErrorCode.CONTEXT_EXPIRED, () -> taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("guard-expired-ctx"))));
        assertEquals(0, countTasksWithKey(newKey("unused-placeholder")));
    }

    @Test
    void foreignContextIsHiddenBehind404() {
        UUID owner = seedOwner("guard-foreign-ctx");
        UUID other = seedOwner("guard-foreign-owner");
        UUID contextId = seedContext(other, 1, now().plusMinutes(30));

        assertCode(ErrorCode.DATA_NOT_FOUND, () -> taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("guard-foreign-ctx"))));
    }

    @Test
    void shrunkContextCannotBeUsedToSubmit() {
        UUID owner = seedOwner("guard-shrunk-ctx");
        UUID contextId = seedContext(owner, 1, now().plusMinutes(30));
        bumpScopeVersion(owner, 5);

        assertCode(ErrorCode.PERMISSION_DATA_SCOPE, () -> taskService.create(command(owner,
                Map.of("contextId", contextId.toString()), newKey("guard-shrunk-ctx"))));
    }

    @Test
    void taskTypeNarrowedByPermissionCannotBeSubmitted() {
        UUID owner = seedOwner("guard-type-narrow", List.of("analysis:create"));

        assertCode(ErrorCode.PERMISSION_DENIED,
                () -> taskService.create(command(owner, Map.of(), newKey("guard-type-narrow"))));
    }

    @Test
    void exportResultMustBelongToCurrentOwner() {
        UUID owner = seedOwner("guard-asset");
        UUID other = seedOwner("guard-asset-other");
        UUID foreignAsset = seedFileAsset(other, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-asset")));
        ClaimedTask claimed = claim(task.id(), "it-guard-asset");

        TaskRow outcome = taskService.finish(task.id(), "it-guard-asset", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, foreignAsset);

        assertEquals(TaskStatus.FAILED, outcome.status());
        assertEquals(TaskScopeGuard.RESULT_REFERENCE_INVALID, outcome.errorCode());
        assertNull(reload(task.id()).resultAssetId());
    }

    @Test
    void exportedFileMustBeActiveAtPublishTime() {
        UUID owner = seedOwner("guard-asset-del");
        UUID deletedAsset = seedFileAsset(owner, "DELETED");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-asset-del")));
        ClaimedTask claimed = claim(task.id(), "it-guard-asset-del");

        TaskRow outcome = taskService.finish(task.id(), "it-guard-asset-del", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, deletedAsset);

        assertEquals(TaskStatus.FAILED, outcome.status());
        assertEquals(TaskScopeGuard.RESULT_REFERENCE_INVALID, outcome.errorCode());
    }

    @Test
    void cancelRequestIsNotACompletedCancellation() {
        UUID owner = seedOwner("guard-cancel");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-cancel")));
        ClaimedTask claimed = claim(task.id(), "it-guard-cancel");

        TaskService.CancelOutcome accepted = taskService.cancel(task.id(), owner);
        assertFalse(accepted.immediate(), "RUNNING 的取消只是已受理，不是已完成");
        assertEquals("RUNNING", taskStatus(task.id()));
        assertNotNull(jdbc.queryForObject(
                "SELECT cancel_requested_at FROM analysis.async_task WHERE id = ?",
                OffsetDateTime.class, task.id()));

        // 已请求取消的任务不得发布结果——包括"最后一刻跑完"的那次。
        assertCode(ErrorCode.TASK_CONFLICT, () -> taskService.finish(task.id(), "it-guard-cancel",
                claimed.task().leaseGeneration(), TaskStatus.SUCCESS, null, null, asset));
        assertEquals("RUNNING", taskStatus(task.id()));

        // 租约到期后由 reaper 收成 CANCELLED，而不是"取消请求"永久挂着。
        expireLease(task.id());
        taskService.reapExpiredLeases(now());
        assertEquals("CANCELLED", taskStatus(task.id()));
        assertEquals(TaskService.LEASE_EXPIRED, taskErrorCode(task.id()));
    }

    @Test
    void staleGenerationAfterReclaimCannotPublish() {
        UUID owner = seedOwner("guard-gen");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-gen")));
        ClaimedTask first = claim(task.id(), "it-gen-w1");
        long staleGeneration = first.task().leaseGeneration();

        expireLease(task.id());
        taskService.reapExpiredLeases(now());
        // 收回后 next_run_at 被推到 now+retryDelay（30s），这里把重排时间拉回当下再领取，
        // 否则断言会退化成"等 30 秒"。
        jdbc.update("UPDATE analysis.async_task SET next_run_at = now() - interval '1 second' WHERE id = ?", task.id());
        assertEquals("QUEUED", taskStatus(task.id()),
                () -> "收回纯计算任务后应重新排队，实际 " + taskStatus(task.id())
                        + " errorCode=" + taskErrorCode(task.id()));
        ClaimedTask second = claim(task.id(), "it-gen-w2");
        assertTrue(second.task().leaseGeneration() > staleGeneration);

        assertCode(ErrorCode.TASK_CONFLICT, () -> taskService.finish(task.id(), "it-gen-w1",
                staleGeneration, TaskStatus.SUCCESS, null, null, null));
        assertEquals("RUNNING", taskStatus(task.id()));

        // 新持有者用新代际可以正常收尾。
        TaskRow done = taskService.finish(task.id(), "it-gen-w2", second.task().leaseGeneration(),
                TaskStatus.CANCELLED, TaskService.LEASE_EXPIRED, "收回", null);
        assertEquals(TaskStatus.CANCELLED, done.status());
    }

    @Test
    void wrongOwnerCannotPublishEvenWithRightGeneration() {
        UUID owner = seedOwner("guard-owner");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-owner")));
        ClaimedTask claimed = claim(task.id(), "it-owner-w1");

        assertCode(ErrorCode.TASK_CONFLICT, () -> taskService.finish(task.id(), "it-owner-w2",
                claimed.task().leaseGeneration(), TaskStatus.SUCCESS, null, null, null));
        assertEquals("RUNNING", taskStatus(task.id()));
        assertEquals(0, countAttempts(task.id(), "SUCCESS"));
    }

    @Test
    void expiredLeaseCannotPublish() {
        UUID owner = seedOwner("guard-expiry");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-expiry")));
        ClaimedTask claimed = claim(task.id(), "it-expiry-w1");
        expireLease(task.id());

        assertCode(ErrorCode.TASK_CONFLICT, () -> taskService.finish(task.id(), "it-expiry-w1",
                claimed.task().leaseGeneration(), TaskStatus.SUCCESS, null, null, null));
        assertEquals("RUNNING", taskStatus(task.id()));
        assertEquals(0, countAttempts(task.id(), "SUCCESS"));
    }

    @Test
    void rejectedPublishLeavesConsistentAttemptEventAndPointerState() {
        UUID owner = seedOwner("guard-atomic");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-atomic")));
        ClaimedTask claimed = claim(task.id(), "it-guard-atomic");
        seedExportJob(task.id(), asset);
        revoke(owner, "capture:export");

        taskService.finish(task.id(), "it-guard-atomic", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, asset);

        TaskRow row = reload(task.id());
        assertEquals(TaskStatus.FAILED, row.status());
        assertEquals(TaskScopeGuard.PERMISSION_REVOKED, row.errorCode());
        assertNull(row.resultAssetId());
        assertEquals(1, countAttempts(task.id(), "FAILED"));
        assertEquals(0, countAttempts(task.id(), "SUCCESS"));
        // attempt 行的终态与任务行一致：不能出现"任务 FAILED、attempt 仍 RUNNING"的半截。
        assertEquals("FAILED", jdbc.queryForObject("""
                SELECT result FROM analysis.task_attempt WHERE task_id = ? ORDER BY attempt_no DESC LIMIT 1
                """, String.class, task.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM analysis.task_attempt WHERE task_id = ?", Integer.class, task.id()),
                "被拒绝的发布只关闭当前 attempt，不得凭空多插一条");
    }

    @Test
    void leaseMustStillBeValidAtPublishAndHeartbeatExtendsIt() {
        UUID owner = seedOwner("guard-hb");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-hb")));
        ClaimedTask claimed = claim(task.id(), "it-guard-hb");
        // 把租约压到 5 秒后再心跳：Instants.nowUtc() 截到秒，同一秒内 claim→renew 的 now 相同，
        // 直接比较会得到“续租没有变化”的假失败；这里制造可观察的推进。
        jdbc.update("UPDATE analysis.async_task SET lease_until = now() + interval '5 seconds' WHERE id = ?",
                task.id());
        OffsetDateTime before = reload(task.id()).leaseUntil();

        assertTrue(taskService.renew(task.id(), "it-guard-hb", claimed.task().leaseGeneration()),
                "未过期的租约必须能续租");
        OffsetDateTime after = reload(task.id()).leaseUntil();
        assertTrue(after.isAfter(before),
                () -> "续租必须把 lease_until 推到 now+60s：before=" + before + " after=" + after);
        Duration window = Duration.between(now(), after);
        assertTrue(window.toSeconds() >= 55 && window.toSeconds() <= 65,
                "租约窗口应为 60 秒，实际 " + window);
    }

    /**
     * M05：并发“取消”与“完成”只会产生一个终态，落败的一侧不得留下半截结果。
     *
     * <p>两边的写操作都是单条带栅栏的 UPDATE（取消写 {@code cancel_requested_at}、完成要求
     * {@code cancel_requested_at IS NULL}），所以数据库只能让一个先赢；这里用真库并发把
     * “先赢后输”的两种交错都跑到，断言不存在“SUCCESS + 取消请求”或“取消请求 + 结果指针”的混合态。
     */
    @Test
    void concurrentCancelAndFinishNeverProduceTwoTerminalOutcomes() throws Exception {
        UUID owner = seedOwner("guard-race");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("guard-race")));
        ClaimedTask claimed = claim(task.id(), "it-guard-race");
        seedExportJob(task.id(), asset);
        long generation = claimed.task().leaseGeneration();

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.atomic.AtomicReference<Throwable> finishFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> cancelFailure =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.Future<?> finisher = pool.submit(() -> {
            try {
                barrier.await();
                taskService.finish(task.id(), "it-guard-race", generation,
                        TaskStatus.SUCCESS, null, null, asset);
            } catch (Throwable failure) {
                finishFailure.set(failure);
            }
        });
        java.util.concurrent.Future<?> canceller = pool.submit(() -> {
            try {
                barrier.await();
                taskService.cancel(task.id(), owner);
            } catch (Throwable failure) {
                cancelFailure.set(failure);
            }
        });
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS),
                "并发取消/完成必须在 30 秒内收敛");
        finisher.get();
        canceller.get();

        TaskRow after = reload(task.id());
        if (after.status() == TaskStatus.SUCCESS) {
            // 完成先赢：结果指针、attempt 与任务行一致，取消请求不得落在同一行上。
            assertEquals(asset, after.resultAssetId());
            assertNull(after.cancelRequestedAt(), "SUCCESS 行上不能残留取消请求");
            assertEquals(1, countAttempts(task.id(), "SUCCESS"));
            assertEquals(0, countAttempts(task.id(), "CANCELLED") + countAttempts(task.id(), "FAILED"));
        } else {
            // 取消先赢：完成必须被 409 拒绝，且没有发布任何结果。
            assertEquals(TaskStatus.RUNNING, after.status(), "取消只登记请求，终态由 reaper 收");
            assertNotNull(after.cancelRequestedAt(), "取消先赢时必须已登记 cancel_requested_at");
            assertNull(after.resultAssetId(), "完成被拒绝时不得留下结果指针");
            assertEquals(0, countAttempts(task.id(), "SUCCESS"));
            assertNotNull(finishFailure.get(), "取消先赢时完成必须显式失败，不能静默成功");
            assertTrue(finishFailure.get() instanceof com.xxplatform.api.common.ApiException api
                            && api.code() == ErrorCode.TASK_CONFLICT,
                    "完成被取消请求挡住时应是 409 TASK_CONFLICT，实际 " + finishFailure.get());
            assertNull(cancelFailure.get());
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM analysis.task_attempt WHERE task_id = ?", Integer.class, task.id()),
                "并发下只允许一条 attempt（不得一边完成一边再插 attempt）");
    }
}
