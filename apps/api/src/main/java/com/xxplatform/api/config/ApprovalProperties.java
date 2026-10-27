package com.xxplatform.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感动作审批的可调参数（{@code xx.approval.*}）。
 *
 * <p>时间口径来自 {@code docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md}
 * （PD-2026-10-19.v1，暂定）：申请 24 小时过期；凑齐批准后给 10 分钟执行许可，
 * 解密类 5 分钟；许可截止不超过申请本身的 {@code expiresAt}。这些是**策略**而不是
 * 代码常量，甲方确认实际审批人/时限后只改配置，不用改服务。
 *
 * <p>{@code payloadKey} 是冻结快照的 AES-256 密钥（base64，32 字节）。库里的
 * {@code frozen_payload_ciphertext} 是 NOT NULL，所以没有密钥时审批**不可用**（503），
 * 而不是退化成明文落库。本地 {@code db} profile 在 {@code application-db.yml}
 * 写了一个仅限开发的默认值，生产必须由部署 secret 注入。
 */
@ConfigurationProperties(prefix = "xx.approval")
public class ApprovalProperties {

    /** AES-256 密钥，base64 编码的 32 字节；空串表示未配置，审批创建/读取返回 503。 */
    private String payloadKey = "";

    /** 密钥标识，落库到 {@code encryption_key_id}，用于将来轮换时区分历史密文。 */
    private String keyId = "local-dev-v1";

    /** 申请有效期（24h）。 */
    private Duration ttl = Duration.ofHours(24);

    /** 普通审批通过后的执行许可时长（10 分钟）。 */
    private Duration grantTtl = Duration.ofMinutes(10);

    /** 解密类审批通过后的执行许可时长（5 分钟）。 */
    private Duration decryptionGrantTtl = Duration.ofMinutes(5);

    /** 协议版本号，落库到 {@code policy_version}。 */
    private String policyVersion = "PD-2026-10-19.v1";

    /** 列表默认页大小。 */
    private int pageSizeDefault = 20;

    /** 列表页大小上限。 */
    private int pageSizeMax = 100;

    public String getPayloadKey() {
        return payloadKey;
    }

    public void setPayloadKey(String payloadKey) {
        this.payloadKey = payloadKey == null ? "" : payloadKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getGrantTtl() {
        return grantTtl;
    }

    public void setGrantTtl(Duration grantTtl) {
        this.grantTtl = grantTtl;
    }

    public Duration getDecryptionGrantTtl() {
        return decryptionGrantTtl;
    }

    public void setDecryptionGrantTtl(Duration decryptionGrantTtl) {
        this.decryptionGrantTtl = decryptionGrantTtl;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public int getPageSizeDefault() {
        return pageSizeDefault;
    }

    public void setPageSizeDefault(int pageSizeDefault) {
        this.pageSizeDefault = pageSizeDefault;
    }

    public int getPageSizeMax() {
        return pageSizeMax;
    }

    public void setPageSizeMax(int pageSizeMax) {
        this.pageSizeMax = pageSizeMax;
    }
}
