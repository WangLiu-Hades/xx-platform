package com.xxplatform.api.infra;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code GET /client-config} 的四张表读取面（任务 7）。
 *
 * <p>一次请求读四个来源，都是"整表很小、按 key 排序"的配置表：
 * <ul>
 *   <li>{@code core.config_item} —— 部署级数值（版本、轮询间隔、TTL、查询预算等）</li>
 *   <li>{@code core.feature_config} —— 12 个待确认能力开关（唯一能力目录）</li>
 *   <li>{@code core.upload_policy} —— 分类上传白名单</li>
 *   <li>{@code core.dictionary_binding} —— 字段到字典的绑定</li>
 * </ul>
 *
 * <p><b>为什么不在 SQL 里 join 成一行</b>：四者的基数完全独立（1 行配置、12 行能力、
 * N 行策略、M 行绑定），join 会变成笛卡尔积再在 Java 里去重，反而更容易写错。
 *
 * <p><b>为什么排序写死在 SQL 里</b>：契约没有规定数组顺序，前端按 code 建索引。
 * 但验收脚本要能逐项比对，所以这里给出稳定顺序（能力按 {@code feature_code}、
 * 策略按 {@code category}、绑定按 {@code field_key}），避免"同样数据两次请求顺序不同"。
 */
@Repository
public class ClientConfigRepository {

    /** 部署配置行的固定 key；与 {@code V2__seed_deployment_client_config.sql} 一致。 */
    public static final String CONFIG_KEY = "client-config";

    private static final String SELECT_CONFIG_ITEM = """
            SELECT value::text AS value_json, config_version
              FROM core.config_item
             WHERE config_key = ?
               AND enabled = true
               AND public_readable = true
            """;

    private static final String SELECT_FEATURES = """
            SELECT feature_code, enabled, pending_reason
              FROM core.feature_config
             ORDER BY feature_code
            """;

    private static final String SELECT_UPLOAD_POLICIES = """
            SELECT category, max_bytes, allowed_mime_types, allowed_extensions, enabled
              FROM core.upload_policy
             ORDER BY category
            """;

    /**
     * 只取"字典本身启用"的绑定。
     *
     * <p>契约原文：字典未启用时没有绑定，不能硬编码 Mock 分类。{@code core.dictionary_binding}
     * 的外键只保证字典**存在**，不保证启用，所以启用判断必须放在这里。
     */
    private static final String SELECT_CATALOG_BINDINGS = """
            SELECT b.field_key, b.dictionary_code, b.label
              FROM core.dictionary_binding b
              JOIN core.dictionary d
                ON d.code = b.dictionary_code
               AND d.enabled = true
             ORDER BY b.field_key
            """;

    private final JdbcTemplate jdbc;

    public ClientConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 取部署配置行。
     *
     * <p>过滤 {@code enabled} 与 {@code public_readable}：前者是"这条配置当前生效吗"，
     * 后者是"允许下发到前端吗"。漏掉后一个就把只该给内部用的配置项泄给浏览器了。
     *
     * @return 空表示这行不存在或被停用——调用方必须当作**部署故障**处理，不能编默认值
     */
    public Optional<ConfigItemRow> findConfigItem() {
        List<ConfigItemRow> rows = jdbc.query(SELECT_CONFIG_ITEM,
                (rs, rowNum) -> new ConfigItemRow(rs.getString("value_json"), rs.getLong("config_version")),
                CONFIG_KEY);
        return rows.stream().findFirst();
    }

    public List<FeatureRow> features() {
        return jdbc.query(SELECT_FEATURES, (rs, rowNum) -> new FeatureRow(
                rs.getString("feature_code"),
                rs.getBoolean("enabled"),
                rs.getString("pending_reason")));
    }

    public List<UploadPolicyRow> uploadPolicies() {
        return jdbc.query(SELECT_UPLOAD_POLICIES, (rs, rowNum) -> new UploadPolicyRow(
                rs.getString("category"),
                rs.getLong("max_bytes"),
                textArray(rs.getArray("allowed_mime_types")),
                textArray(rs.getArray("allowed_extensions")),
                rs.getBoolean("enabled")));
    }

    public List<CatalogBindingRow> catalogBindings() {
        return jdbc.query(SELECT_CATALOG_BINDINGS, (rs, rowNum) -> new CatalogBindingRow(
                rs.getString("field_key"),
                rs.getString("dictionary_code"),
                rs.getString("label")));
    }

    /**
     * 读 {@code text[]} 列。
     *
     * <p>{@code getArray()} 返回的 {@link Array} 必须先 {@code free()}：它背后是一个服务端游标，
     * 不释放时连接归还池子后游标仍在，长时间运行会累积。这里用 try/finally 保证释放。
     */
    private static List<String> textArray(Array sqlArray) {
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object raw = sqlArray.getArray();
            if (raw instanceof String[] values) {
                return List.of(values);
            }
            if (raw instanceof Object[] values) {
                String[] text = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    text[i] = String.valueOf(values[i]);
                }
                return List.of(text);
            }
            return List.of();
        } catch (SQLException ex) {
            throw new IllegalStateException("读取 text[] 列失败", ex);
        } finally {
            try {
                sqlArray.free();
            } catch (SQLException ignored) {
                // 释放失败不影响数据正确性，忽略即可（连接关闭时驱动仍会清理）
            }
        }
    }

    /** {@code core.config_item} 的原始行；{@code valueJson} 交给服务层解析。 */
    public record ConfigItemRow(String valueJson, long configVersion) {
    }

    public record FeatureRow(String featureCode, boolean enabled, String pendingReason) {
    }

    public record UploadPolicyRow(
            String category,
            long maxBytes,
            List<String> mimeTypes,
            List<String> extensions,
            boolean enabled) {
    }

    public record CatalogBindingRow(String fieldKey, String dictionaryCode, String label) {
    }
}
