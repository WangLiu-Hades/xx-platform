package com.xxplatform.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Instants;
import com.xxplatform.api.infra.AnalysisContextRepository;
import com.xxplatform.api.infra.AnalysisContextRepository.ContextRow;
import com.xxplatform.api.infra.AsyncTaskRepository.TaskRow;
import com.xxplatform.api.infra.ExportJobRepository;
import com.xxplatform.api.infra.RbacRepository;
import com.xxplatform.api.service.task.TaskCommand;
import com.xxplatform.api.service.task.TaskService;
import com.xxplatform.api.service.task.TaskType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提交一次异步导出（{@code POST /exports}，W2 任务 6）。
 *
 * <p>这个服务只做<b>提交</b>，不做执行：把"导出什么"冻结成任务参数与导出意图行，返回
 * {@code taskId}。真正的分片读取、压缩与落文件由 worker 按任务参数执行（W3+ 的文件模型）。
 * 这样导出就自动继承了任务底座的三样东西——持久化、重试、取消，而不需要给导出再造一套
 * 状态机。
 *
 * <p>三个关键判断都在这里，且都有代价，写清楚以便评审：
 * <ol>
 *   <li><b>范围必须有界</b>。契约的 100 万行/1 GiB 是<b>执行期</b>预算（worker 读到上限就停并
 *       报 {@code DATA_QUERY_TOO_BROAD}），提交时 Context 里没有行数，无法提前算出来。
 *       提交时能做、也必须做的是拒绝"完全无界"的范围（没有任何时间/场所/设备/点名/区域条件），
 *       否则一次全库导出会直接拖垮在线查询。无界时按契约返 422 {@code DATA_QUERY_TOO_BROAD}。</li>
 *   <li><b>列白名单在提交时校验</b>。列名进任务参数，worker 只按枚举名取数；放进来一个未知列
 *       会让任务在跑完之后才失败，白等一次排队。</li>
 *   <li><b>权限在提交时校验，且执行期还要再校验</b>。契约要求"下载授权 5 分钟且每次校验当前
 *       权限"——提交时校验的是"能不能发起导出"，worker 落文件与下载时还要按当时的权限再查一次。</li>
 * </ol>
 *
 * <p><b>已知缺口（有意留到 W2 第 12–13 行）</b>：契约的 {@code X-Approval-Id} 对高风险导出
 * 是必填的（{@code SENSITIVE_ACTION_PROTOCOL.md}），而审批服务尚未实现。本行不强制该头，
 * 也不假装校验过——否则会出现"带了任意审批 id 就能导出"的假安全。审批落地后在这里补
 * "校验 + 一次性消费"，不要把它挪进 Filter（审批要绑定路径资源与请求体摘要，Filter 看不到
 * 反序列化之后的 body）。
 */
@Service
public class ExportService {

    /** 契约 {@code x-permission}。 */
    public static final String PERMISSION = "capture:export";
    /** 单一导出的行数硬上限（契约：最多 100 万行）。 */
    public static final int MAX_ROWS = 1_000_000;
    /** 单一导出的字节硬上限（契约：1 GiB）。 */
    public static final long MAX_BYTES = 1_073_741_824L;
    /** 分片行数：大结果集按 5 万行一片，便于失败后从分片续跑（契约要求）。 */
    public static final int SHARD_ROWS = 50_000;
    /** 文件保留期：12 个自然月（契约）。 */
    public static final int RETENTION_MONTHS = 12;
    /** 下载授权有效期（契约）：每次下载都要按当前权限重新校验。 */
    public static final Duration DOWNLOAD_TTL = Duration.ofMinutes(5);
    /** 任务参数契约版本，参与参数指纹。 */
    public static final String PARAMETER_SCHEMA_VERSION = "1";
    /** 导出意图来源的筛选快照版本。 */
    public static final String SOURCE_SCHEMA_VERSION = "context-filter-v1";
    /** 任务行上的领域资源类型，与契约 {@code TaskCreated.resourceType} 一致。 */
    public static final String RESOURCE_TYPE = "EXPORT";

    /**
     * 所有数据类型都有的列。
     *
     * <p>顺序就是默认导出的列顺序：先定位（id/类型/时间/设备/场所），再展示（目标/来源/状态），
     * 最后是坐标，最后才是类型专有列。这样即便用户不选列，导出的表也能直接看懂。
     */
    private static final List<String> COMMON_COLUMNS = List.of(
            "captureId", "dataType", "captureTime", "ingestedAt", "deviceId", "siteId",
            "displayTarget", "source", "status", "longitude", "latitude");

    /** 数据类型枚举顺序；默认列按这个顺序拼接，保证同一请求永远生成同样的列顺序。 */
    private static final List<String> DATA_TYPES = List.of("FENCE", "MAC", "PLATE", "FACE");

    /** 类型专有列：只有对应数据类型才有意义，默认导出时按需追加。 */
    private static final Map<String, List<String>> TYPE_COLUMNS = typeColumns();

    private final TaskService tasks;
    private final AnalysisContextRepository contexts;
    private final ExportJobRepository jobs;
    private final RbacRepository rbac;
    private final ObjectMapper objectMapper;

    public ExportService(TaskService tasks,
                         AnalysisContextRepository contexts,
                         ExportJobRepository jobs,
                         RbacRepository rbac,
                         ObjectMapper objectMapper) {
        this.tasks = tasks;
        this.contexts = contexts;
        this.jobs = jobs;
        this.rbac = rbac;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交导出并返回任务摘要。
     *
     * <p>整个方法一个事务：任务行、创建事件、导出意图行要么都出现，要么都不出现。否则会出现
     * "任务已在队列里、worker 却读不到导出意图"的半条数据——worker 只能把它当失败反复重试，
     * 而用户看到的是一个永远不动的任务。
     */
    @Transactional
    public TaskCreatedView create(AuthenticatedSession session, ExportCommand command) {
        if (command.contextId() == null || command.format() == null || command.idempotencyKey() == null) {
            throw new IllegalArgumentException("contextId/format/idempotencyKey 不能为空");
        }
        requirePermission(session.userId());
        ContextRow context = loadContext(command.contextId(), session.userId());
        OffsetDateTime now = Instants.nowUtc();
        if (!context.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.CONTEXT_EXPIRED, "查询上下文已过期，请重新筛选后再导出");
        }
        JsonNode filter = parseFilter(context);
        List<String> dataTypes = dataTypesOf(context, filter);
        List<String> columns = resolveColumns(command.columns(), dataTypes);
        Map<String, Object> parameters = taskParameters(context, command, columns, filter);

        TaskRow task = tasks.create(new TaskCommand(
                session.userId(),
                TaskType.EXPORT,
                taskName(context, command.format()),
                PARAMETER_SCHEMA_VERSION,
                parameters,
                command.idempotencyKey(),
                command.traceId(),
                null,
                // 领域资源类型/ id 都不填：{@code TaskCommand} 的约束是"要指类型就必须同时指 id"，
                // 而导出意图行的主键就是 task.id，创建任务时这个 id 还不存在。worker 用 task_id
                // 反查 export_job 即可，不需要在任务行上再存一份指向自己的引用。
                // 对外的 TaskCreated.resourceType 仍然是 EXPORT（契约枚举值）。
                null,
                null));

        jobs.insert(task.id(), "CAPTURE", SOURCE_SCHEMA_VERSION,
                sourceSnapshotJson(context, columns, filter, command),
                command.format().name(), columns, context.filterType(), command.purpose(), now);

        return new TaskCreatedView(task.id(), task.status().name(), Instants.iso(task.createdAt()), RESOURCE_TYPE);
    }

    /** 提交端的权限判定。别把它挪进 Filter：Filter 不解析 body，也不知道"这是导出"。 */
    private void requirePermission(UUID userId) {
        if (!rbac.permissionCodes(userId).contains(PERMISSION)) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "无权导出采集数据（缺少 " + PERMISSION + "）");
        }
    }

    /**
     * 读上下文并按"不可见"处理两类失败：不存在、以及属于别人。
     *
     * <p>两者都返回 404 是刻意的：如果别人的 context 返回 403，攻击者就能用状态码差异枚举
     * 出哪些 context id 存在。
     */
    private ContextRow loadContext(UUID contextId, UUID userId) {
        ContextRow context = contexts.findById(contextId)
                .orElseThrow(() -> new ApiException(ErrorCode.DATA_NOT_FOUND, "查询上下文不存在"));
        if (!context.ownerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.DATA_NOT_FOUND, "查询上下文不存在");
        }
        return context;
    }

    private JsonNode parseFilter(ContextRow context) {
        try {
            JsonNode filter = objectMapper.readTree(context.filterSnapshot());
            if (filter == null || !filter.isObject()) {
                throw new ApiException(ErrorCode.DATA_INVALID_FILTER, "查询上下文的筛选条件不是对象");
            }
            return filter;
        } catch (JsonProcessingException e) {
            // 库里存的是合法 jsonb，读出来解析失败说明数据被绕过约束写坏，属于缺陷而不是用户输入错误。
            throw new IllegalStateException("查询上下文筛选条件无法解析: " + context.id(), e);
        }
    }

    /**
     * 取出这次导出涉及的数据类型，并顺带完成"范围是否有界"的判定。
     *
     * <p>当前只接受两种 Context：{@code CAPTURE_SEARCH}（单类型检索）与 {@code GENERAL}
     * （跨类型/跨页的通用采集范围）。{@code DATASET}/{@code ANALYSIS_RESULTS}/{@code PROFILE}
     * 的导出语义（列、权限、脱敏）还没在契约里冻结，贸然支持会导出一张"列对不上"的表，
     * 所以按 422 {@code DATA_INVALID_FILTER} 明确拒绝，而不是猜。
     */
    private List<String> dataTypesOf(ContextRow context, JsonNode filter) {
        String filterType = context.filterType();
        if ("CAPTURE_SEARCH".equals(filterType)) {
            JsonNode captureFilter = filter.path("captureFilter");
            if (!captureFilter.isObject()) {
                throw new ApiException(ErrorCode.DATA_INVALID_FILTER, "查询上下文缺少 captureFilter");
            }
            if (captureFilter.path("timeRange").isMissingNode() || captureFilter.path("timeRange").isNull()) {
                throw new ApiException(ErrorCode.DATA_QUERY_TOO_BROAD,
                        "导出范围过宽：采集检索上下文必须有时间范围，请缩小时间窗后再导出");
            }
            String dataType = captureFilter.path("dataType").asText("");
            if (!DATA_TYPES.contains(dataType)) {
                throw new ApiException(ErrorCode.DATA_INVALID_FILTER, "查询上下文的数据类型不受支持: " + dataType);
            }
            return List.of(dataType);
        }
        if ("GENERAL".equals(filterType)) {
            if (!hasBoundary(filter)) {
                throw new ApiException(ErrorCode.DATA_QUERY_TOO_BROAD,
                        "导出范围过宽：通用上下文至少需要有界条件（时间范围、场所、设备、指定记录或区域）");
            }
            List<String> dataTypes = new ArrayList<>();
            JsonNode declared = filter.path("dataTypes");
            if (declared.isArray()) {
                for (JsonNode item : declared) {
                    String dataType = item.asText("");
                    if (!DATA_TYPES.contains(dataType)) {
                        throw new ApiException(ErrorCode.DATA_INVALID_FILTER, "查询上下文的数据类型不受支持: " + dataType);
                    }
                    if (!dataTypes.contains(dataType)) {
                        dataTypes.add(dataType);
                    }
                }
            }
            // 没有声明数据类型 ≠ 没有数据；按"全部四种"处理，默认列取并集。
            return dataTypes.isEmpty() ? DATA_TYPES : List.copyOf(dataTypes);
        }
        throw new ApiException(ErrorCode.DATA_INVALID_FILTER,
                "当前只支持采集检索（CAPTURE_SEARCH）与通用采集范围（GENERAL）的导出，收到: " + filterType);
    }

    /** 通用上下文至少要有一个有界条件，否则一次全库扫描。 */
    private static boolean hasBoundary(JsonNode filter) {
        JsonNode timeRange = filter.path("timeRange");
        if (timeRange.isObject() && !timeRange.path("from").asText("").isBlank()) {
            return true;
        }
        return nonEmptyArray(filter.path("siteIds"))
                || nonEmptyArray(filter.path("deviceIds"))
                || nonEmptyArray(filter.path("sourceCaptureIds"))
                || nonEmptyArray(filter.path("regionCodes"));
    }

    private static boolean nonEmptyArray(JsonNode node) {
        return node.isArray() && !node.isEmpty();
    }

    /**
     * 校验并收敛导出列。
     *
     * @param requested 客户端显式指定的列；{@code null} 表示"按数据类型给默认列"
     */
    private List<String> resolveColumns(List<String> requested, List<String> dataTypes) {
        if (requested == null) {
            return defaultColumns(dataTypes);
        }
        if (requested.isEmpty()) {
            throw validationError("columns", "columns 不能为空数组；不传该字段表示按数据类型导出全部列");
        }
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                throw validationError("columns", "columns 不能包含空值");
            }
            String column = raw.trim();
            String owner = ownerOf(column);
            if (owner == null && !COMMON_COLUMNS.contains(column)) {
                throw validationError("columns", "不支持的导出列: " + column);
            }
            if (owner != null && !dataTypes.contains(owner)) {
                throw validationError("columns", "列 " + column + " 仅适用于数据类型 " + owner);
            }
            columns.add(column);
        }
        return List.copyOf(columns);
    }

    /** 默认列 = 公共列 + 本次数据类型各自的专有列。 */
    private static List<String> defaultColumns(List<String> dataTypes) {
        List<String> columns = new ArrayList<>(COMMON_COLUMNS);
        for (String dataType : DATA_TYPES) {
            if (dataTypes.contains(dataType)) {
                columns.addAll(TYPE_COLUMNS.get(dataType));
            }
        }
        return List.copyOf(columns);
    }

    /** 列 → 拥有它的数据类型；公共列返回 null。 */
    private static String ownerOf(String column) {
        for (Map.Entry<String, List<String>> entry : TYPE_COLUMNS.entrySet()) {
            if (entry.getValue().contains(column)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 任务参数快照：worker 只读这里，不再回查 Context。
     *
     * <p>为什么把筛选快照整份塞进参数：Context 会过期（默认几小时），而任务可能排队很久；
     * 如果 worker 到时候再去读 Context，就会出现"任务还在队列、范围已经过期"的必然失败。
     * 冻结之后，任务的输入就是不可变的，也便于复现"同一个 taskId 到底导了什么"。
     *
     * <p>{@code limits} 一起进参数指纹：调大上限会被视为不同参数，避免同一个幂等键在
     * 预算变更前后拿到语义不同的任务。
     */
    private Map<String, Object> taskParameters(ContextRow context, ExportCommand command,
                                               List<String> columns, JsonNode filter) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("contextId", context.id().toString());
        parameters.put("format", command.format().name());
        parameters.put("columns", columns);
        parameters.put("purpose", command.purpose());
        parameters.put("filterType", context.filterType());
        parameters.put("sourceModule", context.sourceModule());
        parameters.put("permissionVersion", context.permissionVersion());
        parameters.put("filterSnapshot", objectMapper.convertValue(filter, Object.class));
        parameters.put("limits", limits());
        return parameters;
    }

    /** 冻结的执行预算；worker 与审计都以这里的值为准。 */
    public static Map<String, Object> limits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxRows", MAX_ROWS);
        limits.put("maxBytes", MAX_BYTES);
        limits.put("shardRows", SHARD_ROWS);
        limits.put("retentionMonths", RETENTION_MONTHS);
        limits.put("downloadTtlSeconds", DOWNLOAD_TTL.toSeconds());
        return limits;
    }

    private String sourceSnapshotJson(ContextRow context, List<String> columns,
                                      JsonNode filter, ExportCommand command) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contextId", context.id().toString());
        snapshot.put("filterType", context.filterType());
        snapshot.put("filterSchemaVersion", context.filterSchemaVersion());
        snapshot.put("sourceModule", context.sourceModule());
        snapshot.put("permissionVersion", context.permissionVersion());
        snapshot.put("filterSnapshot", objectMapper.convertValue(filter, Object.class));
        snapshot.put("columns", columns);
        snapshot.put("format", command.format().name());
        snapshot.put("limits", limits());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导出意图快照无法序列化: " + context.id(), e);
        }
    }

    private static String taskName(ContextRow context, ExportFormat format) {
        return "采集数据导出（" + context.filterType() + " / " + format.name() + "）";
    }

    private static ApiException validationError(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, reason, List.of(new FieldErrorDetail(field, reason)));
    }

    private static Map<String, List<String>> typeColumns() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("FENCE", List.of("imsiMasked", "imeiMasked"));
        map.put("MAC", List.of("macMasked"));
        map.put("PLATE", List.of("plateMasked"));
        map.put("FACE", List.of("faceImageAssetId"));
        return Map.copyOf(map);
    }

    /** 提交参数（控制层解析 header 与 body 之后交进来）。 */
    public record ExportCommand(
            UUID contextId,
            ExportFormat format,
            List<String> columns,
            String purpose,
            String idempotencyKey,
            String traceId) {
    }
}
