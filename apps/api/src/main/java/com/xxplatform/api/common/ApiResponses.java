package com.xxplatform.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 统一响应壳（契约 {@code {code,message,data,traceId}} / 错误 {@code {code,message,data:null,traceId,details}}）。
 *
 * <p>契约要点（{@code contracts/api/api-conventions.md} §1、{@code error-codes.md}）：
 * <ul>
 *   <li>成功 {@code code} 是字符串 {@code "0"}，不是数字 200。</li>
 *   <li>成功体只有 {@code code/message/data/traceId} 四个字段，<b>不带 details</b>；
 *       失败体才带 {@code details}（可为空数组），且 <b>data 必须出现并为 null</b>。
 *       这两种形状由 {@link #success} 与 {@link ErrorResponse} 分别固定，不要用全局
 *       NON_NULL 策略去"顺便"解决，否则会把失败体的 data 字段也吃掉。</li>
 *   <li>所有响应带 {@code X-Trace-Id} 头，值与壳内 {@code traceId} 相同（见 {@link RequestIdFilter}）。</li>
 *   <li>文件下载是唯一例外：不套此壳，只带 {@code X-Trace-Id} 头；出错时改回本壳。</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponses {

    /** 成功响应的固定 code 值。 */
    public static final String SUCCESS_CODE = "0";

    /** 成功响应的固定 message 值。 */
    public static final String SUCCESS_MESSAGE = "success";

    private final String code;
    private final String message;
    private final Object data;
    private final String traceId;

    /** 供子类（错误壳）使用。 */
    protected ApiResponses(String code, String message, Object data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /** 成功：{@code {code:"0", message:"success", data, traceId}}。 */
    public static ApiResponses success(Object data, String traceId) {
        return new ApiResponses(SUCCESS_CODE, SUCCESS_MESSAGE, data, traceId);
    }

    /** 失败：{@code {code, message, data:null, traceId, details}}。 */
    public static ErrorResponse error(ErrorCode code, String message, String traceId, List<FieldErrorDetail> details) {
        return new ErrorResponse(code.name(), message, traceId, details == null ? List.of() : details);
    }

    /** 失败（无字段明细）。 */
    public static ErrorResponse error(ErrorCode code, String message, String traceId) {
        return error(code, message, traceId, List.of());
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * 字段级错误明细，对应契约 {@code FieldError}（required: field, reason）。
     *
     * @param field  camelCase 字段路径
     * @param reason 可展示的原因
     */
    public record FieldErrorDetail(String field, String reason) {
    }
}
