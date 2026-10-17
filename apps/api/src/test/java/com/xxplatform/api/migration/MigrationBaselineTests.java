package com.xxplatform.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 迁移基线守卫：保证 Flyway 的 V1 迁移与契约里的 {@code schema.sql} 逐字节一致。
 *
 * <p>为什么需要这个测试：{@code contracts/database/schema.sql} 是**契约**，
 * {@code src/main/resources/db/migration/V1__baseline_design_2.sql} 是**实现**。
 * 两份文件一旦漂移，就会出现"契约说 163 张表、实际库是另一套"的情况，
 * 而这种错误在空库上不会报错、只会让后续接口静默对不上字段。
 *
 * <p>所以 V1 采取**逐字节复制**而不是重写：schema.sql 有任何变更，
 * 这个测试立刻失败，强制责任人明确决定"该改契约还是该加 V2 增量迁移"。
 *
 * <h2>正确做法与错误做法</h2>
 * <ul>
 *   <li>契约新增字段/表 → 先改 {@code schema.sql} + 数据字典 + api-storage-map，
 *       再**新增 {@code V2__xxx.sql} 增量迁移**，并同步更新本测试里的基线哈希。</li>
 *   <li><b>禁止</b>直接编辑已发布的 {@code V1__*.sql}：Flyway 会因 checksum 变化
 *       拒绝启动（这正是我们想要的保护）。</li>
 * </ul>
 *
 * <p>本测试不启动 Spring、不连数据库，因此不依赖任何外部环境。
 */
class MigrationBaselineTests {

    /**
     * 仓库根目录相对 {@code apps/api} 的偏移。
     *
     * <p>Surefire 的 {@code user.dir} 是模块目录（{@code apps/api}），所以往上一层是本仓库根。
     * 用相对路径而不是绝对路径，避免在别人机器上失败。
     */
    private static final Path REPO_ROOT = Path.of("..", "..");

    private static final Path CONTRACT_SCHEMA = REPO_ROOT.resolve("contracts/database/schema.sql");

    private static final Path V1_MIGRATION =
            Path.of("src/main/resources/db/migration/V1__baseline_design_2.sql");

    /**
     * 契约 schema.sql 的 SHA256，来自综合测试报告与本机核对（2026-10-17 一致）。
     *
     * <p>这一行是**有意写死的**：它让"契约悄悄改了但没人升级迁移"变成一条红测试。
     * 契约合法变更时，同时更新此处哈希与 V2 迁移即可。
     */
    private static final String EXPECTED_CONTRACT_SHA256 =
            "7d3534b536e46a5879d338df08cc70a02012ef86d0efe423d25747cf37a26ab1";

    @Test
    @DisplayName("V1 迁移与契约 schema.sql 逐字节一致")
    void baselineMigrationMatchesContractSchema() throws IOException {
        assertThat(CONTRACT_SCHEMA)
                .as("契约 schema.sql 必须存在：%s", CONTRACT_SCHEMA.toAbsolutePath().normalize())
                .exists();
        assertThat(V1_MIGRATION)
                .as("V1 迁移必须存在：%s", V1_MIGRATION.toAbsolutePath().normalize())
                .exists();

        // 用**字节**比较，不用字符串断言：
        //   - 基线要求逐字节一致，任何编码/换行差异都应被发现；
        //   - AssertJ 的 hasContent/isEqualTo(String) 在失败时要生成 diff，
        //     而 diff 走的是平台默认字符集（本机 Windows 为 GBK），
        //     会对 UTF-8 中文注释抛 MalformedInputException，掩盖真正的差异。
        byte[] contract = Files.readAllBytes(CONTRACT_SCHEMA);
        byte[] migration = Files.readAllBytes(V1_MIGRATION);

        assertThat(migration)
                .as("V1 迁移必须与 schema.sql 字节一致（长度 %d）。若契约有意变更，"
                        + "请新增 V2 增量迁移并更新本测试的基线哈希，不要直接编辑已发布的 V1",
                        contract.length)
                .isEqualTo(contract);
    }

    @Test
    @DisplayName("契约 schema.sql 未被静默改动（基线哈希守卫）")
    void contractSchemaHashIsUnchanged() throws IOException, NoSuchAlgorithmException {
        byte[] raw = Files.readAllBytes(CONTRACT_SCHEMA);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));

        assertThat(actual)
                .as("contracts/database/schema.sql 的 SHA256 变了。"
                        + "这通常意味着契约被修改：请同步数据字典、api-storage-map，"
                        + "新增 V2 增量迁移，并更新本测试的 EXPECTED_CONTRACT_SHA256")
                .isEqualTo(EXPECTED_CONTRACT_SHA256);
    }

    @Test
    @DisplayName("基线迁移包含六个 schema 与版本守卫，可安全用于空库")
    void baselineCoversAllSchemasAndGuards() throws IOException {
        // 显式指定 UTF-8（不用无参 readString，避免跟随平台默认编码）
        String sql = new String(Files.readAllBytes(V1_MIGRATION), StandardCharsets.UTF_8);

        // 六个 schema 是数据所有权边界，缺一个就意味着后续迁移会写错地方
        for (String schema : new String[] {"auth", "core", "capture", "analysis", "operation", "audit"}) {
            assertThat(sql)
                    .as("基线迁移必须创建 schema `%s`", schema)
                    .containsIgnoringCase("CREATE SCHEMA IF NOT EXISTS " + schema + ";");
        }

        // 版本守卫：PG14+ 与"不接受 PostGIS 3.0.x"，避免在错误环境上静默建出一半
        assertThat(sql).contains("server_version_num");
        assertThat(sql).containsIgnoringCase("CREATE EXTENSION IF NOT EXISTS postgis");

        // 事务包裹：空库基线整体成功或整体失败，不留半套表
        assertThat(sql.trim()).startsWith("--");
        assertThat(sql).containsIgnoringCase("BEGIN;");
        assertThat(sql).containsIgnoringCase("COMMIT;");
    }

    @Test
    @DisplayName("迁移目录只有 V1，且命名为 V<序号>__<描述>.sql")
    void migrationDirectoryFollowsNamingConvention() throws IOException {
        Path dir = V1_MIGRATION.getParent();
        try (var stream = Files.list(dir)) {
            var names = stream.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".sql"))
                    .sorted()
                    .toList();

            assertThat(names)
                    .as("迁移文件命名必须是 V<序号>__<描述>.sql（双下划线），否则 Flyway 会忽略它")
                    .allSatisfy(n -> assertThat(n).matches("^V\\d+__[A-Za-z0-9_]+\\.sql$"));

            // 当前阶段只允许基线：增量迁移应是后续任务的产物，
            // 出现意外文件通常意味着有人手工塞了脚本。
            assertThat(names)
                    .as("本阶段应只有基线与（将来的）增量迁移；如新增，请同步本测试与 README")
                    .contains("V1__baseline_design_2.sql");
        }
    }
}
