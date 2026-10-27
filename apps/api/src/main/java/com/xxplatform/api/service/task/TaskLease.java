package com.xxplatform.api.service.task;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 任务租约（{@code lease_owner}／{@code lease_until}／{@code lease_generation}）的算术部分。
 *
 * <p>租约解决的是"worker 领取了任务然后消失了"：没有租约，任务永远停在 {@code RUNNING}，
 * 谁也不知道它是还在跑还是已经死了。租约到期后由 {@link TaskReaper} 收回并重新排队。
 *
 * <p>三件东西必须一起用：
 * <ul>
 *   <li>{@code lease_until}：死线，过了就视为 worker 失联；</li>
 *   <li>{@code lease_owner}：谁领的，防止 A 的续租/完成回报作用到 B 领取的同一条任务上；</li>
 *   <li>{@code lease_generation}：每次领取递增的栅栏令牌（fencing token）。只比 owner 不够——
 *       worker 失联后重连，可能带着"上一次领取"的过期认知回来写结果，代际号能拦住它。</li>
 * </ul>
 *
 * <p>本类只做纯计算，不做任何 I/O，好在单测里枚举边界。
 */
public final class TaskLease {

    private TaskLease() {
    }

    /**
     * 续租后的新死线。
     *
     * @param now      当前时间（调用方传入，避免一次操作里出现两个 {@code now}）
     * @param duration 租约时长
     */
    public static OffsetDateTime deadline(OffsetDateTime now, Duration duration) {
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("租约时长必须为正");
        }
        return now.plus(duration);
    }

    /**
     * 租约是否已到期。
     *
     * <p>用 {@code !leaseUntil.isAfter(now)} 而不是 {@code leaseUntil.isBefore(now)}：恰好等于
     * {@code now} 的租约已经没有剩余时间，继续认为它有效会让 reaper 在边界上多等一拍。
     *
     * <p>{@code leaseUntil == null} 视为已到期：{@code RUNNING} 却没有租约只可能是数据被手工改过，
     * 保守起见让 reaper 收回去，而不是让它永久卡住。
     */
    public static boolean isExpired(OffsetDateTime leaseUntil, OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        return leaseUntil == null || !leaseUntil.isAfter(now);
    }

    /**
     * 任务是否需要被 reaper 处理：必须处于 {@code RUNNING} 且租约已到期。
     *
     * <p>非 {@code RUNNING} 的任务没有在途 worker，与租约无关；把这条判断收在一处，
     * SQL 的 {@code WHERE status='RUNNING' AND lease_until <= ?} 与它对得上。
     */
    public static boolean needsReaping(TaskStatus status, OffsetDateTime leaseUntil, OffsetDateTime now) {
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        return status == TaskStatus.RUNNING && isExpired(leaseUntil, now);
    }
}
