package com.xxplatform.api.infra;

import com.xxplatform.api.service.task.TaskCommand;
import com.xxplatform.api.service.task.TaskSortBy;
import com.xxplatform.api.service.task.TaskStatus;
import com.xxplatform.api.service.task.TaskType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            parameter_hash, idempotency_key, resource_type, resource_id,
            submitted_at, started_at, finished_at, lease_owner, lease_until, lease_generation,
            next_run_at, cancel_requested_at, retry_count, current_attempt_no,
            error_code, error_message, result_asset_id, trace_id, deleted, version, created_at
            """;

    private static final String INSERT = """
            INSERT INTO analysis.async_task
                (id, name, task_type, owner_user_id, status, priority, parameter_schema_version,
                 parameter_snapshot, parameter_hash, idempotency_key, resource_type, resource_id,
                 next_run_at, trace_id, submitted_at, created_at)
            VALUES (?, ?, ?, ?, 'PENDING', COALESCE(?, 5), ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** 软删除的任务对外一律视为不存在：读取、取消、重试都不应该再触达它。 */
    private static final String SELECT_BY_ID = "SELECT " + COLUMNS
            + " FROM analysis.async_task WHERE id = ? AND deleted = false";

    private static final String SELECT_BY_IDEMPOTENCY_KEY = "SELECT " + COLUMNS
            + " FROM analysis.async_task WHERE owner_user_id = ? AND task_type = ? AND idempotency_key = ?";

    private static final String SELECT_BY_OWNER = "SELECT " + COLUMNS + " FROM analysis.async_task";

    private static final String COUNT_BY_OWNER = "SELECT count(*) FROM analysis.async_task";

    /**
     * 把最早的一条 {@code PENDING} 提升为 {@code QUEUED}。
     *
     * <p>为什么需要这一步：迁移里的触发器只允许 {@code PENDING -> QUEUED -> RUNNING}，
     * 直接从 {@code PENDING} 置 {@code RUNNING} 会被守卫拒绝（"invalid task transition"）。
     * 这不是刁难——{@code PENDING} 的语义是"已受理但还没排期"，跳过排期等于跳过
     * {@code next_run_at} 的延迟语义（重试退避要靠它）。
     */
    private static final String PROMOTE_PENDING = """
            WITH picked AS (
                SELECT id
                  FROM analysis.async_task
                 WHERE status = 'PENDING' AND deleted = false AND next_run_at <= ?
                 ORDER BY priority, next_run_at, created_at, id
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE analysis.async_task t
               SET status = 'QUEUED', version = t.version + 1
              FROM picked
             WHERE t.id = picked.id
            RETURNING t.id
            """;

    /**
     * 领取下一条可执行任务：优先级高（数值小）、到期早、创建早者优先。
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} 是这里的核心：并发的 worker 不会排队等同一行，
     * 抢不到的直接换下一条，吞吐随 worker 数线性增长，而不是串行化。
     *
     * <p>{@code current_attempt_no + 1} 与 {@code lease_generation + 1} 都在同一条 UPDATE 里完成，
     * 返回的 {@code previous_status} 供调用方写 {@code task_event} 的"从哪来"。
     */
    private static final String CLAIM_NEXT = """
            WITH picked AS (
                SELECT id, status AS previous_status
                  FROM analysis.async_task
                 WHERE status = 'QUEUED' AND deleted = false AND next_run_at <= ?
                 ORDER BY priority, next_run_at, created_at, id
                 LIMIT 1
                   FOR UPDATE SKIP LOCKED
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
             WHERE t.id = picked.id
            RETURNING t.*, picked.previous_status
            """;

    /**
     * 续租：只有"还是我的、代际没变、没收到取消"才成功。
     *
     * <p>{@code cancel_requested_at IS NULL} 是刻意的：一旦有人请求取消，worker 的续租就失败，
     * 它会据此停下来——比让 reaper 到点后才发现"这条早该停了"更快。
     */
    private static final String RENEW_LEASE = """
            UPDATE analysis.async_task
               SET lease_until = ?, version = version + 1
             WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?
               AND cancel_requested_at IS NULL
            """;

    /** 完成：栅栏令牌不匹配（被 reaper 收回后重领）则命中 0 行。 */
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
            """;

    private static final String REQUEST_CANCEL = """
            UPDATE analysis.async_task
               SET cancel_requested_at = COALESCE(cancel_requested_at, ?), version = version + 1
             WHERE id = ? AND status = 'RUNNING' AND deleted = false
            """;

    private static final String CANCEL_QUEUED = """
            UPDATE analysis.async_task
               SET status = 'CANCELLED', finished_at = ?, lease_owner = NULL, lease_until = NULL,
                   version = version + 1
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
     * <p>三种去向都在 SQL 里定了：已请求取消的转 {@code CANCELLED}；其余转 {@code QUEUED} 并推迟
     * {@code next_run_at}。是否"尝试次数用尽改判 {@code FAILED}"留给调用方——那需要读
     * {@code max-attempts} 配置，SQL 里塞业务阈值会让迁移与配置两处各有一份。
     */
    private static final String EXPIRE_LEASES = """
            WITH expired AS (
                SELECT id, current_attempt_no, (cancel_requested_at IS NOT NULL) AS cancel_requested
                  FROM analysis.async_task
                 WHERE status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= ?)
                 ORDER BY lease_until NULLS FIRST, id
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
            ), updated AS (
                UPDATE analysis.async_task t
                   SET status = CASE WHEN e.cancel_requested THEN 'CANCELLED' ELSE 'QUEUED' END,
                       lease_owner = NULL,
                       lease_until = NULL,
                       finished_at = CASE WHEN e.cancel_requested THEN ?::timestamptz ELSE NULL END,
                       next_run_at = ?,
                       error_code = 'TASK_LEASE_EXPIRED',
                       error_message = '租约到期未完成，任务被收回',
                       version = t.version + 1
                  FROM expired e
                 WHERE t.id = e.id
                RETURNING t.id, t.status, e.current_attempt_no
            )
            SELECT id, status, current_attempt_no FROM updated
            """;

    private static final String FAIL_EXHAUSTED = """
            UPDATE analysis.async_task
               SET status = 'FAILED', finished_at = ?, error_code = ?, error_message = ?,
                   version = version + 1
             WHERE id = ? AND status = 'QUEUED'
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
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public AsyncTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入一条 {@code PENDING} 任务；幂等键重复时抛 {@code DuplicateKeyException}（由服务层转译）。 */
    public void insert(UUID id, TaskCommand command, OffsetDateTime now) {
        if (id == null || command == null) {
            throw new IllegalArgumentException("id 与 command 不能为空");
        }
        jdbc.update(INSERT,
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
                now,
                command.traceId(),
                now,
                now);
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
        return jdbc.query(PROMOTE_PENDING, (rs, rowNum) -> rs.getObject("id", UUID.class), now)
                .stream().findFirst();
    }

    /** 领取一条任务；没有可领时返回 {@code empty}（不是错误，worker 下一轮再来）。 */
    public Optional<ClaimedTask> claimNext(String workerId, OffsetDateTime now, Duration lease) {
        if (workerId == null || now == null || lease == null) {
            throw new IllegalArgumentException("workerId/now/lease 不能为空");
        }
        OffsetDateTime leaseUntil = now.plus(lease);
        List<ClaimedTask> rows = jdbc.query(CLAIM_NEXT, (rs, rowNum) -> new ClaimedTask(
                mapRow(rs),
                TaskStatus.valueOf(rs.getString("previous_status"))),
                now, now, workerId, leaseUntil);
        return rows.stream().findFirst();
    }

    /** 续租；返回 0 表示"已不是我的 / 代际过期 / 已请求取消"。 */
    public int renewLease(UUID taskId, String workerId, long generation, OffsetDateTime leaseUntil) {
        if (taskId == null || workerId == null || leaseUntil == null) {
            throw new IllegalArgumentException("taskId/workerId/leaseUntil 不能为空");
        }
        return jdbc.update(RENEW_LEASE, leaseUntil, taskId, workerId, generation);
    }

    /** 完成（成功/失败/取消均由 worker 回报）；返回 0 表示栅栏令牌已过期，结果不能采用。 */
    public int finish(UUID taskId, String workerId, long generation, TaskStatus outcome,
                      String errorCode, String errorMessage, UUID resultAssetId, OffsetDateTime finishedAt) {
        if (taskId == null || workerId == null || outcome == null || finishedAt == null) {
            throw new IllegalArgumentException("taskId/workerId/outcome/finishedAt 不能为空");
        }
        return jdbc.update(FINISH,
                outcome.name(), finishedAt, errorCode, errorMessage, resultAssetId,
                outcome.name(), taskId, workerId, generation);
    }

    /** 运行中请求取消：只记时间戳，状态留给 worker 或 reaper 收尾。 */
    public int requestCancel(UUID taskId, OffsetDateTime at) {
        return jdbc.update(REQUEST_CANCEL, at, taskId);
    }

    /** 排队中直接取消。 */
    public int cancelQueued(UUID taskId, OffsetDateTime at) {
        return jdbc.update(CANCEL_QUEUED, at, taskId);
    }

    /** 把失败任务重新排队（重试同 taskId、attemptNo 由下一次领取递增）。 */
    public int requeueForRetry(UUID taskId, OffsetDateTime at) {
        return jdbc.update(REQUEUE_FOR_RETRY, at, taskId);
    }

    /** 收回过期租约，返回被回收的任务（含上一轮 attempt 号，供关闭 attempt 行）。 */
    public List<ExpiredLease> expireLeases(OffsetDateTime now, OffsetDateTime nextRunAt, int batchSize) {
        if (now == null || nextRunAt == null || batchSize <= 0) {
            throw new IllegalArgumentException("now/nextRunAt 不能为空，batchSize 必须为正");
        }
        return jdbc.query(EXPIRE_LEASES, (rs, rowNum) -> new ExpiredLease(
                rs.getObject("id", UUID.class),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getLong("current_attempt_no")), now, batchSize, now, nextRunAt);
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

    private static TaskRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return ROW_MAPPER.mapRow(rs, 0);
    }

    /** 任务行的读写视图；时间均为 UTC。 */
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
            OffsetDateTime createdAt) {
    }

    /** 一次领取的结果：任务行 + 领取前的状态（写事件用）。 */
    public record ClaimedTask(TaskRow task, TaskStatus previousStatus) {
    }

    /** 一条被收回的任务：新状态、以及需要关闭的 attempt 号。 */
    public record ExpiredLease(UUID taskId, TaskStatus status, long attemptNo) {
    }
}
