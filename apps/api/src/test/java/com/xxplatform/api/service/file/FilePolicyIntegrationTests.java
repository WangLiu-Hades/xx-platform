package com.xxplatform.api.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.FileProperties;
import com.xxplatform.api.infra.UserAccountRepository;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.ClientConfigService;
import com.xxplatform.api.service.ClientConfigView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * M17（上传策略同源同值 / enabled / 全局与分类取较小值 / 100MiB·10 万行）与 M19（V5 回填）真库证据。
 *
 * <p>这些断言必须打在真库上：策略的"唯一数据源"是 {@code core.upload_policy} 与
 * {@code core.config_item}，用桩无法证明下发值和服务端执行值一致。
 */
@SpringBootTest
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class FilePolicyIntegrationTests {

    private static final Path STORAGE_ROOT = tempDir();
    private static final UUID OWNER = UUID.fromString("7a52c1d0-0000-4000-8000-000000000101");

    @Autowired
    private UploadPolicyProvider provider;

    @Autowired
    private ClientConfigService clientConfigService;

    @Autowired
    private FileService fileService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileProperties properties;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String multipartMaxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String multipartMaxRequestSize;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> env("XX_TASK_IT_DB_URL", ""));
        registry.add("spring.datasource.username", () -> env("XX_TASK_IT_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> env("XX_TASK_IT_DB_PASSWORD", ""));
        registry.add("xx.file.storage-root", STORAGE_ROOT::toString);
        registry.add("xx.task.reaper-enabled", () -> "false");
    }

    @AfterAll
    static void cleanUp() {
        deleteRecursively(STORAGE_ROOT);
    }

    @BeforeEach
    void seedUser() {
        jdbc.update("DELETE FROM core.file_asset WHERE owner_user_id = ?", OWNER);
        jdbc.update("DELETE FROM core.idempotency_request WHERE user_id = ?", OWNER);
        jdbc.update("INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash,"
                        + " status, password_changed_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', now())"
                        + " ON CONFLICT (id) DO NOTHING",
                OWNER, "IT_POLICY", "it_policy", "策略集成测试", "x");
    }

    @Test
    void seededPoliciesAreTheSameSourceForServerAndClientConfig() {
        ClientConfigView view = clientConfigService.load();
        Map<String, UploadPolicyProvider.CategoryPolicy> server = byCategory(provider.policies());

        assertThat(server).containsOnlyKeys("FACE_SEARCH", "PROFILE_PORTRAIT", "DATASET_IMPORT",
                "DEVICE_IMPORT", "PERSON_LIBRARY_IMPORT", "RAW_INGESTION");
        assertThat(view.uploadPolicies()).hasSize(server.size());

        // 同一分类、同一数值：下发值就是服务端执行值（maxBytes 是分类自身上限）。
        for (ClientConfigView.UploadPolicyView downlink : view.uploadPolicies()) {
            UploadPolicyProvider.CategoryPolicy policy = server.get(downlink.category());
            assertThat(policy).as("下发分类必须存在于服务端策略：%s", downlink.category()).isNotNull();
            assertThat(downlink.maxBytes()).isEqualTo(policy.maxBytes());
            assertThat(downlink.enabled()).isEqualTo(policy.enabled()).isTrue();
            assertThat(sorted(downlink.mimeTypes())).isEqualTo(sorted(policy.mimeTypes()));
            assertThat(sorted(downlink.extensions())).isEqualTo(sorted(policy.extensions()));
        }

        // P-003-02：图片 10MiB、普通导入 100MiB / 10 万行。
        assertThat(server.get("FACE_SEARCH").maxBytes()).isEqualTo(10L * 1024 * 1024);
        assertThat(server.get("PROFILE_PORTRAIT").maxBytes()).isEqualTo(10L * 1024 * 1024);
        for (String category : List.of("DATASET_IMPORT", "DEVICE_IMPORT",
                "PERSON_LIBRARY_IMPORT", "RAW_INGESTION")) {
            UploadPolicyProvider.CategoryPolicy policy = server.get(category);
            assertThat(policy.maxBytes()).isEqualTo(100L * 1024 * 1024);
            assertThat(policy.maxRows()).isEqualTo(UploadPolicyCatalog.IMPORT_MAX_ROWS);
            assertThat(server.get(category).enabled()).isTrue();
        }
        assertThat(server.get("FACE_SEARCH").mimeTypes())
                .containsExactlyInAnyOrder("image/jpeg", "image/png");
        assertThat(server.get("DATASET_IMPORT").mimeTypes()).contains("text/csv", "application/json");
        assertThat(server.get("DATASET_IMPORT").extensions()).contains(".csv", ".json", ".xlsx");

        // 全局上限也已纠正到 100MiB，否则 min(全局, 分类) 仍会把导入压回 10MiB。
        assertThat(view.maxUploadBytes()).isEqualTo(100L * 1024 * 1024);
        assertThat(provider.globalMaxBytes()).isEqualTo(100L * 1024 * 1024);
        assertThat(provider.forCategory(FileCategory.DATASET_IMPORT).effectiveMaxBytes())
                .isEqualTo(100L * 1024 * 1024);
        assertThat(provider.forCategory(FileCategory.FACE_SEARCH).effectiveMaxBytes())
                .isEqualTo(10L * 1024 * 1024);
    }

    @Test
    void disabledPolicyRejectsUploadAndIsVisibleDownstream() {
        jdbc.update("UPDATE core.upload_policy SET enabled = false WHERE category = 'DATASET_IMPORT'");
        try {
            assertThat(clientConfigService.load().uploadPolicies().stream()
                    .filter(p -> p.category().equals("DATASET_IMPORT"))
                    .findFirst().orElseThrow().enabled()).isFalse();

            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> fileService.upload(session(), "DATASET_IMPORT", "a.csv",
                            "text/csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), "trace-disable"))
                    .satisfies(ex -> {
                        assertThat(ex.code()).isEqualTo(ErrorCode.FILE_MEDIA_UNSUPPORTED);
                        assertThat(ex.getMessage()).contains("未启用");
                    });
        } finally {
            jdbc.update("UPDATE core.upload_policy SET enabled = true WHERE category = 'DATASET_IMPORT'");
        }
    }

    @Test
    void effectiveLimitIsTheSmallerOfGlobalAndCategory() {
        String original = jdbc.queryForObject(
                "SELECT value::text FROM core.config_item WHERE config_key = 'client-config'", String.class);
        jdbc.update("UPDATE core.config_item SET value = jsonb_set(value, '{maxUploadBytes}',"
                + " to_jsonb(?::bigint), true) WHERE config_key = 'client-config'", 1024L);
        try {
            assertThat(provider.globalMaxBytes()).isEqualTo(1024L);
            assertThat(clientConfigService.load().maxUploadBytes()).isEqualTo(1024L);
            assertThat(provider.forCategory(FileCategory.DATASET_IMPORT).effectiveMaxBytes())
                    .as("实际限制取全局与分类的较小值").isEqualTo(1024L);

            byte[] tooBig = csv(1200);
            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> fileService.upload(session(), "DATASET_IMPORT", "big.csv",
                            "text/csv", tooBig, "trace-global-cap"))
                    .satisfies(ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
        } finally {
            jdbc.update("UPDATE core.config_item SET value = ?::jsonb WHERE config_key = 'client-config'",
                    original);
        }
        assertThat(provider.globalMaxBytes()).isEqualTo(100L * 1024 * 1024);
    }

    @Test
    void elevenMiBImportIsAcceptedAndGetsTwelveMonthExpiry() {
        byte[] content = csv(11 * 1024 * 1024);
        FileService.Uploaded uploaded = fileService.upload(session(), "DATASET_IMPORT", "big.csv",
                "text/csv", content, "trace-11mib");
        assertThat(uploaded.status()).isEqualTo("ACTIVE");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT byte_size, expires_at, created_at FROM core.file_asset WHERE id = ?",
                uploaded.fileAssetId());
        assertThat(((Number) row.get("byte_size")).longValue()).isEqualTo(content.length);
        assertThat(row.get("expires_at")).as("M19：ACTIVE 必须有到期时间").isNotNull();
        // 12 个自然月，不是 365 天：按 Asia/Shanghai 加 12 个月。
        assertThat(jdbc.queryForObject(
                "SELECT expires_at = ((created_at AT TIME ZONE 'Asia/Shanghai') + INTERVAL '12 months')"
                        + " AT TIME ZONE 'Asia/Shanghai' FROM core.file_asset WHERE id = ?",
                Boolean.class, uploaded.fileAssetId())).isTrue();
    }

    @Test
    void importRowLimitIsEnforced() {
        byte[] content = csvRows(UploadPolicyCatalog.IMPORT_MAX_ROWS + 1);
        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> fileService.upload(session(), "DATASET_IMPORT", "rows.csv",
                        "text/csv", content, "trace-rows"))
                .satisfies(ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.FILE_TOO_LARGE);
                    assertThat(ex.getMessage()).contains("100000");
                });

        // 恰好 10 万行是允许的。
        FileService.Uploaded ok = fileService.upload(session(), "DATASET_IMPORT", "rows-ok.csv",
                "text/csv", csvRows(UploadPolicyCatalog.IMPORT_MAX_ROWS), "trace-rows-ok");
        assertThat(ok.status()).isEqualTo("ACTIVE");
    }

    @Test
    void containerMultipartLimitsCoverTheBusinessLimit() {
        long maxFile = DataSize.parse(multipartMaxFileSize).toBytes();
        long maxRequest = DataSize.parse(multipartMaxRequestSize).toBytes();
        long importLimit = properties.getImportMaxBytes();
        assertThat(maxFile).as("multipart max-file-size 必须≥业务导入上限（M17：容器不能先拒）")
                .isGreaterThanOrEqualTo(importLimit);
        assertThat(maxRequest).as("multipart max-request-size 必须容纳 multipart 开销")
                .isGreaterThanOrEqualTo(importLimit + 1024 * 1024);
    }

    @Test
    void v5BackfillIsIdempotentAndUsesNaturalMonths() {
        UUID historical = UUID.randomUUID();
        jdbc.update("INSERT INTO core.file_asset (id, storage_key, file_name, category, mime_type,"
                        + " byte_size, sha256, owner_user_id, status, expires_at, trace_id, created_at)"
                        + " VALUES (?, ?, 'old.png', 'FACE_SEARCH', 'image/png', 1, ?, ?, 'ACTIVE', NULL, ?, ?)",
                historical, "test/historical.png", "0".repeat(64), OWNER, "trace-v5",
                OffsetDateTime.of(2024, 2, 29, 10, 0, 0, 0, ZoneOffset.ofHours(8)));

        String migration = read("db/migration/V5__backfill_file_asset_retention.sql");
        jdbc.execute(migration);

        assertThat(jdbc.queryForObject(
                "SELECT to_char(expires_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS')"
                        + " FROM core.file_asset WHERE id = ?", String.class, historical))
                .as("2024-02-29 10:00+08 的 12 个自然月是 2025-02-28，不是 365 天后的 2025-02-28T10:00Z")
                .isEqualTo("2025-02-28T02:00:00");

        String first = jdbc.queryForObject(
                "SELECT expires_at::text FROM core.file_asset WHERE id = ?", String.class, historical);
        jdbc.execute(migration);
        assertThat(jdbc.queryForObject(
                "SELECT expires_at::text FROM core.file_asset WHERE id = ?", String.class, historical))
                .as("V5 可重复执行（幂等）").isEqualTo(first);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM core.file_asset WHERE status = 'ACTIVE' AND expires_at IS NULL",
                Long.class)).as("迁移后不应再有\"永久可用\"的 ACTIVE 文件").isZero();
    }

    // ------------------------------------------------------------------

    private static Map<String, UploadPolicyProvider.CategoryPolicy> byCategory(
            List<UploadPolicyProvider.CategoryPolicy> policies) {
        return policies.stream().collect(Collectors.toMap(
                UploadPolicyProvider.CategoryPolicy::category, Function.identity()));
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    private static AuthenticatedSession session() {
        UserAccountRepository.UserAccountRow user = new UserAccountRepository.UserAccountRow(
                OWNER, "IT_POLICY", "it_policy", "策略集成测试", "x", "ACTIVE",
                OffsetDateTime.now().minusDays(1), 0L);
        return new AuthenticatedSession(OWNER, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusDays(1), user);
    }

    /** 单行 CSV，总字节数恰好 >= {@code size}。 */
    private static byte[] csv(int size) {
        String head = "a,b\n";
        int filler = Math.max(1, size - head.length() - 2);
        return (head + "1," + "x".repeat(filler) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /** 表头 + {@code dataRows} 行的 CSV。 */
    private static byte[] csvRows(int dataRows) {
        StringBuilder sb = new StringBuilder("a,b\n");
        for (int i = 0; i < dataRows; i++) {
            sb.append(i).append(',').append(i).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String read(String classpath) {
        try (var in = new ClassPathResource(classpath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private static Path tempDir() {
        try {
            return Files.createTempDirectory("xx-file-policy-it-");
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
