package com.xxplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.xxplatform.api.ApiApplication;
import com.xxplatform.api.service.AuditService.Actor;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M20 的真库验证：失败路径的审计必须活过业务事务回滚。
 *
 * <p>为什么必须真库：审计写入走 {@code PROPAGATION_NESTED}（SAVEPOINT），"回滚是否把 SAVEPOINT
 * 里那行一起撤销"只有真 PostgreSQL 说得清；单测用的内存替身没有事务语义，测了也不算数。
 *
 * <p>覆盖三种结果：业务事务回滚（审计靠补偿补写）、业务事务提交（只有一行，不重复）、
 * 没有外层事务（直接写一条）。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class AuditRollbackIntegrationTests {

    private static ConfigurableApplicationContext context;
    private static AuditService audit;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void startContext() {
        String url = System.getenv("XX_TASK_IT_DB_URL");
        String user = System.getenv().getOrDefault("XX_TASK_IT_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("XX_TASK_IT_DB_PASSWORD", "");
        context = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("db")
                .run("--server.port=0",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + password,
                        "--xx.task.reaper-enabled=false");
        audit = context.getBean(AuditService.class);
        jdbc = context.getBean(JdbcTemplate.class);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void failureAuditSurvivesBusinessRollback() {
        String traceId = "it-audit-" + UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            audit.failure("auth.login", "AUTH", "user_account", null,
                    Actor.ofLoginName("intruder"), "203.0.113.9", null, traceId,
                    "AUTH_INVALID_CREDENTIALS", Map.of("reason", "bad password"));
            throw new IllegalStateException("登录失败后业务事务回滚");
        }));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT action, result, failure_code, actor_login_name_snapshot, host(source_ip) AS ip "
                        + "FROM audit.audit_event WHERE trace_id = ?", traceId);
        assertEquals("auth.login", row.get("action"));
        assertEquals("FAILURE", row.get("result"));
        assertEquals("AUTH_INVALID_CREDENTIALS", row.get("failure_code"));
        assertEquals("intruder", row.get("actor_login_name_snapshot"));
        assertEquals("203.0.113.9", row.get("ip"));
    }

    @Test
    void successAuditIsWrittenOnceOnCommit() {
        String traceId = "it-audit-" + UUID.randomUUID();

        transaction.executeWithoutResult(status -> audit.success("export.create", "DATA", "export_job",
                null, Actor.ofLoginName("it.auditor"), null, null, traceId, Map.of("rows", 10)));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE trace_id = ?", Integer.class, traceId);
        assertEquals(1, count, "提交的事务只应落一条审计，补偿不能重复插入");
    }

    @Test
    void auditWithoutOuterTransactionIsWritten() {
        String traceId = "it-audit-" + UUID.randomUUID();

        // 这条事务是 savepoint 版 NESTED 在无外层事务时的等价 REQUIRED：它自己就是一个事务。
        audit.success("file.upload", "DATA", "file_asset", null, Actor.unknown(), null, null,
                traceId, Map.of("bytes", 1));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM audit.audit_event WHERE trace_id = ?", Integer.class, traceId);
        assertEquals(1, count);
    }

    @Test
    void auditRowIsAppendOnly() {
        String traceId = "it-audit-" + UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit.audit_event
                  (id, actor_login_name_snapshot, actor_display_name_snapshot, action, action_category,
                   resource_type, occurred_at, result, trace_id, created_at)
                VALUES (?, '', '', 'auth.logout', 'AUTH', 'session', now(), 'SUCCESS', ?, now())
                """, id, traceId);

        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE audit.audit_event SET result = 'FAILURE' WHERE id = ?", id));
        assertThrows(Exception.class, () -> jdbc.update(
                "DELETE FROM audit.audit_event WHERE id = ?", id));
    }
}
