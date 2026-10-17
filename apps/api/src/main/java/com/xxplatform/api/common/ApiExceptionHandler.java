package com.xxplatform.api.common;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常 → 契约错误壳 的兜底。
 *
 * <p>契约要求：每个操作都声明了 400/401/403/500，所以异常兜底不是可选项；
 * HTTP 状态与 {@code code} 必须由同一处决定，禁止在业务代码里各写各的。
 *
 * <p>当前覆盖"框架层异常"：参数校验、报文解析（含未知字段）、方法不支持、路径不存在、未捕获异常。
 * 业务异常在阶段 2 加入：先定义 {@code ApiException(ErrorCode, message, details)}，
 * 再在下面加一个 handler，不要在 Controller 里手动拼错误壳。
 *
 * <p><b>顺序不能乱</b>：Spring 会选最具体的 {@code @ExceptionHandler}；但任何新加的框架异常
 * （如 {@code HttpRequestMethodNotSupportedException}、{@code MissingServletRequestPartException}）
 * 都必须显式处理，否则会掉进 {@code Exception} 兜底变成 500，把客户端错误伪装成服务故障。
 *
 * <p>越权语义提醒：单对象不可见用 404 {@code DATA_NOT_FOUND} 隐藏存在性，
 * 403 {@code PERMISSION_DENIED}/{@code PERMISSION_DATA_SCOPE} 只用于"操作整体无权"或"范围被收缩"。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 请求体校验失败 → 400 VALIDATION_ERROR，details 给出 camelCase 字段路径。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return error(ErrorCode.VALIDATION_ERROR, "字段校验失败", details);
    }

    /** 方法参数校验失败（@Validated 上的路径/查询参数） → 400。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDetail> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorDetail(
                                String.valueOf(result.getMethodParameter().getParameterName()),
                                error.getDefaultMessage())))
                .toList();
        return error(ErrorCode.VALIDATION_ERROR, "参数校验失败", details);
    }

    /** 直接抛出的约束校验异常 → 400。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDetail(lastNode(v), v.getMessage()))
                .toList();
        return error(ErrorCode.VALIDATION_ERROR, "参数校验失败", details);
    }

    /**
     * 报文无法解析 → 400。
     *
     * <p>契约要求所有业务 DTO 是 {@code additionalProperties:false}，因此"未知字段"必须被拒绝
     * 而不是静默吞掉；{@code WebConfiguration} 已开启 {@code failOnUnknownProperties}，
     * Jackson 会把它包成 {@link HttpMessageNotReadableException}。
     * 这里把字段名还原进 {@code details}，让前端能定位到底哪个字段多传/拼错了。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        UnrecognizedPropertyException unknown = findCause(ex, UnrecognizedPropertyException.class);
        if (unknown != null) {
            String field = unknown.getPropertyName();
            return error(ErrorCode.VALIDATION_ERROR, "请求包含未知字段",
                    List.of(new FieldErrorDetail(field, "该字段不在请求 Schema 中（additionalProperties:false）")));
        }
        log.debug("请求报文不可解析: {}", ex.getMessage());
        return error(ErrorCode.VALIDATION_ERROR, "请求报文不可解析", List.of());
    }

    /**
     * 请求方法不被该路由支持 → 405 {@code METHOD_NOT_ALLOWED}。
     *
     * <p>必须单独处理：否则会落到下面的 {@code Exception} 兜底变成 500，
     * 把客户端错误误报成服务故障（综合测试报告 2026-10-16 实测确认过这个现象）。
     * 契约当前未声明 405，已提报补充；在此之前按 HTTP 标准语义返回。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不被支持", List.of());
    }

    /** 缺少必填查询参数 → 400。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(ErrorCode.VALIDATION_ERROR, "缺少必填参数",
                List.of(new FieldErrorDetail(ex.getParameterName(), "必填参数缺失")));
    }

    /** 参数类型不匹配（例如 UUID 格式错误） → 400。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(ErrorCode.VALIDATION_ERROR, "参数类型非法",
                List.of(new FieldErrorDetail(ex.getName(), "类型或格式非法")));
    }

    /** 路径不存在 → 404 DATA_NOT_FOUND（不暴露内部路由信息）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return error(ErrorCode.DATA_NOT_FOUND, "资源不存在", List.of());
    }

    /** 其他未捕获异常 → 500 SYSTEM_INTERNAL，只记日志、不外泄堆栈。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("未处理异常，traceId={}", RequestIdFilter.currentTraceIdOrFallback(), ex);
        return error(ErrorCode.SYSTEM_INTERNAL, "服务内部错误", List.of());
    }

    private static ResponseEntity<ErrorResponse> error(ErrorCode code, String message, List<FieldErrorDetail> details) {
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponses.error(code, message, RequestIdFilter.currentTraceIdOrFallback(), details));
    }

    /** 沿异常 cause 链查找指定类型（Jackson 会把根因包两层以上）。 */
    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
        int idx = path.lastIndexOf('.');
        return idx < 0 ? path : path.substring(idx + 1);
    }
}
