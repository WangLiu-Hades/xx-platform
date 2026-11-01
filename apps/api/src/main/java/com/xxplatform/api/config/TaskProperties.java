package com.xxplatform.api.config;

import com.xxplatform.api.service.task.TaskType;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务底座（B-05）的策略参数（{@code xx.task.*}）。
 *
 * <p>这些值决定"任务卡多久算死""重试几次放弃"，是**运维策略**而不是业务规则，所以做成配置：
 * 生产环境按 worker 的部署密度调，不必重新发版。
 *
 * <p><b>租约默认 60 秒</b>（M06，{@code INTERNAL_TASK_PROTOCOL.md:16}）：协议写的是"heartbeat 每
 * 10 秒；有效且未过期、generation 匹配时延长至当前时刻 + 60 秒"。所以这里两件事必须一起成立：
 * 租约长度 = 60 秒、心跳间隔 = 10 秒（= 1/6 租约，连续丢 5 次心跳才过期）。旧默认 2 分钟与协议
 * 不符，会让"租约到期"比协议晚整整一分钟，reaper 收回与告警都被推迟。
 *
 * <p><b>心跳是调用方的动作，不是服务端的定时器</b>：协议把 heartbeat 定义成一个 Lease 操作，
 * 由持有租约的 worker 每 10 秒调用 {@code TaskService.renew}；服务端只负责"延长到 now+60 秒"。
 * 反过来由服务端定时给所有 RUNNING 任务续租是危险的——worker 已经死掉、但它名下的租约还在被
 * 服务端续，reaper 永远收不回。本类只提供 {@link #getHeartbeat()} 作为"约定的节奏"，另外给
 * 同进程 worker（{@link #getWorkerIds()}）提供一条可选的本地心跳，见 {@code TaskReaper#heartbeat}。
 *
 * <p><b>为什么默认值写在这里、计数上限写成常量</b>：租约/重试/批量是部署策略；而"每用户 2 个重任务
 * + 5 个排队"是甲方签认的产品配额（P-003-02），做成可配会让不同环境对"同一用户能堆多少活"给出
 * 不同答案，且领取/提升的 SQL 把它内联进去才能让"计数 + 迁移状态"落在同一条语句里。
 */
@ConfigurationProperties(prefix = "xx.task")
public class TaskProperties {

    /**
     * 每用户最多"排队中"（{@code PENDING+QUEUED}）的任务数，P-003-02（USER_CONFIRMED）。
     *
     * <p><b>为什么是常量而不是配置</b>：这是甲方签认的产品配额，不是部署策略。做成可配会让
     * 不同环境对"同一用户能堆多少活"给出不同答案，排障时还要先问配置；而领取/提升的 SQL
     * 把它内联进去，才能让"计数 + 迁移状态"落在同一条语句、同一个快照里。
     *
     * <p>口径是 {@code PENDING+QUEUED}：{@code PENDING} 的语义是"已受理、尚未排期"，它同样占着
     * 用户的待办额度。只数 {@code QUEUED} 会让"提交了 100 条但一条都没排期"绕过上限。
     */
    public static final int MAX_QUEUED_TASKS_PER_OWNER = 5;

    /**
     * 每用户最多并行的重型任务数，P-003-02（USER_CONFIRMED）。
     *
     * <p>重型类型清单见 {@link #complexTaskTypes}（默认与
     * {@code AsyncTaskRepository.HEAVY_TASK_TYPES} 一致）：导出、导入、回填、分析、模型执行、
     * 档案合并/拆分、集合运算、报告。它们共同点是长时间占用 CPU/IO 或外部资源，和"每用户 2 个"
     * 一起构成公平性闸门，避免一个用户把 worker 全占满。
     */
    public static final int MAX_RUNNING_HEAVY_TASKS_PER_OWNER = 2;

    /**
     * 协议约定的心跳间隔（{@code INTERNAL_TASK_PROTOCOL.md:16}：每 10 秒）。
     *
     * <p>写成常量而不是配置：它是协议的一部分（与租约 60 秒绑定），改了就不再兼容既有 worker。
     * 服务端用它做两件事：校验配置的租约不短于一个心跳周期、给同进程 worker 提供默认定时节奏。
     */
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    /**
     * 设备命令（{@code DEVICE_COMMAND}）的命令 TTL，P-018-02：120 秒。
     *
     * <p>命令超时后必须转 UNKNOWN 并先回读/对账，不能重发；所以租约不许无限续到 TTL 之外——
     * {@code TaskService.renew} 把租约截到 {@code 领取时刻 + TTL}，到期后由 reaper 按副作用类型
     * 收成 {@code FAILED + TASK_OUTCOME_UNKNOWN}。
     */
    public static final Duration COMMAND_TTL = Duration.ofSeconds(120);

    /** 默认的全局"待执行 + 运行中"任务上限（见 {@link #maxActiveTasks}）。 */
    public static final int DEFAULT_MAX_ACTIVE_TASKS = 1000;

    /** 一次领取的租约时长；worker 必须在到期前每 10 秒续租一次。 */
    private Duration lease = Duration.ofSeconds(60);

    /**
     * 心跳间隔的配置镜像，只用于校验（{@code heartbeat < lease}）与同进程 worker 的定时节奏。
     *
     * <p>为什么还要可配：本地调试时把租约调到 10 秒、心跳调到 2 秒能更快复现"租约丢失"；
     * 但协议要求生产默认 60/10，所以默认值来自 {@link #HEARTBEAT_INTERVAL}。
     */
    private Duration heartbeat = HEARTBEAT_INTERVAL;

    /**
     * 全局"未终态"任务上限（414 / 429 的阈值），契约只写了"排队满 429 并给 Retry-After"，
     * 没有给全局数字；1000 是骨架暂定的容量默认值，部署时按数据库与 worker 规模覆盖。
     *
     * <p>计数口径：{@code PENDING+QUEUED+RUNNING} 且未删除。超过即拒绝新的提交（429 + Retry-After），
     * 而不是先收下再无限堆积——队列无界时任何"每用户 5 个"都只是把爆炸推迟。
     */
    private int maxActiveTasks = DEFAULT_MAX_ACTIVE_TASKS;

    /**
     * 本进程内运行的 worker id 集合，供可选的服务端心跳使用（默认空 = 不接管任何租约）。
     *
     * <p>协议把 heartbeat 定义为 worker 自己的动作；只有"worker 与 API 同进程部署"（单机/嵌入式
     * worker）时，本进程才可以替它续租。把 worker id 显式列出来，而不是"续所有 RUNNING"，
     * 是为了避免把别的节点上已经失联的 worker 的租约也续上——那会让 reaper 永远收不回任务。
     */
    private Set<String> workerIds = new LinkedHashSet<>();

    /** 普通任务的最长排队时间，P-003-01：5 分钟（从 submittedAt 起算，不含执行）。 */
    private Duration normalQueueLimit = Duration.ofMinutes(5);

    /** 复杂任务的最长排队时间，P-003-01：15 分钟。 */
    private Duration complexQueueLimit = Duration.ofMinutes(15);

    /** 普通任务的最长执行时间，P-020-02：30 分钟（从实际 RUNNING 起算）。 */
    private Duration normalExecutionLimit = Duration.ofMinutes(30);

    /** 复杂任务的最长执行时间，P-020-02：2 小时。 */
    private Duration complexExecutionLimit = Duration.ofHours(2);

    /**
     * 复杂任务类型（P-003-01 "Task 类型全量分类见配置"）。
     *
     * <p>默认取重型集合（与 {@code AsyncTaskRepository.HEAVY_TASK_TYPES} 同源）。{@code REPORT}
     * 默认按普通任务处理（P-020-02）：BUNDLE/跨月才升级为复杂，而"报表是不是 BUNDLE"属于报表
     * 领域尚未接线的输入，这里不猜测参数内容。
     */
    private Set<TaskType> complexTaskTypes = EnumSet.of(
            TaskType.ANALYSIS, TaskType.SET_OPERATION, TaskType.MODEL_RUN,
            TaskType.PROFILE_MERGE, TaskType.PROFILE_SPLIT, TaskType.EXPORT,
            TaskType.INGESTION, TaskType.BACKFILL, TaskType.DATASET_IMPORT);

    /** 最多允许几次尝试（reaper 收回 + 统一预算，不是协议里可调的业务值）。 */
    private int maxAttempts = 3;

    /** 每轮 reaper 最多处理多少条，避免一次停机后堆积的租约把数据库压住。 */
    private int reaperBatchSize = 50;

    /** 被收回的任务等多久再允许领取（给下游一点恢复时间）。 */
    private Duration retryDelay = Duration.ofSeconds(30);

    /** 是否启用后台 reaper + 心跳 + 时限巡检（默认关；{@code db} profile 里打开）。 */
    private boolean reaperEnabled = false;

    public Duration getLease() {
        return lease;
    }

    public void setLease(Duration lease) {
        this.lease = lease;
    }

    public Duration getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Duration heartbeat) {
        this.heartbeat = heartbeat;
    }

    public int getMaxActiveTasks() {
        return maxActiveTasks;
    }

    public void setMaxActiveTasks(int maxActiveTasks) {
        this.maxActiveTasks = maxActiveTasks;
    }

    public Set<String> getWorkerIds() {
        return workerIds;
    }

    public void setWorkerIds(Set<String> workerIds) {
        this.workerIds = workerIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(workerIds);
    }

    public Duration getNormalQueueLimit() {
        return normalQueueLimit;
    }

    public void setNormalQueueLimit(Duration normalQueueLimit) {
        this.normalQueueLimit = normalQueueLimit;
    }

    public Duration getComplexQueueLimit() {
        return complexQueueLimit;
    }

    public void setComplexQueueLimit(Duration complexQueueLimit) {
        this.complexQueueLimit = complexQueueLimit;
    }

    public Duration getNormalExecutionLimit() {
        return normalExecutionLimit;
    }

    public void setNormalExecutionLimit(Duration normalExecutionLimit) {
        this.normalExecutionLimit = normalExecutionLimit;
    }

    public Duration getComplexExecutionLimit() {
        return complexExecutionLimit;
    }

    public void setComplexExecutionLimit(Duration complexExecutionLimit) {
        this.complexExecutionLimit = complexExecutionLimit;
    }

    public Set<TaskType> getComplexTaskTypes() {
        return complexTaskTypes;
    }

    public void setComplexTaskTypes(Set<TaskType> complexTaskTypes) {
        this.complexTaskTypes = complexTaskTypes == null
                ? EnumSet.noneOf(TaskType.class) : EnumSet.copyOf(complexTaskTypes);
    }

    /** 该类型是否属于复杂任务（决定排队/执行时限与重型并发额度）。 */
    public boolean isComplex(TaskType taskType) {
        return taskType != null && complexTaskTypes.contains(taskType);
    }

    public Duration queueLimit(TaskType taskType) {
        return isComplex(taskType) ? complexQueueLimit : normalQueueLimit;
    }

    public Duration executionLimit(TaskType taskType) {
        return isComplex(taskType) ? complexExecutionLimit : normalExecutionLimit;
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
