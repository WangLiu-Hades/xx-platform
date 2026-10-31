package com.xxplatform.api.service.task;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态迁移规则（{@code analysis.guard_task_transition()} 触发器的 Java 镜像）。
 *
 * <p><b>谁说了算</b>：数据库触发器是唯一权威——它拦住任何被手工 SQL、旧版本代码或脚本
 * 写坏的状态迁移，并在违规时抛 {@code 23514}。本类不是"另一份规则"，而是**让失败发生在
 * Java 里、带着契约错误码**：没有它，一次非法迁移会以 {@code 23514} 冒到兜底 handler，
 * 变成 500 {@code SYSTEM_INTERNAL}，客户端看到的故障和真实原因（状态冲突）不符。
 *
 * <p>因此两处必须逐条一致；{@code TaskStateMachineTests} 逐对枚举了全部 36 种组合，
 * 迁移一旦变更、测试会先红。
 *
 * <p>允许的迁移（与触发器逐字对应）：
 * <pre>
 * PENDING → QUEUED, FAILED, CANCELLED
 * QUEUED  → RUNNING, FAILED, CANCELLED
 * RUNNING → SUCCESS, FAILED, CANCELLED, QUEUED   （QUEUED 是租约到期被 reaper 收回）
 * FAILED  → QUEUED                                （重试）
 * SUCCESS → （终态）
 * CANCELLED → （终态）
 * </pre>
 *
 * <p><b>注意 {@code RUNNING -> QUEUED}</b>：这不是"重试"，而是"租约丢了、这条任务从未完成"。
 * 它保留 {@code started_at} 与已递增的 {@code current_attempt_no}，下一次领取会再开一条
 * {@code task_attempt} 行，所以历史尝试不会互相覆盖。
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = buildAllowed();

    private TaskStateMachine() {
    }

    private static Map<TaskStatus, Set<TaskStatus>> buildAllowed() {
        Map<TaskStatus, Set<TaskStatus>> allowed = new EnumMap<>(TaskStatus.class);
        allowed.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.QUEUED, TaskStatus.FAILED, TaskStatus.CANCELLED));
        allowed.put(TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        allowed.put(TaskStatus.RUNNING, EnumSet.of(
                TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.QUEUED));
        allowed.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.QUEUED));
        allowed.put(TaskStatus.SUCCESS, EnumSet.noneOf(TaskStatus.class));
        allowed.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        return Map.copyOf(allowed);
    }

    /**
     * 是否允许 {@code from -> to}。
     *
     * <p>同状态视为允许：写路径上"已经是这个状态"的重复调用（并发重试、取消后又收到一次完成回报）
     * 不应该报错，调用方据此决定是否写库；真正的非法组合才返回 {@code false}。
     */
    public static boolean allows(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("状态不能为空");
        }
        if (from == to) {
            return true;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 断言迁移合法，否则抛 409 {@code TASK_CONFLICT}。
     *
     * <p>用 409 而不是 400：状态冲突是"当前资源状态不允许这个动作"，与参数格式错误不同，
     * 契约对 {@code retryTask}／{@code deleteDeviceCommand} 等操作的非法状态统一返回 409。
     */
    public static void require(TaskStatus from, TaskStatus to) {
        if (!allows(from, to)) {
            throw new ApiException(ErrorCode.TASK_CONFLICT,
                    "任务当前状态为 " + from + "，不允许执行该操作");
        }
    }
}
