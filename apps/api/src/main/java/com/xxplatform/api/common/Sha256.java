package com.xxplatform.api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 工具（用于"库内不存原始凭据"的哈希指纹）。
 *
 * <p>适用对象：**本身已是高熵随机值**的凭据，例如
 * {@code auth.csrf_session.cookie_hash}/{@code token_hash} 与
 * {@code auth.user_session.sid_hash}。这些值来自 {@code SecureRandom}（256 位），
 * 不存在字典攻击面，因此用无盐 SHA-256 做"不可逆存储"是合适的。
 *
 * <p><b>不适用口令</b>：口令是低熵人类输入，必须用带盐慢哈希（见
 * {@code auth.PasswordHasher}）。两者不可互换——把口令丢进本类会让整库口令
 * 在离线爆破下几秒内被还原。
 */
public final class Sha256 {

    private Sha256() {
    }

    /**
     * 计算 UTF-8 字节的 SHA-256。
     *
     * @param value 原文（null 视为非法，调用方应先判空）
     * @return 32 字节摘要
     */
    public static byte[] of(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须提供的算法，走到这里说明 JRE 被裁剪过
            throw new IllegalStateException("JRE 缺少 SHA-256", e);
        }
    }

    /**
     * 常量时间比较两个摘要。
     *
     * <p>用 {@link MessageDigest#isEqual} 而不是 {@code Arrays.equals}：后者发现首个
     * 不同字节就返回，耗时随匹配前缀长度变化，可被用来逐字节猜测凭据。
     *
     * @param a 摘要 A（可为 null，视为不相等）
     * @param b 摘要 B（可为 null，视为不相等）
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}
