package com.xxplatform.api.common;

import com.xxplatform.api.common.ApiResponses.FieldErrorDetail;
import java.util.List;

/**
 * 契约 {@code IdempotencyKey} 请求头的统一校验（{@code POST /exports}、{@code POST /tasks/{id}/cancel}、
 * {@code POST /tasks/{id}/retry} 共用）。
 *
 * <p>抽出来是因为同一个 header 在三处出现，而契约只写了一遍形状（必填、{@code minLength: 16}、
 * {@code maxLength: 160}）。三份复制迟早会漂移：一处允许 8 字符、另一处 16，客户端按哪个写？
 *
 * <p>为什么长度下限是 16：太短的键（如 {@code "1"}）很容易在客户端被复用，把"不同请求"
 * 当成"同一次重放"；上限 160 与库列 {@code varchar(200)} 对齐，留出服务端可能的命名前缀。
 */
public final class IdempotencyKeyHeader {

    /** 契约里的 header 名。 */
    public static final String NAME = "Idempotency-Key";
    /** 契约 {@code minLength}。 */
    public static final int MIN_LENGTH = 16;
    /** 契约 {@code maxLength}。 */
    public static final int MAX_LENGTH = 160;

    private IdempotencyKeyHeader() {
    }

    /**
     * 校验并返回去掉首尾空白的键。
     *
     * @throws ApiException 缺失、空白或长度越界时抛 400 {@code VALIDATION_ERROR}，
     *                      {@code details[0].field} 固定为 {@code Idempotency-Key}
     */
    public static String require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validation("缺少 Idempotency-Key 请求头");
        }
        String key = raw.trim();
        if (key.length() < MIN_LENGTH || key.length() > MAX_LENGTH) {
            throw validation("Idempotency-Key 长度必须在 " + MIN_LENGTH + "–" + MAX_LENGTH + " 字符之间");
        }
        return key;
    }

    private static ApiException validation(String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, reason,
                List.of(new FieldErrorDetail(NAME, reason)), null);
    }
}
