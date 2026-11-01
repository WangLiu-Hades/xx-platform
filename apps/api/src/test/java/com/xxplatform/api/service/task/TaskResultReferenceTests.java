package com.xxplatform.api.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.xxplatform.api.infra.AsyncTaskRepository.ClaimedTask;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 结果引用的类型化验证（M08）。
 *
 * <p>两处都要验：服务层发布后的视图（{@link TaskViewMapper}）与真实 HTTP 响应。只验 mapper 会漏掉
 * "controller 丢字段/序列化改形状"，只验 HTTP 则很难覆盖每种类型（需要造全部领域行）。
 */
class TaskResultReferenceTests extends TaskIntegrationTestSupport {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        boot();
    }

    @Test
    @DisplayName("每种任务类型发布的结果引用形状符合契约")
    void everyTaskTypePublishesTheContractShape() {
        UUID owner = seedOwner("ref-shape");

        UUID exportTask = create(owner, TaskType.EXPORT, "ref-export");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        seedExportJob(exportTask, asset);
        Map<String, Object> exportRef = finishAndReference(exportTask, owner, asset);
        assertEquals("EXPORT", exportRef.get("resultType"));
        assertEquals(asset.toString(), exportRef.get("fileAssetId"),
                "EXPORT 的结果引用必须是 fileAssetId");

        UUID context = seedContext(owner, 1, now().plusHours(1));
        UUID analysisTask = create(owner, TaskType.ANALYSIS, "ref-analysis");
        UUID analysisJob = seedAnalysisJob(owner, analysisTask, context);
        Map<String, Object> analysisRef = finishAndReference(analysisTask, owner, null);
        assertEquals("ANALYSIS", analysisRef.get("resultType"));
        assertEquals(analysisJob.toString(), analysisRef.get("resultId"),
                "ANALYSIS 的 resultId 是 analysis_job.id，不是 taskId");

        UUID setTask = create(owner, TaskType.SET_OPERATION, "ref-set");
        UUID setJob = seedSetJob(setTask);
        assertEquals(setJob.toString(), finishAndReference(setTask, owner, null).get("resultId"),
                "SET_OPERATION 的 resultId 是 set_job.id");

        UUID reportTask = create(owner, TaskType.REPORT, "ref-report");
        UUID reportJob = seedReportJob(owner, reportTask);
        assertEquals(reportJob.toString(), finishAndReference(reportTask, owner, null).get("resultId"),
                "REPORT 的 resultId 是 report_job.id");

        UUID modelTask = create(owner, TaskType.MODEL_RUN, "ref-model");
        UUID modelId = seedModelRun(owner, modelTask);
        Map<String, Object> modelRef = finishAndReference(modelTask, owner, null);
        assertEquals(modelTask.toString(), modelRef.get("resultId"), "MODEL_RUN 的 resultId 是任务自身 id");
        assertEquals(modelId.toString(), modelRef.get("modelId"), "MODEL_RUN 必须返回 modelId");

        UUID ingestionTask = create(owner, TaskType.INGESTION, "ref-ingestion");
        UUID batchId = seedIngestionBatch(ingestionTask);
        assertEquals(batchId.toString(), finishAndReference(ingestionTask, owner, null).get("resultId"),
                "INGESTION 的 resultId 是 batchId");

        UUID faceTask = create(owner, TaskType.FACE_SEARCH, "ref-face");
        seedFaceSearchJob(owner, faceTask);
        assertEquals(faceTask.toString(), finishAndReference(faceTask, owner, null).get("resultId"),
                "FACE_SEARCH 的 resultId 是 taskId");

        UUID deviceTask = create(owner, TaskType.DEVICE_COMMAND, "ref-device");
        assertEquals(deviceTask.toString(), finishAndReference(deviceTask, owner, null).get("resultId"));

        UUID notificationTask = create(owner, TaskType.NOTIFICATION, "ref-notification");
        assertEquals(notificationTask.toString(),
                finishAndReference(notificationTask, owner, null).get("resultId"));

        UUID mergeTask = create(owner, TaskType.PROFILE_MERGE, "ref-merge");
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        seedProfileRevision(owner, mergeTask, List.of(p1, p2), "MERGE");
        Map<String, Object> mergeRef = finishAndReference(mergeTask, owner, null);
        @SuppressWarnings("unchecked")
        List<String> profileIds = (List<String>) mergeRef.get("profileIds");
        assertNotNull(profileIds, "PROFILE_MERGE 必须返回 profileIds");
        assertTrue(profileIds.containsAll(List.of(p1.toString(), p2.toString())));
        assertEquals(2, profileIds.size(), "profileIds 去重后应是两条");
    }

    @Test
    @DisplayName("真实 HTTP 响应里的 resultReference 与服务层一致")
    void httpResponseExposesTheTypedReference() throws Exception {
        UUID owner = seedOwner("ref-http");
        String cookie = issueSession(owner);

        UUID exportTask = create(owner, TaskType.EXPORT, "ref-http-export");
        UUID asset = seedFileAsset(owner, "ACTIVE");
        seedExportJob(exportTask, asset);
        finishAndReference(exportTask, owner, asset);

        JsonNode body = get("/api/v1/tasks/" + exportTask, cookie);
        assertEquals("EXPORT", body.path("data").path("resultReference").path("resultType").asText());
        assertEquals(asset.toString(), body.path("data").path("resultReference").path("fileAssetId").asText(),
                "HTTP 响应必须带 fileAssetId，而不是只给 resultId");

        UUID modelTask = create(owner, TaskType.MODEL_RUN, "ref-http-model");
        UUID modelId = seedModelRun(owner, modelTask);
        finishAndReference(modelTask, owner, null);
        JsonNode modelBody = get("/api/v1/tasks/" + modelTask, cookie);
        assertEquals(modelId.toString(),
                modelBody.path("data").path("resultReference").path("modelId").asText());
    }

    @Test
    @DisplayName("领域行缺失/类型不符时不能发布 SUCCESS，也不留结果引用")
    void missingDomainRowBlocksPublish() {
        UUID owner = seedOwner("ref-invalid");

        UUID ingestion = create(owner, TaskType.INGESTION, "ref-invalid-ingestion");
        TaskRow failed = finish(ingestion, owner, null);
        assertEquals(TaskStatus.FAILED, failed.status(), "没有 batch 的 INGESTION 不能发布 SUCCESS");
        assertEquals("TASK_RESULT_REFERENCE_INVALID", failed.errorCode());
        assertFalse(mapper.view(reload(ingestion)).containsKey("resultReference"),
                "未成功任务不得返回 resultReference");

        UUID model = create(owner, TaskType.MODEL_RUN, "ref-invalid-model");
        assertEquals(TaskStatus.FAILED, finish(model, owner, null).status(),
                "没有 model_execution 的 MODEL_RUN 不能发布 SUCCESS");

        UUID report = create(owner, TaskType.REPORT, "ref-invalid-report");
        assertEquals(TaskStatus.FAILED, finish(report, owner, null).status(),
                "没有 report_job 的 REPORT 不能发布 SUCCESS");

        UUID export = create(owner, TaskType.EXPORT, "ref-invalid-export");
        assertEquals(TaskStatus.FAILED, finish(export, owner, null).status(),
                "没有输出文件的 EXPORT 不能发布 SUCCESS");

        UUID face = create(owner, TaskType.FACE_SEARCH, "ref-invalid-face");
        assertEquals(TaskStatus.FAILED, finish(face, owner, null).status(),
                "没有 face_search_job 的 FACE_SEARCH 不能发布 SUCCESS");
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> finishAndReference(UUID taskId, UUID owner, UUID resultAsset) {
        TaskRow done = finish(taskId, owner, resultAsset);
        assertEquals(TaskStatus.SUCCESS, done.status());
        Object reference = mapper.view(reload(taskId)).get("resultReference");
        assertNotNull(reference, "SUCCESS 任务必须带 resultReference");
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) reference;
        return typed;
    }

    private TaskRow finish(UUID taskId, UUID owner, UUID resultAsset) {
        ClaimedTask claimed = claim(taskId, "it-worker-ref");
        return taskService.finish(taskId, "it-worker-ref", claimed.task().leaseGeneration(),
                TaskStatus.SUCCESS, null, null, resultAsset);
    }

    private UUID create(UUID owner, TaskType type, String key) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        return taskService.create(command(owner, type, parameters, newKey(key))).id();
    }

    private JsonNode get(String path, String cookie) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
                .header("Cookie", cookie)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "GET " + path + " 失败: " + response.body());
        return json.readTree(response.body());
    }
}
