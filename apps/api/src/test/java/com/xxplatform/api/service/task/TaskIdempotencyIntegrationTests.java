package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * M15 + M16：任务取消/重试的请求级幂等账本，以及"并发创建同键任务"。
 *
 * <p><b>为什么 M15 必须走 HTTP</b>：契约规定的是"带 {@code Idempotency-Key} 的写接口"行为——
 * 控制器是否把键传下来、账本是否落库、重放是否返回**原来那份响应**，只有经过
 * {@code TaskController} 才能一次性证明。直接调 service 会绕过"控制器把键丢了"这类真实缺口。
 *
 * <p><b>为什么断言 SQL 行数与返回的任务 id</b>：只断言 200/202 无法区分"真的回放了"与
 * "又执行了一遍但结果碰巧相同"。取消一个排队任务第二次执行会抛 TASK_CONFLICT，所以能靠状态码
 * 区分；但重试第二次执行会让 {@code retry_count} 变成 2——必须查行。
 */
@EnabledIfEnvironmentVariable(named = "XX_TASK_IT_DB_URL", matches = ".+")
class TaskIdempotencyIntegrationTests extends TaskIntegrationTestSupport {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        boot();
    }

    // ---------------------------------------------------------------- M15: cancel

    @Test
    void cancelWithSameKeyReplaysOriginalResponseWithoutSecondSideEffect() throws Exception {
        UUID owner = seedOwner("idem-cancel");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("idem-cancel")));
        String cookie = issueSession(owner);
        String key = newKey("key-cancel");

        HttpResult first = post("/tasks/" + task.id() + "/cancel", key, cookie);
        assertEquals(202, first.status());
        assertEquals("CANCELLED", first.data().path("status").asText());

        int eventsAfterFirst = countEvents(task.id());
        HttpResult replay = post("/tasks/" + task.id() + "/cancel", key, cookie);
        assertEquals(202, replay.status());
        assertEquals(first.data().path("taskId").asText(), replay.data().path("taskId").asText());
        assertEquals(first.data().path("status").asText(), replay.data().path("status").asText());

        // 账本：一行、SUCCEEDED、存了 202 与原响应；任务只被取消一次（事件行不增加）。
        assertEquals(1, ledgerRows(owner, key));
        assertEquals("SUCCEEDED", ledgerStatus(owner, key));
        assertEquals(202, ledgerResponseStatus(owner, key));
        assertEquals(eventsAfterFirst, countEvents(task.id()));
        assertEquals("CANCELLED", taskStatus(task.id()));
    }

    @Test
    void sameKeyDifferentTaskConflictsWith409() throws Exception {
        UUID owner = seedOwner("idem-diff");
        TaskRow first = taskService.create(command(owner, Map.of(), newKey("idem-diff-a")));
        TaskRow second = taskService.create(command(owner, Map.of(), newKey("idem-diff-b")));
        String cookie = issueSession(owner);
        String key = newKey("key-diff");

        assertEquals(202, post("/tasks/" + first.id() + "/cancel", key, cookie).status());
        HttpResult conflict = post("/tasks/" + second.id() + "/cancel", key, cookie);

        assertEquals(409, conflict.status());
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.code());
        // 第二个任务根本没被取消：冲突请求不得执行副作用。
        assertEquals("PENDING", taskStatus(second.id()));
        assertEquals(1, ledgerRows(owner, key));
    }

    @Test
    void idempotencyScopeIsActorAndOperation() throws Exception {
        UUID ownerA = seedOwner("idem-actor-a");
        UUID ownerB = seedOwner("idem-actor-b");
        TaskRow taskA = taskService.create(command(ownerA, Map.of(), newKey("idem-actor-a")));
        TaskRow taskB = taskService.create(command(ownerB, Map.of(), newKey("idem-actor-b")));
        String key = newKey("key-shared");

        // 同一个键、不同账号：各自独立，都能成功（作用域含账号）。
        assertEquals(202, post("/tasks/" + taskA.id() + "/cancel", key, issueSession(ownerA)).status());
        assertEquals(202, post("/tasks/" + taskB.id() + "/cancel", key, issueSession(ownerB)).status());
        assertEquals(1, ledgerRows(ownerA, key));
        assertEquals(1, ledgerRows(ownerB, key));
        assertEquals("CANCELLED", taskStatus(taskA.id()));
        assertEquals("CANCELLED", taskStatus(taskB.id()));
    }

    // ----------------------------------------------------------------- M15: retry

    @Test
    void retryWithSameKeyReplaysAndDoesNotRetryTwice() throws Exception {
        UUID owner = seedOwner("idem-retry");
        TaskRow task = taskService.create(command(owner, Map.of(), newKey("idem-retry")));
        claim(task.id(), "it-idem-worker");
        TaskRow running = reload(task.id());
        taskService.finish(task.id(), "it-idem-worker", running.leaseGeneration(),
                TaskStatus.FAILED, "BOOM", "模拟失败", null);
        String cookie = issueSession(owner);
        String key = newKey("key-retry");

        HttpResult first = post("/tasks/" + task.id() + "/retry", key, cookie);
        assertEquals(202, first.status());
        assertEquals("QUEUED", first.data().path("status").asText());
        assertEquals(1, first.data().path("retryCount").asInt());

        HttpResult replay = post("/tasks/" + task.id() + "/retry", key, cookie);
        assertEquals(202, replay.status());
        assertEquals(1, replay.data().path("retryCount").asInt());
        // 回放不得再执行一次：retry_count 仍是 1，事件行也不增加。
        assertEquals(1, reload(task.id()).retryCount());
        assertEquals(1, ledgerRows(owner, key));
        assertEquals("SUCCEEDED", ledgerStatus(owner, key));
        assertEquals(202, ledgerResponseStatus(owner, key));
    }

    @Test
    void unknownOutcomeCannotBeRetriedAndLeavesNoLedgerRow() throws Exception {
        UUID owner = seedOwner("idem-unknown");
        TaskRow task = taskService.create(command(owner, TaskType.DEVICE_COMMAND, Map.of(), newKey("idem-unknown")));
        claim(task.id(), "it-idem-unknown");
        expireLease(task.id());
        taskService.reapExpiredLeases(now());
        assertEquals(TaskService.OUTCOME_UNKNOWN, taskErrorCode(task.id()));

        String key = newKey("key-unknown");
        HttpResult refused = post("/tasks/" + task.id() + "/retry", key, issueSession(owner));

        assertEquals(409, refused.status());
        assertEquals("TASK_CONFLICT", refused.code());
        // 失败不落账本快照（类注释：失败只承诺"可重做"，不承诺回放失败）。
        assertEquals(0, ledgerRows(owner, key));
        assertEquals("FAILED", taskStatus(task.id()));
    }

    // ------------------------------------------------------------- M16: 并发创建

    @Test
    void concurrentCreateWithSameKeyYieldsOneTaskForEveryCaller() throws Exception {
        UUID owner = seedOwner("idem-concurrent");
        String key = newKey("idem-concurrent");

        List<TaskRow> created = runConcurrently(8,
                () -> taskService.create(command(owner, Map.of("format", "csv"), key)));

        UUID expected = created.get(0).id();
        for (TaskRow row : created) {
            assertEquals(expected, row.id(), "并发创建必须全部拿到同一个任务 id");
        }
        assertEquals(1, countTasksWithKey(key));

        // 同键不同内容仍按契约 409，而不是 500 或"事务中止后回读"。
        assertCode(ErrorCode.IDEMPOTENCY_CONFLICT,
                () -> taskService.create(command(owner, Map.of("format", "xlsx"), key)));
        assertEquals(1, countTasksWithKey(key));
    }

    @Test
    void concurrentCreateWithDifferentPayloadsHasExactlyOneWinner() throws Exception {
        UUID owner = seedOwner("idem-race");
        String key = newKey("idem-race");

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String format = i % 2 == 0 ? "csv" : "xlsx";
                calls.add(() -> {
                    try {
                        return taskService.create(command(owner, Map.of("format", format), key));
                    } catch (com.xxplatform.api.common.ApiException failure) {
                        return failure;
                    }
                });
            }
            List<Future<Object>> futures = pool.invokeAll(calls);
            java.util.Set<UUID> winners = new java.util.LinkedHashSet<>();
            int conflicts = 0;            for (Future<Object> future : futures) {
                Object result = future.get(30, TimeUnit.SECONDS);
                if (result instanceof TaskRow row) {
                    winners.add(row.id());
                } else {
                    com.xxplatform.api.common.ApiException failure =
                            (com.xxplatform.api.common.ApiException) result;
                    assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, failure.code());
                    conflicts++;
                }
            }
            assertEquals(1, winners.size(), "同键不同内容只能有一个赢家");
            assertEquals(4, conflicts, "另一半内容的并发请求必须全部冲突");
            // 唯一的赢家决定那份内容，冲突方没有写坏任何行。
            assertEquals(1, countTasksWithKey(key));
            String stored = jdbc.queryForObject(
                    "SELECT parameter_snapshot->>'format' FROM analysis.async_task WHERE idempotency_key = ?",
                    String.class, key);
            assertTrue(stored.equals("csv") || stored.equals("xlsx"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<T>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(task);
            }
            List<Future<T>> futures = pool.invokeAll(calls);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static int ledgerRows(UUID owner, String key) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM core.idempotency_request
                 WHERE user_id = ? AND idempotency_key = ?
                """, Integer.class, owner, key);
        return count == null ? 0 : count;
    }

    private static String ledgerStatus(UUID owner, String key) {
        return jdbc.queryForObject("""
                SELECT status FROM core.idempotency_request WHERE user_id = ? AND idempotency_key = ?
                """, String.class, owner, key);
    }

    private static Integer ledgerResponseStatus(UUID owner, String key) {
        return jdbc.queryForObject("""
                SELECT response_status FROM core.idempotency_request WHERE user_id = ? AND idempotency_key = ?
                """, Integer.class, owner, key);
    }

    /** 一次 HTTP 调用结果：状态码、壳内 code、{@code data} 节点。 */
    /**
     * {@code GET /auth/csrf}：写请求必须同时带 CSRFID Cookie 与同步器 token
     * （{@code CsrfFilter} 对所有 POST 统一拦截，缺一即 403 AUTH_CSRF_INVALID）。
     */
    private static Csrf csrf() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + httpPort + "/api/v1/auth/csrf"))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        String cookie = response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("CSRFID="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GET /auth/csrf 未下发 CSRFID Cookie"));
        JsonNode data = json.readTree(response.body()).path("data");
        return new Csrf(data.path("headerName").asText("X-CSRF-Token"), data.path("token").asText(), cookie);
    }

    private record Csrf(String headerName, String token, String cookie) {
    }

    private record HttpResult(int status, String code, JsonNode data) {
    }

    private static HttpResult post(String path, String idempotencyKey, String cookie) throws Exception {
        Csrf csrf = csrf();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + httpPort + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header(csrf.headerName(), csrf.token())
                .header("Cookie", csrf.cookie() + "; " + cookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (!body.startsWith("{")) {
            throw new IllegalStateException("POST " + path + " 返回非 JSON：" + response.statusCode()
                    + " " + (body.length() > 200 ? body.substring(0, 200) : body));
        }
        JsonNode jsonBody = json.readTree(body);
        return new HttpResult(response.statusCode(), jsonBody.path("code").asText(), jsonBody.path("data"));
    }
}
