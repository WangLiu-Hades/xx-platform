package com.xxplatform.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 迁移基线与契约的守卫。
 *
 * <p>分工：{@code contracts/database/schema.sql} 是**契约**（唯一事实来源），
 * {@code src/main/resources/db/migration/V*.sql} 是**实现**（可重放的增量历史）。
 * 两者一旦漂移，就会出现"契约说 165 张表、空库建出来只有 163 张"的情况，
 * 而这种错误**在空库上不报错**，只会在接口运行时炸 42P01（relation does not exist），
 * 或者更糟：老库因为 V1 checksum 没变，Flyway 认为"已是最新"，静默缺少对象。
 *
 * <h2>历史</h2>
 * <ul>
 *   <li>V1 是 2026-10-17 契约的逐字节快照（163 张表），当初用"字节相等"做守卫。</li>
 *   <li>2026-10-18 契约追加 PD-2026-10-18.v1（审批聚合、解密受控字段、feature_config 口径），
 *       变成 165 张表，但没有配套迁移——这正是字节守卫抓到的漂移。</li>
 *   <li>修法不是改 V1（Flyway checksum 会拒绝启动），而是新增
 *       {@code V3__approval_aggregate_and_policy_v1.sql} 承载差异，
 *       并把守卫从"V1 与契约逐字节相同"改成"**迁移集合与契约结构等价**"。</li>
 * </ul>
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>契约新增字段/表 → 先改 {@code schema.sql} + 数据字典 + api-storage-map，
 *       再新增 {@code V<n>__xxx.sql} 增量迁移，并更新本测试的契约哈希。</li>
 *   <li><b>禁止</b>编辑已发布的 {@code V*.sql}：Flyway 会因 checksum 变化拒绝启动。</li>
 *   <li>结构等价由本测试离线校验；DDL 语义等价（可执行、类型/约束落地）由
 *       {@code apps/api/scripts/verify-migration-parity.ps1} 用两个临时库比对。</li>
 * </ul>
 *
 * <p>本测试不启动 Spring、不连数据库，因此不依赖任何外部环境。
 */
class MigrationBaselineTests {

    /** 仓库根目录相对 {@code apps/api} 的偏移（Surefire 的 user.dir 是模块目录）。 */
    private static final Path REPO_ROOT = Path.of("..", "..");

    private static final Path CONTRACT_SCHEMA = REPO_ROOT.resolve("contracts/database/schema.sql");

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    private static final Path V1_MIGRATION = MIGRATION_DIR.resolve("V1__baseline_design_2.sql");

    private static final Path V3_MIGRATION =
            MIGRATION_DIR.resolve("V3__approval_aggregate_and_policy_v1.sql");

    /**
     * 契约 schema.sql 的 SHA256（2026-10-18 PD-2026-10-18.v1 口径，165 张表）。
     *
     * <p>这一行是**有意写死的**：它让"契约悄悄改了但没人升级迁移"变成一条红测试。
     * 契约合法变更时，同时更新此处哈希与对应的 {@code V<n>__*.sql} 增量迁移即可。
     */
    private static final String EXPECTED_CONTRACT_SHA256 =
            "05a894a761ef61dc4bd5ad8075af27dbcb195caf036dd8b0ee64ff85afc84664";

    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?is)\\bCREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"]+)");

    private static final Pattern ADD_COLUMN =
            Pattern.compile(
                    "(?is)\\bALTER\\s+TABLE\\s+(?:ONLY\\s+)?([\\w.\"]+)\\s+ADD\\s+COLUMN\\s+"
                            + "(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w\"]+)");

    private static final Pattern CREATE_INDEX =
            Pattern.compile("(?is)\\bCREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:CONCURRENTLY\\s+)?"
                    + "(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.\"]+)");

    private static final Pattern NAMED_CONSTRAINT =
            Pattern.compile("(?is)\\bCONSTRAINT\\s+([\\w\"]+)");

    private static final Pattern CREATE_TRIGGER =
            Pattern.compile("(?is)\\bCREATE\\s+TRIGGER\\s+([\\w.\"]+)");

    /** 表体里不是列定义的关键字。 */
    private static final Set<String> NON_COLUMN_KEYWORDS = Set.of(
            "CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN", "EXCLUDE", "LIKE");

    @Test
    @DisplayName("迁移集合与契约结构等价（表/列/索引/约束/触发器逐项一致）")
    void migrationsCoverContractStructure() throws IOException {
        assertThat(CONTRACT_SCHEMA)
                .as("契约 schema.sql 必须存在：%s", CONTRACT_SCHEMA.toAbsolutePath().normalize())
                .exists();
        assertThat(migrationFiles())
                .as("迁移目录至少要有一个 V<n>__*.sql：%s", MIGRATION_DIR.toAbsolutePath())
                .isNotEmpty();

        SchemaShape contract = parseSchema(read(CONTRACT_SCHEMA));
        SchemaShape migrations = parseSchema(concatMigrations());

        assertThat(migrations.tables())
                .as("表集合必须完全一致：契约有而迁移没有 = 空库会缺表；反之 = 迁移多建了对象")
                .isEqualTo(contract.tables());

        for (String table : new TreeSet<>(contract.columns().keySet())) {
            assertThat(migrations.columns().getOrDefault(table, Set.of()))
                    .as("表 %s 的列必须与契约一致（含 ALTER TABLE ... ADD COLUMN 的累积结果）", table)
                    .isEqualTo(contract.columns().get(table));
        }

        assertThat(migrations.indexes())
                .as("索引名集合必须一致（含 UNIQUE INDEX 与契约末端的 ALTER 追加）")
                .isEqualTo(contract.indexes());
        assertThat(migrations.constraints())
                .as("具名约束集合必须一致")
                .isEqualTo(contract.constraints());
        assertThat(migrations.triggers())
                .as("触发器集合必须一致")
                .isEqualTo(contract.triggers());
    }

    @Test
    @DisplayName("契约 schema.sql 未被静默改动（契约哈希守卫）")
    void contractSchemaHashIsUnchanged() throws IOException, NoSuchAlgorithmException {
        byte[] raw = Files.readAllBytes(CONTRACT_SCHEMA);
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));

        assertThat(actual)
                .as("contracts/database/schema.sql 的 SHA256 变了。"
                        + "这通常意味着契约被修改：请同步数据字典、api-storage-map，"
                        + "新增 V<n> 增量迁移，并更新本测试的 EXPECTED_CONTRACT_SHA256")
                .isEqualTo(EXPECTED_CONTRACT_SHA256);
    }

    @Test
    @DisplayName("V1 基线仍是历史快照，新增差异只允许走增量迁移")
    void baselineStaysFrozen() throws IOException {
        String v1 = read(V1_MIGRATION);

        assertThat(V1_MIGRATION)
                .as("V1 必须存在且保持 163 张表的历史快照形态（已发布迁移不可编辑）")
                .exists();
        assertThat(parseSchema(v1).tables())
                .as("V1 的表数量变了：说明有人编辑了已发布迁移，Flyway 会因 checksum 变化拒绝启动")
                .hasSize(163);
        assertThat(v1)
                .as("V1 不得直接包含审批聚合（它属于 V3 增量）")
                .doesNotContain("CREATE TABLE operation.action_approval");
        assertThat(parseSchema(read(V3_MIGRATION)).tables())
                .as("审批聚合应由 V3 增量引入")
                .contains("operation.action_approval", "audit.action_approval_decision");
    }

    @Test
    @DisplayName("基线迁移包含六个 schema 与版本守卫，可安全用于空库")
    void baselineCoversAllSchemasAndGuards() throws IOException {
        // 显式指定 UTF-8（不用无参 readString，避免跟随平台默认编码）
        String sql = read(V1_MIGRATION);

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
    @DisplayName("迁移文件按 V<序号>__<描述>.sql 命名，版本从 1 连续")
    void migrationDirectoryFollowsNamingConvention() throws IOException {
        List<Path> files = migrationFiles();
        List<Integer> versions = new ArrayList<>();

        assertThat(files)
                .as("迁移文件命名必须是 V<序号>__<描述>.sql（双下划线），否则 Flyway 会忽略它")
                .allSatisfy(p -> assertThat(p.getFileName().toString())
                        .matches("^V\\d+__[A-Za-z0-9_]+\\.sql$"));

        for (Path p : files) {
            String name = p.getFileName().toString();
            versions.add(Integer.parseInt(name.substring(1, name.indexOf("__"))));
        }
        versions.sort(Integer::compareTo);

        // 版本跳号意味着有人删了中间迁移：老库不会再补上那段 DDL
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i))
                    .as("迁移版本必须从 1 连续递增，实际为 %s", versions)
                    .isEqualTo(i + 1);
        }
        assertThat(versions).contains(1);
    }

    // ------------------------------------------------------------------ 解析

    /** 契约或迁移集合的可比较结构。 */
    private record SchemaShape(
            Set<String> tables,
            Map<String, Set<String>> columns,
            Set<String> indexes,
            Set<String> constraints,
            Set<String> triggers) {}

    private static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                    .toList();
        }
    }

    private static String concatMigrations() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path p : migrationFiles()) {
            sb.append('\n').append(read(p));
        }
        return sb.toString();
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * 从 SQL 文本抽取结构指纹。
     *
     * <p>只做"文本层面的结构比对"，不连库：表名与列名来自 {@code CREATE TABLE} 表体，
     * 外加 {@code ALTER TABLE ... ADD COLUMN} 的累积结果；索引/具名约束/触发器取名字集合。
     * 数据类型与默认值的等价性由 verify-migration-parity.ps1 在真实库上比对。
     */
    private static SchemaShape parseSchema(String rawSql) {
        String sql = stripComments(rawSql);
        Map<String, Set<String>> columns = new LinkedHashMap<>();

        Matcher tableMatcher = CREATE_TABLE.matcher(sql);
        while (tableMatcher.find()) {
            String table = normalize(tableMatcher.group(1));
            int bodyStart = sql.indexOf('(', tableMatcher.end());
            if (bodyStart < 0) {
                continue;
            }
            int bodyEnd = matchingParen(sql, bodyStart);
            if (bodyEnd < 0) {
                continue;
            }
            columns.computeIfAbsent(table, k -> new TreeSet<>())
                    .addAll(columnNames(sql.substring(bodyStart + 1, bodyEnd)));
        }

        Matcher addColumn = ADD_COLUMN.matcher(sql);
        while (addColumn.find()) {
            columns.computeIfAbsent(normalize(addColumn.group(1)), k -> new TreeSet<>())
                    .add(normalize(addColumn.group(2)));
        }

        Set<String> indexes = new TreeSet<>();
        Matcher indexMatcher = CREATE_INDEX.matcher(sql);
        while (indexMatcher.find()) {
            indexes.add(normalize(indexMatcher.group(1)));
        }

        Set<String> constraints = new TreeSet<>();
        Matcher constraintMatcher = NAMED_CONSTRAINT.matcher(sql);
        while (constraintMatcher.find()) {
            constraints.add(normalize(constraintMatcher.group(1)));
        }

        Set<String> triggers = new TreeSet<>();
        Matcher triggerMatcher = CREATE_TRIGGER.matcher(sql);
        while (triggerMatcher.find()) {
            triggers.add(normalize(triggerMatcher.group(1)));
        }

        return new SchemaShape(new TreeSet<>(columns.keySet()), columns, indexes, constraints, triggers);
    }

    private static int matchingParen(String sql, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Set<String> columnNames(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());

        Set<String> names = new TreeSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String first = trimmed.split("\\s+")[0].toUpperCase(Locale.ROOT);
            if (NON_COLUMN_KEYWORDS.contains(first)) {
                continue;
            }
            names.add(normalize(trimmed.split("\\s+")[0]));
        }
        return names;
    }

    private static String normalize(String identifier) {
        return identifier.replace("\"", "").toLowerCase(Locale.ROOT).trim();
    }

    /**
     * 去掉 {@code --} 行注释，跳过字符串字面量与 {@code $$ ... $$} 函数体。
     *
     * <p>注释里会出现 CREATE TABLE 之类的字样（本文件注释尤其多），不清掉会误判结构。
     */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inDollarQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (inDollarQuote) {
                if (c == '$' && i + 1 < sql.length() && sql.charAt(i + 1) == '$') {
                    inDollarQuote = false;
                    out.append("$$");
                    i++;
                } else {
                    out.append(c);
                }
                continue;
            }

            if (inSingleQuote) {
                out.append(c);
                if (c == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (c == '\'') {
                inSingleQuote = true;
                out.append(c);
                continue;
            }

            if (c == '$' && i + 1 < sql.length() && sql.charAt(i + 1) == '$') {
                inDollarQuote = true;
                out.append("$$");
                i++;
                continue;
            }

            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') {
                    i++;
                }
                out.append('\n');
                continue;
            }

            out.append(c);
        }
        return out.toString();
    }
}
