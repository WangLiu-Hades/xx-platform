package com.xxplatform.api.service.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.FileAssetRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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

    private StubAssets assets;
    private StubStorage storage;
    private StubRbac rbac;
    private FileService service;

    @BeforeEach
    void setUp() {
        assets = new StubAssets();
        storage = new StubStorage();
        rbac = new StubRbac();
        FileProperties properties = new FileProperties();
        properties.setMaxBytes(64);
        service = new FileService(assets, storage, rbac, properties);
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
    void uploadRejectsOversizedContent() {
        byte[] tooBig = new byte[65];
        png().clone();
        System.arraycopy(png(), 0, tooBig, 0, 8);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.upload(owner(), "FACE_SEARCH", "a.png", "image/png", tooBig, "t"));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.code());
        assertTrue(ex.getMessage().contains("64"));
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
    }

    @Test
    void uploadQuarantinesWhenMagicDisagreesWithDeclaredType() {
        byte[] notPng = "this is not a png".getBytes();
        FileService.Uploaded uploaded = service.upload(owner(), "FACE_SEARCH", "fake.png",
                "image/png", notPng, "trace-2");

        assertEquals("QUARANTINED", uploaded.status());
        FileAssetRepository.FileAssetRow row = assets.byId.get(uploaded.fileAssetId());
        assertEquals(FileService.SCAN_REJECTED, row.errorCode());
    }

    @Test
    void uploadQuarantinesCsvThatContainsNulBytes() {
        byte[] binary = new byte[]{'a', 'b', 0, 'c'};
        FileService.Uploaded uploaded = service.upload(owner(), "DATASET_IMPORT", "data.csv",
                "text/csv", binary, "trace-3");
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
    }

    private static AuthenticatedSession owner() {
        return new AuthenticatedSession(OWNER, UUID.randomUUID(), Instants.nowUtc(),
                Instants.nowUtc().plusHours(8), null);
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
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
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
        public int markStatus(UUID id, String status, String errorCode) {
            FileAssetRow row = byId.get(id);
            if (row == null || !"PROCESSING".equals(row.status())) {
                return 0;
            }
            byId.put(id, new FileAssetRow(row.id(), row.storageKey(), row.fileName(), row.category(),
                    row.mimeType(), row.byteSize(), row.sha256(), row.ownerUserId(), status,
                    row.expiresAt(), errorCode, row.traceId(), row.createdAt()));
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
}
