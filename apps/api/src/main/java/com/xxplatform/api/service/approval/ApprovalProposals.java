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

/** Validate complete proposals using the original packaged OpenAPI, bind quoted If-Match, compute canonical digests and generate masked review items. */
public final class ApprovalProposals {

    /**
     * 复核清单里被打码的键名片段（小写匹配，子串命中即打码）。
     *
     * <p>为什么用"包含"而不是"等于"：真实字段名是 {@code newPassword}、{@code accessToken}、
     * {@code privateKey}、{@code smsOtp} 这类组合词，用等值匹配会漏掉绝大部分。
     * 代价是 {@code monkey} 这类无关字段也会被打码——在审批页上"多打一次码"比"漏一个口令"便宜得多。
     */
    private static final List<String> SENSITIVE_KEY_PARTS = List.of(
            "password", "secret", "token", "ciphertext", "private", "credential", "otp", "key");

    /** 打码后的占位值；长度不暴露原值长度。 */
    private static final String MASKED = "***";

    /** 顶层允许的键；{@code ifMatch} 是 M10 新增。 */
    private static final Set<String> ALLOWED_KEYS =
            Set.of("operationId", "requestBody", "pathParameters", "ifMatch");

    /** 复核项上限（M11）：超过就拒，而不是悄悄截断——截断会让审批人以为看到的是全部。 */
    private static final int MAX_REVIEW_ITEMS = 10_000;

    /**
     * 单个展示值上限（M11）：契约 {@code ActionApprovalDetail.reviewItems[].displayValue}
     * 的 maxLength 就是 4096。超过不再"切段拼接"——切段仍是同一份超长内容塞进接口预算，
     * 契约明确要求"超上限拒绝并要求拆分申请"。
     */
    private static final int MAX_DISPLAY_VALUE = 4096;

    /** 全部展示值总字符预算（M11）：10000 项 × 4096 字符约 40MB，接口与前端都撑不住。 */
    private static final int MAX_REVIEW_TOTAL_CHARS = 4_000_000;

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
        com.xxplatform.api.common.OpenApiRequests.validate("ActionApprovalProposal", raw, "proposal");
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
        // api-conventions section 4 distinguishes an administrator reset from /me/password.
        // The shared PasswordChangeInput schema alone does not express that operation-specific ban.
        if (operation == ApprovalOperation.CHANGE_USER_PASSWORD && requestBody.containsKey("currentPassword")) {
            throw invalid("管理员重置密码不接收currentPassword；请提供newPassword和reason");
        }
        if (pathParameters.isEmpty() && requestBody.isEmpty()) {
            throw invalid("proposal 必须包含 requestBody 或 pathParameters");
        }

        Object ifMatch = raw.get("ifMatch");
        if (ifMatch != null && (!(ifMatch instanceof String text) || !text.matches("\"[0-9]+\""))) {
            throw invalid("proposal.ifMatch 必须是双引号包裹的资源版本号");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operation.operationId());
        if (!pathParameters.isEmpty()) {
            payload.put("pathParameters", pathParameters);
        }
        if (!requestBody.isEmpty()) {
            payload.put("requestBody", requestBody);
        }
        if (ifMatch != null) {
            // 参与摘要：否则"批的时候版本是 3、执行的时候版本是 5"会共享同一个指纹。
            payload.put("ifMatch", ifMatch);
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
            collect(items, "path", map);
        }
        Object requestBody = payload.get("requestBody");
        if (requestBody instanceof Map<?, ?> map) {
            collect(items, "request", map);
        }
        if (items.isEmpty()) {
            String operationId = String.valueOf(payload.get("operationId"));
            items.add(new ReviewItem("proposal", "申请内容", operationId));
        }
        requireReviewBudget(items);
        return List.copyOf(items);
    }

    static void requireReviewBudget(List<ReviewItem> items) {
        for (ReviewItem item : items) {
            if (item.displayValue().length() > MAX_DISPLAY_VALUE)
                throw invalid("审批展示值超过4096字符，请拆分申请");
        }
        if (items.size() > MAX_REVIEW_ITEMS) {
            // M11：不截断。截断会让审批人以为"就这些"，而实际被执行的内容可能在被砍掉的部分里。
            // 契约要求"超上限拒绝并要求拆分申请"，所以这里的错误信息要直接告诉调用方怎么做。
            throw invalid("审批展示项超过 " + MAX_REVIEW_ITEMS + " 项上限，请拆分申请后重新提交");
        }
        int totalChars = items.stream().mapToInt(item -> item.displayValue().length()).sum();
        if (totalChars > MAX_REVIEW_TOTAL_CHARS) {
            // 数组/对象之外还有"总预算"：10000 项 × 4096 字符约 40MB，接口和前端都撑不住。
            // 同样是拒绝 + 要求拆分，而不是把内容砍掉一部分继续批。
            throw invalid("审批展示内容超过 " + MAX_REVIEW_TOTAL_CHARS + " 字符预算，请拆分申请后重新提交");
        }
    }

    /**
     * 递归展开一个对象：敏感键打码、嵌套对象继续下钻、数组按下标展开。
     *
     * <p>为什么要递归：敏感值几乎从不放在第一层。真实载荷是
     * {@code {requestBody:{command:{parameters:{password:"..."}}}}} 这种形状，只打码第一层等于没打。
     *
     * @param fieldPrefix 字段码前缀（{@code request}/{@code path}），逐层拼上 key 与下标
     */
    private static void collect(List<ReviewItem> items, String fieldPrefix, Map<?, ?> map) {
        if (map.isEmpty()) {
            addItem(items, fieldPrefix, fieldPrefix, "{}");
            return;
        }
        for (Map.Entry<?, ?> entry : sortedEntries(map)) {
            String key = String.valueOf(entry.getKey());
            String fieldCode = fieldPrefix + "." + key;
            if (isSensitive(key)) {
                // 打码后不再下钻：敏感子结构（例如 privateKey 里的字段）整体不展示。
                addItem(items, fieldCode, key, MASKED);
                continue;
            }
            collectValue(items, fieldCode, key, entry.getValue());
        }
    }

    /** 递归处理单个值；标量/空集合直接成为一项，长值拒绝并要求拆分申请。 */
    private static void collectValue(List<ReviewItem> items, String fieldCode, String label, Object value) {
        if (value instanceof Map<?, ?> nested) {
            collect(items, fieldCode, nested);
            return;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                addItem(items, fieldCode, label, "[]");
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                collectValue(items, fieldCode + "[" + i + "]", label + " " + (i + 1), list.get(i));
            }
            return;
        }
        if (value instanceof String text) {
            // M11：值为"序列化 JSON 字符串"时（例如调用方把整个参数对象再 JSON.stringify 了一次，
            // 或上游把 {password:...} 塞进一个 text 字段），按标量展示会把里面的明文原样漏给审批人，
            // 而按标量递归打码又永远看不到子键。这里尝试解析：能解析成对象/数组就继续递归，
            // 于是 password/secret 这类嵌套键照样被打码；解析失败才当普通文本。
            Object embedded = parseEmbeddedJson(text);
            if (embedded instanceof Map<?, ?> nested) {
                collect(items, fieldCode, nested);
                return;
            }
            if (embedded instanceof List<?> list) {
                if (list.isEmpty()) {
                    addItem(items, fieldCode, label, "[]");
                    return;
                }
                for (int i = 0; i < list.size(); i++) {
                    collectValue(items, fieldCode + "[" + i + "]", label + " " + (i + 1), list.get(i));
                }
                return;
            }
        }
        addItem(items, fieldCode, label, value);
    }

    /**
     * 尝试把一个字符串当作 JSON 对象/数组解析。
     *
     * <p>只接受以 {@code {} 或 {@code [} 开头的字符串，避免把 {@code 18500000000} 这种手机号
     * 或普通文本误判成结构；解析失败返回 {@code null}，由调用方按普通标量处理。
     */
    private static Object parseEmbeddedJson(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return null;
        }
        char first = trimmed.charAt(0);
        if (first != '{' && first != '[') {
            return null;
        }
        try {
            return DISPLAY_MAPPER.readValue(trimmed, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加一项复核项（M11）。
     *
     * <p>为什么不截断也不切段：审批人必须能看到完整内容才能为它负责；截断会让"没看到的尾巴"
     * 跟着批准一起生效，切段则把同一份超长内容继续塞进接口预算。契约要求超上限时
     * 明确拒绝并让调用方拆分申请，所以这里直接 4xx。
     */
    private static void addItem(List<ReviewItem> items, String fieldCode, String label, Object value) {
        String text = display(value);
        if (text.length() > MAX_DISPLAY_VALUE) {
            throw invalid("审批展示值 " + fieldCode + " 超过 " + MAX_DISPLAY_VALUE
                    + " 字符，请拆分申请（例如减少单次设备数或拆开长文本）后重新提交");
        }
        items.add(new ReviewItem(fieldCode, label, text));
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
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return json(value);
        }
        return String.valueOf(value);
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

    /**
     * 服务端从载荷里数"这次涉及多少台设备"（M09）。
     *
     * <p>为什么必须在服务端数，而不是信调用方传来的票数：{@code required_approvals} 决定
     * 要几个人批，如果由客户端决定，它只要声称"1 台设备"就能把双人审批降成单人。
     *
     * <p>三个来源按顺序取：{@code deviceIds}（下发/修订/批量设备命令）、
     * {@code failedDeviceIds}（重试失败设备）、{@code path.deviceIds}（老形状）。
     * 都取不到时按 0 处理——宁可少算成 1 票，也不要把"没写清楚"当成"批量"。
     */
    private static int batchSize(ApprovalOperation operation,
                                 Map<String, Object> requestBody,
                                 Map<String, Object> pathParameters) {
        if (!operation.name().contains("DEVICE")) {
            return 0;
        }
        int size = listSize(requestBody.get("deviceIds"));
        if (size < 0) {
            size = listSize(requestBody.get("failedDeviceIds"));
        }
        if (size < 0) {
            size = listSize(pathParameters.get("deviceIds"));
        }
        return Math.max(size, 0);
    }

    /** 列表元素个数；不是列表时返回 -1（表示"这个来源没给"）。 */
    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : -1;
    }

    private static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

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

    /**
     * 供测试核对"枚举与请求形状表没有漂移"。
     *
     * <p>两张表分开维护是刻意的：{@link ApprovalOperation} 管权限与票数，本类管字段白名单。
     * 漂移的后果是某个操作在审批层永远解析失败（新增操作忘了加 spec），所以用一条单测钉住。
     */
    static Set<String> supportedOperationIds() {
        return com.xxplatform.api.common.OpenApiRequests.approvalOperations();
    }

    /**
     * 30 个操作的请求形状。
     *
     * @param pathParameters 路径模板变量集合；无路径参数的操作是空集合
     * @param requestBodyKeys 请求体顶层键集合；无可选/必填 body 的操作是空集合
     */
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
