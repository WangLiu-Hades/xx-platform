package com.xxplatform.api.config;

import java.time.Duration;
import java.util.List;

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
 * 而不是退化成明文落库。仓库里不保存任何默认密钥：{@code application-db.yml} 只从
 * 环境变量 {@code XX_APPROVAL_PAYLOAD_KEY} 取值，未注入时审批整体 fail closed
 * （见 {@code config/ApprovalCryptoConfigurationTests}）。
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

    /**
     * 审批人白名单（登录名）。空列表 = 按权限判定：持有该操作权限码的可用账号都是候选审批人。
     *
     * <p><b>为什么需要这个开关</b>：协议只说"两名独立审批人"，没说是谁。真实部署里审批权
     * 往往收窄到少数几个人（值班主管），而 RBAC 权限码是给"能调用这个接口的人"用的，两者
     * 不是一回事。用配置表达部署口径，比在数据库里再造一张审批人表更轻，也不用改代码。
     *
     * <p><b>为什么要"白名单人数不足就直接拒"</b>：如果一个申请按规则需要 2 票，而部署里
     * 只有 1 个合格审批人，那么这个申请会一直卡在 PENDING 直到 24h 过期——申请人以为
     * 有人会批，审批人也以为不关自己的事。在创建时就 400「审批人不足」能把问题前移到
     * 最显眼的位置。
     */
    private List<String> approverLogins = List.of();

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

    public List<String> getApproverLogins() {
        return approverLogins;
    }

    public void setApproverLogins(List<String> approverLogins) {
        this.approverLogins = approverLogins == null ? List.of() : List.copyOf(approverLogins);
    }
}
