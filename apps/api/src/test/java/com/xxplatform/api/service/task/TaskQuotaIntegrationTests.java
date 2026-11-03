package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 领取/配额/时限的真库用例（M06）。
 *
 * <p>每个用例自建 owner 与任务，并在结束前把领到的任务收成终态：{@code claimNext} 是全局队列，
 * 残留的 RUNNING 会占住系统级 I/O 槽位，让下一个用例领不到任务。
 */
class TaskQuotaIntegrationTests extends TaskIntegrationTestSupport {

    private static final String WORKER = "it-worker-quota";

    @Test
    void pendingTasksCountTowardTheFiveWaitingTaskLimit() {
        UUID owner = seedOwner("pending-budget");
        for (int i = 0; i < 5; i++) taskService.create(command(owner, Map.of(), newKey("pending-budget")));
        assertEquals(5, repository.countQueuedByOwner(owner));
        assertCode(ErrorCode.RATE_LIMITED, () -> taskService.create(command(owner, Map.of(), newKey("pending-overflow"))));
    }

    @Test
    void threeQueuedHeavyTasksStillAllowTheFirstTwoToBeClaimed() {
        UUID owner = seedOwner("three-heavy");
        for (int i = 0; i < 3; i++) taskService.create(command(owner, TaskType.ANALYSIS, Map.of(), newKey("three-heavy")));
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(status -> { for (int i = 0; i < 3; i++) repository.promoteNext(now()); });
        assertEquals(2, taskService.claimNext("it-three-heavy", List.of(TaskType.ANALYSIS), 3).size());
        assertEquals(1, queuedOf(owner));
    }

    @Test
    void fullComputeBudgetDoesNotBlockAnAvailableNotificationSlot() {
        UUID owner = seedOwner("mixed-slot");
        for (int i = 0; i < 2; i++) taskService.create(command(owner, TaskType.ANALYSIS, Map.of(), newKey("mixed-compute"), 10));
        taskService.create(command(owner, TaskType.NOTIFICATION, Map.of(), newKey("mixed-notification"), 1));
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .executeWithoutResult(status -> { for (int i = 0; i < 3; i++) repository.promoteNext(now()); });
        assertEquals(2, taskService.claimNext("it-fill-compute", List.of(TaskType.ANALYSIS), 2).size());
        List<ClaimedTask> ready = taskService.claimNext("it-mixed-worker", List.of(TaskType.ANALYSIS, TaskType.NOTIFICATION), 1);
        assertEquals(1, ready.size());
        assertEquals(TaskType.NOTIFICATION, ready.get(0).task().taskType());
    }

    @BeforeAll
    static void startContext() {
        boot();
    }

    @AfterEach
    void clearQueue() {
        // claimNext 使用全局队列，用例结束时释放占用的 I/O 槽位，避免影响其他测试类。
        abandonAllNonTerminalTasks();
    }

    @Test
    @DisplayName("priority 数值大者先领，同级按 submittedAt、id 升序")
    void claimOrderFollowsPriorityThenSubmittedAtThenId() {
        UUID owner = seedOwner("quota-order");
        UUID low = create(owner, TaskType.EXPORT, "order-low");
        UUID high = create(owner, TaskType.EXPORT, "order-high");
        UUID mid = create(owner, TaskType.EXPORT, "order-mid");
        // async_task.priority 的 CHECK 是 0..10（schema.sql），10 最大。
        setPriority(low, 1);
        setPriority(high, 10);
        setPriority(mid, 5);

        assertEquals(high, claimAndRelease(owner), "最高优先级必须先领");
        assertEquals(mid, claimAndRelease(owner));
        assertEquals(low, claimAndRelease(owner));

        // 同级：先提交（submitted_at 更早）的先领；id 只在同一时刻时才用于决胜。
        UUID older = create(owner, TaskType.EXPORT, "order-tie-older");
        UUID newer = create(owner, TaskType.EXPORT, "order-tie-newer");
        setSubmittedAt(older, now().minusMinutes(2));
        setSubmittedAt(newer, now().minusMinutes(1));
        assertEquals(older, claimAndRelease(owner), "同优先级先提交的先领");
        assertEquals(newer, claimAndRelease(owner));
    }

    @Test
    @DisplayName("worker 只领 supportedTypes，且不超过 limit / 系统 I/O 槽位")
    void workerOnlyClaimsSupportedTypesWithinLimit() {
        UUID owner = seedOwner("quota-worker");
        abandonAllNonTerminalTasks();
        UUID export = create(owner, TaskType.EXPORT, "worker-export");
        create(owner, TaskType.NOTIFICATION, "worker-notification");

        List<ClaimedTask> claimed = taskService.claimNext(
                WORKER + "-export-only", List.of(TaskType.EXPORT), 5);
        assertFalse(claimed.isEmpty(), "只有一条 EXPORT，应该领得到");
        assertEquals(export, claimed.get(0).task().id(), "只支持 EXPORT 的 worker 不能领到通知任务");
        assertEquals(1, claimed.size(), "limit=5 但队列里只有一条 EXPORT");

        UUID n1 = create(owner, TaskType.NOTIFICATION, "worker-n1");
        create(owner, TaskType.NOTIFICATION, "worker-n2");
        // I/O 总槽位 2：已经跑着 1 条 EXPORT（长 I/O，占 1 槽），剩下的那个槽专门留给通知/命令。
        List<ClaimedTask> notify = taskService.claimNext(
                WORKER + "-notify", List.of(TaskType.NOTIFICATION), 5);
        assertFalse(notify.isEmpty(), "保留槽位必须给通知留一条");
        assertTrue(notify.size() <= 1, "长 I/O 已占 1 槽时通知最多再占 1 槽");
        assertTrue(taskService.claimNext(WORKER + "-notify", List.of(TaskType.NOTIFICATION), 5).isEmpty(),
                "系统 I/O 并发 2 已占满，再领必须为空");
        assertNotNull(n1, "通知任务已建");
    }

    @Test
    @DisplayName("每用户排队上限 5，第 6 个提交 429 并带 Retry-After")
    void queuedTasksPerOwnerAreCappedAtFive() {
        UUID owner = seedOwner("quota-queue");
        for (int i = 0; i < 5; i++) {
            create(owner, TaskType.NOTIFICATION, "queue-" + i);
        }
        // 用"只支持 ANALYSIS 的 worker"把 PENDING 逐条提升到 QUEUED 而不真正领取。
        for (int i = 0; i < 5; i++) {
            taskService.claimNext(WORKER + "-promote", List.of(TaskType.ANALYSIS), 1);
        }
        assertEquals(5, queuedOf(owner), "5 条应全部进入 QUEUED");

        ApiException failure = assertCode(ErrorCode.RATE_LIMITED,
                () -> create(owner, TaskType.NOTIFICATION, "queue-6"));
        assertNotNull(failure.retryAfter(), "排队满必须给 Retry-After");
        assertEquals(5, queuedOf(owner), "被拒绝的提交不能落库");
    }

    @Test
    @DisplayName("系统全局队列上限 1000，超出后提交 429")
    void globalQueueCapIsEnforced() {
        UUID owner = seedOwner("quota-global");
        jdbc.update("""
                INSERT INTO analysis.async_task
                    (id, owner_user_id, task_type, name, parameter_schema_version, parameter_snapshot,
                     parameter_hash, idempotency_key, trace_id, priority)
                SELECT gen_random_uuid(), ?, 'NOTIFICATION', 'bulk-' || g, 'notification.v1', '{}'::jsonb,
                       repeat('a', 64), 'bulk-' || ? || '-' || g, 'it-trace', 10
                  FROM generate_series(1, 1000) g
                """, owner, owner.toString());

        ApiException failure = assertCode(ErrorCode.RATE_LIMITED,
                () -> create(owner, TaskType.NOTIFICATION, "global-over"));
        assertNotNull(failure.retryAfter(), "全局队列满必须给 Retry-After");
        assertEquals(1000, jdbc.queryForObject(
                "SELECT count(*) FROM analysis.async_task WHERE deleted = false", Integer.class));
    }

    @Test
    @DisplayName("8 线程并发领取 20 个任务：不重复、不超系统 I/O 槽位、不超每用户上限")
    void concurrentClaimsNeverHandOutTheSameTaskTwice() throws Exception {
        List<UUID> owners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            UUID owner = seedOwner("quota-concurrent-" + i);
            owners.add(owner);
            for (int j = 0; j < 5; j++) {
                create(owner, TaskType.NOTIFICATION, "concurrent-" + i + "-" + j);
            }
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<UUID> claimedIds = new ConcurrentLinkedQueue<>();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String worker = WORKER + "-concurrent-" + i;
            futures.add(pool.submit(() -> {
                start.await();
                while (true) {
                    List<ClaimedTask> claimed = taskService.claimNext(
                            worker, List.of(TaskType.NOTIFICATION), 1);
                    if (claimed.isEmpty()) {
                        return null;
                    }
                    claimedIds.add(claimed.get(0).task().id());
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发领取必须在 60 秒内收敛");
        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }

        Set<UUID> unique = new HashSet<>(claimedIds);
        assertEquals(unique.size(), claimedIds.size(), "同一条任务不能被两个 worker 领到");
        assertTrue(claimedIds.size() <= AsyncTaskRepository.MAX_RUNNING_IO,
                "领取数不能超过系统 I/O 并发上限 " + AsyncTaskRepository.MAX_RUNNING_IO
                        + "，实际 " + claimedIds.size());
        for (UUID owner : owners) {
            assertTrue(runningHeavy(owner) <= 2, "每用户 RUNNING 重任务不能超过 2");
            assertTrue(queuedOf(owner) <= 5, "每用户 QUEUED 不能超过 5");
        }
    }

    @Test
    @DisplayName("排队超时（普通 5 分钟）由 reaper 收成 FAILED/TASK_QUEUE_TIMEOUT")
    void queueDeadlineFailsTasksThatWaitedTooLong() {
        UUID owner = seedOwner("quota-queue-deadline");
        UUID taskId = create(owner, TaskType.NOTIFICATION, "queue-timeout");
        backdateSubmitted(taskId, Duration.ofMinutes(6));

        taskService.reapExpiredLeases(now());

        assertEquals("FAILED", taskStatus(taskId));
        assertEquals("TASK_QUEUE_TIMEOUT", taskErrorCode(taskId));
    }

    @Test
    @DisplayName("执行超时（普通 30 分钟）发起取消，而不是直接改终态")
    void executionDeadlineRequestsCancellation() {
        UUID owner = seedOwner("quota-exec-deadline");
        UUID taskId = create(owner, TaskType.NOTIFICATION, "exec-timeout");
        ClaimedTask claimed = claim(taskId, WORKER + "-exec");
        backdateStarted(taskId, Duration.ofMinutes(31));

        taskService.reapExpiredLeases(now());

        assertEquals("RUNNING", taskStatus(taskId), "超时只能发起取消，终态由租约到期收尾");
        assertNotNull(jdbc.queryForObject(
                "SELECT cancel_requested_at FROM analysis.async_task WHERE id = ?",
                OffsetDateTime.class, taskId), "必须写入取消请求");
        assertCode(ErrorCode.TASK_CONFLICT,
                () -> taskService.finish(taskId, WORKER + "-exec", claimed.task().leaseGeneration(),
                        TaskStatus.SUCCESS, null, null, null));
        assertEquals("RUNNING", taskStatus(taskId), "已请求取消后不得再发布 SUCCESS");
    }

    @Test
    @DisplayName("租约 60 秒、心跳 10 秒：心跳把 lease_until 延长到 now+60s")
    void heartbeatExtendsLeaseByLeaseDuration() {
        UUID owner = seedOwner("quota-heartbeat");
        UUID taskId = create(owner, TaskType.NOTIFICATION, "heartbeat");
        claim(taskId, WORKER + "-heartbeat");
        jdbc.update("UPDATE analysis.async_task SET lease_until = now() + interval '5 seconds' WHERE id = ?",
                taskId);

        properties.setWorkerIds(Set.of(WORKER + "-heartbeat"));
        int renewed = taskService.heartbeatLeases(now());
        properties.setWorkerIds(Set.of());

        assertEquals(1, renewed);
        Long seconds = jdbc.queryForObject("""
                SELECT EXTRACT(EPOCH FROM (lease_until - now()))::bigint
                  FROM analysis.async_task WHERE id = ?
                """, Long.class, taskId);
        assertNotNull(seconds);
        assertTrue(seconds >= 55 && seconds <= 62, "心跳后租约应约为 60 秒，实际 " + seconds);
        assertEquals(60, properties.getLease().toSeconds(), "契约要求租约 60 秒");
        assertEquals(10, properties.getHeartbeat().toSeconds(), "契约要求心跳 10 秒");
    }

    // ------------------------------------------------------------------ helpers

    private UUID create(UUID owner, TaskType type, String key) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        return taskService.create(command(owner, type, parameters, newKey(key))).id();
    }

    /**
     * 按队列顺序领一条并立刻收成 CANCELLED，返回被领到的任务 id。
     *
     * <p>为什么要收尾：EXPORT 属于长 I/O，系统只允许 1 条长 I/O 同时在跑；前一条不收尾，
     * 后一条就领不到，排序断言会退化成"第二条领取为空"。
     */
    private UUID claimAndRelease(UUID owner) {
        List<ClaimedTask> claimed = taskService.claimNext(WORKER + "-order", List.of(TaskType.EXPORT), 1);
        if (claimed.isEmpty()) {
            throw new AssertionError("期望领到 owner " + owner + " 的 EXPORT 任务");
        }
        ClaimedTask task = claimed.get(0);
        assertEquals(owner, task.task().ownerUserId());
        assertEquals(TaskStatus.CANCELLED, taskService.finish(task.task().id(), WORKER + "-order",
                task.task().leaseGeneration(), TaskStatus.CANCELLED, "TEST_NOISE", "排序用例收尾", null).status());
        return task.task().id();
    }
}
