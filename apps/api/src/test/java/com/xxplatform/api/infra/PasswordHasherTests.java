package com.xxplatform.api.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordHasher} 的行为约束。
 *
 * <p>用纯 JUnit + AssertJ，**不用 Mockito**：本机沙箱下 Mockito 的 ByteBuddy self-attach
 * 被拦（{@code Could not self-attach to current VM using external process}），
 * 而口令哈希本来也不需要 mock 任何东西。
 *
 * <p>注意 {@code ITERATIONS=600000}，每次 {@code hash}/{@code verify} 约 600 ms。
 * 本类共 6 个用例、约 10 次派生，合计 ~7 秒，可接受；如果将来觉得慢，
 * 不要降低迭代数，而是把用例数压下来（迭代数是安全参数，不是性能旋钮）。
 */
class PasswordHasherTests {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    @DisplayName("hash → verify 往返成功，且存储值是可识别的自描述格式")
    void hashThenVerify() {
        String stored = hasher.hash("correct-horse-battery-staple");

        assertThat(stored).startsWith(PasswordHasher.prefix() + "$" + PasswordHasher.iterations() + "$");
        // 前缀 + 迭代数 + 盐 + 摘要 = 4 段（升级算法时按前缀分派，所以段数也是契约的一部分）
        assertThat(stored.split("\\$")).hasSize(4);
        // 列宽是 varchar(255)，留出余量给将来更长的算法名
        assertThat(stored.length()).isLessThan(255);

        assertThat(hasher.verify("correct-horse-battery-staple", stored)).isTrue();
    }

    @Test
    @DisplayName("错误口令一律 false（含大小写、前后空格差异）")
    void wrongPasswordRejected() {
        String stored = hasher.hash("Secret-1234");

        assertThat(hasher.verify("secret-1234", stored)).isFalse();
        assertThat(hasher.verify("Secret-1234 ", stored)).isFalse();
        assertThat(hasher.verify("", stored)).isFalse();
        assertThat(hasher.verify(null, stored)).isFalse();
    }

    @Test
    @DisplayName("同一口令两次哈希结果不同（盐随机），但都能校验通过")
    void saltIsRandomPerHash() {
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.verify("same-password", first)).isTrue();
        assertThat(hasher.verify("same-password", second)).isTrue();
    }

    @Test
    @DisplayName("存储值损坏时返回 false 而不是抛异常（库数据坏了不该变成 500 之外的怪状态）")
    void malformedStoredValueIsRejected() {
        assertThat(hasher.verify("x", null)).isFalse();
        assertThat(hasher.verify("x", "")).isFalse();
        assertThat(hasher.verify("x", "   ")).isFalse();
        assertThat(hasher.verify("x", "plain-text-password")).isFalse();
        assertThat(hasher.verify("x", "bcrypt$10$abc$def")).isFalse();
        assertThat(hasher.verify("x", "pbkdf2-sha256$notanumber$c2FsdA$aGFzaA")).isFalse();
        assertThat(hasher.verify("x", "pbkdf2-sha256$0$c2FsdA$aGFzaA")).isFalse();
        assertThat(hasher.verify("x", "pbkdf2-sha256$1000$!!!$!!!")).isFalse();
    }

    @Test
    @DisplayName("超长口令不被截断：前 72 字节相同、尾部不同的两个口令互不通过")
    void longPasswordNotTruncatedAt72Bytes() {
        // 这正是 BCrypt 的行为差异所在：BCrypt 只用前 72 字节，
        // 于是这两个口令在 BCrypt 下会互相通过。PBKDF2 必须能区分它们。
        String base = "a".repeat(72);
        String passwordOne = base + "ONE";
        String passwordTwo = base + "TWO";

        String stored = hasher.hash(passwordOne);

        assertThat(hasher.verify(passwordOne, stored)).isTrue();
        assertThat(hasher.verify(passwordTwo, stored)).isFalse();
    }

    @Test
    @DisplayName("UTF-8 口令（中文/emoji）可正常往返")
    void nonAsciiPasswordRoundTrip() {
        String password = "口令-测试-🔐";
        String stored = hasher.hash(password);

        assertThat(hasher.verify(password, stored)).isTrue();
        assertThat(hasher.verify("口令-测试", stored)).isFalse();
        // 口令原文绝不能出现在存储值里
        assertThat(stored).doesNotContain(new String(password.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
    }
}
