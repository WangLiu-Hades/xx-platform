package com.xxplatform.api.service.task;

/**
 * 统一执行任务的状态（契约 {@code TaskStatus} 与 {@code analysis.async_task.status} 的唯一取值集）。
 *
 * <p>迁移 {@code V1__baseline_design_2.sql} 里 {@code analysis.async_task.status} 用
 * {@code CHECK (status IN (...))} 限定，{@code analysis.guard_task_transition()} 触发器限定合法迁移；
 * 这里的枚举必须与两者逐字一致，改一处就要同时改另两处，否则代码会写出库直接拒绝的状态值。
 *
 * <p><b>为什么 {@code FAILED} 算终态却还能被重试</b>：{@code FAILED} 是"这一次执行失败了"，
 * 不是一个不可撤销的结论——契约 {@code retryTask} 明确"仅 FAILED 允许重试，同 taskId 新建尝试"。
 * 所以 {@link #isTerminal()} 表示"不再有在途工作"，重试由状态机单独放行 {@code FAILED -> QUEUED}。
 */
public enum TaskStatus {

    /** 已创建，等待入队（同时是表默认值）。 */
    PENDING,

    /** 已入队，等待某个 worker 领取。 */
    QUEUED,

    /** 已被 worker 领取并持有租约。 */
    RUNNING,

    /** 执行成功，结果已发布。 */
    SUCCESS,

    /** 执行失败；可被重试改回 {@code QUEUED}。 */
    FAILED,

    /** 已取消（排队中直接取消，或运行中收到取消请求后由 worker／reaper 收尾）。 */
    CANCELLED;

    /** 是否已无在途工作（成功、失败或取消）。 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }

    /** 是否可以再次排队（只有 {@code FAILED} 允许重试）。 */
    public boolean isRetryable() {
        return this == FAILED;
    }
}
