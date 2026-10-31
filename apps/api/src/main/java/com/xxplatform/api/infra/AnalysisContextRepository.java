package com.xxplatform.api.infra;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 读 {@code analysis.analysis_context}（M06 跨页查询上下文）。
 *
 * <p>为什么只读不写：Context 的创建端点在 M06，归 DEV-02；本仓库这一步只需要在导出提交时
 * 把"用户选定的不可变筛选范围"读出来，随任务一起冻结。写路径等 M06 的 Context 端点落地后
 * 再补，避免两处各写一套插入逻辑。
 *
 * <p>读的时候刻意<b>不过滤过期</b>：调用方需要区分"不存在/不是我的"（404 隐藏存在性）与
 * "存在但已过期"（410 {@code CONTEXT_EXPIRED}）。在 SQL 里顺手过滤会把后者也变成 404，
 * 前端就无法提示"范围已过期，请重新筛选"。
 */
@Repository
public class AnalysisContextRepository {

    private static final String SELECT_BY_ID = """
            SELECT id,
                   owner_user_id,
                   source_module,
                   filter_type,
                   filter_schema_version,
                   filter_snapshot::text AS filter_snapshot,
                   permission_version,
                   expires_at
            FROM analysis.analysis_context
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    public AnalysisContextRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按 id 读上下文；不存在返回空。 */
    public Optional<ContextRow> findById(UUID contextId) {
        if (contextId == null) {
            throw new IllegalArgumentException("contextId 不能为空");
        }
        return jdbc.query(SELECT_BY_ID, rs -> {
            if (!rs.next()) {
                return Optional.<ContextRow>empty();
            }
            return Optional.of(new ContextRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("owner_user_id", UUID.class),
                    rs.getString("source_module"),
                    rs.getString("filter_type"),
                    rs.getLong("filter_schema_version"),
                    rs.getString("filter_snapshot"),
                    rs.getLong("permission_version"),
                    rs.getObject("expires_at", OffsetDateTime.class)));
        }, contextId);
    }

    /**
     * 一行上下文。
     *
     * @param filterSnapshot jsonb 的原文（保持原样，不在这里反序列化：调用方各自决定要不要
     *                       解析，并且解析失败应该表现为 500 而不是"范围非法"）
     */
    public record ContextRow(
            UUID id,
            UUID ownerUserId,
            String sourceModule,
            String filterType,
            long filterSchemaVersion,
            String filterSnapshot,
            long permissionVersion,
            OffsetDateTime expiresAt) {
    }
}
