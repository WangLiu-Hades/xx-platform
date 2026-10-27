package com.xxplatform.api.api;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.ExportFormat;
import com.xxplatform.api.service.ExportService;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.TaskCreatedView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * <p><b>X-Approval-Id</b>：契约要求高风险导出携带审批 id，审批服务属 W2 第 12–13 行，
 * 尚未实现；本端点不强制、也不解析该头，避免"带任意 id 即可通过"的假安全。补齐时在
 * {@code ExportService} 里校验绑定与一次性消费（Filter 看不到反序列化后的请求体摘要）。
 */
@RestController
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    /** 契约里 {@code IdempotencyKey} 的 header 名。 */
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    /** 契约 minLength。 */
    public static final int IDEMPOTENCY_MIN = 16;
    /** 契约 maxLength。 */
    public static final int IDEMPOTENCY_MAX = 160;

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
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ExportRequest body,
            HttpServletRequest request) {

        AuthenticatedSession session = sessionAuthenticator.require(request);
        String key = requireIdempotencyKey(idempotencyKey);
        ExportFormat format = requireFormat(body.format());
        String traceId = RequestIdFilter.currentTraceIdOrFallback();

        TaskCreatedView created = exportService.create(session, new ExportService.ExportCommand(
                body.contextId(), format, body.columns(), body.purpose(), key, traceId));

        log.info("导出已受理 traceId={} taskId={} format={}", traceId, created.taskId(), format);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponses.success(created, traceId));
    }

    /**
     * 契约要求 {@code Idempotency-Key} 必填且 16–160 字符。
     *
     * <p>为什么长度下限是 16：太短的键（如 {@code "1"}）很容易在客户端被复用，导致"不同导出
     * 被当成同一次"；下限抬高了这种碰撞的概率成本。上限 160 与库列 {@code varchar(200)} 对齐
     * （还留了 40 字符给服务端可能的前缀）。
     */
    private static String requireIdempotencyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validationError(IDEMPOTENCY_HEADER, "缺少 Idempotency-Key 请求头");
        }
        String key = raw.trim();
        if (key.length() < IDEMPOTENCY_MIN || key.length() > IDEMPOTENCY_MAX) {
            throw validationError(IDEMPOTENCY_HEADER,
                    "Idempotency-Key 长度必须在 " + IDEMPOTENCY_MIN + "–" + IDEMPOTENCY_MAX + " 字符之间");
        }
        return key;
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
