package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 任务底座里不依赖数据库的三块：状态迁移表、租约边界、参数指纹。 */
class TaskDomainTests {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 27, 8, 0, 0, 0, ZoneOffset.UTC);

    // ------------------------------------------------------------- 状态机

    /** 与迁移 V1 的 {@code guard_task_transition()} 逐条对齐；改一处必须同时改两处。 */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = Map.of(
            TaskStatus.PENDING, EnumSet.of(TaskStatus.QUEUED, TaskStatus.FAILED, TaskStatus.CANCELLED),
            TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED),
            TaskStatus.RUNNING, EnumSet.of(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.QUEUED),
            TaskStatus.FAILED, EnumSet.of(TaskStatus.QUEUED),
            TaskStatus.SUCCESS, EnumSet.noneOf(TaskStatus.class),
            TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));

    @Test
    void transitionTableMatchesTheDatabaseTriggerForEveryPair() {
        for (TaskStatus from : TaskStatus.values()) {
            for (TaskStatus to : TaskStatus.values()) {
                boolean expected = from == to || ALLOWED.get(from).contains(to);
                assertEquals(expected, TaskStateMachine.allows(from, to),
                        "迁移 " + from + " -> " + to);
            }
        }
    }

    @Test
    void requireAcceptsLegalTransitionAndRejectsIllegalOne() {
        assertDoesNotThrow(() -> TaskStateMachine.require(TaskStatus.PENDING, TaskStatus.QUEUED));

        ApiException failure = assertThrows(ApiException.class,
                () -> TaskStateMachine.require(TaskStatus.SUCCESS, TaskStatus.QUEUED));
        assertEquals(ErrorCode.TASK_CONFLICT, failure.code());
    }

    @Test
    void stateMachineRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> TaskStateMachine.allows(null, TaskStatus.QUEUED));
        assertThrows(IllegalArgumentException.class, () -> TaskStateMachine.allows(TaskStatus.PENDING, null));
    }

    @Test
    void terminalStatesAreTerminal() {
        assertTrue(TaskStatus.SUCCESS.isTerminal());
        assertTrue(TaskStatus.FAILED.isTerminal());
        assertTrue(TaskStatus.CANCELLED.isTerminal());
        assertFalse(TaskStatus.PENDING.isTerminal());
        assertFalse(TaskStatus.RUNNING.isTerminal());
        assertTrue(TaskStatus.FAILED.isRetryable());
        assertFalse(TaskStatus.SUCCESS.isRetryable());
    }

    // ------------------------------------------------------------- 租约

    @Test
    void deadlineIsNowPlusLease() {
        assertEquals(NOW.plusMinutes(2), TaskLease.deadline(NOW, Duration.ofMinutes(2)));
        assertThrows(IllegalArgumentException.class, () -> TaskLease.deadline(null, Duration.ofMinutes(2)));
        assertThrows(IllegalArgumentException.class, () -> TaskLease.deadline(NOW, null));
    }

    @Test
    void leaseIsExpiredAtTheExactBoundary() {
        assertTrue(TaskLease.isExpired(NOW, NOW), "到期时刻即视为过期，避免 0 秒窗口");
        assertTrue(TaskLease.isExpired(NOW.minusSeconds(1), NOW));
        assertFalse(TaskLease.isExpired(NOW.plusSeconds(1), NOW));
        assertTrue(TaskLease.isExpired(null, NOW), "没有租约等于没人持有，必须可被收回");
    }

    @Test
    void onlyRunningTasksWithExpiredLeasesNeedReaping() {
        assertTrue(TaskLease.needsReaping(TaskStatus.RUNNING, NOW.minusSeconds(1), NOW));
        assertFalse(TaskLease.needsReaping(TaskStatus.RUNNING, NOW.plusSeconds(1), NOW));
        assertFalse(TaskLease.needsReaping(TaskStatus.QUEUED, NOW.minusSeconds(1), NOW));
        assertFalse(TaskLease.needsReaping(TaskStatus.SUCCESS, NOW.minusSeconds(1), NOW));
    }

    // ------------------------------------------------------------- 参数指纹

    @Test
    void canonicalJsonSortsKeysSoInsertionOrderDoesNotMatter() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertEquals(TaskParameters.canonicalJson(first), TaskParameters.canonicalJson(second));
        assertEquals("{\"a\":1,\"b\":2}", TaskParameters.canonicalJson(first));
    }

    @Test
    void hashCoversSchemaVersionAndIsSixtyFourLowercaseHex() {
        Map<String, Object> parameters = Map.of("format", "csv");
        String v1 = TaskParameters.hash("export.v1", parameters);
        String v2 = TaskParameters.hash("export.v2", parameters);

        assertEquals(64, v1.length());
        assertEquals(v1, v1.toLowerCase());
        assertTrue(v1.matches("[0-9a-f]{64}"));
        assertFalse(v1.equals(v2), "参数版本参与指纹，版本升级不能被当成同一请求");
        assertEquals(v1, TaskParameters.hash("export.v1", new LinkedHashMap<>(parameters)),
                "同一参数不同 Map 实现必须得到同一指纹");
    }

    @Test
    void hashAllowsNullParameterValues() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("note", null);
        assertDoesNotThrow(() -> TaskParameters.hash("export.v1", parameters));
    }

    @Test
    void commandValidatesIdentityAndPriority() {
        Map<String, Object> parameters = Map.of("format", "csv");
        assertThrows(IllegalArgumentException.class, () -> new TaskCommand(null, TaskType.EXPORT, "n",
                "v1", parameters, "k", "t", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TaskCommand(UUID.randomUUID(), TaskType.EXPORT, "n",
                "v1", parameters, "k", "t", 11, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TaskCommand(UUID.randomUUID(), TaskType.EXPORT,
                "n", "v1", parameters, "k", "t", null, "EXPORT_JOB", null));
    }
}
