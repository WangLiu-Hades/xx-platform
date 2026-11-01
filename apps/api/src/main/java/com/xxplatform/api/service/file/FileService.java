package com.xxplatform.api.service.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.FileAssetRepository;
import com.xxplatform.api.infra.FileAssetRepository.FileAssetRow;
import com.xxplatform.api.infra.IdempotencyRequestRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuditService;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.storage.FileStorage;
import com.xxplatform.api.storage.FileStorage.StoredObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 受控文件的完整生命周期：上传 → 扫描 → ACTIVE/QUARANTINED → 读取/下载。
 *
 * <p><b>为什么状态机是"先 PROCESSING 再扫描"</b>：上传接口必须在 202 就返回 id，
 * 不能等扫描；而"是否可下载"又必须以扫描结论为准。所以行先落 PROCESSING，扫描在
 * 同一请求内同步完成，结论写回后再返回 status——调用方拿到的 ACTIVE/QUARANTINED
 * 已是最终状态。将来接入病毒扫描等慢速检查时，把这里换成异步消费者即可，状态机不用改。
 *
 * <p><b>为什么下载要"实时重校"</b>：契约没有下载许可 token 或签名 URL 参数，
 * "下载授权 5 分钟一次性"在这个端点上无法表达成一次性票据。实现取"每次下载都重新校验
 * 会话 + 权限 + 归属 + 状态 + 过期"，并回 {@code Cache-Control: no-store} 禁止中间层缓存；
 * 一次性短期 URL 属于 P1 待定（需要新增端点与票据表），不在本行伪造。
 *
 * <p><b>M17 策略同源</b>：上传上限与 GET /client-config 下发的 {@code uploadPolicies}
 * 都来自 {@link UploadPolicyProvider}，执行上限是 {@code min(全局 maxUploadBytes, 分类 maxBytes)}。
 *
 * <p><b>M15 幂等</b>：带 {@code Idempotency-Key} 的上传走 {@link IdempotencyRequestRepository}
 * 账本：同账号+同操作+同键且请求摘要一致时重放首次结果，不重复插入 {@code core.file_asset}；
 * 摘要不一致回 409 {@code IDEMPOTENCY_CONFLICT}。
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

    /** 幂等作用域里的 {@code operationId}：上传端点。 */
    public static final String OPERATION_UPLOAD = "POST /files/uploads";

    /** 幂等记录的保留窗口（契约要求键可回收，这里给 24 小时，足够覆盖重试与网络重发）。 */
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final FileAssetRepository assets;
    private final FileStorage storage;
    private final RbacRepository rbac;
    private final FileProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final UploadPolicyProvider policyProvider;
    private final IdempotencyRequestRepository idempotency;

    /** 生产构造：策略与幂等都走真实依赖。 */
    @Autowired
    public FileService(FileAssetRepository assets, FileStorage storage, RbacRepository rbac,
                       FileProperties properties, ObjectMapper objectMapper, AuditService audit,
                       UploadPolicyProvider policyProvider, IdempotencyRequestRepository idempotency) {
        this.assets = assets;
        this.storage = storage;
        this.rbac = rbac;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.policyProvider = policyProvider;
        this.idempotency = idempotency;
    }

    /**
     * 单元测试用的最小构造：策略回落到内置目录，没有幂等账本。
     *
     * <p>保留它是为了不让文件生命周期的主要分支（校验/状态机/权限）必须起数据库才能测；
     * 幂等与"策略同源"由真库集成测试证明。
     */
    public FileService(FileAssetRepository assets, FileStorage storage, RbacRepository rbac,
                       FileProperties properties, ObjectMapper objectMapper, AuditService audit) {
        this(assets, storage, rbac, properties, objectMapper, audit,
                UploadPolicyProvider.builtInOnly(properties, objectMapper), null);
    }

    /** 审计里的操作者快照；账号已被删除时退化为未知，审计本身不能因此失败。 */
    private static AuditService.Actor actorOf(AuthenticatedSession session) {
        if (session == null || session.user() == null) {
            return AuditService.Actor.unknown();
        }
        return new AuditService.Actor(session.userId(), session.user().loginName(),
                session.user().displayName());
    }

    /** 不带幂等键的上传（内部/旧调用方）；生产入口在 {@link #upload} 的 7 参版本。 */
    public Uploaded upload(AuthenticatedSession session, String categoryRaw, String fileName,
                           String contentType, byte[] content, String traceId) {
        return upload(session, categoryRaw, fileName, contentType, content, traceId, null);
    }

    /**
     * 受理一次上传：校验 → 落盘 → 插行 → 同步扫描 → 返回最终状态。
     *
     * @param session        当前会话（上传者即 owner）
     * @param categoryRaw    客户端传入的分类
     * @param fileName       原始文件名（只用于展示与扩展名校验）
     * @param contentType    原始 Content-Type
     * @param content        文件字节
     * @param traceId        请求 traceId
     * @param idempotencyKey 幂等键（已通过形状校验；null 表示不走账本）
     */
    public Uploaded upload(AuthenticatedSession session, String categoryRaw, String fileName,
                           String contentType, byte[] content, String traceId,
                           String idempotencyKey) {
        if (idempotency == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return uploadInternal(session, categoryRaw, fileName, contentType, content, traceId);
        }
        String key = idempotencyKey.trim();
        String digest = requestDigest(categoryRaw, fileName, contentType, content);
        Optional<IdempotencyRequestRepository.LedgerRow> existing =
                idempotency.find(session.userId(), OPERATION_UPLOAD, key);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), digest);
        }
        UUID ledgerId = UUID.randomUUID();
        boolean claimed = idempotency.insertProcessing(ledgerId, session.userId(), OPERATION_UPLOAD,
                key, digest, Instants.nowUtc().plusHours(IDEMPOTENCY_TTL_HOURS));
        if (!claimed) {
            // 并发窗口：另一请求刚抢到同一个键。读回账本，按同一语义处理。
            return idempotency.find(session.userId(), OPERATION_UPLOAD, key)
                    .map(row -> replayOrConflict(row, digest))
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_CONFLICT,
                            "幂等记录状态异常，请稍后重试"));
        }
        try {
            Uploaded result = uploadInternal(session, categoryRaw, fileName, contentType, content,
                    traceId);
            idempotency.markSucceeded(ledgerId, 202, "FileUploadData", snapshot(result));
            return result;
        } catch (RuntimeException ex) {
            // 失败不占用键：用户修正内容后可以用同一个键重试（拒绝事件已单独写审计）。
            idempotency.deleteProcessing(ledgerId);
            throw ex;
        }
    }

    /**
     * 已存在账本行时的三种结局：同摘要成功→重放；不同摘要→409；仍在处理→409。
     *
     * <p>PROCESSING 行只有在进程崩溃时才会长期残留，因此按"正在处理"拒绝并让调用方稍后重试，
     * 比直接复用半成品更安全（此时还不知道首个请求最终是否落库）。
     */
    private Uploaded replayOrConflict(IdempotencyRequestRepository.LedgerRow row, String digest) {
        if (!row.requestDigest().equals(digest)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 已用于不同的上传内容");
        }
        if ("SUCCEEDED".equals(row.status())) {
            return readSnapshot(row);
        }
        if ("FAILED".equals(row.status())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "该 Idempotency-Key 的首次请求已失败，请换新键重试");
        }
        throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "同一 Idempotency-Key 的请求正在处理中");
    }

    private Uploaded readSnapshot(IdempotencyRequestRepository.LedgerRow row) {
        try {
            String raw = idempotency.findSnapshot(row.id())
                    .orElseThrow(() -> new IllegalStateException("幂等记录缺少响应快照"));
            JsonNode snapshot = objectMapper.readTree(raw);
            return new Uploaded(UUID.fromString(snapshot.get("fileAssetId").asText()),
                    snapshot.get("status").asText());
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, "幂等记录无法重放，请稍后重试");
        }
    }

    private String snapshot(Uploaded uploaded) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "fileAssetId", uploaded.fileAssetId().toString(),
                    "status", uploaded.status()));
        } catch (Exception ex) {
            throw new IllegalStateException("序列化上传响应快照失败", ex);
        }
    }

    /**
     * 请求摘要：规范化后的分类/文件名/声明类型/字节数/内容 SHA-256。
     *
     * <p>只用内容 SHA-256 不够：同名不同分类、同内容不同声明类型都会落到不同的策略，
     * 而契约要求"输入相同才重放"。
     */
    private static String requestDigest(String category, String fileName, String contentType,
                                        byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content;
        String canonical = (category == null ? "" : category.trim())
                + "\n" + (fileName == null ? "" : fileName)
                + "\n" + (contentType == null ? "" : contentType)
                + "\n" + bytes.length
                + "\n" + Sha256.hex(Sha256.ofBytes(bytes));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private Uploaded uploadInternal(AuthenticatedSession session, String categoryRaw,
                                    String fileName, String contentType, byte[] content,
                                    String traceId) {
        FileCategory category = parseCategory(categoryRaw);
        String categoryName = categoryRaw == null ? "UNKNOWN" : categoryRaw.trim().toUpperCase();
        if (!category.uploadable()) {
            throw rejection(session, traceId, categoryName, ErrorCode.VALIDATION_ERROR,
                    "该分类不接受客户端上传", validation("category", "该分类不接受客户端上传"),
                    contentLength(content));
        }
        if (content == null || content.length == 0) {
            throw rejection(session, traceId, categoryName, ErrorCode.VALIDATION_ERROR, "文件内容为空",
                    validation("file", "文件内容为空"), 0);
        }
        if (fileName == null || fileName.isBlank()) {
            throw rejection(session, traceId, categoryName, ErrorCode.VALIDATION_ERROR, "缺少文件名",
                    validation("file", "缺少文件名"), content.length);
        }
        UploadPolicyProvider.CategoryPolicy policy;
        try {
            policy = policyProvider.forCategory(category);
        } catch (IllegalArgumentException ex) {
            throw rejection(session, traceId, categoryName, ErrorCode.VALIDATION_ERROR,
                    "该分类不接受客户端上传", validation("category", "该分类不接受客户端上传"),
                    content.length);
        }
        if (!policy.enabled()) {
            // 部署配置 enabled=false：与契约 uploadPolicies.enabled 一致地拒绝，并留审计。
            throw rejection(session, traceId, categoryName, ErrorCode.FILE_MEDIA_UNSUPPORTED,
                    "该分类未启用上传", new ApiException(ErrorCode.FILE_MEDIA_UNSUPPORTED,
                            "该分类未启用上传：" + category), content.length);
        }
        String mime = UploadPolicyCatalog.normalizeMime(contentType);
        if (!policy.allowsMime(mime)) {
            throw rejection(session, traceId, categoryName, ErrorCode.FILE_MEDIA_UNSUPPORTED,
                    "媒体类型不在策略内", new ApiException(ErrorCode.FILE_MEDIA_UNSUPPORTED,
                            "该分类只接受 " + policy.describe() + "，收到 "
                                    + (mime.isBlank() ? "未知类型" : mime)), content.length);
        }
        String extension = UploadPolicyCatalog.extensionOf(fileName);
        if (!policy.allowsExtension(extension)) {
            throw rejection(session, traceId, categoryName, ErrorCode.FILE_MEDIA_UNSUPPORTED,
                    "扩展名不在策略内", new ApiException(ErrorCode.FILE_MEDIA_UNSUPPORTED,
                            "该分类只接受扩展名 " + String.join("/", policy.extensions())),
                    content.length);
        }
        if (content.length > policy.effectiveMaxBytes()) {
            throw rejection(session, traceId, categoryName, ErrorCode.FILE_TOO_LARGE,
                    "超过有效字节上限", new ApiException(ErrorCode.FILE_TOO_LARGE,
                            "文件超过 " + policy.effectiveMaxBytes() + " 字节上限（实际 "
                                    + content.length + "）"), content.length);
        }

        ContentValidator.Result validationResult = ContentValidator.validate(
                category, mime, content, policy.maxRows(), objectMapper);
        if (validationResult.tooManyRows()) {
            throw rejection(session, traceId, categoryName, ErrorCode.FILE_TOO_LARGE,
                    "超过行数上限", new ApiException(ErrorCode.FILE_TOO_LARGE,
                            "文件超过 " + policy.maxRows() + " 行上限（实际 "
                                    + validationResult.dataRows() + " 行）"), content.length);
        }

        StoredObject stored = storage.store(content, extension);
        UUID id = UUID.randomUUID();
        OffsetDateTime now = Instants.nowUtc();
        assets.insert(id, stored.storageKey(), fileName, category.name(), mime, stored.byteSize(),
                stored.sha256Hex(), session.userId(), traceId, now);

        String verdict = validationResult.valid() ? STATUS_ACTIVE : STATUS_QUARANTINED;
        String errorCode = STATUS_ACTIVE.equals(verdict) ? null : SCAN_REJECTED;
        // 只有 ACTIVE 的文件才对外可下载，才需要保留期；QUARANTINED 不设到期时间。
        OffsetDateTime expiresAt = STATUS_ACTIVE.equals(verdict) ? FileRetention.expiresAt(now) : null;
        int updated = assets.markStatus(id, verdict, errorCode, expiresAt);
        if (updated == 0) {
            // 刚落库就被并发处置（例如管理员隔离）：保持库里的结论，不回退
            log.warn("文件状态未被扫描结论覆盖 asset={} verdict={}", id, verdict);
        }
        log.info("文件上传完成 asset={} category={} bytes={} status={} rows={} detail={}", id, category,
                content.length, verdict, validationResult.dataRows(), validationResult.detail());
        if (STATUS_ACTIVE.equals(verdict)) {
            audit.success("file.upload", "FILE", "FILE_ASSET", id, actorOf(session), null, null,
                    traceId, Map.<String, Object>of("category", category.name(),
                            "status", verdict, "byteSize", content.length));
        } else {
            // M20：内容校验失败（QUARANTINED）也必须留正式事件；只记结论，不记解析器回显的内容片段。
            audit.failure("file.upload", "FILE", "FILE_ASSET", id, actorOf(session), null, null,
                    traceId, SCAN_REJECTED, Map.<String, Object>of("category", category.name(),
                            "status", verdict, "byteSize", content.length));
        }
        return new Uploaded(id, verdict);
    }

    /**
     * 拒绝路径的统一出口：先写失败审计，再抛业务异常。
     *
     * <p>审计与业务异常不在同一个事务里（上传本身没有 {@code @Transactional}），因此
     * 抛异常不会把拒绝事件吞掉；{@code reason} 只放机器可读的短原因，不写文件名、内容片段。
     */
    private ApiException rejection(AuthenticatedSession session, String traceId, String category,
                                   ErrorCode errorCode, String reason, ApiException exception,
                                   long byteSize) {
        audit.failure("file.upload", "FILE", "FILE_ASSET", null, actorOf(session), null, null,
                traceId, errorCode.name(),
                Map.<String, Object>of("category", category, "reason", reason, "byteSize", byteSize));
        return exception;
    }

    private static long contentLength(byte[] content) {
        return content == null ? 0 : content.length;
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
     *
     * <p>M20：成功与每一种拒绝（缺权限/越权/过期/非 ACTIVE）都写 {@code file.download} 事件；
     * 失败事件的 {@code failureCode} 用契约错误码名，摘要只放分类与字节数。
     */
    public Download download(AuthenticatedSession session, UUID fileAssetId) {
        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        List<String> permissions = rbac.permissionCodes(session.userId());
        if (!permissions.contains(PERMISSION_DOWNLOAD)) {
            audit.failure("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null,
                    null, traceId, ErrorCode.PERMISSION_DENIED.name(),
                    Map.of("reason", "缺少下载权限"));
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少下载权限");
        }
        FileAssetRow row;
        try {
            row = visibleAsset(session, fileAssetId);
        } catch (ApiException ex) {
            audit.failure("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null,
                    null, traceId, ex.code().name(), Map.of("reason", "文件不存在或不属于当前账号"));
            throw ex;
        }
        if (needsFacePermission(row.category()) && !permissions.contains(FACE_DOWNLOAD)) {
            audit.failure("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null,
                    null, traceId, ErrorCode.PERMISSION_DENIED.name(),
                    Map.of("reason", "缺少人脸文件下载权限", "category", row.category()));
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "缺少人脸文件下载权限");
        }
        if (expired(row, Instants.nowUtc())) {
            audit.failure("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null,
                    null, traceId, ErrorCode.FILE_EXPIRED.name(),
                    Map.<String, Object>of("reason", "文件已过期", "category", row.category()));
            throw new ApiException(ErrorCode.FILE_EXPIRED, "文件已过期");
        }
        if (!STATUS_ACTIVE.equals(row.status())) {
            // PROCESSING/QUARANTINED/DELETED 都不对外暴露具体原因，统一按不存在处理
            audit.failure("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null,
                    null, traceId, ErrorCode.DATA_NOT_FOUND.name(),
                    Map.<String, Object>of("reason", "文件不可下载", "category", row.category(),
                            "status", row.status()));
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "文件不可下载");
        }
        audit.success("file.download", "FILE", "FILE_ASSET", fileAssetId, actorOf(session), null, null,
                traceId,
                Map.<String, Object>of("category", row.category(), "byteSize", row.byteSize()));
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
