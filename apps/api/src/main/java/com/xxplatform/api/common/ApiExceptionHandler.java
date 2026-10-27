package com.xxplatform.api.common;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常 → 契约错误壳 的兜底。
 *
 * <p>契约要求：每个操作都声明了 400/401/403/500，所以异常兜底不是可选项；
 * HTTP 状态与 {@code code} 必须由同一处决定，禁止在业务代码里各写各的。
 *
 * <p>当前覆盖两类：<b>框架层异常</b>（参数校验、报文解析含未知字段、方法不支持、路径不存在、
 * 未捕获异常）与<b>业务异常</b> {@link ApiException}。后者让 Service/Controller 只需
 * "抛一个带 {@link ErrorCode} 的异常"，错误壳与 HTTP 状态仍由本类唯一决定。
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
     *
     * <p>同时补 {@code Allow} 头：RFC 9110 要求 405 说明该资源支持哪些方法，
     * 不补这一处会让"方法不被支持"变成客户端无法自助纠正的死胡同。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity<ErrorResponse> error =
                error(ErrorCode.METHOD_NOT_ALLOWED, "请求方法不被支持", List.of());
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported == null || supported.isEmpty()) {
            return error;
        }
        String allow = supported.stream()
                .filter(Objects::nonNull)
                .map(HttpMethod::name)
                .sorted()
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(error.getStatusCode())
                .headers(headers -> {
                    headers.addAll(error.getHeaders());
                    headers.set(HttpHeaders.ALLOW, allow);
                })
                .body(error.getBody());
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

    /**
     * multipart 里的文件超过容器上限 → 413 {@code FILE_TOO_LARGE}。
     *
     * <p>这条分支与业务层的 {@code xx.file.max-bytes} 是两道闸门：容器闸门先于任何控制器代码，
     * 触发时请求体已被拒收（不会把超大文件读进内存/临时目录）。不映射的话它会掉进
     * {@link #handleUnexpected(Exception)} 变成 500，前端无法区分"文件太大"和"服务坏了"。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return error(ErrorCode.FILE_TOO_LARGE, "文件超过上传上限", List.of());
    }

    /** multipart 缺少必需的文件/表单部件 → 400（带字段名，便于前端定位）。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return error(ErrorCode.VALIDATION_ERROR, "缺少必填表单部件",
                List.of(new FieldErrorDetail(ex.getRequestPartName(), "必填部件缺失")));
    }

    /** 路径不存在 → 404 DATA_NOT_FOUND（不暴露内部路由信息）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return error(ErrorCode.DATA_NOT_FOUND, "资源不存在", List.of());
    }

    /**
     * 业务异常 → 由其自带的 {@link ErrorCode} 决定状态码与 {@code code}。
     *
     * <p>{@code Retry-After} 只对 429 与 503 写：契约（{@code error-codes.md}）把
     * {@code Retry-After} 定义为 429/503 的响应头，其他状态码带上它会让前端误以为可以退避重试。
     * 值是**向上取整的秒数**且至少 1 —— {@code Retry-After: 0} 等于"立刻重试"，
     * 那正是限流与"依赖还没好"要防的行为。
     *
     * <p>日志用 debug：业务失败是控制流（口令错误、限流），不是缺陷。真正的故障仍走
     * {@link #handleUnexpected(Exception)} 的 error 级别。
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.debug("业务异常 code={} message={} traceId={}",
                ex.code(), ex.getMessage(), RequestIdFilter.currentTraceIdOrFallback());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.code().httpStatus());
        Duration retryAfter = ex.retryAfter();
        boolean retryable = ex.code() == ErrorCode.RATE_LIMITED || ex.code() == ErrorCode.SYSTEM_UNAVAILABLE;
        if (retryable && retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1L, retryAfter.toSeconds())));
        }
        return builder.body(ApiResponses.error(
                ex.code(), ex.getMessage(), RequestIdFilter.currentTraceIdOrFallback(), ex.details()));
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
