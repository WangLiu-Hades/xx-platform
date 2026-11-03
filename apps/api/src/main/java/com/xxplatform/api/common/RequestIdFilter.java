package com.xxplatform.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求建立 traceId：写入日志 MDC、响应头 {@code X-Trace-Id}，并暴露到 {@link #currentTraceId()}。
 *
 * <p>契约要求（{@code api-conventions.md} §1）：全部响应带 {@code X-Trace-Id}，
 * 且与 JSON 壳里的 {@code traceId} 相同。因此业务代码取 traceId 只能用
 * {@link ApiResponses#success(Object, String)} 配合 {@link #currentTraceId()}，
 * 不要各写一个来源。
 *
 * <h2>traceId 由谁决定</h2>
 *
 * <p><b>默认由服务端生成</b>（随机 UUID）。综合测试报告 2026-10-16 实测发现：
 * 客户端传 {@code X-Trace-Id: audit-client-controlled-123} 时旧实现原样回显，
 * 与 {@code docs/system-design/08_可靠性与可观测性设计.md} "traceId 由服务端生成"冲突，
 * 也和"审计/日志可信"的要求冲突——调用方可以任意伪造、覆盖或复用别人的 traceId。
 *
 * <p>但"完全不接受上游 traceId"也不对：接入网关或链路追踪时，需要把上游的 traceId
 * 透传下来才能跨系统串联。所以保留透传能力，但**默认关闭**，且只在两个条件同时满足时生效：
 * <ol>
 *   <li>{@code xx.trace.accept-client-trace-id=true}（默认 false，仅当前面确实有可信代理时打开）；</li>
 *   <li>传入值符合 {@link #TRACE_ID_PATTERN}（防止注入换行、超长串、日志伪造）。</li>
 * </ol>
 * 任一不满足就用服务端生成的值，<b>不做静默截断</b>。这样既修掉"谁都能决定"的问题，
 * 又不用等将来接网关时再推翻。
 *
 * <p><b>顺序必须是 {@code HIGHEST_PRECEDENCE}</b>：CORS 过滤器（{@code HIGHEST_PRECEDENCE + 10}）
 * 可能在进入 MVC 之前就拒绝请求，而拒绝响应里也要写 traceId。排在最前面才能保证
 * 任何后续过滤器/控制器出错时，{@code X-Trace-Id} 与响应壳里的 {@code traceId} 都一致。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 请求/响应用的追踪头名，契约固定。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** 日志 MDC key，logback pattern 里的 %X{traceId} 对应它。 */
    public static final String TRACE_MDC_KEY = "traceId";

    /**
     * 可接受的 traceId 形状：字母数字开头，允许 {@code - _ . :}，总长 8–64。
     *
     * <p>排除空白、换行、引号与中文，避免日志注入和非法头值；长度上限与契约
     * {@code maxLength: 64} 一致。注意这里比"必须 UUID"更宽松，是为了兼容常见网关
     * 的 W3C traceparent 片段式 ID。
     */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,63}$");

    private final boolean acceptClientTraceId;

    public RequestIdFilter(
            @Value("${xx.trace.accept-client-trace-id:false}") boolean acceptClientTraceId) {
        this.acceptClientTraceId = acceptClientTraceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
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

    /**
     * 决定本次请求的 traceId。
     *
     * <p>默认服务端生成；仅当显式允许透传且上游值合法时才采用上游值。
     */
    private String resolveTraceId(HttpServletRequest request) {
        if (acceptClientTraceId) {
            String candidate = request.getHeader(TRACE_HEADER);
            if (candidate != null) {
                String trimmed = candidate.trim();
                if (TRACE_ID_PATTERN.matcher(trimmed).matches()) {
                    return trimmed;
                }
            }
        }
        return UUID.randomUUID().toString();
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
