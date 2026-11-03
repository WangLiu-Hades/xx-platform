package com.xxplatform.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.common.Sha256;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

/**
 * M15 / M17 / M18 / M19 / M20 的 HTTP + 真库证据（multipart 上传、详情、下载、审计、幂等）。
 *
 * <p>用 MockMvc 走真实的 Spring MVC + 真实的 JDBC：上传返回的 {@code fileAssetId} 必须真的落在
 * {@code core.file_asset}，审计必须真的落在 {@code audit.audit_event}，不能靠桩冒充。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class FileHttpIntegrationTests {

    private static final Path STORAGE_ROOT = tempDir();

    private static final UUID OWNER = UUID.fromString("7a52c1d0-0000-4000-8000-000000000201");
    private static final UUID OTHER = UUID.fromString("7a52c1d0-0000-4000-8000-000000000202");
    private static final UUID NO_PERM = UUID.fromString("7a52c1d0-0000-4000-8000-000000000203");
    private static final UUID ROLE = UUID.fromString("7a52c1d0-0000-4000-8000-000000000204");

    private static final String SID_OWNER = "it-http-owner-session-0001";
    private static final String SID_OTHER = "it-http-other-session-0002";
    private static final String SID_NO_PERM = "it-http-noperm-session-003";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.xxplatform.api.config.FileProperties fileProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("XX_TASK_IT_DB_URL", ""));
        registry.add("spring.datasource.username", () -> env("XX_TASK_IT_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("XX_TASK_IT_DB_PASSWORD", ""));
        registry.add("xx.file.storage-root", STORAGE_ROOT::toString);
        registry.add("xx.task.reaper-enabled", () -> "false");
        registry.add("xx.file.download-permit-key", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }

    @AfterAll
    static void cleanUp() {
        deleteRecursively(STORAGE_ROOT);
    }

    @BeforeEach
    void seedAccounts() {
        jdbc.update("DELETE FROM core.file_asset WHERE owner_user_id IN (?, ?, ?)", OWNER, OTHER, NO_PERM);
        jdbc.update("DELETE FROM core.idempotency_request WHERE user_id IN (?, ?, ?)", OWNER, OTHER, NO_PERM);
        jdbc.update("DELETE FROM auth.user_session WHERE user_id IN (?, ?, ?)", OWNER, OTHER, NO_PERM);
        jdbc.update("DELETE FROM auth.user_role WHERE user_id IN (?, ?, ?)", OWNER, OTHER, NO_PERM);

        user(OWNER, "IT_HTTP_OWNER", "it_http_owner");
        user(OTHER, "IT_HTTP_OTHER", "it_http_other");
        user(NO_PERM, "IT_HTTP_NOPERM", "it_http_noperm");

        jdbc.update("INSERT INTO auth.role (id, role_code, name) VALUES (?, ?, ?)"
                + " ON CONFLICT (id) DO NOTHING", ROLE, "IT_FILE_ROLE", "文件下载测试角色");
        jdbc.update("INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", OWNER, ROLE);
        jdbc.update("INSERT INTO auth.user_role (user_id, role_id) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", OTHER, ROLE);
        jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", ROLE, "file:download");
        jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", ROLE, "face:download");
        for (String permission : List.of("dataset:write", "device:write", "person-library:write", "ingestion:write"))
            jdbc.update("INSERT INTO auth.role_permission(role_id,permission_code) VALUES (?,?) ON CONFLICT DO NOTHING", ROLE, permission);
        // M17：V6 种入的策略必须可用；被别的用例改过就恢复。
        jdbc.update("UPDATE core.upload_policy SET enabled = true");

        session(OWNER, SID_OWNER);
        session(OTHER, SID_OTHER);
        session(NO_PERM, SID_NO_PERM);
    }

    // ---------------------------------------------------------------- M18 / M19

    @Test
    void uploadPngIsActiveAndDetailAndDownloadWork() throws Exception {
        byte[] png = png();
        JsonNode body = upload(SID_OWNER, "FACE_SEARCH", "portrait.png", "image/png", png, key());
        String id = body.path("data").path("fileAssetId").asText();
        assertThat(body.path("data").path("status").asText()).isEqualTo("ACTIVE");

        mvc.perform(get("/files/" + id).cookie(cookie(SID_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileAssetId").value(id))
                .andExpect(jsonPath("$.data.category").value("FACE_SEARCH"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // M19：契约 required 列表里有 expiresAt（nullable），必须出现且非空。
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());

        mvc.perform(get("/files/" + id + "/download").cookie(downloadCookies(SID_OWNER, id)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(png));

        assertThat(audit("file.upload", UUID.fromString(id)).get("result")).isEqualTo("SUCCESS");
        assertThat(audit("file.download", UUID.fromString(id)).get("result")).isEqualTo("SUCCESS");
    }

    @Test
    void invalidJsonIsQuarantinedAndCannotBeDownloaded() throws Exception {
        byte[] notJson = "this is not JSON".getBytes(StandardCharsets.UTF_8);
        JsonNode body = upload(SID_OWNER, "DATASET_IMPORT", "bad.json", "application/json", notJson, key());
        String id = body.path("data").path("fileAssetId").asText();
        assertThat(body.path("data").path("status").asText()).isEqualTo("QUARANTINED");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, error_code, expires_at FROM core.file_asset WHERE id = ?", UUID.fromString(id));
        assertThat(row.get("status")).isEqualTo("QUARANTINED");
        assertThat(row.get("error_code")).isEqualTo("FILE_INVALID");
        assertThat(row.get("expires_at")).as("隔离文件不设保留期").isNull();

        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        mvc.perform(get("/files/" + id).cookie(cookie(SID_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUARANTINED"))
                .andExpect(jsonPath("$.data.expiresAt").isEmpty());

        // M20：内容校验失败必须有正式失败事件。
        assertThat(audit("file.upload", UUID.fromString(id)).get("result")).isEqualTo("FAILURE");
        assertThat(audit("file.upload", UUID.fromString(id)).get("failure_code"))
                .isEqualTo("FILE_INVALID");
    }

    @Test
    void validJsonBecomesActive() throws Exception {
        JsonNode body = upload(SID_OWNER, "DATASET_IMPORT", "ok.json", "application/json",
                "{\"rows\":[{\"a\":1}]}".getBytes(StandardCharsets.UTF_8), key());
        assertThat(body.path("data").path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void xlsxIsParsedForRealAndTruncatedXlsxIsQuarantined() throws Exception {
        JsonNode ok = upload(SID_OWNER, "DATASET_IMPORT", "ok.xlsx", XLSX_MIME, xlsx(true), key());
        assertThat(ok.path("data").path("status").asText()).isEqualTo("ACTIVE");

        JsonNode broken = upload(SID_OWNER, "DATASET_IMPORT", "broken.xlsx", XLSX_MIME, xlsx(false), key());
        assertThat(broken.path("data").path("status").asText())
                .as("有 zip 头但缺 worksheet 的结构必须被解析判死，不能只看 ZIP 头").isEqualTo("QUARANTINED");
    }

    @Test
    void csvQuotedNewlinesAreAcceptedAndColumnMismatchIsQuarantined() throws Exception {
        byte[] quoted = "a,b\n\"x\ny\",2\n".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(SID_OWNER, "DATASET_IMPORT", "quoted.csv", "text/csv", quoted, key())
                .path("data").path("status").asText()).isEqualTo("ACTIVE");

        byte[] mismatch = "a,b\n1\n".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(SID_OWNER, "DATASET_IMPORT", "mismatch.csv", "text/csv", mismatch, key())
                .path("data").path("status").asText()).isEqualTo("QUARANTINED");
    }

    // ---------------------------------------------------------------- M15 幂等

    @Test
    void idempotentReplayReturnsSameAssetAndDoesNotDuplicateRows() throws Exception {
        byte[] png = png();
        String key = key();
        String first = upload(SID_OWNER, "FACE_SEARCH", "idem.png", "image/png", png, key)
                .path("data").path("fileAssetId").asText();
        String second = upload(SID_OWNER, "FACE_SEARCH", "idem.png", "image/png", png, key)
                .path("data").path("fileAssetId").asText();
        assertThat(second).isEqualTo(first);
        assertThat(fileAssetRows(OWNER)).as("同键同内容不得新增 file_asset 行").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM core.idempotency_request"
                + " WHERE user_id = ? AND operation_id = 'POST /files/uploads' AND idempotency_key = ?",
                String.class, OWNER, key)).isEqualTo("SUCCEEDED");

        // 同键不同内容 → 409，且仍只有一行。
        mvc.perform(multipart("/files/uploads")
                        .file(new MockMultipartFile("file", "idem.png", "image/png", png2()))
                        .param("category", "FACE_SEARCH")
                        .header("Idempotency-Key", key)
                        .cookie(cookie(SID_OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(fileAssetRows(OWNER)).isEqualTo(1);

        // 不同账号用同一个键 → 各自独立，不串用。
        String other = upload(SID_OTHER, "FACE_SEARCH", "idem.png", "image/png", png, key)
                .path("data").path("fileAssetId").asText();
        assertThat(other).isNotEqualTo(first);
        assertThat(fileAssetRows(OWNER)).isEqualTo(1);
        assertThat(fileAssetRows(OTHER)).isEqualTo(1);

        // 键缺失 → 400。
        mvc.perform(multipart("/files/uploads")
                        .file(new MockMultipartFile("file", "nokey.png", "image/png", png))
                        .param("category", "FACE_SEARCH")
                        .cookie(cookie(SID_OWNER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------------------------- M17 / M20

    @Test
    void disabledPolicyRejectsUploadAndIsAudited() throws Exception {
        jdbc.update("UPDATE core.upload_policy SET enabled = false WHERE category = 'DATASET_IMPORT'");
        try {
            MvcResult result = uploadRequest(SID_OWNER, "DATASET_IMPORT", "secret.csv", "text/csv",
                    "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), key());
            assertThat(result.getResponse().getStatus()).isEqualTo(415);
            assertThat(objectMapper.readTree(result.getResponse().getContentAsByteArray())
                    .path("code").asText()).isEqualTo("FILE_MEDIA_UNSUPPORTED");
            Map<String, Object> event = auditByTrace(traceOf(result), "file.upload");
            assertThat(event.get("result")).isEqualTo("FAILURE");
            assertThat(event.get("failure_code")).isEqualTo("FILE_MEDIA_UNSUPPORTED");
            // M20：拒绝事件不写文件名/内容片段。
            assertThat(String.valueOf(event.get("after_summary"))).doesNotContain("secret.csv");
        } finally {
            jdbc.update("UPDATE core.upload_policy SET enabled = true WHERE category = 'DATASET_IMPORT'");
        }
    }

    @Test
    void oversizeAndUnsupportedTypeAreRejectedAndAudited() throws Exception {
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        MvcResult tooLarge = uploadRequest(SID_OWNER, "FACE_SEARCH", "big.png", "image/png", tooBig, key());
        assertThat(tooLarge.getResponse().getStatus()).isEqualTo(413);
        assertThat(objectMapper.readTree(tooLarge.getResponse().getContentAsByteArray())
                .path("code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(auditByTrace(traceOf(tooLarge), "file.upload").get("failure_code"))
                .isEqualTo("FILE_TOO_LARGE");

        MvcResult unsupported = uploadRequest(SID_OWNER, "FACE_SEARCH", "x.gif", "image/gif",
                "GIF89a".getBytes(StandardCharsets.US_ASCII), key());
        assertThat(unsupported.getResponse().getStatus()).isEqualTo(415);
        assertThat(objectMapper.readTree(unsupported.getResponse().getContentAsByteArray())
                .path("code").asText()).isEqualTo("FILE_MEDIA_UNSUPPORTED");
        assertThat(auditByTrace(traceOf(unsupported), "file.upload").get("failure_code"))
                .isEqualTo("FILE_MEDIA_UNSUPPORTED");
    }

    // ---------------------------------------------------------------- M19 / M20 下载

    @Test
    void expiredFileCannotBeReadOrDownloaded() throws Exception {
        UUID id = insertAsset(OWNER, "expired.png", "FACE_SEARCH", "image/png", "ACTIVE",
                OffsetDateTime.now().minusDays(1));
        mvc.perform(get("/files/" + id).cookie(cookie(SID_OWNER)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_EXPIRED"));
        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("FILE_EXPIRED"));
        assertThat(audit("file.download", id).get("failure_code")).isEqualTo("FILE_EXPIRED");
    }

    @Test
    void processingAndForeignAssetsAreHidden() throws Exception {
        UUID processing = insertAsset(OWNER, "proc.png", "FACE_SEARCH", "image/png", "PROCESSING", null);
        mvc.perform(get("/files/" + processing + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        assertThat(audit("file.download", processing).get("failure_code")).isEqualTo("DATA_NOT_FOUND");

        UUID foreign = insertAsset(OTHER, "foreign.png", "FACE_SEARCH", "image/png", "ACTIVE",
                OffsetDateTime.now().plusMonths(12));
        mvc.perform(get("/files/" + foreign + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_NOT_FOUND"));
        // 越权（不是自己的文件）也必须留正式拒绝事件，而不是只回一个 404。
        assertThat(audit("file.download", foreign).get("failure_code")).isEqualTo("DATA_NOT_FOUND");
    }

    @Test
    void downloadRequiresFileDownloadAndRevocationInvalidatesTheOldUrl() throws Exception {
        UUID id = insertAsset(OWNER, "perm.png", "FACE_SEARCH", "image/png", "ACTIVE", OffsetDateTime.now().plusMonths(12));
        // 补一份真实存储文件，成功路径才走得通。
        storeBytes(id);

        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_NO_PERM)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(audit("file.download", id).get("failure_code")).isEqualTo("PERMISSION_DENIED");

        mvc.perform(get("/files/" + id + "/download").cookie(downloadCookies(SID_OWNER, id.toString())))
                .andExpect(status().isOk());

        // 撤权：同一个 URL 立刻失效（每次下载都重校当前权限与账号权限版本）。
        jdbc.update("DELETE FROM auth.role_permission WHERE role_id = ? AND permission_code = 'file:download'",
                ROLE);
        try {
            mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        } finally {
            jdbc.update("INSERT INTO auth.role_permission (role_id, permission_code) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", ROLE, "file:download");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private void user(UUID id, String code, String login) {
        jdbc.update("INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash,"
                        + " status, password_changed_at) VALUES (?, ?, ?, ?, 'x', 'ACTIVE', now())"
                        + " ON CONFLICT (id) DO NOTHING",
                id, code, login, login);
    }

    private void session(UUID userId, String sid) {
        jdbc.update("INSERT INTO auth.user_session (id, user_id, sid_hash, idle_expires_at,"
                        + " absolute_expires_at, trace_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, Sha256.of(sid),
                OffsetDateTime.now().plusHours(2), OffsetDateTime.now().plusDays(1), "trace-it-http");
        jdbc.update("UPDATE auth.user_session SET client_label=? WHERE user_id=?",
                com.xxplatform.api.service.LoginService.clientBinding(null, "127.0.0.1"), userId);
    }

    private jakarta.servlet.http.Cookie[] downloadCookies(String sid, String id) throws Exception {
        MvcResult metadata = mvc.perform(get("/files/" + id).cookie(cookie(sid))).andExpect(status().isOk()).andReturn();
        jakarta.servlet.http.Cookie permit = metadata.getResponse().getCookie(com.xxplatform.api.service.file.DownloadPermits.COOKIE_NAME);
        assertThat(permit).isNotNull();
        return new jakarta.servlet.http.Cookie[] {cookie(sid), permit};
    }

    @Test
    void activeFileWithoutExpiryIsNeverPermanentlyDownloadable() throws Exception {
        UUID id = insertAsset(OWNER, "null-expiry.png", "FACE_SEARCH", "image/png", "ACTIVE", null);
        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("FILE_EXPIRED"));
    }

    @Test
    void downloadRequiresItsOwnShortLivedPermit() throws Exception {
        JsonNode upload = upload(SID_OWNER, "FACE_SEARCH", "permit.png", "image/png", png(), key());
        String id = upload.path("data").path("fileAssetId").asText();
        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER)))
                .andExpect(status().isForbidden());
        jakarta.servlet.http.Cookie[] cookies = downloadCookies(SID_OWNER, id);
        assertThat(cookies[1].isHttpOnly()).isTrue();
        assertThat(cookies[1].getMaxAge()).isEqualTo(300);
        mvc.perform(get("/files/" + id + "/download").cookie(cookies)).andExpect(status().isOk());
    }

    @Test
    void authenticatedOwnerCanPollAnUploadWithoutDownloadPermissions() throws Exception {
        JsonNode uploaded = upload(SID_NO_PERM, "FACE_SEARCH", "own-upload.png", "image/png", png(), key());
        String id = uploaded.path("data").path("fileAssetId").asText();
        MvcResult metadata = mvc.perform(get("/files/" + id).cookie(cookie(SID_NO_PERM))).andExpect(status().isOk()).andReturn();
        assertThat(metadata.getResponse().getCookie(com.xxplatform.api.service.file.DownloadPermits.COOKIE_NAME)).isNull();
        mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_NO_PERM))).andExpect(status().isForbidden());
    }

    @Test
    void missingDownloadKeyKeepsMetadataAvailableAndDownloadUnavailable() throws Exception {
        JsonNode uploaded = upload(SID_OWNER, "FACE_SEARCH", "no-key.png", "image/png", png(), key());
        String id = uploaded.path("data").path("fileAssetId").asText();
        String original = fileProperties.getDownloadPermitKey();
        fileProperties.setDownloadPermitKey("");
        try {
            mvc.perform(get("/files/" + id).cookie(cookie(SID_OWNER))).andExpect(status().isOk());
            mvc.perform(get("/files/" + id + "/download").cookie(cookie(SID_OWNER))).andExpect(status().isServiceUnavailable());
        } finally { fileProperties.setDownloadPermitKey(original); }
    }

    private JsonNode upload(String sid, String category, String fileName, String mime, byte[] content,
                            String idempotencyKey) throws Exception {
        MvcResult result = uploadRequest(sid, category, fileName, mime, content, idempotencyKey);
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /**
     * 走一次 multipart 上传，并在 MDC 里放一个本次请求专属的 traceId。
     *
     * <p>测试用 {@code @AutoConfigureMockMvc(addFilters = false)} 关掉了 RequestIdFilter（为了绕开
     * CSRF 与同源校验），业务代码取 traceId 会退化成常量 {@code unknown-trace}，审计事件就无法按
     * trace 唯一定位。生产环境由 RequestIdFilter 把 traceId 写进 MDC，这里显式做同一件事，
     * 保证审计断言命中的就是本次请求那一行，而不是历史累积的别的事件。
     */
    private MvcResult uploadRequest(String sid, String category, String fileName, String mime,
                                    byte[] content, String idempotencyKey) throws Exception {
        MDC.put(RequestIdFilter.TRACE_MDC_KEY, "it-http-" + UUID.randomUUID());
        try {
            return mvc.perform(multipart("/files/uploads")
                            .file(new MockMultipartFile("file", fileName, mime, content))
                            .param("category", category)
                            .header("Idempotency-Key", idempotencyKey)
                            .cookie(cookie(sid)))
                    .andReturn();
        } finally {
            MDC.remove(RequestIdFilter.TRACE_MDC_KEY);
        }
    }

    private UUID insertAsset(UUID owner, String fileName, String category, String mime, String status,
                             OffsetDateTime expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO core.file_asset (id, storage_key, file_name, category, mime_type,"
                        + " byte_size, sha256, owner_user_id, status, expires_at, trace_id, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                id, "test/" + id + ".png", fileName, category, mime, 1L, "0".repeat(64), owner,
                status, expiresAt, "trace-it-http");
        return id;
    }

    /** 让 ACTIVE 行有真实落盘文件（下载成功路径需要）。 */
    private void storeBytes(UUID id) throws IOException {
        byte[] png = png();
        Path target = STORAGE_ROOT.resolve("test").resolve(id + ".png");
        Files.createDirectories(target.getParent());
        Files.write(target, png);
        long size = png.length;
        jdbc.update("UPDATE core.file_asset SET storage_key = ?, byte_size = ? WHERE id = ?",
                "test/" + id + ".png", size, id);
    }

    private Map<String, Object> audit(String action, UUID resourceId) {
        return jdbc.queryForMap("SELECT result, failure_code, after_summary FROM audit.audit_event"
                + " WHERE action = ? AND resource_id = ? ORDER BY created_at DESC LIMIT 1", action, resourceId);
    }

    private Map<String, Object> auditByTrace(String traceId, String action) {
        return jdbc.queryForMap("SELECT result, failure_code, after_summary FROM audit.audit_event"
                + " WHERE action = ? AND trace_id = ? ORDER BY created_at DESC LIMIT 1", action, traceId);
    }

    private String traceOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("traceId").asText();
    }

    private long fileAssetRows(UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM core.file_asset WHERE owner_user_id = ?",
                Long.class, owner);
    }

    private static String key() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Cookie cookie(String sid) {
        return new Cookie("SID", sid);
    }

    private static byte[] png() {
        return png(0xFF0000);
    }

    private static byte[] png2() {
        return png(0x00FF00);
    }

    private static byte[] png(int rgb) {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, rgb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 合成一个最小但结构完整的 xlsx：Content_Types + workbook + rels + worksheet。
     * {@code withSheet=false} 时故意缺 worksheet，用来证明"只看 ZIP 头"会放过坏文件。
     */
    private static byte[] xlsx(boolean withSheet) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>");
                put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                        + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"S\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                if (withSheet) {
                    put(zip, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                            + "<sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>a</t></is></c></row>"
                            + "<row r=\"2\"><c r=\"A2\" t=\"inlineStr\"><is><t>1</t></is></c></row>"
                            + "</sheetData></worksheet>");
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("xx-file-http-it-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 清理尽力而为。
                }
            });
        } catch (IOException ignored) {
            // 清理尽力而为。
        }
    }
}
