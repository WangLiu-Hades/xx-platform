package com.xxplatform.api.service.approval;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.config.ApprovalProperties;

/**
 * 审批冻结载荷的对称加密（AES-256-GCM）。
 *
 * <p>为什么必须加密：{@code operation.action_approval.frozen_payload_ciphertext} 是 NOT NULL，
 * 而载荷里会出现设备口令、身份证号、导出筛选条件等敏感值。审批的复核人只能看到
 * {@code reviewItems} 里脱敏后的摘要，原始载荷直到执行时才解封——所以密文落库、明文只在
 * 内存里出现一次，是这条链路的既定形态。
 *
 * <p>密文格式 {@code nonce(12) || ciphertext+tag}，每次加密都生成新 nonce（GCM 的 nonce
 * 重用会直接毁掉机密性）。密钥来自 {@code xx.approval.payload-key}：未配置时**不放行**
 * （503），不退回明文；配置长度不是 32 字节同样视为配置错误。
 *
 * <p>密钥轮换：库里另存 {@code encryption_key_id}，将来支持"旧密文用旧 key 解、新密文用新
 * key 封"；本轮只有一个本地开发 key，轮换流程不实现。
 */
@Component
public class ApprovalPayloadCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final ApprovalProperties properties;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ApprovalPayloadCipher(ApprovalProperties properties) {
        this.properties = properties;
        String raw = properties.getPayloadKey();
        if (raw == null || raw.isBlank()) {
            this.key = null;
            return;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("xx.approval.payload-key 不是合法的 base64", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("xx.approval.payload-key 必须是 32 字节（AES-256），实际 " + bytes.length);
        }
        this.key = new SecretKeySpec(bytes, KEY_ALGORITHM);
    }

    /** 是否配置了密钥；未配置时创建审批直接 503，而不是静默落明文。 */
    public boolean configured() {
        return key != null;
    }

    public String keyId() {
        return properties.getKeyId();
    }

    public byte[] seal(byte[] plaintext) {
        requireKey();
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("审批载荷加密失败", e);
        }
    }

    public byte[] open(byte[] blob) {
        requireKey();
        if (blob == null || blob.length <= NONCE_BYTES) {
            throw new IllegalStateException("审批载荷密文长度异常");
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] sealed = new byte[buffer.remaining()];
        buffer.get(sealed);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(sealed);
        } catch (GeneralSecurityException e) {
            // 密钥不匹配或密文被篡改都落在这里；不向前端暴露差异。
            throw new IllegalStateException("审批载荷解密失败", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ApiException(ErrorCode.SYSTEM_UNAVAILABLE, "审批载荷密钥未配置，敏感动作审批暂不可用");
        }
    }
}
