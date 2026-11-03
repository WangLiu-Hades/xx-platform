package com.xxplatform.api.service.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.infra.AnalysisContextRepository;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.AsyncTaskRepository;
import com.xxplatform.api.infra.AsyncTaskRepository.FileAssetState;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.RbacRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Live business permission, typed Context scope and published-result checks. Submission and publication share the same current authorization rules. */
@Component
public class TaskScopeGuard {

    private static final Logger log = LoggerFactory.getLogger(TaskScopeGuard.class);

    /** 任务写操作要求的权限码（与 {@code TaskWriteService} 同源，避免两处各写一个字符串）。 */
    public static final String PERMISSION = "task:manage";

    /** 账号被禁用/权限被收回：任务不能再发布 SUCCESS。 */
    public static final String PERMISSION_REVOKED = "TASK_PERMISSION_REVOKED";

    /** Context 冻结的授权版本落后于当前范围版本：范围已收缩。 */
    public static final String SCOPE_SHRUNK = "TASK_SCOPE_SHRUNK";

    /** 结果指针不合法：不存在、不属于当前用户、类型不符或文件已过期。 */
    public static final String RESULT_REFERENCE_INVALID = "TASK_RESULT_REFERENCE_INVALID";

    /** 提交时引用的 Context 不存在或不属于调用方（对外 404，不暴露他人 Context 的存在性）。 */
    public static final String CONTEXT_NOT_FOUND = "TASK_CONTEXT_NOT_FOUND";

    /** 提交时引用的 Context 已过 30 分钟 TTL（对外 410 CONTEXT_EXPIRED）。 */
    public static final String CONTEXT_EXPIRED = "TASK_CONTEXT_EXPIRED";

    private final JdbcTemplate jdbc;
    private final RbacRepository rbac;
    private final AnalysisContextRepository contexts;
    private final AsyncTaskRepository tasks;
    private final ObjectMapper objectMapper;
    private final com.xxplatform.api.service.CurrentDataScope liveScope;

    public TaskScopeGuard(JdbcTemplate jdbc, RbacRepository rbac, AnalysisContextRepository contexts,
                          AsyncTaskRepository tasks, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.rbac = rbac;
        this.contexts = contexts;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
        this.liveScope = new com.xxplatform.api.service.CurrentDataScope(jdbc, rbac);
    }

    /**
     * 提交前复核：账号可用、Context 未过期、数据范围未收缩。
     *
     * <p>为什么要收紧到"账号 ACTIVE"：禁用账号可能仍有历史角色行（授权版本要等下一次
     * 重算才更新），只看权限码会让禁用账号继续创建任务。
     *
     * @throws ApiException 403 {@code PERMISSION_DENIED}（账号不可用）/ 404 {@code DATA_NOT_FOUND}
     *                      （Context 不存在或不属于调用方）/ 410 {@code CONTEXT_EXPIRED}
     *                      （Context 已过期）/ 403 {@code PERMISSION_DATA_SCOPE}（范围已收缩）
     */
    public void requireSubmission(UUID ownerUserId, Map<String, Object> parameters, OffsetDateTime now) {
        requireActiveAccount(ownerUserId);
        UUID contextId = contextIdOf(parameters);
        if (contextId == null) {
            return;
        }
        ContextRow context = contexts.findById(contextId)
                .filter(row -> ownerUserId.equals(row.ownerUserId()))
                // 不区分"不存在"与"别人的"：用 403/404 的差异可以探测他人 Context 是否存在。
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "上下文不存在或已失效"));
        if (!context.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.CONTEXT_EXPIRED, "上下文已过期（TTL 30 分钟按创建时间固定）");
        }
        if (!liveScope.contextAllowed(ownerUserId, context)) {
            throw new ApiException(ErrorCode.PERMISSION_DATA_SCOPE,
                    "数据范围已收缩，请重新构建上下文后再提交");
        }
    }

    /**
     * 发布（{@code finish(SUCCESS)}）前复核；返回 {@code null} 表示可以公开，否则返回任务错误码。
     *
     * <p>返回错误码而不是抛异常：发布已经在 worker 事务里，抛异常只会回滚成"任务依旧 RUNNING、
     * 租约还在"，worker 会反复重试同一份越权结果。改判 FAILED 才是终局。
     */
    public String publishBlocker(TaskRow task, UUID resultAssetId, OffsetDateTime now) {
        jdbc.queryForList("SELECT id FROM auth.app_user WHERE id=? FOR SHARE", task.ownerUserId());
        String accountBlocker = accountBlocker(task.ownerUserId());
        if (accountBlocker != null) {
            return accountBlocker;
        }
        // 授权被收窄到具体类型时，已跑完的旧类型同样不能再公开（M05「当前授权」）。
        if (!businessAllowed(task.ownerUserId(), task.taskType(), parseSnapshot(task.parameterSnapshot()))) {
            return PERMISSION_REVOKED;
        }
        UUID contextId = contextIdOf(parseSnapshot(task.parameterSnapshot()));
        if (contextId != null) {
            Optional<ContextRow> context = contexts.findById(contextId)
                    .filter(row -> task.ownerUserId().equals(row.ownerUserId()));
            if (context.isEmpty()) {
                return SCOPE_SHRUNK;
            }
            if (!context.get().expiresAt().isAfter(now)) {
                return "TASK_CONTEXT_EXPIRED";
            }
            if (!liveScope.contextAllowed(task.ownerUserId(), context.get())) {
                return SCOPE_SHRUNK;
            }
        }
        return resultBlocker(task, resultAssetId, now);
    }

    /**
     * 结果引用的类型化复核（M08）：发布前确认"这个指针指向一个当前用户还能看到的结果"。
     *
     * <p>为什么必须在发布时校验而不是只在读取时校验：{@code result_asset_id} 可能指向别人的
     * 文件、已删除的文件，或某个已过期的下载许可。只靠读取端过滤，库里仍然留着一份
     * "已发布但越权"的 SUCCESS 真相，事后审计无法还原。
     */
    private String resultBlocker(TaskRow task, UUID resultAssetId, OffsetDateTime now) {
        switch (task.taskType()) {
            case EXPORT -> {
                UUID declared = tasks.exportOutputFileId(task.id()).orElse(null);
                if (declared != null && resultAssetId != null && !declared.equals(resultAssetId)) {
                    return RESULT_REFERENCE_INVALID;
                }
                UUID assetId = resultAssetId != null ? resultAssetId : declared;
                if (assetId == null) {
                    return RESULT_REFERENCE_INVALID;
                }
                Optional<FileAssetState> asset = tasks.fileAssetState(assetId);
                if (asset.isEmpty() || !"ACTIVE".equals(asset.get().status())) {
                    return RESULT_REFERENCE_INVALID;
                }
                if (asset.get().ownerUserId() != null
                        && !asset.get().ownerUserId().equals(task.ownerUserId())) {
                    return RESULT_REFERENCE_INVALID;
                }
                if (asset.get().expiresAt() == null || !asset.get().expiresAt().isAfter(now)) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case INGESTION, BACKFILL -> {
                if (tasks.ingestionBatchId(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case REPORT -> {
                if (tasks.reportJobId(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case ANALYSIS -> {
                if (tasks.analysisJobId(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case SET_OPERATION -> {
                if (tasks.setJobId(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case FACE_SEARCH -> {
                if (!tasks.faceSearchJobExists(task.id())) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case DATASET_IMPORT, PERSON_LIBRARY_IMPORT, DEVICE_IMPORT -> {
                if (!tasks.importJobExists(task.id())) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case PROFILE_MERGE, PROFILE_SPLIT -> {
                if (tasks.profileIdsForTask(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            case MODEL_RUN -> {
                // 契约要求 MODEL_RUN 必须返回 modelId；modelId 只存在于模型执行行，
                // algorithm_version 是算法版本号、不是模型 id，不能拿来顶替。
                if (tasks.modelIdForTask(task.id()).isEmpty()) {
                    return RESULT_REFERENCE_INVALID;
                }
            }
            default -> {
                // DEVICE_COMMAND/NOTIFICATION：结果就是命令/通知任务自身，任务行存在即成立。
            }
        }
        return null;
    }

    /** Check the existing business permission and current typed Context scope before accepting work. */
    public String submissionBlocker(UUID ownerUserId, TaskType taskType, Map<String, Object> parameters,
                                    OffsetDateTime now) {
        if (accountBlocker(ownerUserId) != null || !businessAllowed(ownerUserId, taskType, parameters)) {
            return PERMISSION_REVOKED;
        }
        UUID contextId = contextIdOf(parameters);
        if (contextId == null) {
            return null;
        }
        Optional<ContextRow> context = contexts.findById(contextId)
                .filter(row -> ownerUserId.equals(row.ownerUserId()));
        if (context.isEmpty()) {
            return CONTEXT_NOT_FOUND;
        }
        if (!context.get().expiresAt().isAfter(now)) {
            return CONTEXT_EXPIRED;
        }
        if (!liveScope.contextAllowed(ownerUserId, context.get())) {
            return SCOPE_SHRUNK;
        }
        return null;
    }

    /** Task types allowed by existing main OpenAPI business permissions; task management is not a domain permission exemption. */
    public java.util.Set<TaskType> allowedTaskTypes(UUID userId) {
        List<String> granted = rbac.permissionCodes(userId);
        java.util.Set<TaskType> allowed = java.util.EnumSet.noneOf(TaskType.class);
        for (TaskType type : TaskType.values()) {
            if (granted.contains(permissionFor(type)) || type == TaskType.EXPORT
                    && java.util.Arrays.stream(com.xxplatform.api.service.approval.ApprovalOperation.values())
                    .filter(op -> op.name().contains("EXPORT")).anyMatch(op -> granted.contains(op.permission()))) {
                allowed.add(type);
            }
        }
        return allowed;
    }

    private boolean businessAllowed(UUID userId, TaskType type, Map<String, Object> parameters) {
        String permission = permissionFor(type);
        if (type == TaskType.EXPORT && parameters != null && parameters.get("operationId") instanceof String id) {
            var operation = com.xxplatform.api.service.approval.ApprovalOperation.parse(id)
                    .filter(op -> op.name().contains("EXPORT"));
            if (operation.isEmpty()) return false;
            permission = operation.get().permission();
        }
        return rbac.permissionCodes(userId).contains(permission);
    }

    /** Existing business permissions from main's OpenAPI; task:manage controls task management only. */
    public static String permissionFor(TaskType type) {
        return switch (type) {
            case INGESTION -> "ingestion:write";
            case BACKFILL -> "ingestion:replay";
            case EXPORT -> "capture:export";
            case FACE_SEARCH -> "face:search";
            case ANALYSIS -> "analysis:create";
            case DATASET_IMPORT -> "dataset:write";
            case SET_OPERATION -> "dataset:write";
            case PROFILE_MERGE, PROFILE_SPLIT -> "profile:merge";
            case MODEL_RUN -> "model:execute";
            case DEVICE_COMMAND -> "device:command";
            case REPORT -> "report:create";
            case NOTIFICATION -> "notification:send";
            case PERSON_LIBRARY_IMPORT -> "person-library:write";
            case DEVICE_IMPORT -> "device:write";
        };
    }

    /** 提交复核的失败码 → 4xx 语义（{@link TaskService} 用它决定状态码）。 */
    public static boolean isSubmissionRejection(String blocker) {
        return PERMISSION_REVOKED.equals(blocker) || CONTEXT_NOT_FOUND.equals(blocker)
                || CONTEXT_EXPIRED.equals(blocker) || SCOPE_SHRUNK.equals(blocker);
    }

    /** 面向用户的失败说明：写进 {@code async_task.error_message}，便于审计定位。 */
    public String describe(String blocker) {
        return switch (blocker) {
            case PERMISSION_REVOKED -> "发布复核未通过：账号已禁用或任务权限已被收回，结果不予公开";
            case SCOPE_SHRUNK -> "发布复核未通过：数据范围已收缩，旧上下文的结果不予公开";
            case CONTEXT_EXPIRED -> "发布复核未通过：上下文已过期，结果不予公开";
            case RESULT_REFERENCE_INVALID -> "发布复核未通过：结果引用不存在、不属于当前用户或已失效";
            default -> "发布复核未通过";
        };
    }

    /** 账号可用性；返回 {@code null} 表示可用。 */
    private String accountBlocker(UUID ownerUserId) {
        List<String> status = jdbc.query(
                "SELECT status FROM auth.app_user WHERE id = ? AND deleted = false",
                (rs, rowNum) -> rs.getString("status"), ownerUserId);
        if (status.isEmpty() || !"ACTIVE".equals(status.get(0))) {
            return PERMISSION_REVOKED;
        }
        // Business authorization is checked separately for the actual task operation.
        return null;
    }

    /** 提交/发布共同的前置：账号必须存在且 ACTIVE。 */
    private void requireActiveAccount(UUID ownerUserId) {
        if (accountBlocker(ownerUserId) != null) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "账号不可用或无权提交任务");
        }
    }



    /** 冻结参数里的 {@code contextId}；没有就是"这条任务不依赖 Context"，不做范围复核。 */
    private UUID contextIdOf(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        Object raw = parameters.get("contextId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "contextId格式错误");
        }
    }

    private Map<String, Object> parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.convertValue(node, Map.class);
            return parsed;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("任务参数快照无法解析，拒绝发布", failure);
        }
    }
}
