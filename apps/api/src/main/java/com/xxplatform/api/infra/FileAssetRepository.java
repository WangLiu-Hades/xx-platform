package com.xxplatform.api.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code core.file_asset} 的读写。
 *
 * <p>状态机是"上传即 PROCESSING，扫描后转 ACTIVE/QUARANTINED"：
 * <ul>
 *   <li>插入时固定写 PROCESSING（不接受调用方传入状态，避免绕过扫描）；</li>
 *   <li>{@link #markStatus} 带 {@code AND status = 'PROCESSING'} 条件——扫描结果只能从一个
 *       确定的前态出发，重复调用不会把已被人工处置成 DELETED 的行改回 ACTIVE。</li>
 * </ul>
 */
@Repository
public class FileAssetRepository {

    private static final String COLUMNS = """
            id, storage_key, file_name, category, mime_type, byte_size, sha256,
            owner_user_id, status, expires_at, error_code, trace_id, created_at
            """;

    private static final String INSERT = """
            INSERT INTO core.file_asset
                (id, storage_key, file_name, category, mime_type, byte_size, sha256,
                 owner_user_id, status, trace_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
            """;

    private static final String FIND_BY_ID =
            "SELECT " + COLUMNS + " FROM core.file_asset WHERE id = ?";

    private static final String MARK_STATUS = """
            UPDATE core.file_asset
               SET status = ?, error_code = ?
             WHERE id = ? AND status = 'PROCESSING'
            """;

    private static final RowMapper ROW_MAPPER = new RowMapper();

    private final JdbcTemplate jdbc;

    public FileAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条 PROCESSING 状态的元数据。
     *
     * @param id        文件资产 id（服务层生成，便于落盘失败时不留悬挂行）
     * @param storageKey 存储键
     * @param fileName  原始文件名
     * @param category  分类名
     * @param mimeType  归一化后的媒体类型
     * @param byteSize  字节数
     * @param sha256Hex 内容指纹
     * @param ownerUserId 上传者
     * @param traceId   请求 traceId
     * @param createdAt 创建时间（UTC）
     */
    public void insert(UUID id, String storageKey, String fileName, String category, String mimeType,
                       long byteSize, String sha256Hex, UUID ownerUserId, String traceId,
                       OffsetDateTime createdAt) {
        requireNonNull(id, "id");
        requireNonNull(storageKey, "storageKey");
        requireNonNull(fileName, "fileName");
        requireNonNull(category, "category");
        requireNonNull(mimeType, "mimeType");
        requireNonNull(sha256Hex, "sha256Hex");
        requireNonNull(ownerUserId, "ownerUserId");
        requireNonNull(traceId, "traceId");
        requireNonNull(createdAt, "createdAt");
        jdbc.update(INSERT, id, storageKey, fileName, category, mimeType, byteSize, sha256Hex,
                ownerUserId, traceId, createdAt);
    }

    /** 按 id 查元数据；不存在返回空。 */
    public Optional<FileAssetRow> findById(UUID id) {
        requireNonNull(id, "id");
        List<FileAssetRow> rows = jdbc.query(FIND_BY_ID, ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    /**
     * 扫描结论落库，只允许从 PROCESSING 出发。
     *
     * @return 影响行数；0 表示行不存在或已不在 PROCESSING（调用方按并发处置处理）
     */
    public int markStatus(UUID id, String status, String errorCode) {
        requireNonNull(id, "id");
        requireNonNull(status, "status");
        return jdbc.update(MARK_STATUS, status, errorCode, id);
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * {@code core.file_asset} 的一行。
     *
     * <p>{@code status} 用字符串而不是枚举：库里的 CHECK 是开放集合的粗校验，未来加状态
     * （例如 DELETING）时不该让整个服务起不来；只有服务层认识的四个状态才参与分支。
     */
    public record FileAssetRow(
            UUID id,
            String storageKey,
            String fileName,
            String category,
            String mimeType,
            long byteSize,
            String sha256,
            UUID ownerUserId,
            String status,
            OffsetDateTime expiresAt,
            String errorCode,
            String traceId,
            OffsetDateTime createdAt) {
    }

    private static final class RowMapper implements org.springframework.jdbc.core.RowMapper<FileAssetRow> {

        @Override
        public FileAssetRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FileAssetRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("storage_key"),
                    rs.getString("file_name"),
                    rs.getString("category"),
                    rs.getString("mime_type"),
                    rs.getLong("byte_size"),
                    rs.getString("sha256"),
                    rs.getObject("owner_user_id", UUID.class),
                    rs.getString("status"),
                    rs.getObject("expires_at", OffsetDateTime.class),
                    rs.getString("error_code"),
                    rs.getString("trace_id"),
                    rs.getObject("created_at", OffsetDateTime.class));
        }
    }
}
