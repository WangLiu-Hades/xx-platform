package com.xxplatform.api.common;

/**
 * 契约错误码（{@code contracts/api/error-codes.md} 与 openapi.yaml 的 {@code ErrorCode} 枚举逐项一致）。
 *
 * <p>约定：
 * <ul>
 *   <li>前端按 {@code code} 判断，禁止按中文 message 判断；本枚举的字符串值不可更名。</li>
 *   <li>每个错误码对应唯一 HTTP 状态，见 {@link #httpStatus()}；映射关系以 error-codes.md 为准。</li>
 *   <li>批量接口的 200 不代表逐项成功，逐项失败复用这里的取值。</li>
 * </ul>
 *
 * <p>TODO(阶段 2)：与 {@code ApiExceptionHandler} 一起完善到全 34 项的实际使用点；
 * 当前枚举已按契约补全，避免后续再改一遍字面量。
 */
public enum ErrorCode {

    /** 400 字段/格式/范围或跨字段非法；details.field 给 camelCase 路径。 */
    VALIDATION_ERROR(400),
    /**
     * 405 请求方法不被该路由支持。
     *
     * <p><b>这一项是契约需要补充的</b>：{@code openapi.yaml} 当前每个操作只声明了
     * 400/401/403/500，没有 405。但把一个 GET-only 路由用 POST 命中时，
     * 强行报成 500 {@code SYSTEM_INTERNAL} 会把"客户端错误"错误地表达成"服务故障"
     * （综合测试报告 2026-10-16 已实测确认），语义与日志都会失真。
     *
     * <p>因此骨架按 HTTP 标准语义返回 405，并已提报在契约通用响应中补 405 声明。
     * 在契约补声明之前，本码是"实现比契约多一个状态码"的唯一例外，不要据此再扩展其他码。
     */
    METHOD_NOT_ALLOWED(405),
    /** 401 未建立会话。 */
    AUTH_REQUIRED(401),
    /** 401 凭据不正确。 */
    AUTH_INVALID_CREDENTIALS(401),
    /** 403 同步器 token 或可信 Origin 不匹配。 */
    AUTH_CSRF_INVALID(403),
    /** 401 会话过期，前端清身份且不自动重放写请求。 */
    AUTH_SESSION_EXPIRED(401),
    /** 403 操作级无权。 */
    PERMISSION_DENIED(403),
    /** 403 数据/场所范围被收缩。 */
    PERMISSION_DATA_SCOPE(403),
    /** 422 筛选条件非法。 */
    DATA_INVALID_FILTER(422),
    /** 422 超出在线查询预算（maxOnlineQuerySeconds/maxOnlineWindowDays）。 */
    DATA_QUERY_TOO_BROAD(422),
    /** 404 不存在/已删/不可见；越权单对象用它隐藏存在性。 */
    DATA_NOT_FOUND(404),
    /** 409 状态/引用/唯一性冲突。 */
    RESOURCE_CONFLICT(409),
    /** 409 ETag/version 不匹配。 */
    RESOURCE_VERSION_CONFLICT(409),
    /** 409 任务状态冲突。 */
    TASK_CONFLICT(409),
    /** 409 终态或副作用不可撤回。 */
    TASK_NOT_CANCELLABLE(409),
    /** 422 任务执行失败。 */
    TASK_FAILED(422),
    /** 422 分析规则非法。 */
    ANALYSIS_INVALID_RULE(422),
    /** 422 设备离线。 */
    DEVICE_OFFLINE(422),
    /** 422 告警规则未生效。 */
    ALERT_RULE_INACTIVE(422),
    /** 422 文件非法。 */
    FILE_INVALID(422),
    /** 410 资产/结果已清理。 */
    FILE_EXPIRED(410),
    /** 429 限流，带 Retry-After 秒数。 */
    RATE_LIMITED(429),
    /** 500 内部错误。 */
    SYSTEM_INTERNAL(500),
    /** 503 依赖不可用，带 Retry-After 秒数。 */
    SYSTEM_UNAVAILABLE(503),
    /** 410 Context 过期，需重新建立。 */
    CONTEXT_EXPIRED(410),
    /** 409 任务结果未就绪，轮询到 SUCCESS 再读。 */
    TASK_RESULT_NOT_READY(409),
    /** 422 历史未决错误码，保留兼容；当前按PD暂定方案开发，实际资源门禁另校验。 */
    REQUIREMENT_PENDING(422),
    /** 422 收件人或计划 recipientId 非法。 */
    NOTIFICATION_INVALID_RECIPIENT(422),
    /** 422 模型参数编码/类型/签认范围非法。 */
    MODEL_PARAMETER_INVALID(422),
    /** 422 CAPTURE/TARGET 不兼容，不隐式转换。 */
    DATASET_ELEMENT_TYPE_MISMATCH(422),
    /** 413 超 uploadPolicy.maxBytes。 */
    FILE_TOO_LARGE(413),
    /** 415 实际文件类型不允许。 */
    FILE_MEDIA_UNSUPPORTED(415),
    /** 400 游标失效/被篡改/条件不匹配。 */
    DATA_CURSOR_INVALID(400),
    /** 409 同幂等键不同 payload。 */
    IDEMPOTENCY_CONFLICT(409),
    /** 422 几何拓扑/范围非法。 */
    MAP_GEOMETRY_INVALID(422);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    /** 契约声明的 HTTP 状态码。 */
    public int httpStatus() {
        return httpStatus;
    }
}
