package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.service.HealthView.DependencyStatus;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HealthService} 的判定单测（任务 8）。
 *
 * <p>覆盖的是"哪些依赖坏了会得出 NOT_READY、原因怎么写"这些分支。端到端只能验证一种真实状态
 * （库是好的、迁移是好的、配置行是好的），而"迁移失败""配置行缺失""连不上库"这些分支在
 * 真实环境里根本造不出来——所以必须用单测钉住。
 *
 * <p>不用 Mockito（沙箱禁止 JVM 自附加）：{@code ObjectProvider} 用真实的
 * {@link DefaultListableBeanFactory} 造（空工厂 → {@code getIfAvailable()} 返回 null，
 * 注册一个 bean → 返回它），{@link JdbcTemplate} 用子类覆写 {@code queryForObject} 当作桩。
 *
 * <p>最后一组断言是安全相关的：探针是 PUBLIC 端点，503 的 {@code details} 会原样返回给任何
 * 调用方，所以原因文案**不得**包含异常信息。
 */
class HealthServiceTests {

    private static ObjectProvider<JdbcTemplate> emptyProvider() {
        return new DefaultListableBeanFactory().getBeanProvider(JdbcTemplate.class);
    }

    private static ObjectProvider<JdbcTemplate> provider(JdbcTemplate jdbc) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("jdbcTemplate", jdbc);
        return factory.getBeanProvider(JdbcTemplate.class);
    }

    /**
     * 造一个只认得本服务那四条 SQL 的桩。
     *
     * @param failedMigrations {@code success = false} 的迁移数
     * @param appliedMigrations 迁移总行数
     * @param configRows      {@code client-config} 配置行数
     */
    private static JdbcTemplate fakeJdbc(Long failedMigrations, Long appliedMigrations, Long configRows) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                Object value;
                if (sql.startsWith("SELECT 1")) {
                    value = 1;
                } else if (sql.contains("success = false")) {
                    value = failedMigrations;
                } else if (sql.contains("core.config_item")) {
                    value = configRows;
                } else {
                    value = appliedMigrations;
                }
                return requiredType.cast(value);
            }
        };
    }

    private static JdbcTemplate failingJdbc() {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                throw new DataAccessResourceFailureException(
                        "FATAL: password authentication failed for user \"xxplatform_owner\"");
            }
        };
    }

    private static List<String> names(List<DependencyStatus> dependencies) {
        return dependencies.stream().map(DependencyStatus::name).collect(Collectors.toList());
    }

    private static List<String> statuses(List<DependencyStatus> dependencies) {
        return dependencies.stream().map(DependencyStatus::status).collect(Collectors.toList());
    }

    private static List<String> failedNames(HealthService.ReadinessResult result) {
        return result.failures().stream().map(FieldErrorDetail::field).collect(Collectors.toList());
    }

    @Test
    @DisplayName("存活检查永远是 UP，时间戳是契约格式")
    void healthIsAlwaysUp() {
        HealthView.HealthData data = new HealthService(emptyProvider()).health();

        assertEquals("UP", data.status());
        assertTrue(data.checkedAt().matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"),
                "checkedAt 必须是截到秒的 UTC RFC3339：" + data.checkedAt());
    }

    @Test
    @DisplayName("没有数据源时如实报 NOT_READY，三项都算未就绪")
    void readinessWithoutDataSourceIsNotReady() {
        HealthService.ReadinessResult result = new HealthService(emptyProvider()).readiness();

        assertFalse(result.ready());
        assertEquals("NOT_READY", result.view().status());
        assertEquals(List.of("database", "schema", "deployment-config"), names(result.view().dependencies()));
        assertEquals(List.of("NOT_READY", "NOT_READY", "NOT_READY"), statuses(result.view().dependencies()));
        assertEquals(List.of("database", "schema", "deployment-config"), failedNames(result));
        assertTrue(result.failures().get(0).reason().contains("未配置数据源"));
        assertTrue(result.view().checkedAt().matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"));
    }

    @Test
    @DisplayName("三项依赖都正常时 READY 且没有失败原因")
    void readinessWhenEverythingIsHealthy() {
        HealthService.ReadinessResult result =
                new HealthService(provider(fakeJdbc(0L, 2L, 1L))).readiness();

        assertTrue(result.ready());
        assertEquals("READY", result.view().status());
        assertEquals(List.of("READY", "READY", "READY"), statuses(result.view().dependencies()));
        assertTrue(result.failures().isEmpty());
    }

    @Test
    @DisplayName("存在失败的迁移 → 只有 schema 未就绪")
    void readinessWithFailedMigration() {
        HealthService.ReadinessResult result =
                new HealthService(provider(fakeJdbc(1L, 3L, 1L))).readiness();

        assertFalse(result.ready());
        assertEquals(List.of("READY", "NOT_READY", "READY"), statuses(result.view().dependencies()));
        assertEquals(List.of("schema"), failedNames(result));
        assertEquals("存在失败的迁移", result.failures().get(0).reason());
    }

    @Test
    @DisplayName("迁移表为空（没跑过迁移）也算未就绪")
    void readinessWithEmptyMigrationHistory() {
        HealthService.ReadinessResult result =
                new HealthService(provider(fakeJdbc(0L, 0L, 1L))).readiness();

        assertFalse(result.ready());
        assertEquals(List.of("schema"), failedNames(result));
        assertEquals("数据库结构未迁移", result.failures().get(0).reason());
    }

    @Test
    @DisplayName("缺 client-config 配置行 → 未就绪（否则 /client-config 必然 500）")
    void readinessWithMissingDeploymentConfig() {
        HealthService.ReadinessResult result =
                new HealthService(provider(fakeJdbc(0L, 2L, 0L))).readiness();

        assertFalse(result.ready());
        assertEquals(List.of("deployment-config"), failedNames(result));
        assertEquals("缺少 client-config 配置行", result.failures().get(0).reason());
    }

    @Test
    @DisplayName("数据库报错时用固定文案，不把异常信息泄露给 PUBLIC 调用方")
    void readinessDoesNotLeakDatabaseErrors() {
        HealthService.ReadinessResult result = new HealthService(provider(failingJdbc())).readiness();

        assertFalse(result.ready());
        // 库连不上时三项都读不出来，所以每一项各自给固定原因——这正是"如实反映"：
        // 只报 database 一项会让运维以为 schema/config 是好的。
        assertEquals(List.of("database", "schema", "deployment-config"), failedNames(result));
        assertEquals("数据库连接异常", result.failures().get(0).reason());
        assertEquals("迁移状态无法读取", result.failures().get(1).reason());
        assertEquals("部署配置无法读取", result.failures().get(2).reason());

        for (FieldErrorDetail detail : result.failures()) {
            assertFalse(detail.reason().contains("password"), "原因里不能出现凭据：" + detail.reason());
            assertFalse(detail.reason().contains("xxplatform_owner"), "原因里不能出现库内账号：" + detail.reason());
            assertFalse(detail.reason().contains("FATAL"), "原因里不能出现驱动原文：" + detail.reason());
        }
    }
}
