package com.xxplatform.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@link IdempotencyKeyHeader} 的形状校验（契约 {@code minLength: 16}、{@code maxLength: 160}）。
 *
 * <p>这条规则被导出、取消、重试三处复用，所以单独测一遍边界：16 与 160 本身合法，
 * 15 与 161 非法，首尾空白被裁掉。
 */
class IdempotencyKeyHeaderTests {

    @Test
    void rejectsMissingKey() {
        ApiException failure = assertThrows(ApiException.class, () -> IdempotencyKeyHeader.require(null));

        assertEquals(ErrorCode.VALIDATION_ERROR, failure.code());
        assertEquals(IdempotencyKeyHeader.NAME, failure.details().get(0).field());
    }

    @Test
    void rejectsBlankKey() {
        assertThrows(ApiException.class, () -> IdempotencyKeyHeader.require("   "));
    }

    @Test
    void rejectsKeyBelowMinimum() {
        assertThrows(ApiException.class, () -> IdempotencyKeyHeader.require("a".repeat(15)));
    }

    @Test
    void rejectsKeyAboveMaximum() {
        assertThrows(ApiException.class, () -> IdempotencyKeyHeader.require("a".repeat(161)));
    }

    @Test
    void acceptsBoundaryLengthsAndTrims() {
        assertEquals("a".repeat(16), IdempotencyKeyHeader.require("a".repeat(16)));
        assertEquals("a".repeat(160), IdempotencyKeyHeader.require("a".repeat(160)));
        assertEquals("idem-key-000000000001", IdempotencyKeyHeader.require("  idem-key-000000000001  "));
    }
}
