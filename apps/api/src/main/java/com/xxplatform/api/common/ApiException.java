package com.xxplatform.api.common;

import java.time.Duration;
import java.util.List;

/**
 * 业务异常：由 Service/Controller 抛出，统一由 {@link ApiExceptionHandler} 翻译成契约错误体。
 *
 * <p>为什么需要它：没有这个类时，Controller 只能"手工拼一个 {@code ErrorResponse} 并返回
 * ResponseEntity"或者抛裸 {@link RuntimeException}（被兜底 handler 变成 500）。前者会让
 * 错误壳的构造逻辑散落到每个端点，后者把 4xx 业务失败伪装成服务端故障——两者都会让
 * 契约的一致性依赖"每个开发者都记得写对"。
 *
 * <p>使用约定：
 * <ul>
 *   <li>只用于**可预期的业务失败**（401/403/404/409/422/429 等）。真正的缺陷仍然抛普通异常，
 *       由兜底 handler 变成 500 —— 别把 bug 包装成 4xx 掩盖掉。</li>
 *   <li>{@code message} 是**可展示给用户的文本**，不得包含内部细节（SQL、堆栈、账号是否存在）。
 *       诊断信息应进日志，并靠 {@code traceId} 关联。</li>
 * </ul>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiResponses.FieldErrorDetail> details;

    /** 仅供 429/503 使用：告知客户端多久后可以重试（写入 {@code Retry-After} 响应头）。 */
    private final Duration retryAfter;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public ApiException(ErrorCode code, String message, List<ApiResponses.FieldErrorDetail> details) {
        this(code, message, details, null);
    }

    public ApiException(ErrorCode code, String message, List<ApiResponses.FieldErrorDetail> details, Duration retryAfter) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
        this.retryAfter = retryAfter;
    }

    /**
     * 构造"被限流"异常（429 {@code RATE_LIMITED}）。
     *
     * @param message    可展示文本
     * @param retryAfter 建议的重试间隔；为 null 或非正数时不写 {@code Retry-After} 头
     */
    public static ApiException rateLimited(String message, Duration retryAfter) {
        return new ApiException(ErrorCode.RATE_LIMITED, message, List.of(), retryAfter);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiResponses.FieldErrorDetail> details() {
        return details;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /**
     * 不收集堆栈。
     *
     * <p>业务异常是**控制流**而非缺陷，每次登录失败都抓一遍堆栈既慢（毫秒级）又会在日志里
     * 淹没真正的故障。需要定位时靠 {@code traceId} + Service 里主动打的日志。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
