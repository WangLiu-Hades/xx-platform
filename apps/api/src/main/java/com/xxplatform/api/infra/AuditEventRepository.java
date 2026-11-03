package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code audit.audit_event} 的写入面（M20）。
 *
 * <p>只做 INSERT，不提供 UPDATE/DELETE：审计是"只追加"的事实记录。表上没有触发器拦着，
 * 这是靠"本类只暴露一个方法"来保证的——不要为了"修一条写错的审计"而加更新方法，
 * 应该再写一条更正事件。
 *
 * <p><b>为什么 {@code source_ip} 用 {@code ?::inet}</b>：列是 PostgreSQL 的 {@code inet}，
 * 而 JDBC 参数是字符串。显式转换避免依赖驱动的隐式类型推断（在某些参数绑定顺序下会报
 * {@code operator does not exist: inet = character varying}）。非法 IP 会让插入失败，
 * 由 {@link com.xxplatform.api.service.AuditService} 捕获并记日志，不影响业务。
 *
 * <p><b>外键</b>：{@code actor_user_id} 指向 {@code auth.app_user(id)}、{@code business_task_id}
 * 指向 {@code analysis.async_task(id)}。所以登录失败且账号不存在时必须传 null，
 * 不能编一个假 id——外键会直接拒绝这条审计。
 */
@Repository
public class AuditEventRepository {

    private static final String INSERT = """
            INSERT INTO audit.audit_event
              (id, actor_user_id, actor_login_name_snapshot, actor_display_name_snapshot,
               action, action_category, resource_type, resource_id, occurred_at, result,
               failure_code, before_summary, after_summary, source_ip, business_task_id,
               request_id, trace_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public AuditEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 该事件是否已经落库（按主键判断）。
     *
     * <p>供 {@code AuditService} 的回滚补偿使用：失败事务回滚会把同一事务里写下的审计一起撤销，
     * 补偿写之前先查一次，提交成功的事件就不会被重复插入。
     */
    public boolean existsById(UUID id) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM audit.audit_event WHERE id = ?)",
                Boolean.class,
                id);
        return Boolean.TRUE.equals(exists);
    }

    /** 插入一条审计事件。 */
    public void insert(AuditEvent event) {
        if (event == null || event.id() == null) {
            throw new IllegalArgumentException("审计事件与 id 不能为空");
        }
        jdbc.update(INSERT,
                event.id(),
                event.actorUserId(),
                blankToEmpty(event.actorLoginNameSnapshot()),
                blankToEmpty(event.actorDisplayNameSnapshot()),
                event.action(),
                event.actionCategory(),
                event.resourceType(),
                event.resourceId(),
                event.occurredAt(),
                event.result(),
                event.failureCode(),
                event.beforeSummary(),
                event.afterSummary(),
                blankToNull(event.sourceIp()),
                event.businessTaskId(),
                event.requestId(),
                blankToEmpty(event.traceId()),
                event.createdAt());
    }

    /** 快照列是 NOT NULL：调用方漏填时用空串兜底，而不是让整条审计写不进去。 */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** inet 列允许 null；空串不是合法 inet，必须转成 null。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 审计事件行。
     *
     * @param id                        应用生成的 UUID 主键（不是自增序列）
     * @param actorUserId               操作者 id；账号不存在时为 null（外键约束）
     * @param actorLoginNameSnapshot    登录名快照（账号改名/删除后审计仍可读）
     * @param actorDisplayNameSnapshot  显示名快照
     * @param action                    动作，例如 {@code auth.login}
     * @param actionCategory            动作分类，例如 {@code AUTH}
     * @param resourceType              资源类型，例如 {@code SESSION}
     * @param resourceId                资源 id（可为 null）
     * @param occurredAt                业务发生时间（UTC）
     * @param result                    {@code SUCCESS} 或 {@code FAILURE}（表上 CHECK）
     * @param failureCode               失败时的契约错误码名，成功为 null
     * @param beforeSummary             变更前摘要（已脱敏、≤2000）
     * @param afterSummary              变更后摘要（已脱敏、≤2000）
     * @param sourceIp                  来源 IP；非字面量或未知时为 null
     * @param businessTaskId            关联任务 id（可为 null）
     * @param requestId                 请求 id（可为 null）
     * @param traceId                   链路 traceId，便于与日志对齐
     * @param createdAt                 落库时间
     */
    public record AuditEvent(
            UUID id,
            UUID actorUserId,
            String actorLoginNameSnapshot,
            String actorDisplayNameSnapshot,
            String action,
            String actionCategory,
            String resourceType,
            UUID resourceId,
            OffsetDateTime occurredAt,
            String result,
            String failureCode,
            String beforeSummary,
            String afterSummary,
            String sourceIp,
            UUID businessTaskId,
            String requestId,
            String traceId,
            OffsetDateTime createdAt) {
    }
}
