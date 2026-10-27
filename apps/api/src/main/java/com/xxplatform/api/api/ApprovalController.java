package com.xxplatform.api.api;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.IdempotencyKeyHeader;
import com.xxplatform.api.common.RequestIdFilter;
import com.xxplatform.api.service.AuthenticatedSession;
import com.xxplatform.api.service.SessionAuthenticator;
import com.xxplatform.api.service.approval.ApprovalService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 敏感动作审批的四个端点（任务 12/13）。
 *
 * <p>鉴权顺序与其它写端点一致：会话 → {@code Idempotency-Key} 形状 → 业务。CSRF 与 Origin 已由
 * {@link com.xxplatform.api.common.CsrfFilter} 统一拦截，这里不再重复。
 *
 * <p>响应码按契约：创建 {@code 201}（"已创建；不代表动作已执行"），查询与裁决 {@code 200}。
 * 契约把这组接口标成 {@code provisional}，且明写"按 PD-2026-10-19.v1 暂定开发"，所以
 * 审批人/时限/密钥都走配置，等甲方确认。
 */
@RestController
@RequestMapping("/action-approvals")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final SessionAuthenticator sessionAuthenticator;
    private final ApprovalService approvals;

    public ApprovalController(SessionAuthenticator sessionAuthenticator, ApprovalService approvals) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.approvals = approvals;
    }

    @GetMapping
    public ApiResponses page(HttpServletRequest request,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(required = false) Integer pageSize) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        Map<String, Object> data = approvals.page(session, cursor, pageSize);
        return ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback());
    }

    @PostMapping
    public ResponseEntity<ApiResponses> create(HttpServletRequest request,
                                               @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false)
                                               String idempotencyKey,
                                               @RequestBody ActionApprovalRequest body) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        IdempotencyKeyHeader.require(idempotencyKey);
        Map<String, Object> data = approvals.create(session, body.proposal(), body.reason());
        log.info("审批申请已创建 approvalId={} requester={}", data.get("approvalId"), session.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback()));
    }

    @GetMapping("/{approvalId}")
    public ApiResponses get(HttpServletRequest request, @PathVariable UUID approvalId) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        return ApiResponses.success(approvals.get(session, approvalId),
                RequestIdFilter.currentTraceIdOrFallback());
    }

    @PostMapping("/{approvalId}/decisions")
    public ApiResponses decide(HttpServletRequest request,
                               @PathVariable UUID approvalId,
                               @RequestHeader(value = IdempotencyKeyHeader.NAME, required = false)
                               String idempotencyKey,
                               @RequestBody ActionApprovalDecisionRequest body) {
        AuthenticatedSession session = sessionAuthenticator.require(request);
        IdempotencyKeyHeader.require(idempotencyKey);
        Map<String, Object> data = approvals.decide(session, approvalId, body.decision(),
                body.reason(), body.currentPassword());
        log.info("审批决定已提交 approvalId={} reviewer={} status={}",
                approvalId, session.userId(), data.get("status"));
        return ApiResponses.success(data, RequestIdFilter.currentTraceIdOrFallback());
    }
}
