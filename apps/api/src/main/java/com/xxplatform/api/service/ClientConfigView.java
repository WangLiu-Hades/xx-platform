package com.xxplatform.api.service;

import java.util.List;

/**
 * {@code GET /client-config} 的响应 {@code data}（任务 7）。
 *
 * <p>12 个字段全部 required（契约 {@code ClientConfig} 的 {@code required} 列表），所以
 * record 的组件顺序按契约书写顺序排，方便与 {@code openapi.yaml} 逐行对照。
 *
 * <p><b>关于 null</b>：只有 {@link FeatureView#pendingReason()} 允许为 null（契约里是
 * {@code nullable: true} 但仍 required，即"键必须在、值可以为 null"）。这里**不加**
 * {@code @JsonInclude(NON_NULL)}——加了会让"已启用"的能力直接少一个键，
 * 违反 {@code additionalProperties: false} + required 的组合约束。
 */
public record ClientConfigView(
        String version,
        String mapCoordinateSystem,
        int taskPollIntervalSeconds,
        long maxUploadBytes,
        List<FeatureAvailabilityItem> featureAvailability,
        List<UploadPolicyView> uploadPolicies,
        String institutionTimeZone,
        int contextTtlSeconds,
        int maxOnlineQuerySeconds,
        int maxOnlineWindowDays,
        List<FeatureView> features,
        List<CatalogBindingView> catalogBindings) {

    /**
     * 已签认的能力开关（契约 {@code FeatureAvailability}）。
     *
     * @param featureCode   契约 12 值枚举之一
     * @param enabled       是否可用；{@code false} 时前端应隐藏入口而不是点了才报错
     * @param pendingReason 未启用的原因，来自 {@code core.feature_config.pending_reason}；
     *                      允许 null（能力已启用时通常为 null），但**必须出现在响应里**
     */
    public record FeatureView(String featureCode, boolean enabled, String pendingReason) {
    }

    /**
     * 旧兼容别名（契约 {@code ClientConfigFeatureAvailabilityItem}，标了 deprecated）。
     *
     * <p>只做 {@code features} 的投影：同序、同长、只取 {@code code} 与 {@code enabled}。
     * 契约明文禁止"单独计算或覆写"，所以这里不引入任何额外判断——一旦它和
     * {@code features} 出现分歧，前端两个开关会互相矛盾。
     */
    public record FeatureAvailabilityItem(String code, boolean enabled) {
    }

    /**
     * 分类上传限制（契约 {@code UploadPolicy}）。
     *
     * @param maxBytes 该分类的上限；前端实际可用值是 {@code min(全局 maxUploadBytes, 本值)}
     */
    public record UploadPolicyView(
            String category,
            List<String> mimeTypes,
            List<String> extensions,
            long maxBytes,
            boolean enabled) {
    }

    /**
     * 字段到字典的绑定（契约 {@code CatalogBinding}）。
     *
     * <p>前端据此知道"设备分类""人员库分类""性别/民族"该读哪个字典，
     * 而不是猜目录名。
     */
    public record CatalogBindingView(String fieldKey, String dictionaryCode, String label) {
    }
}
