package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 写 {@code analysis.export_job}（M01 统一导出意图）。
 *
 * <p>这张表和 {@code analysis.async_task} 是<b>一对一</b>的：任务行负责"跑不跑、跑几次"，
 * 导出意图行负责"导出什么"（冻结的筛选范围、列、格式）。两者必须在同一事务里写，
 * 否则会出现"任务能领取但读不到导出意图"的半条数据。
 *
 * <p>插入用 {@code ON CONFLICT (task_id) DO NOTHING}：提交接口是幂等的，同一个
 * {@code Idempotency-Key} 重放时任务 id 不变，这里也不应该再插一行或者因为主键冲突报 500。
 * 幂等键的"同键不同参数"已经在 {@code TaskService#create} 里以 409 拦下，本仓库只负责
 * "同一任务至多一条导出意图"。
 */
@Repository
public class ExportJobRepository {

    private static final String INSERT = """
            INSERT INTO analysis.export_job (
                task_id, source_type, source_schema_version, source_snapshot,
                format, columns, export_scope, purpose, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?::text[], ?, ?, ?)
            ON CONFLICT (task_id) DO NOTHING
            """;

    private static final String SELECT_BY_TASK = """
            SELECT task_id, source_type, source_schema_version, source_snapshot::text AS source_snapshot,
                   format, array_to_string(columns, ',') AS columns, export_scope, purpose, created_at
            FROM analysis.export_job
            WHERE task_id = ?
            """;

    private final JdbcTemplate jdbc;

    public ExportJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 写一条导出意图。
     *
     * @param columns 列白名单里的枚举名，顺序即导出的列顺序；调用方已去重
     */
    public void insert(UUID taskId,
                       String sourceType,
                       String sourceSchemaVersion,
                       String sourceSnapshotJson,
                       String format,
                       List<String> columns,
                       String exportScope,
                       String purpose,
                       OffsetDateTime createdAt) {
        if (taskId == null || format == null || exportScope == null) {
            throw new IllegalArgumentException("taskId/format/exportScope 不能为空");
        }
        jdbc.update(INSERT, taskId, sourceType, sourceSchemaVersion, sourceSnapshotJson, format,
                columnArray(columns), exportScope, purpose, createdAt);
    }

    /** 读回一条导出意图；主要用于验收与集成测试。 */
    public Optional<ExportJobRow> findByTaskId(UUID taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return jdbc.query(SELECT_BY_TASK, rs -> {
            if (!rs.next()) {
                return Optional.<ExportJobRow>empty();
            }
            return Optional.of(new ExportJobRow(
                    rs.getObject("task_id", UUID.class),
                    rs.getString("source_type"),
                    rs.getString("source_schema_version"),
                    rs.getString("source_snapshot"),
                    rs.getString("format"),
                    rs.getString("columns"),
                    rs.getString("export_scope"),
                    rs.getString("purpose"),
                    rs.getObject("created_at", OffsetDateTime.class)));
        }, taskId);
    }

    /**
     * 把列名拼成 PostgreSQL 的 {@code text[]} 字面量。
     *
     * <p>为什么不用 {@code PreparedStatement#setArray}：列名来自固定枚举，只含字母，
     * 不可能出现逗号/引号/花括号，拼字面量是安全的；而 {@code setArray} 需要自己管理
     * {@code java.sql.Array} 的生命周期，在 {@code JdbcTemplate.update(sql, Object...)}
     * 下反而更容易写出资源泄漏。真正需要防注入的是用户可控字符串，这里没有。
     */
    private static String columnArray(List<String> columns) {
        return "{" + String.join(",", columns == null ? List.of() : columns) + "}";
    }

    public record ExportJobRow(
            UUID taskId,
            String sourceType,
            String sourceSchemaVersion,
            String sourceSnapshot,
            String format,
            String columns,
            String exportScope,
            String purpose,
            OffsetDateTime createdAt) {
    }
}
