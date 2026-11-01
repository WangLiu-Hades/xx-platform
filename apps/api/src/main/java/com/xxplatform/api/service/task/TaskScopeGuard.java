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

/**
 * 任务侧的"当前授权/数据范围"复核（P-002-05 / M05 / M14）。
 *
 * <p><b>为什么需要独立一个守护类而不是塞进 {@link TaskService}</b>：复核要在两个时刻发生——
 * 提交（{@code create}/{@code import}）与发布（{@code finish} 的 SUCCESS），两处的失败语义不同
 * （提交是 4xx 拒绝；发布是"改判 FAILED + 写原因"，因为任务已经在跑，不能凭空撤销事务）。
 * 把"判定"与"如何收尾"拆开，{@link TaskService} 只决定收尾方式，判定规则只有一份。
 *
 * <p><b>为什么发布复核不能只查"现在还有没有权限码"</b>：任务提交时冻结的是当时的数据范围
 * （{@code analysis_context.permission_version}），缩权之后 {@code auth.user_scope.scope_version}
 * 会增长而冻结值不动——**冻结值落后于当前授权版本**才是"范围已收缩"的可靠信号。
 * 只比权限码会漏掉"权限码没变、但站点/数据类型范围被收窄"的情形。
 *
 * <p><b>为什么提交也要复核</b>：P-009-01 要求 Context 30 分钟 TTL 按创建时间固定、到期 410；
 * 若只在读取时判、提交时不判，调用方可以拿一个已经过期的 Context 继续派生任务，
 * 等于让"过期"只挡住了列表页。
 */
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

    public TaskScopeGuard(JdbcTemplate jdbc, RbacRepository rbac, AnalysisContextRepository contexts,
                          AsyncTaskRepository tasks, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.rbac = rbac;
        this.contexts = contexts;
        this.tasks = tasks;
        this.objectMapper = objectMapper;
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
        long current = currentScopeVersion(ownerUserId);
        if (context.permissionVersion() < current) {
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
        String accountBlocker = accountBlocker(task.ownerUserId());
        if (accountBlocker != null) {
            return accountBlocker;
        }
        // 授权被收窄到具体类型时，已跑完的旧类型同样不能再公开（M05「当前授权」）。
        if (!allowedTaskTypes(task.ownerUserId()).contains(task.taskType())) {
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
            if (context.get().permissionVersion() < currentScopeVersion(task.ownerUserId())) {
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
                if (asset.get().expiresAt() != null && !asset.get().expiresAt().isAfter(now)) {
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

    /**
     * 提交（create/import）时的类型与 Context 复核；返回 {@code null} 表示放行。
     *
     * <p><b>"当前授权"在提交时的含义</b>：不只是"有 {@code task:manage}"——收窄后的账号可能
     * 只剩某些任务类型（{@code task:run:<TYPE>} 授权码），这时仍允许它提交已被收回的类型，
     * 等于绕过了缩权。所以提交要按类型再判一次，且判定必须发生在插入任务行之前（不能先落库
     * 再回滚，那样会消耗积分/占用队列配额，也会留下审计噪音）。
     *
     * <p>Context {@code permissionVersion} 落后于 {@code auth.user_scope.scope_version} 视为
     * 范围已收缩（理由见 {@link #publishBlocker} 的类注释）。
     */
    public String submissionBlocker(UUID ownerUserId, TaskType taskType, Map<String, Object> parameters,
                                    OffsetDateTime now) {
        if (!allowedTaskTypes(ownerUserId).contains(taskType)) {
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
        if (context.get().permissionVersion() < currentScopeVersion(ownerUserId)) {
            return SCOPE_SHRUNK;
        }
        return null;
    }

    /**
     * 当前账号可以提交/发布的任务类型。
     *
     * <p>{@code task:manage} 是全量授权（运维/管理员）；否则按 {@code task:run:<TYPE>} 授权码
     * 逐类型判断。没有任何类型授权时返回空集——"什么都不能提交"，而不是"默认全开"。
     */
    public java.util.Set<TaskType> allowedTaskTypes(UUID userId) {
        List<String> granted = rbac.permissionCodes(userId);
        if (granted.contains(PERMISSION)) {
            return java.util.EnumSet.allOf(TaskType.class);
        }
        java.util.Set<TaskType> allowed = java.util.EnumSet.noneOf(TaskType.class);
        for (TaskType type : TaskType.values()) {
            if (granted.contains("task:run:" + type.name())) {
                allowed.add(type);
            }
        }
        return allowed;
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
        // 权限不在这一层判：{@code task:manage} 只是全量授权，收窄后的账号可以只剩
        // {@code task:run:<TYPE>}。如果这里硬要求 task:manage，按类型授权的账号连"自己还有
        // 权的那一类"都提交不了，等于把 M05 的"当前授权"退化成"全有或全无"。
        // 类型级判定统一由 {@link #allowedTaskTypes(UUID)} 负责，本方法只管账号是否还活着。
        return null;
    }

    /** 提交/发布共同的前置：账号必须存在且 ACTIVE。 */
    private void requireActiveAccount(UUID ownerUserId) {
        if (accountBlocker(ownerUserId) != null) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "账号不可用或无权提交任务");
        }
    }

    private long currentScopeVersion(UUID userId) {
        return rbac.userScope(userId).map(RbacRepository.UserScopeRow::scopeVersion).orElse(0L);
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
            // 参数里写了个非法 UUID：交给参数 schema 校验去拒绝，这里不重复报错。
            return null;
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
            log.warn("任务参数快照无法解析，跳过范围复核: {}", failure.getMessage());
            return Map.of();
        }
    }
}
