package com.xxplatform.api.service.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 把 {@link TaskRow} 映射成契约 {@code Task} 的响应形状（{@code GET /tasks}、{@code GET /tasks/{taskId}}）。
 *
 * <p><b>为什么 {@code parameterSnapshot} 只给四个字段</b>：契约的
 * {@code TaskParameterSnapshot} 是 {@code additionalProperties:false} 的四字段对象
 * （{@code schemaVersion}/{@code contextId}/{@code submittedAt}/{@code parameterHash}），
 * 注释原文是"敏感参数详情由领域任务端点返回，不在通用任务列表泄露"。库里的
 * {@code parameter_snapshot} 是完整冻结参数（含筛选条件、导出列、预算），直接原样返回等于
 * 把别人的检索范围与导出意图摊在列表接口上。这里刻意重建一个"摘要"对象，而不是裁剪原 JSON——
 * 后者一旦以后新增字段就会悄悄泄露。
 *
 * <p><b>为什么 {@code errorCode} 不直接回传库里的值</b>：任务行的 {@code error_code} 是运行期
 * 排障码（如 {@code TASK_LEASE_EXPIRED}、{@code TASK_ATTEMPTS_EXHAUSTED}），而契约的
 * {@code Task.errorCode} 引用的是 {@code ErrorCode} 枚举。两者不是同一套码表，硬回传会让
 * 响应不符合 Schema；统一折成契约里确实存在的 {@code TASK_FAILED}，具体原因留在
 * {@code errorMessage}。
 *
 * <p><b>为什么 {@code resourceType} 有时是推导出来的</b>：任务底座创建时不允许"只给类型不给 id"，
 * 导出的领域资源就是 {@code task.id} 本身（{@code export_job.task_id}），所以行上
 * {@code resource_type} 为空。对外仍然返回 {@code EXPORT}，与 {@code POST /exports} 的
 * {@code TaskCreated.resourceType} 保持一致；{@code resourceId} 继续省略。
 */
@Component
public class TaskViewMapper {

    private final ObjectMapper objectMapper;

    public TaskViewMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 单个任务的契约形状；可选字段为 {@code null} 时省略（契约允许省略，但不允许显式 null）。 */
    public Map<String, Object> view(TaskRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("taskId", row.id().toString());
        view.put("taskType", row.taskType().name());
        view.put("status", row.status().name());
        view.put("progress", row.progress());
        view.put("ownerUserId", row.ownerUserId().toString());
        view.put("createdAt", Instants.iso(row.createdAt()));
        putIfPresent(view, "startedAt", row.startedAt());
        putIfPresent(view, "finishedAt", row.finishedAt());
        putIfPresent(view, "cancelRequestedAt", row.cancelRequestedAt());
        view.put("retryCount", row.retryCount());
        view.put("parameterSnapshot", parameterSnapshot(row));
        putIfPresent(view, "resultReference", resultReference(row));
        if (row.status() == TaskStatus.FAILED) {
            view.put("errorCode", "TASK_FAILED");
            putIfPresent(view, "errorMessage", row.errorMessage());
        }
        view.put("traceId", row.traceId());
        view.put("name", row.name());
        putIfPresent(view, "resourceType", resourceType(row));
        putIfPresent(view, "resourceId", row.resourceId());
        return view;
    }

    /** 一页任务；{@code total} 是同一筛选条件下的总数，不是本页条数。 */
    public TaskPageView page(List<TaskRow> rows, int page, int pageSize, long total) {
        List<Map<String, Object>> items = rows.stream().map(this::view).toList();
        return new TaskPageView(items, page, pageSize, total);
    }

    private Map<String, Object> parameterSnapshot(TaskRow row) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", row.parameterSchemaVersion());
        String contextId = contextIdOf(row.parameterSnapshot());
        if (contextId != null) {
            snapshot.put("contextId", contextId);
        }
        snapshot.put("submittedAt", Instants.iso(row.submittedAt()));
        snapshot.put("parameterHash", row.parameterHash());
        return snapshot;
    }

    /** 从冻结参数里只取 {@code contextId}；解析失败就当作没有，不因为历史的坏行让列表 500。 */
    private String contextIdOf(String parameterSnapshot) {
        if (parameterSnapshot == null || parameterSnapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(parameterSnapshot);
            JsonNode contextId = node.path("contextId");
            return contextId.isTextual() && !contextId.asText().isBlank() ? contextId.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> resultReference(TaskRow row) {
        if (row.resultAssetId() == null) {
            return null;
        }
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("resultType", row.taskType().name());
        reference.put("fileAssetId", row.resultAssetId().toString());
        return reference;
    }

    /**
     * 契约 {@code Task.resourceType}：行上存了就用存储值，否则按任务类型推导。
     *
     * <p>{@code FACE_SEARCH} 在契约的 {@code resourceType} 枚举里没有对应值（只有
     * {@code TaskResultReference.resultType} 才有），所以返回 {@code null} 让它被省略——
     * 编一个 {@code ANALYSIS_TASK} 出来只会让前端导航到错误的对象。
     */
    private static String resourceType(TaskRow row) {
        if (row.resourceType() != null) {
            return row.resourceType();
        }
        return switch (row.taskType()) {
            case INGESTION, BACKFILL -> "INGESTION_BATCH";
            case EXPORT -> "EXPORT";
            case FACE_SEARCH -> null;
            case ANALYSIS -> "ANALYSIS_TASK";
            case DATASET_IMPORT -> "DATASET";
            case SET_OPERATION -> "SET_TASK";
            case PROFILE_MERGE, PROFILE_SPLIT -> "PROFILE";
            case MODEL_RUN -> "MODEL_RUN";
            case DEVICE_COMMAND -> "DEVICE_COMMAND";
            case REPORT -> "REPORT";
            case NOTIFICATION -> "NOTIFICATION_TASK";
            case PERSON_LIBRARY_IMPORT -> "PERSON_LIBRARY";
            case DEVICE_IMPORT -> "DEVICE";
        };
    }

    private static void putIfPresent(Map<String, Object> view, String key, Object value) {
        if (value == null) {
            return;
        }
        view.put(key, value instanceof java.time.OffsetDateTime time ? Instants.iso(time) : value);
    }
}
