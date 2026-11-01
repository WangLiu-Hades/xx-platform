package com.xxplatform.api.infra;

import com.xxplatform.api.config.TaskProperties;
import com.xxplatform.api.service.task.TaskCommand;
import com.xxplatform.api.service.task.TaskSortBy;
import com.xxplatform.api.service.task.TaskStatus;
import com.xxplatform.api.service.task.TaskType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * {@code analysis.async_task} / {@code analysis.task_attempt} / {@code analysis.task_event}
 * 的读写面（B-05 任务底座）。
 *
 * <p><b>为什么所有"领取/完成/续租"都写成带守卫的 UPDATE，而不是先 SELECT 再 UPDATE</b>：
 * 任务的并发点是"多个 worker 抢同一条"和"慢 worker 带着过期租约回来写结果"。前者的正确做法是
 * {@code FOR UPDATE SKIP LOCKED} 让抢输的人立刻换下一条；后者的正确做法是把
 * {@code lease_owner}／{@code lease_generation} 写进 {@code WHERE}，让过期写命中 0 行。
 * 先查后改在两者之间都有窗口，一旦命中就是"同一条任务被跑两遍"或"两次结果互相覆盖"。
 *
 * <p><b>为什么状态迁移不在 SQL 里判断</b>：{@code analysis.guard_task_transition()} 触发器已经在
 * 库里把关（非法迁移抛 {@code 23514}）。SQL 只负责"原子地改成目标状态"，迁移合法性由
 * {@code TaskStateMachine} 在调用前检查，得到的是契约错误码而不是数据库异常。
 *
 * <p>JSON 参数以 {@code ?::jsonb} 写入：{@code parameter_snapshot} 是 {@code jsonb} 列，直接传字符串
 * 会让 PostgreSQL 尝试隐式转换并报"column is of type jsonb but expression is of type character varying"。
 */
@Repository
public class AsyncTaskRepository {

    /** 任务行的完整列集（与 {@link TaskRow} 顺序无关，按列名取值）。 */
    private static final String COLUMNS = """
            id, name, task_type, owner_user_id, status, progress, priority,
            parameter_schema_version, parameter_snapshot::text AS parameter_snapshot,
            parameter_hash, algorithm_version, idempotency_key, resource_type, resource_id,
            submitted_at, started_at, finished_at, lease_owner, lease_until, lease_generation,
            next_run_at, cancel_requested_at, retry_count, current_attempt_no,
            error_code, error_message, result_asset_id, trace_id, deleted, version, created_at
            """;

    /**
     * 插入一条 {@code PENDING} 任务；幂等键已被占用时不插入、只影响 0 行。
     *
     * <p><b>为什么用 {@code ON CONFLICT ... DO NOTHING}，而不是让唯一约束抛错再捕获</b>：
     * 两个并发的同幂等键请求可能同时查不到、同时插入。若让唯一约束抛
     * {@code DuplicateKeyException}，PostgreSQL 会把整个事务置为 aborted，之后连"回读赢家"
     * 的 SELECT 都会以 {@code 25P02 current transaction is aborted} 失败——捕获异常并不能把
     * 事务救回来。{@code DO NOTHING} 把冲突降级成"影响 0 行"的普通结果，事务仍可继续，
     * 服务层可以立刻回读既有行并返回幂等重放。
     *
     * <p>返回影响行数：1 = 新建成功；0 = 幂等键已被别的请求占用，调用方必须回读。
     */
    private static final String INSERT = """
            INSERT INTO analysis.async_task
                (id, name, task_type, owner_user_id, status, priority, parameter_schema_version,
                 parameter_snapshot, parameter_hash, idempotency_key, resource_type, resource_id,
                 algorithm_version, next_run_at, trace_id, submitted_at, created_at)
            VALUES (?, ?, ?, ?, 'PENDING', COALESCE(?, 5), ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (owner_user_id, task_type, idempotency_key) DO NOTHING
            """;

    /** 软删除的任务对外一律视为不存在：读取、取消、重试都不应该再触达它。 */
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM analysis.async_task WHERE id = ? AND deleted = false";

    private static final String SELECT_BY_IDEMPOTENCY_KEY = "SELECT " + COLUMNS
            + " FROM analysis.async_task WHERE owner_user_id = ? AND task_type = ? AND idempotency_key = ?";

    private static final String SELECT_BY_OWNER = "SELECT " + COLUMNS + " FROM analysis.async_task";

    private static final String COUNT_BY_OWNER = "SELECT count(*) FROM analysis.async_task";

    /**
     * 带外部副作用、租约丢失后"结果未知"的任务类型（M07）。
     *
     * <p>为什么单独列出：这些任务可能已经把命令发给了设备、已经发出了通知、或已经写入了
     * 一半导入数据。租约到期时若像纯计算任务那样静默重排，等于让同一批副作用再发生一次；
     * 正确做法是转 {@code FAILED} + {@code TASK_OUTCOME_UNKNOWN}，等人工/对账链路确认。
     * 判断放在 SQL 的 CASE 里，是因为"收回租约"本身要原子完成，先查后写会留下窗口。
     */
    public static final Set<TaskType> SIDE_EFFECT_TASK_TYPES = EnumSet.of(
            TaskType.DEVICE_COMMAND, TaskType.NOTIFICATION, TaskType.INGESTION, TaskType.BACKFILL);

    /** 重型任务类型（P-003-02：每用户最多 2 个并行）；与 SQL 里的 IN 列表同源，避免两处走样。 */
    public static final Set<TaskType> HEAVY_TASK_TYPES = EnumSet.of(
            TaskType.EXPORT, TaskType.INGESTION, TaskType.BACKFILL, TaskType.ANALYSIS,
            TaskType.DATASET_IMPORT, TaskType.MODEL_RUN, TaskType.PROFILE_MERGE,
            TaskType.PROFILE_SPLIT, TaskType.SET_OPERATION, TaskType.REPORT);

    /** 把枚举名拼成 SQL 的字面量列表：只来自枚举常量，不存在注入面。 */
    private static final String HEAVY_TASK_SQL = HEAVY_TASK_TYPES.stream()
            .map(type -> "'" + type.name() + "'")
            .collect(Collectors.joining(","));

    /** 副作用类型列表的 SQL 字面量，与 {@link #SIDE_EFFECT_TASK_TYPES} 同源。 */
    private static final String SIDE_EFFECT_TASK_SQL = SIDE_EFFECT_TASK_TYPES.stream()
            .map(type -> "'" + type.name() + "'")
            .collect(Collectors.joining(","));

    /**
     * 系统级"重计算"槽位（P-003-02：系统重计算 2 并发）。
     *
     * <p>为什么系统级配额要写进领取 SQL 而不是只在服务层计数：副本可以有很多 worker、很多 API
     * 节点，任何"先 count 再 claim"的写法都会在两个并发 worker 之间留下窗口，两边同时看到
     * "还差一个槽"然后一起领走。下推到同一条 UPDATE 的 WHERE 里，计数与状态迁移才在同一个
     * 快照里完成。
     */
    public static final Set<TaskType> COMPUTE_TASK_TYPES = EnumSet.of(
            TaskType.ANALYSIS, TaskType.SET_OPERATION, TaskType.MODEL_RUN,
            TaskType.PROFILE_MERGE, TaskType.PROFILE_SPLIT);

    /**
     * 系统级"长 I/O"槽位（P-003-02：I/O 2 并发，其中 1 个槽位保留给通知/命令）。
     *
     * <p>长 I/O 只允许占 1 个槽，剩下那个槽始终留给 {@link #RESERVED_IO_TASK_TYPES}，
     * 否则一次批量导出就能把通知/设备命令全堵在队列里——这两类任务恰恰是"越晚发越没意义"的。
     */
    public static final Set<TaskType> LONG_IO_TASK_TYPES = EnumSet.of(
            TaskType.EXPORT, TaskType.INGESTION, TaskType.BACKFILL, TaskType.DATASET_IMPORT,
            TaskType.REPORT, TaskType.PERSON_LIBRARY_IMPORT, TaskType.DEVICE_IMPORT,
            TaskType.FACE_SEARCH);

    /** 保留槽位的 I/O 任务：通知与设备命令。它们与长 I/O 共享"系统 I/O 2 并发"的上限。 */
    public static final Set<TaskType> RESERVED_IO_TASK_TYPES = EnumSet.of(
            TaskType.NOTIFICATION, TaskType.DEVICE_COMMAND);

    /** 系统重计算并发上限（P-003-02）。 */
    public static final int MAX_RUNNING_COMPUTE = 2;

    /** 系统 I/O 并发上限（P-003-02）：含保留槽位。 */
    public static final int MAX_RUNNING_IO = 2;

    /** 系统长 I/O 并发上限：2 个 I/O 槽位里最多占 1 个，另一个留给通知/命令。 */
    public static final int MAX_RUNNING_LONG_IO = 1;

    /**
     * 领取临界区的全局事务锁 key（{@code pg_advisory_xact_lock}）。
     *
     * <p>系统级槽位计数天然是跨用户的，只锁 owner 挡不住"两个用户同时占满第 2 个 I/O 槽"。
     * 领取本身是毫秒级 UPDATE，序列化在整个配额记账上不会改变吞吐量级，却把"无超发"变成
     * 可证明的事实。
     */
    private static final long CLAIM_GLOBAL_LOCK = 0x5453_4B30_3031L;

    /**
     * 领取时一次性锁住多少个 owner（按排队优先级窗口取）。
     *
     * <p>为什么是有限窗口而不是"所有有排队任务的 owner"：锁表长度必须与本次领取的工作量同阶，
     * 否则一个积压了几万个 owner 的队列会让每次领取都去锁几万把锁。32 足够覆盖"同一时刻
     * 真正在抢槽位"的并发度；窗口外的 owner 由它自己的领取事务按同一顺序拿锁。
     */
    private static final int CLAIM_QUOTA_OWNER_WINDOW = 32;

    private static String sqlList(Set<TaskType> types) {
        return types.stream().map(type -> "'" + type.name() + "'").collect(Collectors.joining(","));
    }

    private static final String COMPUTE_TASK_SQL = sqlList(COMPUTE_TASK_TYPES);
    private static final String LONG_IO_TASK_SQL = sqlList(LONG_IO_TASK_TYPES);
    private static final String RESERVED_IO_TASK_SQL = sqlList(RESERVED_IO_TASK_TYPES);
    private static final String ALL_IO_TASK_SQL = sqlList(allIoTypes());

    private static Set<TaskType> allIoTypes() {
        Set<TaskType> all = EnumSet.noneOf(TaskType.class);
        all.addAll(LONG_IO_TASK_TYPES);
        all.addAll(RESERVED_IO_TASK_TYPES);
        return all;
    }

    /**
     * 把最早的一条 {@code PENDING} 提升为 {@code QUEUED}。
     *
     * <p>为什么需要这一步：迁移里的触发器只允许 {@code PENDING -> QUEUED -> RUNNING}，
     * 直接从 {@code PENDING} 置 {@code RUNNING} 会被守卫拒绝（"invalid task transition"）。
     * 这不是刁难——{@code PENDING} 的语义是"已受理但还没排期"，跳过排期等于跳过
     * {@code next_run_at} 的延迟语义（重试退避要靠它）。
     *
     * <p><b>为什么提升也要过排队配额</b>：P-003-02 规定每用户最多 5 个排队任务。若只在领取时
     * 拦，队列仍会无限增长（提交不受限，每次领取只提升一条）；在入口处把住，排到第 6 条时
     * 它留在 {@code PENDING} 等别人消化，语义正好是"已受理、未排期"。
     *
     * <p>排序与领取一致：{@code priority DESC, next_run_at ASC, created_at ASC, id ASC}，
     * 保证"先提升/先领取"对同一条任务恒成立，不会出现高优先级任务一直排不到期。
     */
    private static final String PROMOTE_PENDING = """
            WITH picked AS (
                SELECT t.id
                  FROM analysis.async_task t
                 WHERE t.status = 'PENDING' AND t.deleted = false AND t.next_run_at <= now()
                   AND (SELECT count(*)
                          FROM analysis.async_task q
                         WHERE q.owner_user_id = t.owner_user_id
                           AND q.deleted = false
                           AND q.status = 'QUEUED') < %d
                 ORDER BY t.priority DESC, t.submitted_at ASC, t.id ASC
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE analysis.async_task t
               SET status = 'QUEUED',
                   next_run_at = LEAST(t.next_run_at, now()),
                   version = t.version + 1
              FROM picked
             WHERE t.id = picked.id
            RETURNING t.id
            """.formatted(TaskProperties.MAX_QUEUED_TASKS_PER_OWNER);

    /**
     * 领取下一条可执行任务：{@code priority} 数值大者优先（契约排序：priority DESC），
     * 再按到期早、创建早、id 升序；全序保证并发下"谁先谁后"可复现。
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} 是这里的核心：并发的 worker 不会排队等同一行，
     * 抢不到的直接换下一条，吞吐随 worker 数线性增长，而不是串行化。
     *
     * <p>{@code current_attempt_no + 1} 与 {@code lease_generation + 1} 都在同一条 UPDATE 里完成，
     * 返回的 {@code previous_status} 供调用方写 {@code task_event} 的"从哪来"。
     *
     * <p><b>为什么配额过滤写在 WHERE 里而不是先查计数再领取</b>：先查后领之间会有窗口，
     * 并发 worker 可以同时看到"还没到上限"然后一起领走，配额形同虚设。子查询与 UPDATE
     * 在同一条语句、同一快照里完成，计数与领取之间没有可乘之机。
     *
     * <p><b>为什么排队计数要排除候选自身</b>（{@code q.id <> t.id}）：候选行此刻还是
     * {@code QUEUED}，若把自己算进去，排队数恰好等于 5 时每条都会看见"≥5 个排队"而被永久
     * 跳过，队列再也无法被消化。排除自身后，5 条排队仍允许领取一条（领完剩 4 条），
     * 真正的上限由 {@link #PROMOTE_PENDING} 把住。
     *
     * <p>{@code withTypeFilter} 只在 worker 声明了支持类型时加
     * {@code task_type = ANY (string_to_array(?, ','))}：不声明就代表"什么都能干"，
     * 拼一个空数组会让所有任务都匹配不上（{@code ANY('{}')} 恒假），worker 会永远空转。
     */
    private static String claimSql(boolean withTypeFilter) {
        return """
                WITH quota AS (
                    SELECT GREATEST(%d - (SELECT count(*)
                              FROM analysis.async_task c
                             WHERE c.deleted = false AND c.status = 'RUNNING'
                               AND c.task_type IN (%s)), 0) AS compute_left,
                           GREATEST(%d - (SELECT count(*)
                              FROM analysis.async_task c
                             WHERE c.deleted = false AND c.status = 'RUNNING'
                               AND c.task_type IN (%s)), 0) AS long_io_left,
                           GREATEST(%d - (SELECT count(*)
                              FROM analysis.async_task c
                             WHERE c.deleted = false AND c.status = 'RUNNING'
                               AND c.task_type IN (%s)), 0) AS io_left
                ),
                ranked AS (
                    SELECT t.id,
                           t.status AS previous_status,
                           t.priority,
                           t.submitted_at,
                           (SELECT count(*)
                              FROM analysis.async_task h
                             WHERE h.owner_user_id = t.owner_user_id
                               AND h.deleted = false
                               AND h.status = 'RUNNING'
                               AND h.task_type IN (%s)) AS owner_heavy_running,
                           count(*) FILTER (WHERE t.task_type IN (%s))
                               OVER (ORDER BY t.priority DESC, t.submitted_at ASC, t.id ASC) AS compute_rn,
                           count(*) FILTER (WHERE t.task_type IN (%s))
                               OVER (ORDER BY t.priority DESC, t.submitted_at ASC, t.id ASC) AS long_io_rn,
                           count(*) FILTER (WHERE t.task_type IN (%s))
                               OVER (ORDER BY t.priority DESC, t.submitted_at ASC, t.id ASC) AS io_rn,
                           count(*) FILTER (WHERE t.task_type IN (%s))
                               OVER (PARTITION BY t.owner_user_id) AS owner_heavy_rn
                      FROM analysis.async_task t
                     WHERE t.status = 'QUEUED' AND t.deleted = false AND t.next_run_at <= now()
                       %s
                       AND (SELECT count(*)
                              FROM analysis.async_task q
                             WHERE q.owner_user_id = t.owner_user_id
                               AND q.deleted = false
                               AND q.status = 'QUEUED'
                               AND q.id <> t.id) < %d
                ),
                picked AS (
                    SELECT r.id, r.previous_status
                      FROM ranked r, quota q
                     WHERE r.compute_rn <= q.compute_left
                       AND r.long_io_rn <= q.long_io_left
                       AND r.io_rn <= q.io_left
                       AND r.owner_heavy_running + r.owner_heavy_rn <= %d
                     ORDER BY r.priority DESC, r.submitted_at ASC, r.id ASC
                     LIMIT ?
                )
                UPDATE analysis.async_task t
                   SET status = 'RUNNING',
                       started_at = COALESCE(t.started_at, ?),
                       lease_owner = ?,
                       lease_until = ?,
                       lease_generation = t.lease_generation + 1,
                       current_attempt_no = t.current_attempt_no + 1,
                       version = t.version + 1
                  FROM picked
                 WHERE t.id = picked.id AND t.status = 'QUEUED'
                RETURNING t.*, picked.previous_status
                """.formatted(
                MAX_RUNNING_COMPUTE, COMPUTE_TASK_SQL,
                MAX_RUNNING_LONG_IO, LONG_IO_TASK_SQL,
                MAX_RUNNING_IO, ALL_IO_TASK_SQL,
                HEAVY_TASK_SQL,
                COMPUTE_TASK_SQL, LONG_IO_TASK_SQL, ALL_IO_TASK_SQL, HEAVY_TASK_SQL,
                withTypeFilter ? "AND t.task_type = ANY (string_to_array(?, ','))" : "",
                TaskProperties.MAX_QUEUED_TASKS_PER_OWNER,
                TaskProperties.MAX_RUNNING_HEAVY_TASKS_PER_OWNER);
    }

    /** 不按 worker 支持类型过滤（声明"什么都能干"时用）。 */
    private static final String CLAIM_NEXT = claimSql(false);

    /** 只领取 {@code task_type} 在给定列表里的任务。 */
    private static final String CLAIM_NEXT_FILTERED = claimSql(true);

    /**
     * 续租：只有"还是我的、代际没变、没收到取消、租约还没到期"才成功。
     *
     * <p>{@code cancel_requested_at IS NULL} 是刻意的：一旦有人请求取消，worker 的续租就失败，
     * 它会据此停下来——比让 reaper 到点后才发现"这条早该停了"更快。
     *
     * <p>{@code lease_until > now}（M05）：租约已经过期的 worker 不能靠"再续一次"把自己
     * 复活。若允许，reaper 收回后旧 worker 的续租会与新的持有者并存，同一条任务又变成
     * 两个执行者；这里用写库时刻做栅栏，过期即失去续租资格。
     */
    private static final String RENEW_LEASE = """
            UPDATE analysis.async_task
               SET lease_until = ?, version = version + 1
             WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?
               AND cancel_requested_at IS NULL
               AND lease_until > ?
            """;

    /**
     * 完成：栅栏令牌不匹配（被 reaper 收回后重领）、已请求取消、或租约已过期则命中 0 行。
     *
     * <p>为什么"已请求取消"也要拒绝完成（M05）：取消请求意味着调用方已经不想让结果生效，
     * 此时 worker 迟到的成功结果若还能落库，会出现"任务显示 SUCCESS、但用户以为已取消"的
     * 双写。拒绝后由 reaper 在租约到期时把任务收成 {@code CANCELLED}，终态唯一。
     *
     * <p>为什么还要求 {@code lease_until > 完成时刻}：完成与续租必须用同一把尺子。worker
     * 若已经慢到租约过期，说明 reaper 可能已经把任务派给了别人，迟到的结果不能再采用。
     *
     * <p>发布结果前的权限/范围复核（P-002-05）<b>不在这条 SQL 里</b>，而在
     * {@code TaskService#finish} 调用 {@link com.xxplatform.api.service.task.TaskScopeGuard#publishBlocker}
     * 之后：复核需要读任务类型授权、Context 版本与结果指向的文件所有者，属于跨表判断，
     * 放进这条 UPDATE 会把它变成一个难以解释的巨型 WHERE。本 SQL 只负责"租约栅栏"这一半——
     * 复核未通过时由 {@code finish} 改判 FAILED，同样经过这里的栅栏，不会绕过取消/过期检查。
     */
    private static final String FINISH = """
            UPDATE analysis.async_task
               SET status = ?,
                   finished_at = ?,
                   error_code = ?,
                   error_message = ?,
                   result_asset_id = ?,
                   progress = CASE WHEN ? = 'SUCCESS' THEN 100 ELSE progress END,
                   lease_owner = NULL,
                   lease_until = NULL,
                   version = version + 1
             WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?
               AND cancel_requested_at IS NULL
               AND lease_until > ?
            """;

    private static final String REQUEST_CANCEL = """
            UPDATE analysis.async_task
               SET cancel_requested_at = COALESCE(cancel_requested_at, ?), version = version + 1
             WHERE id = ? AND status = 'RUNNING' AND deleted = false
            """;

    private static final String CANCEL_QUEUED = """
            UPDATE analysis.async_task
               SET status = 'CANCELLED', finished_at = ?, cancel_requested_at = COALESCE(cancel_requested_at, ?),
                   lease_owner = NULL, lease_until = NULL, version = version + 1
             WHERE id = ? AND status IN ('PENDING','QUEUED') AND deleted = false
            """;

    private static final String REQUEUE_FOR_RETRY = """
            UPDATE analysis.async_task
               SET status = 'QUEUED', retry_count = retry_count + 1, next_run_at = ?,
                   lease_owner = NULL, lease_until = NULL, error_code = NULL, error_message = NULL,
                   version = version + 1
             WHERE id = ? AND status = 'FAILED' AND deleted = false
            """;

    /**
     * 收回过期租约：{@code RUNNING} 且租约到期（或压根没有租约）。
     *
     * <p>去向在 SQL 里就定了：已请求取消的转 {@code CANCELLED}；带外部副作用的类型
     * （{@link #SIDE_EFFECT_TASK_TYPES}）转 {@code FAILED} + {@code TASK_OUTCOME_UNKNOWN}，
     * 因为"租约到期"不等于"没执行"——重排会让设备命令/通知/导入副作用再来一遍；纯计算
     * 类型转 {@code QUEUED} 并推迟 {@code next_run_at} 重试。判断用 CASE 而不是先查后改，
     * 是为了让"收回"本身保持单语句原子性。
     *
     * <p>是否"尝试次数用尽改判 {@code FAILED}"留给调用方——那需要读 {@code max-attempts}
     * 配置，SQL 里塞业务阈值会让迁移与配置两处各有一份。但"结果未知"的任务已经是
     * {@code FAILED}，不会再重试，自然也不参与这个判定。
     */
    private static final String EXPIRE_LEASES = """
            WITH expired AS (
                SELECT id, current_attempt_no, (cancel_requested_at IS NOT NULL) AS cancel_requested,
                       task_type IN (%s) AS side_effect
                  FROM analysis.async_task
                 WHERE status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= ?)
                 ORDER BY lease_until NULLS FIRST, id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
            ), updated AS (
                UPDATE analysis.async_task t
                   SET status = CASE WHEN e.cancel_requested THEN 'CANCELLED'
                                     WHEN e.side_effect THEN 'FAILED'
                                     ELSE 'QUEUED' END,
                       lease_owner = NULL,
                       lease_until = NULL,
                       finished_at = CASE WHEN e.cancel_requested OR e.side_effect THEN ?::timestamptz ELSE NULL END,
                       next_run_at = CASE WHEN e.cancel_requested OR e.side_effect THEN t.next_run_at ELSE ? END,
                       error_code = CASE WHEN e.side_effect AND NOT e.cancel_requested
                                         THEN 'TASK_OUTCOME_UNKNOWN' ELSE 'TASK_LEASE_EXPIRED' END,
                       error_message = CASE WHEN e.side_effect AND NOT e.cancel_requested
                                            THEN '租约到期且任务可能已产生外部副作用，结果未知，需人工对账'
                                            ELSE '租约到期未完成，任务被收回' END,
                       version = t.version + 1
                  FROM expired e
                 WHERE t.id = e.id
                RETURNING t.id, t.status, e.current_attempt_no, e.side_effect
            )
            SELECT id, status, current_attempt_no, side_effect FROM updated
            """.formatted(SIDE_EFFECT_TASK_SQL);

    private static final String FAIL_EXHAUSTED = """
            UPDATE analysis.async_task
               SET status = 'FAILED', finished_at = ?, error_code = ?, error_message = ?,
                   version = version + 1
             WHERE id = ? AND status = 'QUEUED'
            """;

    /**
     * 排队超时：还没开始执行就已经超过排队时限（P-003-01），直接判 FAILED。
     *
     * <p>为什么不是"继续排队"：排队时限是甲方签认的对外承诺（普通 5 分钟、复杂 15 分钟），
     * 超过就意味着"这条任务已经不能按约定完成了"，继续挂着只会让用户看到一条永远不动的任务。
     * 错误码用任务自己的 {@code TASK_QUEUE_TIMEOUT}（不是契约 {@code ErrorCode}），对外折成
     * {@code TASK_FAILED}，具体原因留在 {@code errorMessage}。
     */
    private static final String FAIL_QUEUE_TIMEOUT = """
            WITH overdue AS (
                SELECT id, current_attempt_no, status AS previous_status
                  FROM analysis.async_task
                 WHERE status IN ('PENDING','QUEUED') AND deleted = false
                   AND task_type IN (%s)
                   AND submitted_at <= ?
                 ORDER BY submitted_at, id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE analysis.async_task t
               SET status = 'FAILED', finished_at = ?, error_code = 'TASK_QUEUE_TIMEOUT',
                   error_message = '排队超过约定时限，已终止', version = t.version + 1
              FROM overdue o
             WHERE t.id = o.id
            RETURNING t.id, o.current_attempt_no, o.previous_status
            """;

    /**
     * 执行超时：从实际 RUNNING 起算超过执行时限（P-003-01 / P-020-02）。
     *
     * <p>只写 {@code cancel_requested_at}、不直接改终态：协议要求"超时告警并发起取消，未响应
     * 租约到期后终止发布"。直接置 CANCELLED 会绕过租约栅栏——worker 可能已经发出了副作用，
     * 必须由它确认（或租约到期由 reaper 收）才算真正结束。
     */
    private static final String REQUEST_EXECUTION_TIMEOUT = """
            WITH overdue AS (
                SELECT id, current_attempt_no
                  FROM analysis.async_task
                 WHERE status = 'RUNNING' AND deleted = false
                   AND cancel_requested_at IS NULL
                   AND task_type IN (%s)
                   AND started_at <= ?
                 ORDER BY started_at, id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE analysis.async_task t
               SET cancel_requested_at = COALESCE(t.cancel_requested_at, ?), version = t.version + 1
              FROM overdue o
             WHERE t.id = o.id
            RETURNING t.id, o.current_attempt_no
            """;

    /** 同进程 worker 的心跳：只续"自己名下、仍在跑、没被请求取消、租约还没到期"的租约。 */
    private static final String RENEW_LOCAL_LEASES = """
            UPDATE analysis.async_task
               SET lease_until = ?, version = version + 1
             WHERE status = 'RUNNING' AND deleted = false
               AND lease_owner = ANY (?)
               AND cancel_requested_at IS NULL
               AND lease_until > ?
            RETURNING id
            """;

    private static final String COUNT_ACTIVE_BY_OWNER = """
            SELECT count(*) FROM analysis.async_task
             WHERE owner_user_id = ? AND deleted = false
               AND status IN ('PENDING','QUEUED','RUNNING')
            """;

    private static final String COUNT_ACTIVE_GLOBAL = """
            SELECT count(*) FROM analysis.async_task
             WHERE deleted = false AND status IN ('PENDING','QUEUED','RUNNING')
            """;

    /** 导出作业发布的文件指针；任务行上的 {@code result_asset_id} 必须与它一致。 */
    private static final String SELECT_EXPORT_OUTPUT = """
            SELECT output_file_id FROM analysis.export_job WHERE task_id = ?
            """;

    /** 接入批：{@code INGESTION}/{@code BACKFILL} 的结果引用要带这个 {@code batchId}（M08）。 */
    private static final String SELECT_INGESTION_BATCH = """
            SELECT id FROM capture.ingestion_batch WHERE task_id = ?
            """;

    /** 报表作业：{@code REPORT} 的结果引用 {@code reportId}（M08）。 */
    private static final String SELECT_REPORT_JOB = """
            SELECT id FROM operation.report_job WHERE task_id = ?
            """;

    /** 发布前的文件可用性快照：不存在/已删/过期/别人的文件都不能作为结果公开。 */
    private static final String SELECT_FILE_ASSET = """
            SELECT status, owner_user_id, expires_at FROM core.file_asset WHERE id = ?
            """;

    /** 分析作业：{@code ANALYSIS} 的 {@code resultId} 是作业 id（契约 {@code analysisTaskId}）。 */
    private static final String SELECT_ANALYSIS_JOB = """
            SELECT id FROM analysis.analysis_job WHERE task_id = ?
            """;

    /** 集合运算作业：{@code SET_OPERATION} 的 {@code resultId} 是作业 id（契约 {@code setTaskId}）。 */
    private static final String SELECT_SET_JOB = """
            SELECT id FROM analysis.set_job WHERE task_id = ?
            """;

    /** 模型执行：{@code MODEL_RUN} 的 {@code modelId}，契约要求必须返回。 */
    private static final String SELECT_MODEL_EXECUTION = """
            SELECT model_id FROM analysis.model_execution WHERE task_id = ?
            """;

    /** 导入作业是否存在（三个 IMPORT 类型的结果引用由领域接口取，任务侧只校验已落作业行）。 */
    private static final String SELECT_IMPORT_JOB_EXISTS = """
            SELECT 1 FROM analysis.import_job WHERE task_id = ?
            """;

    /** 人像搜图作业是否存在（{@code FACE_SEARCH} 的结果就是作业本身）。 */
    private static final String SELECT_FACE_SEARCH_EXISTS = """
            SELECT 1 FROM analysis.face_search_job WHERE task_id = ?
            """;

    /**
     * 合并/拆分任务涉及的 profile id（M08：{@code PROFILE_MERGE}/{@code PROFILE_SPLIT} 的
     * {@code resultReference.profileIds}）。
     *
     * <p>为什么从修订谱系取而不是从任务行取：任务行只存 {@code result_asset_id}，
     * 合并/拆分的产物是一组档案而不是一个文件；权威事实是 {@code analysis.profile_revision}
     * （含 SOURCE/RESULT 两侧成员），任务的 {@code resource_id} 只是入口，不是全集。
     */
    private static final String SELECT_PROFILE_IDS = """
            SELECT DISTINCT member.profile_id
              FROM analysis.profile_revision r
              JOIN analysis.profile_revision_member member ON member.revision_id = r.id
             WHERE r.task_id = ?
            UNION
            SELECT DISTINCT r.profile_id
              FROM analysis.profile_revision r
             WHERE r.task_id = ?
            """;

    /** 某用户当前排队任务数（{@code QUEUED}），提交入口的"每用户 5 个排队"判定用。 */
    private static final String COUNT_QUEUED_BY_OWNER = """
            SELECT count(*) FROM analysis.async_task
             WHERE owner_user_id = ? AND deleted = false AND status = 'QUEUED'
            """;

    private static final String CLOSE_ATTEMPT = """
            UPDATE analysis.task_attempt
               SET finished_at = ?, result = ?, error_code = ?, error_message = ?
             WHERE task_id = ? AND attempt_no = ? AND finished_at IS NULL
            """;

    private static final String INSERT_ATTEMPT = """
            INSERT INTO analysis.task_attempt
                (task_id, attempt_no, started_at, worker_id, lease_generation)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO analysis.task_event
                (id, task_id, attempt_no, previous_status, status, occurred_at, reason, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * 一次任务的全部尝试，按 attempt_no 升序。
     *
     * <p>为什么按 attempt_no 而不是 startedAt 排：重试会沿用同一个 taskId 追加次数，
     * attempt_no 是主键的一部分（{@code (task_id, attempt_no)}），天然唯一且单调；
     * startedAt 在时钟回拨或并发写入时可能乱序，拿它当顺序键会让"第几次尝试"看起来是错的。
     */
    private static final String SELECT_ATTEMPTS = """
            SELECT attempt_no, started_at, finished_at, result, error_code
              FROM analysis.task_attempt
             WHERE task_id = ?
             ORDER BY attempt_no
            """;

    private static final RowMapper<AttemptRow> ATTEMPT_MAPPER = (rs, rowNum) -> new AttemptRow(
            rs.getLong("attempt_no"),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            rs.getString("result"),
            rs.getString("error_code"));

    private static final RowMapper<TaskRow> ROW_MAPPER = (rs, rowNum) -> new TaskRow(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            TaskType.valueOf(rs.getString("task_type")),
            rs.getObject("owner_user_id", UUID.class),
            TaskStatus.valueOf(rs.getString("status")),
            rs.getInt("progress"),
            rs.getInt("priority"),
            rs.getString("parameter_schema_version"),
            rs.getString("parameter_snapshot"),
            rs.getString("parameter_hash"),
            rs.getString("idempotency_key"),
            rs.getString("resource_type"),
            rs.getObject("resource_id", UUID.class),
            rs.getObject("submitted_at", OffsetDateTime.class),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class),
            rs.getString("lease_owner"),
            rs.getObject("lease_until", OffsetDateTime.class),
            rs.getLong("lease_generation"),
            rs.getObject("next_run_at", OffsetDateTime.class),
            rs.getObject("cancel_requested_at", OffsetDateTime.class),
            rs.getLong("retry_count"),
            rs.getLong("current_attempt_no"),
            rs.getString("error_code"),
            rs.getString("error_message"),
            rs.getObject("result_asset_id", UUID.class),
            rs.getString("trace_id"),
            rs.getBoolean("deleted"),
            rs.getLong("version"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getString("algorithm_version"));

    /** 领取结果的映射：任务行 + 领取前状态（写事件用）。 */
    private static final RowMapper<ClaimedTask> CLAIM_MAPPER = (rs, rowNum) -> new ClaimedTask(
            mapRow(rs),
            TaskStatus.valueOf(rs.getString("previous_status")));

    private final JdbcTemplate jdbc;

    public AsyncTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条 {@code PENDING} 任务。
     *
     * @return 1 = 新建成功；0 = 幂等键已被并发请求占用，调用方必须按作用域回读既有任务
     */
    public int insert(UUID id, TaskCommand command, OffsetDateTime now) {
        if (id == null || command == null) {
            throw new IllegalArgumentException("id 与 command 不能为空");
        }
        return jdbc.update(INSERT,
                id,
                command.name(),
                command.taskType().name(),
                command.ownerUserId(),
                command.priority(),
                command.parameterSchemaVersion(),
                command.parameterJson(),
                command.parameterHash(),
                command.idempotencyKey(),
                command.resourceType(),
                command.resourceId(),
                modelIdOf(command),
                now,
                command.traceId(),
                now,
                now);
    }

    /**
     * {@code MODEL_RUN} 的 {@code modelId}：契约（M08）要求该类型的结果引用必须带 {@code modelId}，
     * 而任务行只有 {@code algorithm_version} 一列可存它。
     *
     * <p>为什么从冻结参数里取而不是新增 {@code TaskCommand} 字段：{@code TaskCommand} 是通用提交面，
     * 为一种任务类型加领域字段会把"通用任务"变成"领域任务"；而 {@code modelId} 本来就在模型执行
     * 的冻结参数里，读的是同一份已签名快照，不存在两处不一致。取不到时保持 {@code NULL}，
     * 由发布复核（{@code TaskScopeGuard}）拒绝"没有 modelId 的 MODEL_RUN 成功结果"。
     */
    private static String modelIdOf(TaskCommand command) {
        if (command.taskType() != TaskType.MODEL_RUN || command.parameters() == null) {
            return null;
        }
        Object value = command.parameters().get("modelId");
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    public Optional<TaskRow> findById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        return jdbc.query(SELECT_BY_ID, ROW_MAPPER, id).stream().findFirst();
    }

    /** 按幂等作用域查任务：{@code (owner_user_id, task_type, idempotency_key)} 上有唯一约束。 */
    public Optional<TaskRow> findByIdempotencyKey(UUID ownerUserId, TaskType taskType, String idempotencyKey) {
        if (ownerUserId == null || taskType == null || idempotencyKey == null) {
            throw new IllegalArgumentException("幂等作用域的三个字段都不能为空");
        }
        return jdbc.query(SELECT_BY_IDEMPOTENCY_KEY, ROW_MAPPER,
                ownerUserId, taskType.name(), idempotencyKey).stream().findFirst();
    }

    /**
     * 某用户的一页任务（{@code GET /tasks}）。
     *
     * <p><b>只按 {@code owner_user_id} 过滤</b>：当前任务没有任何"领域/数据范围"字段，B-05 的可见性
     * 就是"自己的任务"。契约里的"领域权限/数据范围内"要等任务类型各自带上资源归属之后再叠加，
     * 现在假装有范围过滤反而会藏起用户自己的任务。
     *
     * <p><b>为什么 {@code task_type IN (?,...)} 是动态拼占位符而不是拼值</b>：占位符数量随筛选值个数
     * 变化，值本身仍然全部走参数绑定。直接拼值（哪怕是枚举名）会把"动态 SQL"变成注入面，而且
     * 一旦以后允许自由字符串就立刻失守。
     *
     * <p><b>为什么排序固定追加 {@code id}</b>：{@code created_at} 精确到微秒仍可能撞上
     * （批量导入、同一事务里连建几条），只按时间排序时同值行的顺序由数据库决定，翻页会出现
     * "第 2 页又看到第 1 页某条"。追加主键让排序成为全序，分页才稳定。
     */
    public List<TaskRow> findPageByOwner(UUID ownerUserId, List<TaskType> taskTypes, TaskStatus status,
                                         OffsetDateTime createdFrom, OffsetDateTime createdTo,
                                         TaskSortBy sortBy, boolean descending, int limit, long offset) {
        if (ownerUserId == null || sortBy == null) {
            throw new IllegalArgumentException("ownerUserId/sortBy 不能为空");
        }
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("limit 必须为正，offset 不能为负");
        }
        Where where = where(ownerUserId, taskTypes, status, createdFrom, createdTo);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT_BY_OWNER + where.sql() + orderBy(sortBy, descending) + " LIMIT ? OFFSET ?",
                ROW_MAPPER, args.toArray());
    }

    /** 与 {@link #findPageByOwner} 同一组筛选条件的总数；两次查询必须用同一个 WHERE，否则总页数会漂。 */
    public long countByOwner(UUID ownerUserId, List<TaskType> taskTypes, TaskStatus status,
                             OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        Where where = where(ownerUserId, taskTypes, status, createdFrom, createdTo);
        Long total = jdbc.queryForObject(COUNT_BY_OWNER + where.sql(), Long.class, where.args().toArray());
        return total == null ? 0L : total;
    }

    private static Where where(UUID ownerUserId, List<TaskType> taskTypes, TaskStatus status,
                               OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        StringBuilder sql = new StringBuilder(" WHERE owner_user_id = ? AND deleted = false");
        List<Object> args = new ArrayList<>();
        args.add(ownerUserId);
        if (taskTypes != null && !taskTypes.isEmpty()) {
            sql.append(" AND task_type IN (");
            for (int i = 0; i < taskTypes.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                args.add(taskTypes.get(i).name());
            }
            sql.append(')');
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (createdFrom != null) {
            sql.append(" AND created_at >= ?");
            args.add(createdFrom);
        }
        if (createdTo != null) {
            sql.append(" AND created_at <= ?");
            args.add(createdTo);
        }
        return new Where(sql.toString(), args);
    }

    private static String orderBy(TaskSortBy sortBy, boolean descending) {
        String column = sortBy == TaskSortBy.STATUS ? "status" : "created_at";
        String direction = descending ? " DESC" : " ASC";
        return " ORDER BY " + column + direction + ", id" + direction;
    }

    /** 拼好的 WHERE 与它的参数，按同一顺序交给 JDBC。 */
    private record Where(String sql, List<Object> args) {
    }

    /** 把最早一条到期的 {@code PENDING} 任务提升为 {@code QUEUED}；没有可提升的返回 {@code empty}。 */
    public Optional<UUID> promoteNext(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        return jdbc.query(PROMOTE_PENDING, (rs, rowNum) -> rs.getObject("id", UUID.class))
                .stream().findFirst();
    }

    /**
     * 领取一条任务；没有可领时返回 {@code empty}（不是错误，worker 下一轮再来）。
     *
     * <p>等价于"不限制任务类型、本轮只领 1 条"的兼容入口。
     */
    public Optional<ClaimedTask> claimNext(String workerId, OffsetDateTime now, Duration lease) {
        return claimNext(workerId, now, lease, List.of(), 1).stream().findFirst();
    }

    /**
     * 按 worker 能力领取任务：只领 {@code supportedTypes} 里声明过的类型，本轮最多 {@code limit} 条。
     *
     * <p>为什么"空列表"表示不限制、而不是"什么都不领"：worker 不声明能力时是通用执行器，
     * 拼一个空数组会让 {@code ANY('{}')} 恒假，队列永远领不到任务——这类静默空转比报错难查。
     *
     * @param supportedTypes 支持的任务类型；空列表 = 不限制
     * @param limit          本轮最多领取条数，必须为正
     * @return 本轮领到的任务（0..limit 条），按领取顺序排列
     */
    public List<ClaimedTask> claimNext(String workerId, OffsetDateTime now, Duration lease,
                                       List<TaskType> supportedTypes, int limit) {
        if (workerId == null || now == null || lease == null) {
            throw new IllegalArgumentException("workerId/now/lease 不能为空");
        }
        if (supportedTypes == null) {
            throw new IllegalArgumentException("supportedTypes 不能为 null，空列表表示不限制类型");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正");
        }
        // 配额临界区：必须先于领取 SQL 拿锁。子查询与 UPDATE 虽在同一语句里，但"读已提交"
        // 下两个并发领取事务仍可能都看到"未达上限"，然后各自把一条任务置 RUNNING——超发。
        // 全局锁挡住系统级槽位（重计算 2 / I/O 2 / 保留 1）的并发，owner 锁挡住"每用户 2 重任务"。
        // 这里是事务级锁，因此要求调用方（TaskService#claimNext）在事务内执行。
        lockClaimQuota(now, CLAIM_QUOTA_OWNER_WINDOW);
        OffsetDateTime leaseUntil = now.plus(lease);
        if (supportedTypes.isEmpty()) {
            return jdbc.query(CLAIM_NEXT, CLAIM_MAPPER, limit, now, workerId, leaseUntil);
        }
        // 任务类型是枚举，逗号拼接后交给 string_to_array：值不来自用户输入，没有注入面。
        String typeFilter = supportedTypes.stream().map(TaskType::name).collect(Collectors.joining(","));
        return jdbc.query(CLAIM_NEXT_FILTERED, CLAIM_MAPPER,
                typeFilter, limit, now, workerId, leaseUntil);
    }

    /**
     * 领取前的配额临界区：拿一把全局事务锁 + 一批 owner 事务锁。
     *
     * <p><b>为什么 owner 锁不够</b>：P-003-02 的一半配额是系统级的（重计算 2、I/O 2、保留槽 1），
     * 计数跨用户；两个用户同时抢最后一个 I/O 槽时，只锁 owner 是挡不住的。
     *
     * <p><b>为什么先全局后 owner、owner 按 UUID 排序</b>：所有领取事务按同一顺序取锁，
     * 就不会出现 A 锁 owner1 等全局、B 锁全局等 owner1 的环形等待。锁是事务级的
     * （{@code pg_advisory_xact_lock}），事务提交/回滚自动释放，不需要 finally。
     *
     * @param queuedOwnersLimit 最多锁多少个 owner（按排队优先级窗口取，避免一次锁全表 owner）
     */
    public int lockClaimQuota(OffsetDateTime now, int queuedOwnersLimit) {
        if (now == null || queuedOwnersLimit <= 0) {
            throw new IllegalArgumentException("now 不能为空，queuedOwnersLimit 必须为正");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?::bigint)", CLAIM_GLOBAL_LOCK);
        List<UUID> owners = jdbc.queryForList("""
                SELECT DISTINCT t.owner_user_id
                  FROM analysis.async_task t
                 WHERE t.status = 'QUEUED' AND t.deleted = false AND t.next_run_at <= now()
                 ORDER BY t.owner_user_id
                 LIMIT ?
                """, UUID.class, queuedOwnersLimit);
        for (UUID owner : owners) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                    owner.toString());
        }
        return owners.size();
    }

    /** 某用户当前"未终态"任务数（{@code PENDING+QUEUED+RUNNING}），提交配额判定用。 */
    public long countActiveByOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        Long count = jdbc.queryForObject(COUNT_ACTIVE_BY_OWNER, Long.class, ownerUserId);
        return count == null ? 0L : count;
    }

    /** 某用户当前排队任务数（{@code QUEUED}），提交入口的"每用户 5 个排队"判定用。 */
    public long countQueuedByOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        Long count = jdbc.queryForObject(COUNT_QUEUED_BY_OWNER, Long.class, ownerUserId);
        return count == null ? 0L : count;
    }

    /**
     * 提交配额的临界区：全局锁 + 该 owner 的锁。
     *
     * <p>为什么提交也要锁：{@code countActiveGlobal() < 1000} 与随后的 INSERT 之间若没有锁，
     * 并发提交可以一起越过 1000；同一 owner 的"排队 5 条"同理。锁与领取用同一把全局 key，
     * 因此"提交计数"与"领取/提升计数"看到的是同一条串行化序列。
     *
     * <p>必须由调用方在事务内调用（{@code pg_advisory_xact_lock} 随事务结束释放）。
     */
    public void lockOwnerAdmission(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?::bigint)", CLAIM_GLOBAL_LOCK);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
                ownerUserId.toString());
    }

    /** 合并/拆分任务涉及的 profile id（{@code PROFILE_MERGE}/{@code PROFILE_SPLIT} 的结果引用）。 */
    public List<UUID> profileIdsForTask(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return jdbc.query(SELECT_PROFILE_IDS,
                (rs, rowNum) -> rs.getObject("profile_id", UUID.class), taskId, taskId);
    }

    /** 全局"未终态"任务数，系统容量判定用（见 {@code TaskProperties#getMaxActiveTasks()}）。 */
    public long countActiveGlobal() {
        Long count = jdbc.queryForObject(COUNT_ACTIVE_GLOBAL, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * 把超过排队时限的任务判失败，返回被处理的行（供服务层写事件）。
     *
     * @param taskTypes 要处理的类型集合；按"普通/复杂"分两次调用，各自有自己的时限
     * @param submittedBefore 该类别的最晚受理时刻（{@code now - queueLimit}）
     */
    public List<TimedOutTask> failQueueTimeouts(Set<TaskType> taskTypes, OffsetDateTime submittedBefore,
                                                OffsetDateTime now, int batchSize) {
        if (taskTypes == null || taskTypes.isEmpty() || submittedBefore == null || now == null) {
            return List.of();
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须为正");
        }
        return jdbc.query(FAIL_QUEUE_TIMEOUT.formatted(sqlList(taskTypes)),
                (rs, rowNum) -> new TimedOutTask(
                        rs.getObject("id", UUID.class),
                        rs.getLong("current_attempt_no"),
                        TaskStatus.valueOf(rs.getString("previous_status"))),
                submittedBefore, batchSize, now);
    }

    /** 对超过执行时限的 RUNNING 任务写"已请求取消"，返回被处理的行（供告警与事件）。 */
    public List<TimedOutTask> requestExecutionTimeouts(Set<TaskType> taskTypes, OffsetDateTime startedBefore,
                                                       OffsetDateTime now, int batchSize) {
        if (taskTypes == null || taskTypes.isEmpty() || startedBefore == null || now == null) {
            return List.of();
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须为正");
        }
        // 超时这一刻任务还是 RUNNING，previous_status 固定是 RUNNING；attempt 号用于事件。
        return jdbc.query(REQUEST_EXECUTION_TIMEOUT.formatted(sqlList(taskTypes)),
                (rs, rowNum) -> new TimedOutTask(
                        rs.getObject("id", UUID.class),
                        rs.getLong("current_attempt_no"),
                        TaskStatus.RUNNING),
                startedBefore, batchSize, now);
    }
    /**
     * 同进程 worker 的心跳续租（{@code TaskReaper#heartbeat} 每 10 秒调用）。
     *
     * @return 实际被延长的任务 id
     */
    public List<UUID> renewLocalLeases(Set<String> workerIds, OffsetDateTime now, OffsetDateTime leaseUntil) {
        if (workerIds == null || workerIds.isEmpty() || now == null || leaseUntil == null) {
            return List.of();
        }
        String[] owners = workerIds.toArray(String[]::new);
        return jdbc.query(RENEW_LOCAL_LEASES,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                leaseUntil, owners, now);
    }

    /** 接入批 id（{@code INGESTION}/{@code BACKFILL} 的 {@code batchId}）；没有作业行时为空。 */
    public Optional<UUID> ingestionBatchId(UUID taskId) {
        return firstUuid(SELECT_INGESTION_BATCH, taskId, "id");
    }

    /** 报表作业 id（{@code REPORT} 的 {@code reportId}）；没有作业行时为空。 */
    public Optional<UUID> reportJobId(UUID taskId) {
        return firstUuid(SELECT_REPORT_JOB, taskId, "id");
    }

    /** 导出作业已登记的输出文件 id；作业行不存在或还没产出文件时为空。 */
    public Optional<UUID> exportOutputFileId(UUID taskId) {
        return firstUuid(SELECT_EXPORT_OUTPUT, taskId, "output_file_id");
    }

    /** 分析作业 id（{@code ANALYSIS.resultId}）；没有作业行时为空。 */
    public Optional<UUID> analysisJobId(UUID taskId) {
        return firstUuid(SELECT_ANALYSIS_JOB, taskId, "id");
    }

    /** 集合运算作业 id（{@code SET_OPERATION.resultId}）；没有作业行时为空。 */
    public Optional<UUID> setJobId(UUID taskId) {
        return firstUuid(SELECT_SET_JOB, taskId, "id");
    }

    /** 模型执行绑定的 {@code modelId}（{@code MODEL_RUN.modelId}，契约要求必返）。 */
    public Optional<UUID> modelIdForTask(UUID taskId) {
        return firstUuid(SELECT_MODEL_EXECUTION, taskId, "model_id");
    }

    /** 导入作业是否已创建（三个 IMPORT 类型发布前的存在性校验）。 */
    public boolean importJobExists(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return !jdbc.query(SELECT_IMPORT_JOB_EXISTS, (rs, rowNum) -> rs.getInt(1), taskId).isEmpty();
    }

    /** 人像搜图作业是否已创建。 */
    public boolean faceSearchJobExists(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return !jdbc.query(SELECT_FACE_SEARCH_EXISTS, (rs, rowNum) -> rs.getInt(1), taskId).isEmpty();
    }

    private Optional<UUID> firstUuid(String sql, UUID taskId, String column) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return jdbc.query(sql, (rs, rowNum) -> rs.getObject(column, UUID.class), taskId)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /** 文件资产的发布前快照；不存在时为空（调用方据此拒绝公开结果）。 */
    public Optional<FileAssetState> fileAssetState(UUID assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("assetId 不能为空");
        }
        return jdbc.query(SELECT_FILE_ASSET,
                (rs, rowNum) -> new FileAssetState(
                        rs.getString("status"),
                        rs.getObject("owner_user_id", UUID.class),
                        rs.getObject("expires_at", OffsetDateTime.class)),
                assetId).stream().findFirst();
    }

    /** 一次排队/执行超时的处理结果：任务 id、attempt 号、迁移前状态。 */
    public record TimedOutTask(UUID taskId, long attemptNo, TaskStatus previousStatus) {
    }

    /** 文件资产的发布前快照（{@code status}/{@code owner}/{@code expires_at}）。 */
    public record FileAssetState(String status, UUID ownerUserId, OffsetDateTime expiresAt) {
    }

    /**
     * 续租；返回 0 表示"已不是我的 / 代际过期 / 已请求取消 / 租约已过期"。
     *
     * @param now 续租发生的时刻，用于比较 {@code lease_until}（不能用数据库时钟，
     *            否则与调用方计算新租约的时钟不一致，边界上会反复抖动）
     */
    public int renewLease(UUID taskId, String workerId, long generation,
                          OffsetDateTime now, OffsetDateTime leaseUntil) {
        if (taskId == null || workerId == null || now == null || leaseUntil == null) {
            throw new IllegalArgumentException("taskId/workerId/now/leaseUntil 不能为空");
        }
        return jdbc.update(RENEW_LEASE, leaseUntil, taskId, workerId, generation, now);
    }

    /**
     * 完成（成功/失败/取消均由 worker 回报）；返回 0 表示结果不能被采用。
     *
     * <p>{@code finishedAt} 同时充当 M05 的租约栅栏时刻：它就是 worker 回报完成的时刻，
     * 再传一个 {@code now} 只会让两个时刻在边界上打架。
     */
    public int finish(UUID taskId, String workerId, long generation, TaskStatus outcome,
                      String errorCode, String errorMessage, UUID resultAssetId, OffsetDateTime finishedAt) {
        if (taskId == null || workerId == null || outcome == null || finishedAt == null) {
            throw new IllegalArgumentException("taskId/workerId/outcome/finishedAt 不能为空");
        }
        return jdbc.update(FINISH,
                outcome.name(), finishedAt, errorCode, errorMessage, resultAssetId,
                outcome.name(), taskId, workerId, generation, finishedAt);
    }

    /** 运行中请求取消：只记时间戳，状态留给 worker 或 reaper 收尾。 */
    public int requestCancel(UUID taskId, OffsetDateTime at) {
        return jdbc.update(REQUEST_CANCEL, at, taskId);
    }

    /** 排队中直接取消。 */
    public int cancelQueued(UUID taskId, OffsetDateTime at) {
        return jdbc.update(CANCEL_QUEUED, at, at, taskId);
    }

    /** 把失败任务重新排队（重试同 taskId、attemptNo 由下一次领取递增）。 */
    public int requeueForRetry(UUID taskId, OffsetDateTime at) {
        return jdbc.update(REQUEUE_FOR_RETRY, at, taskId);
    }

    /**
     * 收回过期租约，返回被回收的任务（含上一轮 attempt 号，供关闭 attempt 行）。
     *
     * <p>{@code unknownOutcome} 来自 SQL 的 {@code side_effect} 判定：哪些任务转 {@code FAILED}
     * 是 SQL 的职责（原子），返回给服务层是为了让它用"结果未知"而不是"租约过期"的理由
     * 关闭 attempt、并且不再自动重试。
     */
    public List<ExpiredLease> expireLeases(OffsetDateTime now, OffsetDateTime nextRunAt, int batchSize) {
        if (now == null || nextRunAt == null || batchSize <= 0) {
            throw new IllegalArgumentException("now/nextRunAt 不能为空，batchSize 必须为正");
        }
        return jdbc.query(EXPIRE_LEASES, (rs, rowNum) -> {
            TaskStatus status = TaskStatus.valueOf(rs.getString("status"));
            boolean sideEffect = rs.getBoolean("side_effect");
            return new ExpiredLease(
                    rs.getObject("id", UUID.class),
                    status,
                    rs.getLong("current_attempt_no"),
                    sideEffect && status == TaskStatus.FAILED);
        }, now, batchSize, now, nextRunAt);
    }

    /** 尝试次数用尽：把刚被收回的任务改判 {@code FAILED}（{@code QUEUED -> FAILED} 是合法迁移）。 */
    public int failExhausted(UUID taskId, OffsetDateTime at, String errorCode, String errorMessage) {
        return jdbc.update(FAIL_EXHAUSTED, at, errorCode, errorMessage, taskId);
    }

    /** 关闭一条未结束的尝试行（租约丢失时以 {@code FAILED} 收尾）。 */
    public int closeAttempt(UUID taskId, long attemptNo, OffsetDateTime at,
                            TaskStatus result, String errorCode, String errorMessage) {
        return jdbc.update(CLOSE_ATTEMPT, at, result.name(), errorCode, errorMessage, taskId, attemptNo);
    }

    /** 记录一次尝试的开始（与领取同一事务）。 */
    public void insertAttempt(UUID taskId, long attemptNo, OffsetDateTime startedAt,
                              String workerId, long leaseGeneration) {
        jdbc.update(INSERT_ATTEMPT, taskId, attemptNo, startedAt, workerId, leaseGeneration);
    }

    /** 记录一次状态迁移（{@code previousStatus} 为 {@code null} 表示创建）。 */
    public void insertEvent(UUID id, UUID taskId, long attemptNo, TaskStatus previousStatus,
                            TaskStatus status, OffsetDateTime occurredAt, String reason, String traceId) {
        jdbc.update(INSERT_EVENT, id, taskId, attemptNo,
                previousStatus == null ? null : previousStatus.name(), status.name(),
                occurredAt, reason, traceId);
    }

    /**
     * 读一个任务的尝试历史（调用方必须已确认任务归属与读权限）。
     *
     * <p>{@code userId} 不在这里过滤：归属判定已在 {@code TaskService.get} 里做过一次，
     * 这里再拼一次 owner 条件会让"任务存在但没尝试"与"不是你的任务"两种 200/404 语义混在一条 SQL 里。
     */
    public List<AttemptRow> listAttempts(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return jdbc.query(SELECT_ATTEMPTS, ATTEMPT_MAPPER, taskId);
    }

    private static TaskRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return ROW_MAPPER.mapRow(rs, 0);
    }

    /**
     * 任务行的读写视图；时间均为 UTC。
     *
     * <p>{@code algorithmVersion} 放在末尾（而不是跟随 {@code parameter_hash}）：它是 M08 为
     * {@code MODEL_RUN} 的 {@code modelId} 追加的列，放在末尾让既有的 30 参构造调用继续可用，
     * 不必改动一堆与本次修复无关的测试夹具。
     */
    public record TaskRow(
            UUID id,
            String name,
            TaskType taskType,
            UUID ownerUserId,
            TaskStatus status,
            int progress,
            int priority,
            String parameterSchemaVersion,
            String parameterSnapshot,
            String parameterHash,
            String idempotencyKey,
            String resourceType,
            UUID resourceId,
            OffsetDateTime submittedAt,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String leaseOwner,
            OffsetDateTime leaseUntil,
            long leaseGeneration,
            OffsetDateTime nextRunAt,
            OffsetDateTime cancelRequestedAt,
            long retryCount,
            long currentAttemptNo,
            String errorCode,
            String errorMessage,
            UUID resultAssetId,
            String traceId,
            boolean deleted,
            long version,
            OffsetDateTime createdAt,
            String algorithmVersion) {

        /**
         * 兼容构造：不含 {@code algorithm_version} 的旧视图（该列本身可空）。
         *
         * <p>为什么保留：既有测试夹具用 30 参构造建行，那些用例不关心算法版本；删掉它会
         * 迫使改动与 M08 无关的文件，违背"只动清单内文件"的约束。
         */
        public TaskRow(UUID id, String name, TaskType taskType, UUID ownerUserId, TaskStatus status,
                       int progress, int priority, String parameterSchemaVersion, String parameterSnapshot,
                       String parameterHash, String idempotencyKey, String resourceType, UUID resourceId,
                       OffsetDateTime submittedAt, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                       String leaseOwner, OffsetDateTime leaseUntil, long leaseGeneration,
                       OffsetDateTime nextRunAt, OffsetDateTime cancelRequestedAt, long retryCount,
                       long currentAttemptNo, String errorCode, String errorMessage, UUID resultAssetId,
                       String traceId, boolean deleted, long version, OffsetDateTime createdAt) {
            this(id, name, taskType, ownerUserId, status, progress, priority, parameterSchemaVersion,
                    parameterSnapshot, parameterHash, idempotencyKey, resourceType, resourceId,
                    submittedAt, startedAt, finishedAt, leaseOwner, leaseUntil, leaseGeneration,
                    nextRunAt, cancelRequestedAt, retryCount, currentAttemptNo, errorCode, errorMessage,
                    resultAssetId, traceId, deleted, version, createdAt, null);
        }
    }

    /** 一次领取的结果：任务行 + 领取前的状态（写事件用）。 */
    public record ClaimedTask(TaskRow task, TaskStatus previousStatus) {
    }

    /**
     * 一条被收回的任务：新状态、需要关闭的 attempt 号，以及"结果未知"标记（M07）。
     *
     * @param unknownOutcome 带外部副作用的任务租约到期：已转 {@code FAILED}，不能再自动重试
     */
    public record ExpiredLease(UUID taskId, TaskStatus status, long attemptNo, boolean unknownOutcome) {
    }

    /**
     * 一次尝试的读视图。
     *
     * <p>{@code result} 用字符串而不是 {@link TaskStatus}：列上允许为 {@code NULL}（还在跑），
     * 而 {@code valueOf(null)} 会抛异常——这里把"未结束"当成正常的读结果，不是错误。
     */
    public record AttemptRow(long attemptNo, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                             String result, String errorCode) {
    }
}
