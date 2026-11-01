package com.xxplatform.api.service.approval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.ApprovalProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * M12：通用 db profile 没有部署密钥时审批必须不可用（fail closed），
 * 且密钥格式/版本、随机 nonce、认证加密这些性质不能靠真库才能验证。
 *
 * <p>这里同时盯住仓库本身：{@code application-db.yml} 不允许出现任何内置默认密钥，
 * 只能引用部署注入的环境变量。
 */
class ApprovalCryptoConfigurationTests {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("unit-approval-key-0123456789abcd".getBytes(StandardCharsets.UTF_8));

    private static ApprovalProperties properties(String key) {
        ApprovalProperties properties = new ApprovalProperties();
        properties.setPayloadKey(key);
        return properties;
    }

    @Test
    void missingDeploymentKeyDisablesApprovalInsteadOfFallingBackToARepositoryValue() {
        ApprovalPayloadCipher cipher = new ApprovalPayloadCipher(properties(""));

        assertFalse(cipher.configured(), "未注入部署密钥时不能声称可用");

        ApiException seal = assertThrows(ApiException.class,
                () -> cipher.seal("{}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, seal.code());
        assertTrue(seal.getMessage().contains("密钥未配置"), seal.getMessage());

        ApiException open = assertThrows(ApiException.class, () -> cipher.open(new byte[28]));
        assertEquals(ErrorCode.SYSTEM_UNAVAILABLE, open.code());
    }

    @Test
    void repositoryConfigurationHasNoBuiltInApprovalKey() throws IOException {
        // Surefire 的 user.dir 是 apps/api。
        Path yml = Path.of("src", "main", "resources", "application-db.yml");
        String text = Files.readString(yml, StandardCharsets.UTF_8);

        assertTrue(text.contains("payload-key: ${XX_APPROVAL_PAYLOAD_KEY:}"),
                "db profile 只能从部署注入的环境变量取密钥");
        assertTrue(text.contains("approval:"), "db profile 必须存在 xx.approval 配置段");
        assertFalse(text.matches("(?s).*payload-key:\\s*[A-Za-z0-9+/=]{20,}.*"),
                "仓库配置里不允许出现内置的 base64 密钥常量");
        assertFalse(text.contains("local-dev"), "密钥标识/密钥都不能在部署配置里写死");
    }

    @Test
    void malformedKeyFailsFastAtStartup() {
        assertThrows(IllegalStateException.class, () -> new ApprovalPayloadCipher(properties("not-base64!!")));
        String tooShort = Base64.getEncoder().encodeToString(new byte[31]);
        assertThrows(IllegalStateException.class, () -> new ApprovalPayloadCipher(properties(tooShort)));
        String tooLong = Base64.getEncoder().encodeToString(new byte[33]);
        assertThrows(IllegalStateException.class, () -> new ApprovalPayloadCipher(properties(tooLong)));
    }

    @Test
    void keyIdIsPersistedWithTheCiphertextButTheKeyNeverIs() {
        ApprovalProperties properties = properties(KEY);
        properties.setKeyId("it-rotation-v7");
        ApprovalPayloadCipher cipher = new ApprovalPayloadCipher(properties);

        assertTrue(cipher.configured());
        assertEquals("it-rotation-v7", cipher.keyId());
        assertFalse(cipher.keyId().contains(KEY), "encryption_key_id 只能是版本标识，不能是密钥本身");
    }

    @Test
    void everySealUsesAFreshNonceAndRoundTrips() {
        ApprovalPayloadCipher cipher = new ApprovalPayloadCipher(properties(KEY));
        byte[] plaintext = "{\"operationId\":\"createExport\"}".getBytes(StandardCharsets.UTF_8);

        byte[] first = cipher.seal(plaintext);
        byte[] second = cipher.seal(plaintext);
        assertNotEquals(Base64.getEncoder().encodeToString(first), Base64.getEncoder().encodeToString(second),
                "同一明文两次加密必须因随机 nonce 不同而不同");
        assertArrayEquals(plaintext, cipher.open(first));
        assertArrayEquals(plaintext, cipher.open(second));
    }

    @Test
    void tamperedCiphertextAndWrongKeyCannotDecrypt() {
        ApprovalPayloadCipher cipher = new ApprovalPayloadCipher(properties(KEY));
        byte[] sealed = cipher.seal("{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> cipher.open(tampered), "GCM 认证标签必须能发现篡改");

        String otherKey = Base64.getEncoder()
                .encodeToString("unit-approval-key-fedcba98765432".getBytes(StandardCharsets.UTF_8));
        ApprovalPayloadCipher wrong = new ApprovalPayloadCipher(properties(otherKey));
        assertThrows(IllegalStateException.class, () -> wrong.open(sealed), "换密钥必须解不开历史密文");
    }

    @Test
    void truncatedCiphertextIsRejected() {
        ApprovalPayloadCipher cipher = new ApprovalPayloadCipher(properties(KEY));
        byte[] sealed = cipher.seal("{}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> cipher.open(new byte[] {1, 2, 3}));
        assertThrows(IllegalStateException.class,
                () -> cipher.open(java.util.Arrays.copyOf(sealed, 12)), "只有 nonce 没有密文时也必须失败");
    }
}
