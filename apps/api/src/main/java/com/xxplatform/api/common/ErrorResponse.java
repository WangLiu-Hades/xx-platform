package com.xxplatform.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 契约失败响应体：{@code {code, message, data:null, traceId, details}}。
 *
 * <p>为什么单独一个类而不是给成功壳加 {@code details} 字段：
 * <ul>
 *   <li>成功体与失败体的字段集合不同（成功不带 details），用一个类会互相污染。</li>
 *   <li>{@code data} 必须在失败体中出现且为 null —— 这是契约对错误结构的硬要求，
 *       前端运行时校验会检查它；因此本类显式 {@code ALWAYS}，不受全局 NON_NULL 影响。</li>
 * </ul>
 *
 * <p>由 {@link ApiResponses#error} 与 {@code GlobalExceptionHandler} 统一构造，
 * 业务代码不要手写这个结构。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ErrorResponse extends ApiResponses {

    private final List<FieldErrorDetail> details;

    ErrorResponse(String code, String message, String traceId, List<FieldErrorDetail> details) {
        super(code, message, null, traceId);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    /** 字段级明细；无明细时为空数组（不是 null）。 */
    public List<FieldErrorDetail> getDetails() {
        return details;
    }
}
