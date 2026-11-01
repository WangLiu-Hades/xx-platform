package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.ExportFormat;
import com.xxplatform.api.service.ExportService;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.TaskCreatedView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出端点（W2 任务 6）：{@code POST /exports}。
 *
 * <p>契约要点（{@code openapi.yaml} → {@code /exports}）：
 * {@code x-permission: capture:export}、{@code security: sessionCookie}、必带
 * {@code Idempotency-Key} 与 {@code X-CSRF-Token}、成功返回 <b>202</b> + {@code TaskCreated}。
 *
 * <p><b>为什么是 202 而不是 200/201</b>：这是一个"已受理、稍后完成"的资源创建，任务此刻
 * 还只是 {@code PENDING}。用 201 会让客户端以为结果已经可读；用 200 又丢掉了"异步"的语义。
 *
 * <p><b>为什么控制器还做一次 CSRF 校验</b>：{@code CsrfFilter} 已经拦过所有写请求了，这里不
 * 重复校验——真正需要控制层做的是三件 Filter 看不到的事：会话（401）、
 * {@code Idempotency-Key} 的形状（400）、以及把请求体翻译成服务层命令。
 *
 * <p><b>X-Approval-Id</b>：敏感导出必须携带审批 id；本端点只负责解析 header 并形状校验，
 * 绑定关系（operationId/摘要/执行人）与一次性消费在 {@link ExportService} 内完成——Filter
 * 看不到反序列化之后的请求体，算不出摘要。
 */
@RestController
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    /** 契约里的审批头名。 */
    static final String APPROVAL_HEADER = "X-Approval-Id";

    private final SessionAuthenticator sessionAuthenticator;
    private final ExportService exportService;

    public ExportController(SessionAuthenticator sessionAuthenticator, ExportService exportService) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.exportService = exportService;
    }

    /**
     * 提交导出。
     *
     * <p>顺序刻意是"先认证、再校验参数"：未登录的调用者不应该通过状态码差异（400 vs 401）
     * 反推出参数校验规则。代价是未登录 + 非法 body 会返回 400，这是 Spring 在进入方法前
     * 就完成 body 绑定与校验的固有行为，接受。
     */
    @PostMapping("/exports")
    public ResponseEntity<ApiResponses> create(
            @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false) String idempotencyKey,
            @RequestHeader(value = APPROVAL_HEADER, required = false) String approvalIdHeader,
            @Valid @RequestBody ExportRequest body,
            HttpServletRequest request) {

        AuthenticatedSession session = sessionAuthenticator.require(request);
        String key = IdempotencyKeyHeader.require(idempotencyKey);
        ExportFormat format = requireFormat(body.format());
        UUID approvalId = parseApprovalId(approvalIdHeader);
        String traceId = RequestIdFilter.currentTraceIdOrFallback();

        TaskCreatedView created = exportService.create(session, new ExportService.ExportCommand(
                body.contextId(), format, body.columns(), body.purpose(), key, traceId, approvalId));

        log.info("导出已受理 traceId={} taskId={} format={} approvalId={}",
                traceId, created.taskId(), format, approvalId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponses.success(created, traceId));
    }

    /**
     * 解析 {@code X-Approval-Id}。
     *
     * <p>缺省返回 null（由服务层统一判"敏感导出需要审批"并回 403）——这里不提前拒绝，是为了
     * 让"缺审批"与"审批 id 写错"在服务层有同一处判定口径。格式非法则直接 400：那说明客户端
     * 拼错了 URL，早点告诉它，而不是拿一个必然查不到的 id 去 409。
     */
    private static UUID parseApprovalId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw validationError(APPROVAL_HEADER, "X-Approval-Id 必须是 UUID");
        }
    }

    private static ExportFormat requireFormat(String raw) {
        try {
            return ExportFormat.parse(raw);
        } catch (IllegalArgumentException e) {
            throw validationError("format", e.getMessage());
        }
    }

    private static ApiException validationError(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, reason, List.of(new FieldErrorDetail(field, reason)));
    }
}
