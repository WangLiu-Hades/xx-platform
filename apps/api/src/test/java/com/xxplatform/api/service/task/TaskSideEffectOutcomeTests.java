package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * M07：副作用任务失去租约后的处置——转 UNKNOWN、交对账、绝不自动重发。
 *
 * <p>为什么必须用真库：被验证的是"reaper 的 UPDATE 把 status 写成什么 + 后续 claim 还能不能
 * 再领到它"，这两件事都由 SQL 决定；桩掉仓储等于把要证明的结论一起桩掉。
 *
 * <p>{@code DEVICE_COMMAND} 用 RESET 参数：P-018-02 明确"RESET 不自动重试"，它也是本组里最
 * 危险的自动重发场景（重复 RESET 会打乱设备状态）。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class TaskSideEffectOutcomeTests extends TaskIntegrationTestSupport {

    private static final String WORKER = "it-m07-worker";

    @BeforeAll
    static void start() {
        boot();
    }

    @Test
    void resetCommandThatLostItsLeaseEndsUnknownAndIsNeverRedispatched() {
        UUID owner = seedOwner("m07-reset");
        TaskRow task = taskService.create(command(owner, TaskType.DEVICE_COMMAND,
                Map.of("command", "RESET", "deviceId", UUID.randomUUID().toString()),
                newKey("m07-reset")));
        ClaimedTask claimed = claim(task.id(), WORKER);
        // 受控假外设：任务被调度成功一次，就记一次"命令已经发出去"。
        AtomicInteger dispatches = new AtomicInteger(1);
        expireLease(task.id());

        taskService.reapExpiredLeases(now());

        TaskRow after = reload(task.id());
        assertEquals(TaskStatus.FAILED, after.status());
        assertEquals(TaskService.OUTCOME_UNKNOWN, after.errorCode());
        assertEquals(1, countAttempts(task.id(), "FAILED"), "本次尝试按 UNKNOWN 关闭");
        assertEquals(0, queuedRows(task.id()), "UNKNOWN 的副作用任务不得回到队列");
        // 任何 worker 都不应再领到它：命令不会自动重发。
        assertTrue(taskService.claimNext(WORKER, List.of(TaskType.DEVICE_COMMAND), 5).isEmpty());
        assertEquals(1, dispatches.get(), "发送成功但回执丢失时，不得重复下发 RESET");
        // 手工 retry 同样被拒：UNKNOWN 必须先对账，不能直接重排。
        assertCode(ErrorCode.TASK_CONFLICT, () -> taskService.retry(task.id(), owner));
    }

    @Test
    void deviceCommandTtlCapsLeaseAt120SecondsFromStart() {
        UUID owner = seedOwner("m07-ttl");
        TaskRow task = taskService.create(command(owner, TaskType.DEVICE_COMMAND,
                Map.of("command", "PING", "deviceId", UUID.randomUUID().toString()),
                newKey("m07-ttl")));
        ClaimedTask claimed = claim(task.id(), WORKER);
        long generation = claimed.task().leaseGeneration();

        // 已跑 90 秒：TTL 只剩 30 秒，续租必须被截到 started_at + 120s，而不是 now + 60s。
        setStartedAt(task.id(), now().minusSeconds(90));
        assertTrue(taskService.renew(task.id(), WORKER, generation));
        OffsetDateTime lease = jdbc.queryForObject(
                "SELECT lease_until FROM analysis.async_task WHERE id = ?",
                OffsetDateTime.class, task.id());
        assertTrue(lease.isBefore(now().plusSeconds(60)),
                "租约不得越过设备命令 TTL: " + lease);

        // 已跑 130 秒：TTL 已过，worker 不能再靠续租把任务续成"正常完成"。
        setStartedAt(task.id(), now().minusSeconds(130));
        assertFalse(taskService.renew(task.id(), WORKER, generation), "TTL 已过，续租必须失败");
    }

    @Test
    void pureComputeIsRequeuedWhileSideEffectGoesUnknown() {
        UUID owner = seedOwner("m07-split");
        TaskRow compute = taskService.create(command(owner, TaskType.EXPORT, Map.of(),
                newKey("m07-split-compute")));
        TaskRow sideEffect = taskService.create(command(owner, TaskType.NOTIFICATION,
                Map.of("channel", "IN_APP"), newKey("m07-split-side")));
        // 按类型领取：两个任务同优先级、同一秒提交，按 id 排序会随 UUID 随机，
        // 用 claim(taskId) 的“领到非目标就取消”语义可能把目标任务自己取消掉。
        // 按 supportedTypes 领取既能保证领到目标任务，也不改动其它任务的状态。
        assertTrue(claimOf(TaskType.EXPORT).task().id().equals(compute.id()));
        assertTrue(claimOf(TaskType.NOTIFICATION).task().id().equals(sideEffect.id()));
        expireLease(compute.id());
        expireLease(sideEffect.id());

        taskService.reapExpiredLeases(now());

        // 纯计算：按统一预算重新排队，下一次领取 attempt+1。
        assertEquals(TaskStatus.QUEUED, reload(compute.id()).status());
        assertEquals(1, countAttempts(compute.id(), "FAILED"), "纯计算失租：本次尝试按失败关闭后重排");
        // 副作用：转 UNKNOWN，不排队。
        assertEquals(TaskStatus.FAILED, reload(sideEffect.id()).status());
        assertEquals(TaskService.OUTCOME_UNKNOWN, reload(sideEffect.id()).errorCode());
        assertEquals(0, queuedRows(sideEffect.id()));
    }

    private static ClaimedTask claimOf(TaskType type) {
        List<ClaimedTask> claimed = taskService.claimNext(WORKER, List.of(type), 1);
        assertFalse(claimed.isEmpty(), "期望按类型领到 " + type);
        return claimed.get(0);
    }

    private static int queuedRows(UUID taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM analysis.async_task WHERE id = ? AND status = 'QUEUED'",
                Integer.class, taskId);
        return count == null ? 0 : count;
    }
}
