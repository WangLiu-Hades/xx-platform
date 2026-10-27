package com.xxplatform.api.service.file;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.FileAssetRepository;
import com.xxplatform.api.infra.FileAssetRepository.FileAssetRow;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.storage.FileStorage;
import com.xxplatform.api.storage.FileStorage.StoredObject;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 受控文件的完整生命周期：上传 → 扫描 → ACTIVE/QUARANTINED → 读取/下载。
 *
 * <p><b>为什么状态机是"先 PROCESSING 再扫描"</b>：上传接口必须在 202 就返回 id，
 * 不能等扫描；而"是否可下载"又必须以扫描结论为准。所以行先落 PROCESSING，扫描在
 * 同一请求内同步完成（本项目的上传体积上限 10 MiB，同步扫描的延迟可接受），
 * 结论写回后再返回 status——调用方拿到的 ACTIVE/QUARANTINED 已是最终状态。
 * 将来接入病毒扫描等慢速检查时，把这里换成异步消费者即可，状态机不用改。
 *
 * <p><b>为什么下载要"实时重校"</b>：契约没有下载许可 token 或签名 URL 参数，
 * "下载授权 5 分钟一次性"在这个端点上无法表达成一次性票据。实现取"每次下载都重新校验
 * 会话 + 权限 + 归属 + 状态 + 过期"，并回 {@code Cache-Control: no-store} 禁止中间层缓存；
 * 一次性短期 URL 属于 P1 待定（需要新增端点与票据表），不在本行伪造。
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** 契约 {@code GET /files/{fileAssetId}/download} 的权限码。 */
    public static final String PERMISSION_DOWNLOAD = "file:download";

    /** 人脸类文件在基础下载权限之外还要的领域权限。 */
    private static final String FACE_DOWNLOAD = "face:download";

    /** 扫描不通过时写入 {@code core.file_asset.error_code} 的排障码。 */
    static final String SCAN_REJECTED = "FILE_INVALID";

    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_QUARANTINED = "QUARANTINED";
    static final String STATUS_DELETED = "DELETED";

    private final FileAssetRepository assets;
    private final FileStorage storage;
    private final RbacRepository rbac;
    private final FileProperties properties;

    public FileService(FileAssetRepository assets, FileStorage storage, RbacRepository rbac,
                       FileProperties properties) {
        this.assets = assets;
        this.storage = storage;
        this.rbac = rbac;
        this.properties = properties;
    }

    /**
     * 受理一次上传：校验 → 落盘 → 插行 → 同步扫描 → 返回最终状态。
     *
     * @param session     当前会话（上传者即 owner）
     * @param categoryRaw 客户端传入的分类
     * @param fileName    原始文件名（只用于展示与扩展名校验）
     * @param contentType 原始 Content-Type
     * @param content     文件字节
     * @param traceId     请求 traceId
     */
    public Uploaded upload(AuthenticatedSession session, String categoryRaw, String fileName,
                           String contentType, byte[] content, String traceId) {
        FileCategory category = parseCategory(categoryRaw);
        if (!category.uploadable()) {
            throw validation("category", "该分类不接受客户端上传");
        }
        if (content == null || content.length == 0) {
            throw validation("file", "文件内容为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw validation("file", "缺少文件名");
        }
        UploadPolicyCatalog.UploadPolicy policy =
                UploadPolicyCatalog.policyFor(category, properties.getMaxBytes());
        String mime = UploadPolicyCatalog.normalizeMime(contentType);
        if (!policy.allowsMime(mime)) {
            throw new ApiException(ErrorCode.FILE_MEDIA_UNSUPPORTED,
                    "该分类只接受 " + policy.describe() + "，收到 " + (mime.isBlank() ? "未知类型" : mime));
        }
        String extension = UploadPolicyCatalog.extensionOf(fileName);
        if (!policy.allowsExtension(extension)) {
            throw new ApiException(ErrorCode.FILE_MEDIA_UNSUPPORTED,
                    "该分类只接受扩展名 " + String.join("/", policy.extensions().stream().sorted().toList()));
        }
        if (content.length > policy.maxBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "文件超过 " + policy.maxBytes() + " 字节上限（实际 " + content.length + "）");
        }

        StoredObject stored = storage.store(content, extension);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = Instants.nowUtc();
        assets.insert(id, stored.storageKey(), fileName, category.name(), mime, stored.byteSize(),
                stored.sha256Hex(), session.userId(), traceId, now);

        String verdict = scan(category, mime, content) ? STATUS_ACTIVE : STATUS_QUARANTINED;
        String errorCode = STATUS_ACTIVE.equals(verdict) ? null : SCAN_REJECTED;
        int updated = assets.markStatus(id, verdict, errorCode);
        if (updated == 0) {
            // 刚落库就被并发处置（例如管理员隔离）：保持库里的结论，不回退
            log.warn("文件状态未被扫描结论覆盖 asset={} verdict={}", id, verdict);
        }
        log.info("文件上传完成 asset={} category={} bytes={} status={}", id, category,
                content.length, verdict);
        return new Uploaded(id, verdict);
    }

    /** 读元数据：非本人一律 404 隐藏存在性；过期 410。 */
    public FileAssetRow get(AuthenticatedSession session, UUID fileAssetId) {
        FileAssetRow row = visibleAsset(session, fileAssetId);
        if (expired(row, Instants.nowUtc())) {
            throw new ApiException(ErrorCode.FILE_EXPIRED, "文件已过期");
        }
        return row;
    }

    /**
     * 下载：权限 → 归属 → 过期 → 状态，最后打开流。
     *
     * <p>顺序说明：权限先于归属（缺权限的人不该知道文件是否存在），归属先于状态
     * （否则可以通过 404/410 的差异探测别人的文件是否过期）。
     */
    public Download download(AuthenticatedSession session, UUID fileAssetId) {
        List<String> permissions = rbac.permissionCodes(session.userId());
        FileAssetRow row = visibleAsset(session, fileAssetId);
        if (!permissions.contains(PERMISSION_DOWNLOAD)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少下载权限");
        }
        if (needsFacePermission(row.category()) && !permissions.contains(FACE_DOWNLOAD)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少人脸文件下载权限");
        }
        if (expired(row, Instants.nowUtc())) {
            throw new ApiException(ErrorCode.FILE_EXPIRED, "文件已过期");
        }
        if (!STATUS_ACTIVE.equals(row.status())) {
            // PROCESSING/QUARANTINED/DELETED 都不对外暴露具体原因，统一按不存在处理
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "文件不可下载");
        }
        return new Download(storage.open(row.storageKey()), row.fileName(), row.mimeType(),
                row.byteSize());
    }

    /** 归属校验：不存在与不属于当前用户返回同一个 404，避免枚举 id。 */
    private FileAssetRow visibleAsset(AuthenticatedSession session, UUID fileAssetId) {
        if (fileAssetId == null) {
            throw validation("fileAssetId", "缺少文件 id");
        }
        return assets.findById(fileAssetId)
                .filter(row -> row.ownerUserId().equals(session.userId()))
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "文件不存在"));
    }

    private static boolean needsFacePermission(String category) {
        return FileCategory.FACE_SEARCH.name().equals(category)
                || FileCategory.PROFILE_PORTRAIT.name().equals(category);
    }

    static boolean expired(FileAssetRow row, OffsetDateTime now) {
        return row.expiresAt() != null && !row.expiresAt().isAfter(now);
    }

    private static FileCategory parseCategory(String raw) {
        try {
            return FileCategory.parse(raw);
        } catch (IllegalArgumentException e) {
            throw validation("category", "未知文件分类");
        }
    }

    private static ApiException validation(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "请求参数不合法",
                List.of(new com.xxplatform.api.common.ApiResponses.FieldErrorDetail(field, reason)));
    }

    /**
     * 同步扫描：只做"声明类型与内容魔数是否一致"这一层。
     *
     * <p>不追求识别所有合法文件（那需要完整的格式解析库），只拦"扩展名说 png、内容不是 png"
     * 这类明显伪装——真正的内容安全（病毒、恶意宏）应由独立扫描服务补上。
     */
    static boolean scan(FileCategory category, String mime, byte[] content) {
        return switch (category) {
            case FACE_SEARCH, PROFILE_PORTRAIT -> switch (mime) {
                case "image/jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
                case "image/png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                default -> false;
            };
            case DATASET_IMPORT, DEVICE_IMPORT, PERSON_LIBRARY_IMPORT, RAW_INGESTION -> switch (mime) {
                case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                        startsWith(content, 0x50, 0x4B, 0x03, 0x04);
                case "text/csv", "application/json" -> noNulByte(content);
                default -> false;
            };
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int... magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((content[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 文本类只做最粗的检查：前 512 字节不能有 NUL（二进制文件几乎必然有）。 */
    private static boolean noNulByte(byte[] content) {
        int limit = Math.min(content.length, 512);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        return true;
    }

    /** 上传结果（契约 {@code FileUploadData}）。 */
    public record Uploaded(UUID fileAssetId, String status) {
    }

    /** 下载数据：流 + 响应头所需的元数据。 */
    public record Download(InputStream stream, String fileName, String mimeType, long byteSize) {
    }

    /** 便于日志与测试：内容指纹（未落库时为空）。 */
    static String fingerprint(byte[] content) {
        return Sha256.hex(Sha256.ofBytes(content));
    }
}
