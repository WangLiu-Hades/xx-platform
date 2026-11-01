package com.xxplatform.api.service.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.AuditEventRepository;
import com.xxplatform.api.infra.FileAssetRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuditService;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.storage.FileStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 文件状态机与三端点的服务层语义。
 *
 * <p>用同包手写桩而不是 Mockito（沙箱禁止 JVM 自附加）；桩记录"插入时的状态"与
 * "扫描结论"，这是两条最容易悄悄退化的断言：插入必须是 PROCESSING（否则等于跳过扫描），
 * 结论必须落回同一行。
 */
class FileServiceTests {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** 真实的 1×1 PNG：M18 起扫描要求 ImageIO 能解码，只有魔数的假图会被隔离。 */
    private static final byte[] PNG = createPng();

    private StubAssets assets;
    private StubStorage storage;
    private StubRbac rbac;
    private StubAudit audit;
    private FileService service;

    @BeforeEach
    void setUp() {
        assets = new StubAssets();
        storage = new StubStorage();
        rbac = new StubRbac();
        FileProperties properties = new FileProperties();
        // 不覆盖上限：默认值就是生产口径（图片 10 MiB / 导入 100 MiB，P-003-02），
        // 这样下面"11 MiB 导入放行、11 MiB 图片拒绝"的用例才验证的是真实策略。
        audit = new StubAudit();
        service = new FileService(assets, storage, rbac, properties, new ObjectMapper(), audit);
    }

    @Test
    void uploadRejectsUnknownCategory() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "NOPE", "a.png", "image/png", png(), "t"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.code());
        assertEquals("category", ex.details().get(0).field());
    }

    @Test
    void uploadRejectsServerGeneratedCategory() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "REPORT", "a.png", "image/png", png(), "t"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.code());
        assertEquals("category", ex.details().get(0).field());
    }

    @Test
    void uploadRejectsEmptyContent() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "FACE_SEARCH", "a.png", "image/png", new byte[0], "t"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.code());
        assertEquals("file", ex.details().get(0).field());
    }

    @Test
    void uploadRejectsMediaOutsideCategoryWhitelist() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "FACE_SEARCH", "a.csv", "text/csv", png(), "t"));
        assertEquals(ErrorCode.FILE_MEDIA_UNSUPPORTED, ex.code());
    }

    @Test
    void uploadRejectsExtensionOutsideCategoryWhitelist() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "FACE_SEARCH", "a.gif", "image/png", png(), "t"));
        assertEquals(ErrorCode.FILE_MEDIA_UNSUPPORTED, ex.code());
    }

    @Test
    void uploadRejectsOversizedImage() {
                // 图片档是 10 MiB（P-003-02）：11 MiB 必须在落盘/解析之前就被 413 挡住。
        byte[] tooBig = new byte[11 * 1024 * 1024];
        System.arraycopy(png(), 0, tooBig, 0, 8);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "FACE_SEARCH", "a.png", "image/png", tooBig, "t"));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.code());
        assertTrue(ex.getMessage().contains(String.valueOf(10 * 1024 * 1024)),
                "错误信息要给出口径值（10 MiB），而不是笼统的'太大'");
    }

    @Test
    void uploadAllowsElevenMiBImportWhenImageLimitWouldRejectIt() {
                // 同一份 11 MiB 内容：导入档（100 MiB）必须放行，证明两个上限确实分开了。
        byte[] bigCsv = new byte[11 * 1024 * 1024];
        Arrays.fill(bigCsv, (byte) 'a');
        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "big.csv",
                "text/csv", bigCsv, "trace-big-import");
        assertEquals("ACTIVE", uploaded.status());
        assertEquals(bigCsv.length, assets.byId.get(uploaded.fileAssetId()).byteSize());
    }

    @Test
    void uploadInsertsProcessingThenActivatesWhenMagicMatches() {
        FileService.Uploaded uploaded = service.upload(owner(), "FACE_SEARCH", "face.png",
                "image/png; charset=binary", png(), "trace-1");

        assertEquals("ACTIVE", uploaded.status());
        assertEquals(1, assets.inserted.size());
        FileAssetRepository.FileAssetRow inserted = assets.inserted.get(0);
        assertEquals("PROCESSING", inserted.status(), "插入必须从 PROCESSING 出发");
        assertEquals(OWNER, inserted.ownerUserId());
        assertEquals("image/png", inserted.mimeType(), "Content-Type 参数必须被剥掉");
        assertEquals(Sha256.hex(Sha256.ofBytes(png())), inserted.sha256());
        assertEquals(64, inserted.sha256().length());
        assertEquals("ACTIVE", assets.byId.get(uploaded.fileAssetId()).status());
        assertNull(assets.byId.get(uploaded.fileAssetId()).errorCode());
                // M19：ACTIVE 时必须按 12 个自然月设置保留期，不能留 NULL 让"永不过期"与"未计算"混淆。
        assertNotNull(assets.byId.get(uploaded.fileAssetId()).expiresAt(),
                "ACTIVE 文件必须有到期时间");
        assertTrue(assets.byId.get(uploaded.fileAssetId()).expiresAt().isAfter(OffsetDateTime.now().plusMonths(11)),
                "到期时间应约为 12 个自然月后");
                // M20：上传成功必须留下审计，且带 actor 与目标 id。
        assertEquals(List.of("file.upload"), audit.actions);
        assertEquals(OWNER, audit.lastActor.userId());
        assertEquals(uploaded.fileAssetId(), audit.lastResourceId);
    }

    @Test
    void uploadQuarantinesWhenMagicDisagreesWithDeclaredType() {
        byte[] notPng = "this is not a png".getBytes();
        FileService.Uploaded uploaded = service.upload(owner(), "FACE_SEARCH", "fake.png",
                "image/png", notPng, "trace-2");

        assertEquals("QUARANTINED", uploaded.status());
        FileAssetRepository.FileAssetRow row = assets.byId.get(uploaded.fileAssetId());
        assertEquals(FileService.SCAN_REJECTED, row.errorCode());
        assertNull(row.expiresAt(), "QUARANTINED 不应有保留期：它永远不会对外可下载");
    }

    @Test
    void uploadQuarantinesCsvThatContainsNulBytes() {
        byte[] binary = new byte[]{'a', 'b', 0, 'c'};
        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.csv",
                "text/csv", binary, "trace-3");
        assertEquals("QUARANTINED", uploaded.status());
    }

    @Test
    void uploadAcceptsValidJsonContent() {
        byte[] json = "{\"dataset\":\"demo\",\"rows\":2}".getBytes(StandardCharsets.UTF_8);

        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.json",
                "application/json", json, "trace-json");

        assertEquals("ACTIVE", uploaded.status(), "合法 JSON 必须能通过结构校验");
    }

    @Test
    void uploadQuarantinesInvalidJsonContent() {
        byte[] broken = "{\"dataset\":".getBytes(StandardCharsets.UTF_8);

        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.json",
                "application/json", broken, "trace-json-bad");

        assertEquals("QUARANTINED", uploaded.status());
        assertEquals(FileService.SCAN_REJECTED, assets.byId.get(uploaded.fileAssetId()).errorCode());
    }

    @Test
    void uploadAcceptsCsvWhoseRowsMatchTheHeaderColumnCount() {
        byte[] csv = "name,age\n张三,30\n李四,41\n".getBytes(StandardCharsets.UTF_8);

        assertEquals("ACTIVE", service.upload(owner(), "DATASET_IMPORT", "data.csv",
                "text/csv", csv, "trace-csv").status());
    }

    @Test
    void uploadQuarantinesCsvWithInconsistentColumnCount() {
        byte[] csv = "name,age\n张三,30\n李四\n".getBytes(StandardCharsets.UTF_8);

        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.csv",
                "text/csv", csv, "trace-csv-bad");

        assertEquals("QUARANTINED", uploaded.status(), "列数不一致的 CSV 不能进入导入链路");
    }

    @Test
    void uploadQuarantinesUnknownXlsxThatIsNotAnOfficePackage() {
        byte[] fakeZip = concat(new byte[]{'P', 'K', 3, 4},
                "not really a workbook".getBytes(StandardCharsets.UTF_8));

        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fakeZip, "trace-xlsx-bad");

        assertEquals("QUARANTINED", uploaded.status());
    }

    @Test
    void getHidesForeignAndUnknownAssets() {
        assets.byId.put(assetId(), row(OTHER, "ACTIVE", null, null));
        assertEquals(ErrorCode.DATA_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.get(owner(), assetId())).code());
        assertEquals(ErrorCode.DATA_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.get(owner(), UUID.randomUUID())).code());
    }

    @Test
    void getRejectsExpiredAsset() {
        assets.byId.put(assetId(), row(OWNER, "ACTIVE", Instants.nowUtc().minusMinutes(1), null));
        assertEquals(ErrorCode.FILE_EXPIRED,
                assertThrows(ApiException.class, () -> service.get(owner(), assetId())).code());
    }

    @Test
    void downloadRequiresFileDownloadPermission() {
        assets.byId.put(assetId(), row(OWNER, "ACTIVE", null, null));
        assertEquals(ErrorCode.PERMISSION_DENIED,
                assertThrows(ApiException.class, () -> service.download(owner(), assetId())).code());
    }

    @Test
    void downloadOfFaceAssetAlsoRequiresFacePermission() {
        assets.byId.put(assetId(), row(OWNER, "ACTIVE", null, "FACE_SEARCH"));
        rbac.codes = List.of(FileService.PERMISSION_DOWNLOAD);
        assertEquals(ErrorCode.PERMISSION_DENIED,
                assertThrows(ApiException.class, () -> service.download(owner(), assetId())).code());
    }

    @Test
    void downloadHidesForeignAssetEvenWithPermissions() {
        assets.byId.put(assetId(), row(OTHER, "ACTIVE", null, "DATASET_IMPORT"));
        rbac.codes = List.of(FileService.PERMISSION_DOWNLOAD);
        assertEquals(ErrorCode.DATA_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.download(owner(), assetId())).code());
    }

    @Test
    void downloadRejectsNonActiveAsset() {
        assets.byId.put(assetId(), row(OWNER, "QUARANTINED", null, "DATASET_IMPORT"));
        rbac.codes = List.of(FileService.PERMISSION_DOWNLOAD);
        assertEquals(ErrorCode.DATA_NOT_FOUND,
                assertThrows(ApiException.class, () -> service.download(owner(), assetId())).code());
    }

    @Test
    void downloadRejectsExpiredAsset() {
        assets.byId.put(assetId(), row(OWNER, "ACTIVE", Instants.nowUtc().minusSeconds(1), "DATASET_IMPORT"));
        rbac.codes = List.of(FileService.PERMISSION_DOWNLOAD);
        assertEquals(ErrorCode.FILE_EXPIRED,
                assertThrows(ApiException.class, () -> service.download(owner(), assetId())).code());
    }

    @Test
    void downloadReturnsBytesForActiveOwnAsset() throws Exception {
        assets.byId.put(assetId(), row(OWNER, "ACTIVE", null, "DATASET_IMPORT"));
        rbac.codes = List.of(FileService.PERMISSION_DOWNLOAD);
        FileService.Download download = service.download(owner(), assetId());
        assertNotNull(download.stream());
        assertArrayEquals(png(), download.stream().readAllBytes());
        assertEquals("data.csv", download.fileName());
                // M20：下载成功同样进审计（数据外流追溯的起点）。
        assertEquals(List.of("file.download"), audit.actions);
        assertEquals(assetId(), audit.lastResourceId);
    }

    private static AuthenticatedSession owner() {
        UserAccountRepository.UserAccountRow user = new UserAccountRepository.UserAccountRow(
                OWNER, "U-OWNER", "owner", "文件主人", "$pbkdf2$stub", "ACTIVE",
                Instants.nowUtc(), 1L);
        return new AuthenticatedSession(OWNER, UUID.randomUUID(), Instants.nowUtc(),
                Instants.nowUtc().plusHours(8), user);
    }

    private static UUID assetId() {
        return UUID.fromString("33333333-3333-3333-3333-333333333333");
    }

    private static FileAssetRepository.FileAssetRow row(UUID owner, String status,
                                                        OffsetDateTime expiresAt, String category) {
        return new FileAssetRepository.FileAssetRow(assetId(), "2026/10/key.csv", "data.csv",
                category == null ? "DATASET_IMPORT" : category, "text/csv", png().length,
                Sha256.hex(Sha256.ofBytes(png())), owner, status, expiresAt, null, "trace", Instants.nowUtc());
    }

    private static byte[] png() {
        return PNG;
    }

    private static byte[] createPng() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0x336699);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("当前 JVM 没有 PNG 编码器，无法生成测试夹具");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成测试 PNG 失败", e);
        }
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] merged = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, merged, head.length, tail.length);
        return merged;
    }

    private static final class StubAssets extends FileAssetRepository {
        private final Map<UUID, FileAssetRow> byId = new HashMap<>();
        private final List<FileAssetRow> inserted = new ArrayList<>();

        private StubAssets() {
            super(new JdbcTemplate());
        }

        @Override
        public void insert(UUID id, String storageKey, String fileName, String category,
                           String mimeType, long byteSize, String sha256Hex, UUID ownerUserId,
                           String traceId, OffsetDateTime createdAt) {
            FileAssetRow row = new FileAssetRow(id, storageKey, fileName, category, mimeType,
                    byteSize, sha256Hex, ownerUserId, "PROCESSING", null, null, traceId, createdAt);
            inserted.add(row);
            byId.put(id, row);
        }

        @Override
        public Optional<FileAssetRow> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public int markStatus(UUID id, String status, String errorCode, OffsetDateTime expiresAt) {
            FileAssetRow row = byId.get(id);
            if (row == null || !"PROCESSING".equals(row.status())) {
                return 0;
            }
            OffsetDateTime effectiveExpiry = expiresAt == null ? row.expiresAt() : expiresAt;
            byId.put(id, new FileAssetRow(row.id(), row.storageKey(), row.fileName(), row.category(),
                    row.mimeType(), row.byteSize(), row.sha256(), row.ownerUserId(), status,
                    effectiveExpiry, errorCode, row.traceId(), row.createdAt()));
            return 1;
        }
    }

    private static final class StubStorage implements FileStorage {

        @Override
        public StoredObject store(byte[] content, String extension) {
            return new StoredObject("2026/10/" + UUID.randomUUID() + extension,
                    Sha256.hex(Sha256.ofBytes(content)), content.length);
        }

        @Override
        public InputStream open(String storageKey) {
            return new ByteArrayInputStream(png());
        }

        @Override
        public void delete(String storageKey) {
            // no-op
        }
    }

    private static final class StubRbac extends RbacRepository {
        private List<String> codes = List.of();

        private StubRbac() {
            super(new JdbcTemplate());
        }

        @Override
        public List<String> permissionCodes(UUID userId) {
            return codes;
        }
    }

    /**
     * 记录审计调用而不落库：这层测的是"业务动作有没有触发审计"，不是审计表写入本身
     * （后者由真库联调覆盖）。{@code AuditService} 的写入方法都是 public，覆写即可。
     */
    private static final class StubAudit extends AuditService {
        private final List<String> actions = new ArrayList<>();
        private AuditService.Actor lastActor;
        private UUID lastResourceId;

        private StubAudit() {
            super(new AuditEventRepository(new JdbcTemplate()), new NoopTransactionManager());
        }

        @Override
        public void success(String action, String category, String resourceType, UUID resourceId,
                            Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                            Map<String, Object> after) {
            actions.add(action);
            lastActor = actor;
            lastResourceId = resourceId;
        }

        @Override
        public void failure(String action, String category, String resourceType, UUID resourceId,
                            Actor actor, String sourceIp, UUID businessTaskId, String traceId,
                            String failureCode, Map<String, Object> after) {
            actions.add("!" + action);
            lastActor = actor;
            lastResourceId = resourceId;
        }
    }

        /** 只为构造 {@link AuditService} 存在；测试里不会真正开启事务。 */
    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }
}

