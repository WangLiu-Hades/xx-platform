package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.task.TaskListQuery;
import com.xxplatform.api.service.task.TaskPageView;
import com.xxplatform.api.service.task.TaskReadService;
import com.xxplatform.api.service.task.TaskSortBy;
import com.xxplatform.api.service.task.TaskStatus;
import com.xxplatform.api.service.task.TaskType;
import com.xxplatform.api.service.task.TaskWriteService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口：只读查询 + 取消/重试 + 尝试历史（{@code GET /tasks}、{@code GET /tasks/{taskId}}、
 * {@code GET/POST /tasks/{taskId}/attempts|cancel|retry}，W2 任务 7–9）。
 *
 * <p>为什么读接口是 {@code GET} 且不需要 CSRF：契约把它们定义为只读查询，
 * 写请求闸门（{@code CsrfFilter}）只拦 {@code POST/PUT/PATCH/DELETE}。读接口不产生状态，
 * 不需要同步令牌；会话仍然要过 {@code SessionAuthenticator}。
 *
 * <p>为什么取消/重试在控制器只校验 {@code Idempotency-Key} 的形状：{@code CsrfFilter} 已经校验了
 * 同步令牌，会话由 {@code SessionAuthenticator} 负责，权限与状态机在 {@code TaskWriteService} 里。
 * 控制器剩下的职责只有"请求头形状"和"把 202 写对"。
 *
 * <p><b>为什么查询参数不交给 {@code @RequestParam} 的类型转换</b>：Spring 的枚举/时间转换失败会抛
 * {@code MethodArgumentTypeMismatchException}，兜底 handler 只给"类型或格式非法"这一句，
 * 前端无法知道是哪个取值不对。这里手工解析，把契约里的字段名（{@code status}、
 * {@code taskType}、{@code createdFrom}）放进 {@code details}。
 *
 * <p><b>不在本行做 {@code SUCCESS} 与设备回执 {@code ACKNOWLEDGED} 的合并</b>：任务的六态
 * （{@code PENDING/QUEUED/RUNNING/SUCCESS/FAILED/CANCELLED}）只描述"本任务的工作流"，
 * 设备指令的"已下发/已回执"是另一个维度，契约也明确 {@code SUCCESS} 不代表外部副作用已完成。
 * 读接口原样返回任务状态，不把两者折叠成一个"成功"。
 */
@RestController
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final SessionAuthenticator sessionAuthenticator;
    private final TaskReadService taskReadService;
    private final TaskWriteService taskWriteService;

    public TaskController(SessionAuthenticator sessionAuthenticator,
                          TaskReadService taskReadService,
                          TaskWriteService taskWriteService) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.taskReadService = taskReadService;
        this.taskWriteService = taskWriteService;
    }

    /** 查询当前用户的任务列表；空集合返回空数组，不返回 404。 */
    @GetMapping("/tasks")
    public ApiResponses listTasks(
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "pageSize", required = false) String pageSize,
            @RequestParam(name = "sortBy", required = false) String sortBy,
            @RequestParam(name = "sortOrder", required = false) String sortOrder,
            @RequestParam(name = "taskType", required = false) List<String> taskType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "createdFrom", required = false) String createdFrom,
            @RequestParam(name = "createdTo", required = false) String createdTo,
            HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        TaskListQuery query = new TaskListQuery(
                parsePage(page),
                parsePageSize(pageSize),
                parseSortBy(sortBy),
                parseDescending(sortOrder),
                parseTaskTypes(taskType),
                parseStatus(status),
                parseTimestamp(createdFrom, "createdFrom"),
                parseTimestamp(createdTo, "createdTo"));
        TaskPageView result = taskReadService.list(session, query);
        return ApiResponses.success(result, RequestIdFilter.currentTraceIdOrFallback());
    }

    /** 读取单个任务；不是当前用户的任务按 404 隐藏存在性。 */
    @GetMapping("/tasks/{taskId}")
    public ApiResponses getTask(@PathVariable("taskId") UUID taskId,
                                HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        return ApiResponses.success(taskReadService.get(session, taskId),
                RequestIdFilter.currentTraceIdOrFallback());
    }

    /**
     * 取消任务（契约 {@code POST /tasks/{taskId}/cancel}）。
     *
     * <p>202 是契约定的，不因"排队态同步取消"改成 200：调用方读响应体里的 {@code status}
     * 判断是否真的停了，HTTP 码只表示"受理"。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<ApiResponses> cancelTask(
            @PathVariable("taskId") UUID taskId,
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String idempotencyKey,
            HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        IdempotencyKeyHeader.require(idempotencyKey);
        Map<String, Object> task = taskWriteService.cancel(session, taskId);
        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        log.info("任务取消已受理 traceId={} taskId={} status={}", traceId, taskId, task.get("status"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponses.success(task, traceId));
    }

    /** 重试失败任务（契约 {@code POST /tasks/{taskId}/retry}），同样 202。 */
    @PostMapping("/tasks/{taskId}/retry")
    public ResponseEntity<ApiResponses> retryTask(
            @PathVariable("taskId") UUID taskId,
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String idempotencyKey,
            HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        IdempotencyKeyHeader.require(idempotencyKey);
        Map<String, Object> task = taskWriteService.retry(session, taskId);
        String traceId = RequestIdFilter.currentTraceIdOrFallback();
        log.info("任务重试已受理 traceId={} taskId={} status={}", traceId, taskId, task.get("status"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponses.success(task, traceId));
    }

    /**
     * 任务尝试历史（契约 {@code GET /tasks/{taskId}/attempts}）。
     *
     * <p>响应是 {@code {items: [...]}} 而不是分页对象：契约的
     * {@code ListTaskAttemptsResponse.data} 只有 {@code items}，一条任务的尝试次数受
     * {@code xx.task.max-attempts}（默认 3）约束，不会长到需要翻页。
     */
    @GetMapping("/tasks/{taskId}/attempts")
    public ApiResponses listTaskAttempts(@PathVariable("taskId") UUID taskId,
                                         HttpServletRequest request) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        Map<String, Object> data = Map.of("items",
                taskReadService.attempts(session, taskId));
        return ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback());
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int value = parseInt(raw, "page");
        if (value < 1) {
            throw validation("page", "页码必须从 1 开始");
        }
        return value;
    }

    private static int parsePageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return 20;
        }
        int value = parseInt(raw, "pageSize");
        if (value < 1 || value > MAX_PAGE_SIZE) {
            throw validation("pageSize", "每页条数必须在 1–100 之间");
        }
        return value;
    }

    private static TaskSortBy parseSortBy(String raw) {
        if (raw == null || raw.isBlank()) {
            return TaskSortBy.CREATED_AT;
        }
        try {
            return TaskSortBy.parse(raw);
        } catch (IllegalArgumentException e) {
            throw validation("sortBy", "排序字段只能是 createdAt 或 status");
        }
    }

    private static boolean parseDescending(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DESC" -> true;
            case "ASC" -> false;
            default -> throw validation("sortOrder", "排序方向只能是 ASC 或 DESC");
        };
    }

    /** 同名多值按契约做 OR；重复值去重，未知类型 400。 */
    private static List<TaskType> parseTaskTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<TaskType> types = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            TaskType type;
            try {
                type = TaskType.valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                throw validation("taskType", "未知的任务类型: " + value);
            }
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    private static TaskStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TaskStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw validation("status", "未知的任务状态: " + raw);
        }
    }

    private static OffsetDateTime parseTimestamp(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim(), TIMESTAMP);
        } catch (DateTimeException e) {
            throw validation(field, "时间必须是带时区偏移的 RFC 3339 格式");
        }
    }

    private static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw validation(field, "必须是整数");
        }
    }

    private static ApiException validation(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "参数校验失败",
                List.of(new FieldErrorDetail(field, reason)), null);
    }
}
