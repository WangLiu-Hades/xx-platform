package com.xxplatform.api.service.approval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 审批冻结快照的信封格式（M10）。
 *
 * <p>V3 迁移的 {@code frozen_payload_ciphertext} 只有一列密文，而契约要求服务端冻结
 * "目标、资源版本、授权版本、策略版本、解析后的设备集合"。本轮**不能加列**：
 * {@code MigrationBaselineTests} 把迁移拼起来与 {@code contracts/database/schema.sql}
 * 做结构全等比较，加列会直接打红（而 schema.sql 属于禁止修改的 contracts）。
 *
 * <p>因此把快照包成两层：{@code request} 是客户端提案（摘要绑定它），{@code frozen}
 * 是服务端解析出来的事实（设备集合、资源版本、authz/策略版本、范围）。两层一起进
 * AES-GCM 密文，任何篡改都会解密失败；摘要仍然只覆盖 {@code request}，这样
 * {@code ExportService} 侧的执行请求可以按同一摘要重建比对。
 */
public final class ApprovalEnvelope {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApprovalEnvelope() {
    }

    /** 解析后的信封：客户端请求载荷 + 服务端冻结事实。 */
    public record Envelope(Map<String, Object> request, Map<String, Object> frozen) {

        public Envelope {
            request = request == null ? Map.of() : Map.copyOf(request);
            frozen = frozen == null ? Map.of() : Map.copyOf(frozen);
        }
    }

    /** 编码成落库前的明文（随后立即被 {@link ApprovalPayloadCipher#seal} 加密）。 */
    public static String encode(String canonicalRequestJson, Map<String, Object> frozen) {
        try {
            Map<String, Object> request = MAPPER.readValue(canonicalRequestJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("request", request);
            root.put("frozen", frozen == null ? Map.of() : frozen);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("审批快照编码失败", e);
        }
    }

    /**
     * 解码。兼容早期只存了裸 {@code canonicalJson} 的历史行（没有 {@code request} 键时
     * 整个对象就是请求载荷，冻结事实为空），避免旧数据在升级后读不出来。
     */
    public static Envelope decode(String plaintext) {
        try {
            Map<String, Object> root = MAPPER.readValue(plaintext,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });
            Object request = root.get("request");
            if (request instanceof Map<?, ?> requestMap) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                requestMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                Object frozen = root.get("frozen");
                Map<String, Object> frozenMap = new LinkedHashMap<>();
                if (frozen instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> frozenMap.put(String.valueOf(key), value));
                }
                return new Envelope(normalized, frozenMap);
            }
            return new Envelope(root, Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("审批快照格式非法", e);
        }
    }

    /**
     * 生成复核清单：客户端请求字段在前，服务端冻结事实在后（审批人必须看到真实目标集合
     * 与版本，而不是调用方自己声称的数量）。
     */
    public static List<ApprovalProposals.ReviewItem> reviewItems(String plaintext) {
        Envelope envelope = decode(plaintext);
        Map<String, Object> display = MAPPER.convertValue(envelope.request(), new TypeReference<LinkedHashMap<String, Object>>() { });
        Map<String, Object> selectionFacts = com.xxplatform.api.service.CurrentDataScope.object(envelope.frozen().get("selection"));
        List<String> sensitive = com.xxplatform.api.service.CurrentDataScope.strings(selectionFacts.get("sensitiveParameterCodes"));
        maskParameterValues(display, sensitive);
        List<ApprovalProposals.ReviewItem> items = new ArrayList<>(ApprovalProposals.reviewItems(canonical(display)));
        appendFrozen(items, envelope.frozen());
        Object selection = envelope.frozen().get("selection");
        if (selection instanceof Map<?, ?> map && map.get("contextFilter") != null) {
            items.addAll(ApprovalProposals.reviewItems(canonical(Map.of("requestBody",
                    Map.of("resolvedContextFilter", map.get("contextFilter"))))));
        }
        ApprovalProposals.requireReviewBudget(items);
        return items;
    }

    private static void maskParameterValues(Object value, List<String> sensitive) {
        if (value instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) raw;
            if (sensitive.contains(String.valueOf(map.get("parameterCode")))) {
                for (String key : List.of("textValue", "integerValue", "numberValue", "booleanValue"))
                    if (map.containsKey(key)) map.put(key, "***");
            }
            for (Object child : map.values()) maskParameterValues(child, sensitive);
        } else if (value instanceof List<?> list) for (Object child : list) maskParameterValues(child, sensitive);
    }

    private static void appendFrozen(List<ApprovalProposals.ReviewItem> items,
                                     Map<String, Object> frozen) {
        if (frozen.isEmpty()) {
            return;
        }
        Object deviceIds = frozen.get("deviceIds");
        if (deviceIds instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                items.add(new ApprovalProposals.ReviewItem("frozen.deviceIds[" + i + "]",
                        "服务端解析的设备 " + (i + 1), String.valueOf(list.get(i))));
            }
            items.add(new ApprovalProposals.ReviewItem("frozen.deviceCount", "设备数量（服务端解析）",
                    String.valueOf(list.size())));
        }
        Object versions = frozen.get("versions");
        if (versions instanceof Map<?, ?> map) {
            map.forEach((key, value) -> items.add(new ApprovalProposals.ReviewItem(
                    "frozen.versions." + key, "冻结版本 " + key, String.valueOf(value))));
        }
        Object scopeVersion = frozen.get("scopeVersion");
        if (scopeVersion != null) {
            items.add(new ApprovalProposals.ReviewItem("frozen.scopeVersion", "授权版本",
                    String.valueOf(scopeVersion)));
        }
        Object policyVersion = frozen.get("policyVersion");
        if (policyVersion != null) {
            items.add(new ApprovalProposals.ReviewItem("frozen.policyVersion", "审批策略版本",
                    String.valueOf(policyVersion)));
        }
        Object dataScope = frozen.get("dataScope");
        if (dataScope != null) {
            items.add(new ApprovalProposals.ReviewItem("frozen.dataScope", "冻结的数据范围",
                    String.valueOf(dataScope)));
        }
    }

    private static String canonical(Map<String, Object> payload) {
        try {
            return com.xxplatform.api.service.task.TaskParameters.canonicalJson(payload);
        } catch (Exception e) {
            throw new IllegalStateException("审批快照规范化失败", e);
        }
    }
}
