package com.xxplatform.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求建立 traceId：写入日志 MDC、响应头 {@code X-Trace-Id}，并暴露到 {@link #currentTraceId()}。
 *
 * <p>契约要求（{@code api-conventions.md} §1）：全部响应带 {@code X-Trace-Id}，
 * 且与 JSON 壳里的 {@code traceId} 相同。因此业务代码取 traceId 只能用
 * {@link ApiResponses#success(Object, String)} 配合 {@link #currentTraceId()}，
 * 不要各写一个来源。
 *
 * <p>当前实现是平台侧最简版本：客户端未传 {@code X-Trace-Id} 时自行生成 UUID。
 * 接入网关或链路追踪后，这里改为透传上游 traceId（不要改字段名与响应头名）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 请求/响应用的追踪头名，契约固定。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** 日志 MDC key，logback pattern 里的 %X{traceId} 对应它。 */
    public static final String TRACE_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = traceId.trim();
        }
        // 头部长度上限与契约一致（maxLength 64），超长不截断而是重生成，避免注入异常值。
        if (traceId.length() > 64) {
            traceId = UUID.randomUUID().toString();
        }
        String previous = MDC.get(TRACE_MDC_KEY);
        MDC.put(TRACE_MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(TRACE_MDC_KEY);
            } else {
                MDC.put(TRACE_MDC_KEY, previous);
            }
        }
    }

    /** 当前请求的 traceId；无请求上下文时返回 null。 */
    public static String currentTraceId() {
        return MDC.get(TRACE_MDC_KEY);
    }

    /** 当前请求的 traceId，取不到时回退为 "unknown-trace"，仅用于兜底响应。 */
    public static String currentTraceIdOrFallback() {
        String traceId = MDC.get(TRACE_MDC_KEY);
        return traceId == null ? "unknown-trace" : traceId;
    }
}
