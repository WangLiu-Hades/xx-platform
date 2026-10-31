package com.xxplatform.api.service.approval;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.xxplatform.api.common.ApiException;
import com.xxplatform.api.common.ApiResponses;
import com.xxplatform.api.common.ErrorCode;
import com.xxplatform.api.common.Sha256;
import com.xxplatform.api.service.task.TaskParameters;

/**
 * {@code ActionApprovalProposal} 的结构校验与指纹计算。
 *
 * <p>契约把 30 个提案做成 {@code oneOf}（discriminator=operationId），共同形状只有三种键：
 * {@code operationId}、{@code requestBody}、{@code pathParameters}。这里做三件事：
 *
 * <ol>
 *   <li>拒绝契约之外的操作与多余的键——审批是安全边界，"顺手多带一个字段"不能被静默忽略；</li>
 *   <li>对规范化 JSON 求 SHA-256 作为 {@code requestDigest}：审批绑定的是**这次请求的完整
 *       内容**，执行时要用同一摘要比对，防止"批的是 A、执行的是 B"；</li>
 *   <li>生成复核清单 {@code reviewItems} 与摘要文本。清单里对 {@code password/secret/token/key}
 *       一类键统一打码——审批人要看清"批什么"，但不能借审批页读走口令。</li>
 * </ol>
 *
 * <p>规范化必须稳定：{@link TaskParameters#canonicalJson} 递归排序对象键，同一份语义内容
 * 无论字段顺序如何都得到同一摘要。数组顺序不排序（顺序可能本身有语义，例如批量设备顺序）。
 */
public final class ApprovalProposals {

    /** 复核清单里被打码的键名片段。 */
    private static final Set<String> SENSITIVE_KEY_PARTS =
            Set.of("password", "secret", "token", "ciphertext", "privatekey", "credential");

    private static final Set<String> ALLOWED_KEYS = Set.of("operationId", "requestBody", "pathParameters");
    private static final int MAX_REVIEW_ITEMS = 10_000;
    private static final int MAX_DISPLAY_VALUE = 4096;
    private static final int MAX_SUMMARY = 2000;
    private static final com.fasterxml.jackson.databind.ObjectMapper DISPLAY_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private ApprovalProposals() {
    }

    /**
     * 解析并校验提案。
     *
     * @param raw 请求体里的 {@code proposal} 对象
     * @return 冻结后的提案视图（含摘要、摘要文本与复核项）
     * @throws ApiException 400 {@code VALIDATION_ERROR}，{@code details[0].field="proposal"}
     */
    public static Proposal parse(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw invalid("proposal 不能为空");
        }
        for (String key : raw.keySet()) {
            if (!ALLOWED_KEYS.contains(key)) {
                throw invalid("proposal 不支持字段 " + key);
            }
        }
        Object operationValue = raw.get("operationId");
        if (!(operationValue instanceof String operationId)) {
            throw invalid("proposal.operationId 必填");
        }
        ApprovalOperation operation = ApprovalOperation.parse(operationId)
                .orElseThrow(() -> invalid("未登记的操作 " + operationId));

        Map<String, Object> pathParameters = asObject(raw.get("pathParameters"), "pathParameters");
        Map<String, Object> requestBody = asObject(raw.get("requestBody"), "requestBody");
        if (pathParameters.isEmpty() && requestBody.isEmpty()) {
            throw invalid("proposal 必须包含 requestBody 或 pathParameters");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operation.operationId());
        if (!pathParameters.isEmpty()) {
            payload.put("pathParameters", pathParameters);
        }
        if (!requestBody.isEmpty()) {
            payload.put("requestBody", requestBody);
        }
        String canonicalJson = TaskParameters.canonicalJson(payload);
        String digest = Sha256.hex(Sha256.ofBytes(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        int batchSize = batchSize(operation, requestBody, pathParameters);
        List<ReviewItem> reviewItems = reviewItems(payload);
        return new Proposal(
                operation,
                Collections.unmodifiableMap(pathParameters),
                Collections.unmodifiableMap(requestBody),
                canonicalJson,
                digest,
                summary(operation, requestBody, pathParameters),
                reviewItems,
                operation.requiredApprovals(batchSize));
    }

    /** 从已解封的规范化 JSON 还原复核项（读取审批时不需要重新走校验）。 */
    public static List<ReviewItem> reviewItems(String canonicalJson) {
        return reviewItems(JsonMaps.parse(canonicalJson));
    }

    private static List<ReviewItem> reviewItems(Map<String, Object> payload) {
        List<ReviewItem> items = new ArrayList<>();
        Object pathParameters = payload.get("pathParameters");
        if (pathParameters instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : sortedEntries(map)) {
                addItem(items, "path." + entry.getKey(), String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        Object requestBody = payload.get("requestBody");
        if (requestBody instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : sortedEntries(map)) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                if (isSensitive(key)) {
                    items.add(new ReviewItem("request." + key, key, "***"));
                    continue;
                }
                if (value instanceof List<?> list && "deviceIds".equals(key)) {
                    for (int i = 0; i < list.size(); i++) {
                        addItem(items, "request.deviceIds[" + i + "]", "设备 " + (i + 1), list.get(i));
                    }
                    continue;
                }
                addItem(items, "request." + key, key, value);
            }
        }
        if (items.isEmpty()) {
            String operationId = String.valueOf(payload.get("operationId"));
            items.add(new ReviewItem("proposal", "申请内容", operationId));
        }
        if (items.size() > MAX_REVIEW_ITEMS) {
            List<ReviewItem> capped = new ArrayList<>(items.subList(0, MAX_REVIEW_ITEMS - 1));
            capped.add(new ReviewItem("proposal.truncated", "其余内容", "仅展示前 " + (MAX_REVIEW_ITEMS - 1) + " 项"));
            return List.copyOf(capped);
        }
        return List.copyOf(items);
    }

    private static void addItem(List<ReviewItem> items, String fieldCode, String label, Object value) {
        items.add(new ReviewItem(fieldCode, label, display(value)));
    }

    /**
     * 按键名排序后再生成复核项。
     *
     * <p>载荷来自 Jackson 反序列化，HashMap 的迭代顺序取决于哈希，同一份申请可能每次渲染出不同的
     * 字段顺序。审批页要让人"两次看到同一屏"，因此这里显式排序；顺序与 {@code canonicalJson}
     * 的按键排序一致，重建结果也能与原始清单逐项对齐。
     */
    private static List<Map.Entry<?, ?>> sortedEntries(Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        return entries;
    }

    private static String display(Object value) {
        String text;
        if (value == null) {
            text = "";
        } else if (value instanceof Map<?, ?> || value instanceof List<?>) {
            text = json(value);
        } else {
            text = String.valueOf(value);
        }
        return text.length() <= MAX_DISPLAY_VALUE ? text : text.substring(0, MAX_DISPLAY_VALUE);
    }

    /** 复核清单里的结构化值用紧凑 JSON 展示；失败就退回 {@code toString}，不因为展示问题失败。 */
    private static String json(Object value) {
        try {
            return DISPLAY_MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static String summary(ApprovalOperation operation,
                                  Map<String, Object> requestBody,
                                  Map<String, Object> pathParameters) {
        List<String> fields = new ArrayList<>();
        pathParameters.keySet().forEach(key -> fields.add("path." + key));
        requestBody.keySet().forEach(key -> fields.add("request." + key));
        String text = operation.label() + "（" + operation.operationId() + "），涉及字段："
                + String.join("、", fields);
        return text.length() <= MAX_SUMMARY ? text : text.substring(0, MAX_SUMMARY);
    }

    private static int batchSize(ApprovalOperation operation,
                                 Map<String, Object> requestBody,
                                 Map<String, Object> pathParameters) {
        if (!operation.name().contains("DEVICE")) {
            return 0;
        }
        Object ids = requestBody.get("deviceIds");
        if (ids == null) {
            ids = pathParameters.get("deviceIds");
        }
        if (ids instanceof List<?> list) {
            return list.size();
        }
        return ids == null ? 0 : 1;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY_PARTS.stream().anyMatch(lower::contains);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String field) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid("proposal." + field + " 必须是对象");
        }
        return (Map<String, Object>) map;
    }

    private static ApiException invalid(String reason) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, reason,
                List.of(new ApiResponses.FieldErrorDetail("proposal", reason)), null);
    }

    /** 复核项。 */
    public record ReviewItem(String fieldCode, String label, String displayValue) {
    }

    /** 冻结后的提案。 */
    public record Proposal(ApprovalOperation operation,
                           Map<String, Object> pathParameters,
                           Map<String, Object> requestBody,
                           String canonicalJson,
                           String digestHex,
                           String summary,
                           List<ReviewItem> reviewItems,
                           int requiredApprovals) {
    }

    /** 把 Jackson 给出的通用 Map/List 收敛为 {@code Map<String,Object>}，供规范化使用。 */
    static final class JsonMaps {

        private JsonMaps() {
        }

        static Map<String, Object> parse(String json) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("审批载荷不是合法 JSON", e);
            }
        }
    }
}
