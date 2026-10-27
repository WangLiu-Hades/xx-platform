package com.xxplatform.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务底座（B-05）的策略参数（{@code xx.task.*}）。
 *
 * <p>这些值决定"任务卡多久算死""重试几次放弃"，是**运维策略**而不是业务规则，所以做成配置：
 * 生产环境按 worker 的部署密度调，不必重新发版。
 *
 * <p><b>默认值只是能跑起来的保守值</b>：租约 2 分钟意味着 worker 崩了最多 2 分钟任务才被收回，
 * 而正常一次导出可能跑十几分钟——worker 必须在中途续租（{@code TaskService.renewLease}），
 * 否则会被自己的 reaper 打断。这一点写进 {@code applications/api/README.md} 的运维说明前，
 * 先按 {@code lease < 单次执行时长} 调大。
 */
@ConfigurationProperties(prefix = "xx.task")
public class TaskProperties {

    /** 一次领取的租约时长；worker 必须在到期前续租。 */
    private Duration lease = Duration.ofMinutes(2);

    /**
     * 最多允许几次尝试。
     *
     * <p>reaper 收回任务时若 {@code current_attempt_no} 已达上限，直接把任务置为
     * {@code FAILED}（{@code TASK_LEASE_EXPIRED}），而不是无限重排——否则一条"一跑就崩"的任务
     * 会让 worker 永远在重领它。
     */
    private int maxAttempts = 3;

    /** 每轮 reaper 最多处理多少条，避免一次停机后堆积的租约把数据库压住。 */
    private int reaperBatchSize = 50;

    /** 被收回的任务等多久再允许领取（给下游一点恢复时间）。 */
    private Duration retryDelay = Duration.ofSeconds(30);

    /** 是否启用后台 reaper（默认关；{@code db} profile 里打开）。 */
    private boolean reaperEnabled = false;

    public Duration getLease() {
        return lease;
    }

    public void setLease(Duration lease) {
        this.lease = lease;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getReaperBatchSize() {
        return reaperBatchSize;
    }

    public void setReaperBatchSize(int reaperBatchSize) {
        this.reaperBatchSize = reaperBatchSize;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public boolean isReaperEnabled() {
        return reaperEnabled;
    }

    public void setReaperEnabled(boolean reaperEnabled) {
        this.reaperEnabled = reaperEnabled;
    }
}
