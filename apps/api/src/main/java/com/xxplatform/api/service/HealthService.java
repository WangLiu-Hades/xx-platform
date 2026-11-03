package com.xxplatform.api.service;

import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.service.HealthView.DependencyStatus;
import com.xxplatform.api.service.HealthView.HealthData;
import com.xxplatform.api.service.HealthView.ReadinessData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 存活/就绪探针的判定逻辑（M15，契约 {@code /health} 与 {@code /ready}）。
 *
 * <h2>为什么 /health 不查数据库</h2>
 * 存活探针回答的问题是"这个进程还能不能接请求"。它只做一件事：能回包就是 UP。
 * 如果把数据库故障反映成存活失败，容器编排会**不断重启进程**——而重启一台应用服务器
 * 永远修不好一台挂掉的数据库，只会把一次可观测的依赖故障放大成全量重启风暴。
 * 依赖健康属于就绪探针（{@code /ready}）的职责，不是存活探针的。
 *
 * <p>因此契约 {@code HealthData.status} 里的 {@code DOWN} 本实现**永不产出**：
 * 进程如果真的不可用，它连这个响应都发不出来。这不是"没实现枚举值"，而是存活探针
 * 的定义决定的（见 README「为什么 /health 永不返回 DOWN」）。
 *
 * <h2>就绪依赖</h2>
 * {@code /ready} 检查三个依赖，顺序固定：
 * <ol>
 *   <li>{@code database} —— 能连上并执行 {@code SELECT 1}；</li>
 *   <li>{@code schema} —— {@code flyway_schema_history} 存在、有成功应用的迁移、且没有失败记录；</li>
 *   <li>{@code deployment-config} —— {@code core.config_item} 里存在启用且可公开读的
 *       {@code client-config} 行。没有它 {@code /client-config} 必然 500，所以它确实是
 *       "能否对外服务"的一部分，而不是可选装饰。</li>
 * </ol>
 *
 * <h2>为什么用 ObjectProvider 而不是直接注入 JdbcTemplate</h2>
 * 默认 profile 不配数据源（见 {@code application-db.yml}）：没有 PostgreSQL 的机器也要能
 * 启动应用、跑探针 {@code /health}、{@code /ready}。若强制依赖 {@code JdbcTemplate}，那些机器会在启动时
 * 直接报"缺 bean"。这里按需取，取不到就如实报告 NOT_READY —— 一个没有数据库的实例
 * **本来就不该被认为就绪**，这不是缺陷，是如实反映。
 *
 * <h2>为什么 reason 是固定文案，不带异常信息</h2>
 * 两个探针都是 PUBLIC（无会话、无权限），503 的 {@code details} 会原样返回给任何调用方。
 * 把 {@code SQLException} 的 message 塞进去等于对外公开数据库地址、库名甚至驱动细节。
 * 所以对外只给"哪一项没就绪 + 固定原因"，真实异常进服务端日志（带 traceId）。
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    /** 契约 {@code HealthData.status} 的枚举值。 */
    public static final String STATUS_UP = "UP";
    /** 契约枚举里的"不健康"。本实现永不产出，理由见类注释。 */
    public static final String STATUS_DOWN = "DOWN";
    /** 契约 {@code ReadinessData.status} 的枚举值。 */
    public static final String STATUS_READY = "READY";
    /** 契约 {@code ReadinessData.status} 的枚举值。 */
    public static final String STATUS_NOT_READY = "NOT_READY";

    /** 依赖名。契约未枚举 {@code name}，这里用稳定短名便于探针与看板对齐。 */
    public static final String DEPENDENCY_DATABASE = "database";
    public static final String DEPENDENCY_SCHEMA = "schema";
    public static final String DEPENDENCY_DEPLOYMENT_CONFIG = "deployment-config";

    /**
     * 未就绪时给出的 {@code Retry-After}（秒）。
     *
     * <p>契约把 503 定义为带 {@code Retry-After} 的响应，且该头 {@code minimum: 1}。
     * 取 5 秒：比常见探针间隔略长，既不会让探针空转，也不至于在依赖刚恢复时形成重试尖峰。
     */
    public static final Duration RETRY_AFTER = Duration.ofSeconds(5);

    private static final String SQL_PING = "SELECT 1";
    private static final String SQL_FAILED_MIGRATIONS =
            "SELECT count(*) FROM flyway_schema_history WHERE success = false";
    private static final String SQL_MIGRATION_COUNT = "SELECT count(*) FROM flyway_schema_history";
    private static final String SQL_CLIENT_CONFIG_ROWS = "SELECT count(*) FROM core.config_item"
            + " WHERE config_key = 'client-config' AND enabled = true AND public_readable = true";

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;

    public HealthService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
    }

    /** 存活检查：只要能执行到这里，进程就是 UP。 */
    public HealthData health() {
        return new HealthData(STATUS_UP, Instants.iso(Instants.nowUtc()));
    }

    /**
     * 就绪检查。
     *
     * <p>返回结果而不是直接抛异常：判定属于本层，**响应状态码的选择属于 api 层**
     * （{@code SystemController} 决定 200 还是 503）。这样单测可以不依赖 Spring MVC 就验证判定，
     * 也避免"service 里写 HTTP 状态码"这种分层倒挂。
     */
    public ReadinessResult readiness() {
        JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
        List<DependencyStatus> dependencies = new ArrayList<>(3);
        List<FieldErrorDetail> failures = new ArrayList<>(3);

        dependencies.add(check(DEPENDENCY_DATABASE, () -> databaseFailure(jdbc), "数据库连接异常", failures));
        dependencies.add(check(DEPENDENCY_SCHEMA, () -> schemaFailure(jdbc), "迁移状态无法读取", failures));
        dependencies.add(check(DEPENDENCY_DEPLOYMENT_CONFIG, () -> deploymentConfigFailure(jdbc),
                "部署配置无法读取", failures));

        String status = failures.isEmpty() ? STATUS_READY : STATUS_NOT_READY;
        return new ReadinessResult(
                new ReadinessData(status, Instants.iso(Instants.nowUtc()), List.copyOf(dependencies)),
                List.copyOf(failures));
    }

    /**
     * 执行一项检查。
     *
     * @param name           依赖名
     * @param probe          返回 {@code Optional.empty()} 表示就绪，否则返回**固定原因文案**
     * @param failureOnError 探针自身抛异常时的兜底原因（同样不得含异常细节）
     * @param failures       未就绪原因收集器，供 api 层放进 503 的 {@code details}
     */
    private DependencyStatus check(String name, Supplier<Optional<String>> probe, String failureOnError,
            List<FieldErrorDetail> failures) {
        Optional<String> failure;
        try {
            failure = probe.get();
        } catch (RuntimeException ex) {
            log.warn("就绪检查项 {} 执行失败", name, ex);
            failure = Optional.of(failureOnError);
        }
        if (failure.isPresent()) {
            failures.add(new FieldErrorDetail(name, failure.get()));
            return new DependencyStatus(name, STATUS_NOT_READY);
        }
        return new DependencyStatus(name, STATUS_READY);
    }

    private static Optional<String> databaseFailure(JdbcTemplate jdbc) {
        if (jdbc == null) {
            return Optional.of("未配置数据源");
        }
        Integer one = jdbc.queryForObject(SQL_PING, Integer.class);
        if (one == null || one != 1) {
            return Optional.of("连接检查未通过");
        }
        return Optional.empty();
    }

    private static Optional<String> schemaFailure(JdbcTemplate jdbc) {
        if (jdbc == null) {
            return Optional.of("未配置数据源");
        }
        Long failed = jdbc.queryForObject(SQL_FAILED_MIGRATIONS, Long.class);
        if (failed != null && failed > 0) {
            return Optional.of("存在失败的迁移");
        }
        Long applied = jdbc.queryForObject(SQL_MIGRATION_COUNT, Long.class);
        if (applied == null || applied == 0) {
            return Optional.of("数据库结构未迁移");
        }
        return Optional.empty();
    }

    private static Optional<String> deploymentConfigFailure(JdbcTemplate jdbc) {
        if (jdbc == null) {
            return Optional.of("未配置数据源");
        }
        Long rows = jdbc.queryForObject(SQL_CLIENT_CONFIG_ROWS, Long.class);
        if (rows == null || rows == 0) {
            // config_key 是主键，所以只可能是 0 行；缺这一行 /client-config 必然 500。
            return Optional.of("缺少 client-config 配置行");
        }
        return Optional.empty();
    }

    /**
     * 就绪判定结果。
     *
     * @param view     {@code /ready} 成功时的 {@code data}（含各依赖状态）
     * @param failures 未就绪原因，为空表示全部就绪；api 层把它放进 503 的 {@code details}
     */
    public record ReadinessResult(ReadinessData view, List<FieldErrorDetail> failures) {

        /** 是否全部依赖就绪。 */
        public boolean ready() {
            return failures.isEmpty();
        }
    }
}
